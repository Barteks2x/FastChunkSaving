package io.github.barteks2x.fastchunksaving.mixin;

import io.github.barteks2x.fastchunksaving.BatchingRegionFile;
import io.github.barteks2x.fastchunksaving.BatchingRegionFileStorage;
import io.github.barteks2x.fastchunksaving.ChunkDataBuffer;
import io.github.barteks2x.fastchunksaving.PendingChunkData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

@Mixin(RegionFileStorage.class)
public abstract class MixinRegionFileStorage implements BatchingRegionFileStorage {

    @Shadow protected abstract RegionFile getRegionFile(ChunkPos p_63712_) throws IOException;

    protected void write(ChunkPos p_63709_, @Nullable CompoundTag p_63710_) throws IOException {
        RegionFile regionfile = this.getRegionFile(p_63709_);
        if (p_63710_ == null) {
            regionfile.clear(p_63709_);
        } else {
            try (DataOutputStream dataoutputstream = regionfile.getChunkDataOutputStream(p_63709_)) {
                NbtIo.write(p_63710_, dataoutputstream);
            }
        }
    }

    @Override public Map<ChunkPos, Optional<Exception>> fastchunksaving_writeBatch(Map<ChunkPos, IOWorker.PendingStore> batch) {

        Map<ChunkPos, Optional<Exception>> results = new HashMap<>();
        Map<RegionFile, List<PendingChunkData>> groups = new HashMap<>();

        for (Map.Entry<ChunkPos, IOWorker.PendingStore> e : batch.entrySet()) {
            try {
                RegionFile regionFile = this.getRegionFile(e.getKey());
                IOWorker.PendingStore pendingStore = e.getValue();
                ByteBuffer data = fastchunksaving_storeNbtToByteBuffer(regionFile, pendingStore);
                PendingChunkData value = new PendingChunkData(e.getKey(), data);
                groups.computeIfAbsent(regionFile, k -> new ArrayList<>()).add(value);
            } catch (Exception ex) {
                results.put(e.getKey(), Optional.of(ex));
            }
        }

        for (Map.Entry<RegionFile, List<PendingChunkData>> entry : groups.entrySet()) {
            RegionFile region = entry.getKey();
            Map<ChunkPos, ByteBuffer> toWrite = new HashMap<>();
            // streams don't work if data is null
            for (PendingChunkData pendingChunkData : entry.getValue()) {
                toWrite.put(pendingChunkData.pos(), pendingChunkData.data());
            }
            Map<ChunkPos, Optional<Exception>> groupResults = ((BatchingRegionFile) region).fastchunksaving_writeBatched(toWrite);
            results.putAll(groupResults);
        }
        return results;
    }

    @Unique private ByteBuffer fastchunksaving_storeNbtToByteBuffer(RegionFile regionFile, IOWorker.PendingStore pendingStore) throws IOException {
        if (((IOWorkerPendingStoreAccess) pendingStore).getData() == null) {
            return null;
        }
        ChunkDataBuffer output = new ChunkDataBuffer(((RegionFileAccess) regionFile).getVersion());
        try (DataOutputStream dataoutputstream = new DataOutputStream(((RegionFileAccess) regionFile).getVersion().wrap(output))) {
            NbtIo.write(((IOWorkerPendingStoreAccess) pendingStore).getData(), dataoutputstream);
        }
        return output.getOutputBuffer();
    }

}
