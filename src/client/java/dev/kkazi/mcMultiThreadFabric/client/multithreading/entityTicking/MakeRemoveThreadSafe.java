package dev.kkazi.mcMultiThreadFabric.client.multithreading.entityTicking;


import net.minecraft.entity.Entity;
import net.minecraft.util.collection.TypeFilterableList;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.Box;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import java.util.Collection;


@Mixin (EntityTrackingSection.class)
public class MakeRemoveThreadSafe <T extends EntityLike> {

    @Shadow
    @Final
    private TypeFilterableList<T> collection;

    /**
     * @author
     * mxsxll
     * @reason
     *crashes when many mobs die due to checking null entities
     */
    @Overwrite
    public boolean remove(T entity){
        if (entity == null){
            return false;
        }
        return this.collection.remove(entity);
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public LazyIterationConsumer.NextIteration forEach(Box box, LazyIterationConsumer<T> consumer){
        for(T entityLike : this.collection) {
            if(entityLike  == null) {
                continue;
            }
            if (entityLike.getBoundingBox().intersects(box) && consumer.accept(entityLike).shouldAbort()) {
                return LazyIterationConsumer.NextIteration.ABORT;
            }
        }

        return LazyIterationConsumer.NextIteration.CONTINUE;
    }
}
