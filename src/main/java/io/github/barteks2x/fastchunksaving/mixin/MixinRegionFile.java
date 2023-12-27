package io.github.barteks2x.fastchunksaving.mixin;

import io.github.barteks2x.fastchunksaving.BatchingRegionFile;
import io.github.barteks2x.fastchunksaving.mixinconfig.NotSynchronized;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionBitmap;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nullable;

@Mixin(value = RegionFile.class, priority = 0)
public abstract class MixinRegionFile implements BatchingRegionFile {

    @Shadow protected abstract int getOffset(ChunkPos p_63687_);

    @Shadow protected static int getSectorNumber(int p_63672_) {
        return 0;
    }

    @Shadow protected static int getNumSectors(int p_63641_) {
        return 0;
    }

    @Shadow @Final private FileChannel file;
    @Shadow @Final private static Logger LOGGER;

    @Shadow protected static boolean isExternalStreamChunk(byte p_63639_) {
        return false;
    }

    @Shadow @Nullable protected abstract DataInputStream createExternalChunkInputStream(ChunkPos p_63648_, byte p_63649_) throws IOException;

    @Shadow protected static byte getExternalChunkVersion(byte p_63670_) {
        return 0;
    }

    @Shadow @Nullable protected abstract DataInputStream createChunkInputStream(ChunkPos p_63651_, byte p_63652_, InputStream p_63653_)
            throws IOException;

    @Shadow protected static ByteArrayInputStream createStream(ByteBuffer p_63660_, int p_63661_) {
        return null;
    }

    @Shadow protected static int getOffsetIndex(ChunkPos p_63689_) {
        return 0;
    }

    @Shadow @Final private IntBuffer offsets;

    @Shadow protected static int sizeToSectors(int p_63677_) {
        return 0;
    }

    @Shadow protected abstract Path getExternalChunkPath(ChunkPos p_63685_);

    @Shadow @Final protected RegionBitmap usedSectors;

    @Shadow protected abstract RegionFile.CommitOp writeToExternalFile(Path p_63663_, ByteBuffer p_63664_) throws IOException;

    @Shadow protected abstract ByteBuffer createExternalStub();

    @Shadow protected abstract int packSectorOffset(int p_63643_, int p_63644_);

    @Shadow private static int getTimestamp() {
        return 0;
    }

    @Shadow @Final private IntBuffer timestamps;

    @Shadow protected abstract void writeHeader() throws IOException;

    @Unique private final ReadWriteLock fastchunksaving_dataLock = new ReentrantReadWriteLock();

    @Redirect(method = "<init>(Ljava/nio/file/Path;Ljava/nio/file/Path;Lnet/minecraft/world/level/chunk/storage/RegionFileVersion;Z)V",
            at = @At(value = "FIELD", target = "Ljava/nio/file/StandardOpenOption;DSYNC:Ljava/nio/file/StandardOpenOption;"))
    private StandardOpenOption noDsync() {
        return StandardOpenOption.WRITE; // can we just do duplicates?
    }

