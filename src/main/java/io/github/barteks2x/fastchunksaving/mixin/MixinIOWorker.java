package io.github.barteks2x.fastchunksaving.mixin;

import io.github.barteks2x.fastchunksaving.BatchingRegionFileStorage;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(IOWorker.class)
public abstract class MixinIOWorker {

    @Shadow @Final private Map<ChunkPos, IOWorker.PendingStore> pendingWrites;

    @Shadow protected abstract void tellStorePending();

    @Shadow @Final private static Logger LOGGER;

    @Shadow @Final private RegionFileStorage storage;

    /**
     * @author Barteks2x
     * @reason batched writes
     */
    @Overwrite
    private void storePendingChunk() {
        if (this.pendingWrites.isEmpty()) {
            return;
        }
        Map<ChunkPos, IOWorker.PendingStore> batchToWrite = new Object2ObjectOpenHashMap<>();

        Iterator<Map.Entry<ChunkPos, IOWorker.PendingStore>> iterator = this.pendingWrites.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ChunkPos, IOWorker.PendingStore> entry = iterator.next();
            iterator.remove();
            batchToWrite.put(entry.getKey(), entry.getValue());
        }

        this.fastchunksaving_runBatchedStore(batchToWrite);
        this.tellStorePending();
    }

    @Unique private void fastchunksaving_runBatchedStore(Map<ChunkPos, IOWorker.PendingStore> batch) {
        Map<ChunkPos, Optional<Exception>> results = ((BatchingRegionFileStorage) (Object) this.storage).fastchunksaving_writeBatch(batch);
        results.forEach((pos, exception) -> {
            IOWorker.PendingStore store = batch.get(pos);
            CompletableFuture<Void> result = ((IOWorkerPendingStoreAccess) store).getResult();
            exception.ifPresentOrElse(ex -> {
                LOGGER.error("Failed to store chunk {}", pos, ex);
                result.completeExceptionally(ex);
            }, () -> result.complete(null));
        });
    }
}
