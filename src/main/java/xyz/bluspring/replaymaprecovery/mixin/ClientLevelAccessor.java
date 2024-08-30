package xyz.bluspring.replaymaprecovery.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {
    @Accessor
    TransientEntitySectionManager<Entity> getEntityStorage();

    @Invoker
    Map<String, MapItemSavedData> callGetAllMapData();
}
