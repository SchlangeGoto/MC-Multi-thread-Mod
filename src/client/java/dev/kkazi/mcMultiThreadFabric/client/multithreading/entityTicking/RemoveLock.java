package dev.kkazi.mcMultiThreadFabric.client.multithreading.entityTicking;

import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.thread.LockHelper;
import org.spongepowered.asm.mixin.*;

import java.util.concurrent.atomic.AtomicLong;

@Mixin (CheckedRandom.class)
public abstract class RemoveLock {

    @Shadow
    @Final
    private AtomicLong seed;

    /**
     * @author
     * mxsxll
     * @reason
     * overwrite because in the original code it throws errors when two threads access at the same time
     */

    @Overwrite
    public int next(int bits){
        int depth = 15;
        for (;;) {
            long l = this.seed.get();
            long m = l * 25214903917L + 11L & 281474976710655L;
            if (this.seed.compareAndSet(l, m) || depth == 0) {
                return (int) (m >> 48 - bits);
            }
            depth--;
        }
    }
}
