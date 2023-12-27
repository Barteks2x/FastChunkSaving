package io.github.barteks2x.fastchunksaving;

import net.minecraft.world.level.chunk.storage.RegionFileVersion;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ChunkDataBuffer extends ByteArrayOutputStream {

    private ByteBuffer outputBuffer;

    public ChunkDataBuffer(RegionFileVersion version) {
        super(8096);
        super.write(0);
        super.write(0);
        super.write(0);
        super.write(0);
        super.write(version.getId());
    }

    @Override
    public void close() {
        ByteBuffer bytebuffer = ByteBuffer.wrap(this.buf, 0, this.count);
        bytebuffer.putInt(0, this.count - 5 + 1);
        this.outputBuffer = bytebuffer;
    }

    public ByteBuffer getOutputBuffer() {
        return outputBuffer;
    }
}