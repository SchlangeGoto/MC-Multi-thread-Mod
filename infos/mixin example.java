@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {

    @Shadow
    @Final
    private List<Entity> entities; // you may need @Shadow accessor depending on mappings

    @Inject(method = "tickEntities", at = @At("HEAD"), cancellable = true)
    private void injectParallelTick(CallbackInfo ci) {
        // Cancel vanilla loop
        ci.cancel();

        // Copy list to avoid concurrent modification
        List<Entity> snapshot = List.copyOf(this.entities);

        // Example parallel tick (use your own thread pool)
        snapshot.parallelStream().forEach(entity -> {
            try {
                entity.tick();
            } catch (Throwable t) {
                // Handle crash
            }
        });
    }
}
