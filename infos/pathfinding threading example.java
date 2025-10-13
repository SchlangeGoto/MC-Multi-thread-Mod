public class ParallelPathfinding {
    private final ForkJoinPool pathfindingPool;
    
    public void tickMobAI(List<Mob> mobs) {
        // All pathfinding happens in parallel
        List<CompletableFuture<Path>> pathFutures = mobs.stream()
            .filter(mob -> mob.needsNewPath())
            .map(mob -> CompletableFuture.supplyAsync(
                () -> calculatePath(mob, mob.getTarget()),
                pathfindingPool
            ))
            .collect(Collectors.toList());
        
        // Wait for all paths
        CompletableFuture.allOf(pathFutures.toArray(new CompletableFuture[0]))
            .join();
        
        // Apply paths on main thread (quick)
        for (int i = 0; i < mobs.size(); i++) {
            if (mobs.get(i).needsNewPath()) {
                mobs.get(i).setPath(pathFutures.get(i).join());
            }
        }
    }
}
