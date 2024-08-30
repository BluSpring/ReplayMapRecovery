package xyz.bluspring.replaymaprecovery.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TransientEntitySectionManager.class)
public interface TransientEntitySectionManagerAccessor<T extends Entity> {
    @Accessor
    EntitySectionStorage<T> getSectionStorage();

    @Accessor
    EntityLookup<T> getEntityStorage();

    @Accessor
    LevelCallback<T> getCallbacks();
}
