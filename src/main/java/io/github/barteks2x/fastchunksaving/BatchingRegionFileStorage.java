package io.github.barteks2x.fastchunksaving;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public interface BatchingRegionFileStorage {

    Map<ChunkPos, Optional<Exception>> fastchunksaving_writeBatch(Map<ChunkPos, IOWorker.PendingStore> batch);
}
