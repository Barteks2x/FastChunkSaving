package io.github.barteks2x.fastchunksaving.mixin;

import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;


@Mixin(targets = "net.minecraft.world.level.chunk.storage.IOWorker$PendingStore")
public interface IOWorkerPendingStoreAccess {
    @Accessor CompoundTag getData();
    @Accessor CompletableFuture<Void> getResult();
}
