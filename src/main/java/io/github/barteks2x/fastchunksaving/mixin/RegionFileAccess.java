package io.github.barteks2x.fastchunksaving.mixin;

import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RegionFile.class)
public interface RegionFileAccess {
    @Accessor RegionFileVersion getVersion();
}
