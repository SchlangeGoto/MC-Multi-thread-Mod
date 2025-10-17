package dev.kkazi.mcMultiThreadFabric.client.multithreading.entityTicking;

import dev.kkazi.mcMultiThreadFabric.client.accessor.EntityListAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.world.EntityList;
import net.minecraft.world.tick.TickManager;
import org.spongepowered.asm.mixin.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mixin (ClientWorld.class)
public abstract class EntityTickingMod {
    @Shadow @Final private EntityList entityList;

    @Shadow @Final private TickManager tickManager;

    @Shadow public abstract void tickEntity(Entity entity);

    ExecutorService pool = Executors.newWorkStealingPool();

    /**
     * @author
     * mxsxll
     * @reason
     * We have to fully change the implementation of this method,
     * for it to work concurrently.
     */
    @Overwrite
    public void tickEntities() {
        Int2ObjectMap<Entity> ents = ((EntityListAccessor)this.entityList).getEntities();
        this.entityList.forEach(entity -> {
            if (entity.isRemoved() || entity.hasVehicle() || this.tickManager.shouldSkipTick(entity)) {
                return;
            }
            pool.submit(() -> {
                this.tickEntity((Entity) entity);
            });
        });
     }

}
