public class EntityTickExecutor {
    private static final ExecutorService pool = Executors.newWorkStealingPool();

    public static void tickEntitiesParallel(List<Entity> entities) {
        List<Callable<Void>> tasks = new ArrayList<>();
        for (Entity e : entities) {
            tasks.add(() -> {
                e.tick();
                return null;
            });
        }
        try {
            pool.invokeAll(tasks);
        } catch (InterruptedException ignored) {}
    }
}
