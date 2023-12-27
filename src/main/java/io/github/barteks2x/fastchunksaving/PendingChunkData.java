package io.github.barteks2x.fastchunksaving;

import net.minecraft.world.level.ChunkPos;

import java.nio.ByteBuffer;

public record PendingChunkData(ChunkPos pos, ByteBuffer data) {

}
