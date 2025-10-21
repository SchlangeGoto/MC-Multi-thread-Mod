package dev.kkazi.mcMultiThreadFabric.client.multithreading.entityTicking;


import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;

@Mixin(World.class)
public class ThreadSafeWorld {
    @Unique
    public Random random = Random.createThreadSafe();

}
