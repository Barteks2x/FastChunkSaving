package io.github.barteks2x.fastchunksaving;

import net.minecraft.world.level.ChunkPos;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;

public interface BatchingRegionFile {

    Map<ChunkPos, Optional<Exception>> fastchunksaving_writeBatched(Map<ChunkPos, ByteBuffer> entries);
}
