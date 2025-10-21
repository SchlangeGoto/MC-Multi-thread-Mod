package dev.kkazi.mcMultiThreadFabric.client.multithreading.entityTicking;


import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.Box;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.SectionedEntityCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SectionedEntityCache.class)
public abstract class MakeSectionedCacheSafe <T extends EntityLike> {

}
