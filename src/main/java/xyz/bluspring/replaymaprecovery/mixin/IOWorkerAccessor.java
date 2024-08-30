package xyz.bluspring.replaymaprecovery.mixin;

import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.nio.file.Path;

@Mixin(IOWorker.class)
public interface IOWorkerAccessor {
    @Invoker("<init>")
    static IOWorker createIOWorker(Path folder, boolean sync, String workerName) {
        throw new UnsupportedOperationException();
    }
}
