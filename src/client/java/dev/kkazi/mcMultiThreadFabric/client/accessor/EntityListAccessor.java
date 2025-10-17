package dev.kkazi.mcMultiThreadFabric.client.accessor;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.entity.Entity;

public interface EntityListAccessor {
    Int2ObjectMap<Entity> getEntities();
}
