package xyz.bluspring.replaymaprecovery.mixin;

import net.minecraft.client.multiplayer.ClientChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientChunkCache.class)
public interface ClientChunkCacheAccessor {
    @Accessor
    ClientChunkCache.Storage getStorage();
}
