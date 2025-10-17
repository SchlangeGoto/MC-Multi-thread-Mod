package dev.kkazi.mcMultiThreadFabric.client.multithreading.entityTicking;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.entity.Entity;

public interface EntityListAccessor {
    Int2ObjectMap<Entity> getEntities();
}
