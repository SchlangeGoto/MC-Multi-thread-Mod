package dev.kkazi.mcMultiThreadFabric.client.multithreading.entityTicking;


import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntitiyThreadSafety {

    @Unique
    private EntitySnapshot lastSnapshot;

    @Unique
    public EntitySnapshot newSnapshot(){
        Entity self = (Entity) (Object) this;
        if(self.isAlive()) {
            LivingEntity livingself = (LivingEntity) self;
            return new EntitySnapshot(
                    self.getUuid(),
                    self.getEntityPos(),
                    self.getVelocity(),
                    self.isOnGround(),
                    livingself.getHealth()
            );
        }else{
            return new EntitySnapshot(
                    self.getUuid(),
                    self.getEntityPos(),
                    self.getVelocity(),
                    self.isOnGround()
            );
        }
    }
}
