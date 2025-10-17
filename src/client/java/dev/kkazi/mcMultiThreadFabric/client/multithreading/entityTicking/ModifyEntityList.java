package dev.kkazi.mcMultiThreadFabric.client.multithreading.entityTicking;

import dev.kkazi.mcMultiThreadFabric.client.accessor.EntityListAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.entity.Entity;
import net.minecraft.world.EntityList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;


@Mixin (EntityList.class)
public abstract class ModifyEntityList implements EntityListAccessor {
    @Shadow
    private Int2ObjectMap<Entity> entities;

    @Unique
    public Int2ObjectMap<Entity> getEntities(){
        return this.entities;
    }
}