    /**
     * @author Barteks2x
     * @reason explicit locks
     */
    @Nullable @Overwrite @NotSynchronized public synchronized DataInputStream getChunkDataInputStream(ChunkPos pos) throws IOException {
        Lock mainLock = fastchunksaving_dataLock.readLock();
        mainLock.lock();
        try {
            int i = this.getOffset(pos);
            if (i == 0) {
                return null;
            }
            int j = getSectorNumber(i);
            int k = getNumSectors(i);
            int l = k * 4096;
            ByteBuffer bytebuffer = ByteBuffer.allocate(l);

            this.file.read(bytebuffer, j * 4096L);
            bytebuffer.flip();
            if (bytebuffer.remaining() < 5) {
                LOGGER.error("Chunk {} header is truncated: expected {} but read {}", pos, l, bytebuffer.remaining());
                return null;
            }
            int i1 = bytebuffer.getInt();
            byte b0 = bytebuffer.get();
            if (i1 == 0) {
                LOGGER.warn("Chunk {} is allocated, but stream is missing", pos);
                return null;
            }
            int j1 = i1 - 1;
            if (isExternalStreamChunk(b0)) {
                if (j1 != 0) {
                    LOGGER.warn("Chunk has both internal and external streams");
                }
                return this.createExternalChunkInputStream(pos, getExternalChunkVersion(b0));
            } else if (j1 > bytebuffer.remaining()) {
                LOGGER.error("Chunk {} stream is truncated: expected {} but read {}", pos, j1, bytebuffer.remaining());
                return null;
            } else if (j1 < 0) {
                LOGGER.error("Declared size {} of chunk {} is negative", i1, pos);
                return null;
            } else {
                return this.createChunkInputStream(pos, b0, createStream(bytebuffer, j1));
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * @author Barteks2x
     * @reason explicit locks, redirect to batched write
     */
    @Overwrite @NotSynchronized protected synchronized void write(ChunkPos pos, ByteBuffer buffer) throws IOException {
        LOGGER.warn("Using slow non-batch write at chunk pos {}", pos);
        this.fastchunksaving_writeBatched(Collections.singletonMap(pos, buffer));
    }

    @Override public Map<ChunkPos, Optional<Exception>> fastchunksaving_writeBatched(Map<ChunkPos, ByteBuffer> entries) {
        if (entries.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<ChunkPos, Optional<Exception>> exceptions = new HashMap<>();

        IntArrayList sectorsToFree = new IntArrayList(entries.size());
        List<RegionFile.CommitOp> commitOps = new ArrayList<>(entries.size());

        Lock mainLock = fastchunksaving_dataLock.writeLock();
        mainLock.lock();
        try {
            // reserve all the sectors and write chunk data to new locations
            // this doesn't overwrite any existing data and doesn't touch headers on disk, only in memory
            for (Map.Entry<ChunkPos, ByteBuffer> entry : entries.entrySet()) {
                ChunkPos pos = entry.getKey();
                ByteBuffer buf = entry.getValue();
                try {
                    int offsetIndex = getOffsetIndex(pos);
                    int location = this.offsets.get(offsetIndex);
                    sectorsToFree.add(location);

                    RegionFile.CommitOp removeOldApplyNewExt;

                    int newLocation;
                    int dataSize = buf == null ? 0 : buf.remaining();
                    int sectorCount = sizeToSectors(dataSize);

                    if (sectorCount >= 256) {
                        assert buf != null;
                        Path path = this.getExternalChunkPath(pos);
                        LOGGER.warn("Saving oversized chunk {} ({} bytes} to external file {}", pos, dataSize, path);
                        sectorCount = 1;
                        newLocation = this.usedSectors.allocate(sectorCount);
                        removeOldApplyNewExt = this.writeToExternalFile(path, buf);
                        ByteBuffer bytebuffer = this.createExternalStub();
                        this.file.write(bytebuffer, newLocation * 4096L);
                    } else {
                        newLocation = buf == null ? 0 : this.usedSectors.allocate(sectorCount);
                        removeOldApplyNewExt = () -> Files.deleteIfExists(this.getExternalChunkPath(pos));
                        if (buf != null) {
                            this.file.write(buf, newLocation * 4096L);
                        }
                    }
                    commitOps.add(removeOldApplyNewExt);
                    this.offsets.put(offsetIndex, this.packSectorOffset(newLocation, sectorCount));
                    this.timestamps.put(offsetIndex, getTimestamp());
                    exceptions.put(pos, Optional.empty());
                } catch (Exception ex) {
                    exceptions.put(pos, Optional.of(ex));
                }
            }
            try {
                // sync to disk
                this.file.force(true);

                // now actually write header to disk to point to the previously written&synced data, and sync again
                this.writeHeader();
                this.file.force(true);
                // also clean up old external chunks, and atomic move temp files with new data to replace old
                for (RegionFile.CommitOp commitOp : commitOps) {
                    commitOp.run();
                }
                // free now actually unused sectors
                for (int i = 0; i < sectorsToFree.size(); i++) {
                    int location = sectorsToFree.getInt(i);
                    int sectorIdx = getSectorNumber(location);
                    int numSector = getNumSectors(location);
                    if (sectorIdx != 0) {
                        this.usedSectors.free(sectorIdx, numSector);
                    }
                }
            } catch (Exception ex) {
                // TODO: merge exceptions?
                for (ChunkPos chunkPos : entries.keySet()) {
                    exceptions.computeIfAbsent(chunkPos, k -> Optional.of(ex));
                }
            }
            return exceptions;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * @author Barteks2x
     * @reason make it actually safe
     */
    @Overwrite
    public void clear(ChunkPos p_156614_) throws IOException {
        Lock mainLock = fastchunksaving_dataLock.writeLock();
        mainLock.lock();
        try {
            int i = getOffsetIndex(p_156614_);
            int j = this.offsets.get(i);
            if (j != 0) {
                this.offsets.put(i, 0);
                this.timestamps.put(i, getTimestamp());
                this.writeHeader();
                Files.deleteIfExists(this.getExternalChunkPath(p_156614_));
                this.usedSectors.free(getSectorNumber(j), getNumSectors(j));
            }
        } finally {
            mainLock.unlock();
        }
    }
}
