# Making Minecraft Multi-threaded: A Practical Guide

This is a **massive** undertaking, but let me break down realistic approaches from easiest to hardest.

## Understanding the Problem First

The main bottleneck is the **game tick**:

```java
// Simplified Minecraft main loop
while (running) {
    // ALL of this runs on ONE thread:
    server.tick();  // ~50ms budget (20 TPS target)
        └─> World.tick()
            ├─> Entity.tick() for ALL entities
            ├─> BlockEntity.tick() for ALL tile entities  
            ├─> Chunk.tick()
            └─> Physics, AI, redstone, etc.
    
    render(); // Separate thread (usually)
}
```

**The challenge:** Everything depends on everything else:
- Entity A's movement affects Entity B's AI
- Block updates trigger neighbor updates
- Redstone is inherently sequential

## Approach 1: Low-Hanging Fruit (Start Here!)

### Target Independent Systems

These can be parallelized without major refactoring:

#### 1. **Entity Ticking (Spatial Partitioning)**

Entities far apart don't interact, so they can tick in parallel:

```java
public class ParallelEntityTicker {
    private final ExecutorService executor;
    
    public void tickEntities(List<Entity> entities) {
        // Partition entities by chunk/region
        Map<ChunkPos, List<Entity>> byChunk = 
            entities.stream().collect(Collectors.groupingBy(
                e -> new ChunkPos(e.blockPosition())
            ));
        
        // Tick each spatial region in parallel
        List<CompletableFuture<Void>> futures = byChunk.values()
            .stream()
            .map(chunkEntities -> CompletableFuture.runAsync(
                () -> chunkEntities.forEach(Entity::tick),
                executor
            ))
            .collect(Collectors.toList());
        
        // Wait for all regions to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .join();
    }
}
```

**Where to inject:**
- Find `Level.tickNonPassenger()` or similar
- Use Mixin/Coremod to replace the entity tick loop

#### 2. **Chunk Generation**

Already somewhat parallel in modern MC, but you can improve it:

```java
@Mixin(ChunkGenerator.class)
public class ParallelChunkGenMixin {
    @Inject(method = "createBiomes", at = @At("HEAD"), cancellable = true)
    public void parallelBiomes(CallbackInfo ci) {
        // Use ForkJoinPool for biome generation
        ForkJoinPool.commonPool().submit(() -> {
            // Generate biomes in parallel
        }).join();
    }
}
```

#### 3. **Pathfinding**

Mob pathfinding is CPU-intensive and independent:

```java
public class AsyncPathfinder {
    private final ExecutorService pathfindingPool = 
        Executors.newFixedThreadPool(4);
    
    public void calculatePath(Mob mob, BlockPos target) {
        CompletableFuture.supplyAsync(() -> {
            // Expensive A* pathfinding here
            return findPath(mob.blockPosition(), target);
        }, pathfindingPool)
        .thenAccept(path -> {
            // Apply path on main thread next tick
            mob.getNavigation().setPath(path);
        });
    }
}
```

## Approach 2: Dimension-Level Parallelism

**Different dimensions can tick in parallel** since they don't interact:

```java
@Mixin(MinecraftServer.class)
public class ParallelDimensionMixin {
    @Redirect(method = "tickChildren", ...)
    public void parallelWorldTick(MinecraftServer server) {
        List<ServerLevel> worlds = server.getAllLevels();
        
        worlds.parallelStream().forEach(world -> {
            // Each dimension ticks on separate thread
            world.tick(() -> true);
        });
    }
}
```

**Caution:** Players can teleport between dimensions, so you need synchronization.

## Approach 3: Paper/Folia's Approach (Regional Threading)

This is what **Folia** (Paper fork) does - the most advanced approach:

### Regional Threading Concept

```
World divided into regions (8x8 chunks):

Region 1 (Thread A) | Region 2 (Thread B)
    Tick entities   |     Tick entities
    Tick blocks     |     Tick blocks
    
When entity crosses boundary → ownership transfer
```

**Implementation Overview:**

```java
public class RegionScheduler {
    private final Map<RegionPos, Region> regions;
    private final ForkJoinPool threadPool;
    
    public void tickRegion(Region region) {
        threadPool.submit(() -> {
            region.lock(); // Prevent concurrent access
            try {
                // Tick everything in this region
                region.tickEntities();
                region.tickBlockEntities();
                region.tickChunks();
            } finally {
                region.unlock();
            }
        });
    }
    
    public void handleEntityTransfer(Entity entity, 
                                     Region from, Region to) {
        // Lock both regions to safely transfer
        synchronized (from, to) {
            from.removeEntity(entity);
            to.addEntity(entity);
        }
    }
}
```

## Approach 4: Task-Based Parallelism

Break the tick into phases that can be parallelized:

```java
public class PhasedTickSystem {
    public void tick(Level level) {
        // Phase 1: AI calculation (parallel)
        level.entities.parallelStream()
            .filter(e -> e instanceof Mob)
            .forEach(mob -> ((Mob)mob).calculateAI());
        
        // Barrier - wait for all AI calculations
        
        // Phase 2: Movement (parallel by region)
        parallelMoveEntities(level.entities);
        
        // Barrier
        
        // Phase 3: Block updates (must be sequential for redstone)
        level.tickBlocks();
        
        // Phase 4: Post-tick cleanup (parallel)
        level.entities.parallelStream()
            .forEach(Entity::postTick);
    }
}
```

## Where to Start: Concrete Steps

### 1. **Set Up Development Environment**

```bash
# Clone Paper (better than Forge for server-side)
git clone https://github.com/PaperMC/Paper.git
cd Paper
./gradlew applyPatches
```

Or for Forge:
```bash
# Set up Forge MDK
# Add Mixin support for bytecode injection
```

### 2. **Profile First!**

Use **Spark** profiler to find bottlenecks:

```bash
/spark profiler start
# Let server run for 5 minutes
/spark profiler stop
```

Look for:
- What percentage is entity ticking?
- Block entity updates?
- Pathfinding?
- Chunk loading?

### 3. **Start with Entity Ticking**

Create a simple parallel entity ticker:

```java
@Mixin(ServerLevel.class)
public abstract class ParallelEntityTickMixin {
    @Shadow private EntityTickList entityTickList;
    
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void parallelEntityTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        // Get entities to tick
        List<Entity> entities = new ArrayList<>();
        entityTickList.forEach(entities::add);
        
        // Group by chunk position for spatial partitioning
        Map<Long, List<Entity>> byChunk = entities.stream()
            .collect(Collectors.groupingBy(
                e -> ChunkPos.asLong(e.blockPosition())
            ));
        
        // Tick chunks in parallel
        ForkJoinPool.commonPool().submit(() -> {
            byChunk.values().parallelStream().forEach(chunkEntities -> {
                chunkEntities.forEach(entity -> {
                    try {
                        entity.tick();
                    } catch (Exception e) {
                        // Error handling
                    }
                });
            });
        }).join();
        
        ci.cancel(); // Replace original tick
    }
}
```

### 4. **Add Thread Safety**

You'll need to protect shared state:

```java
public class ThreadSafeChunk {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    public BlockState getBlockState(BlockPos pos) {
        lock.readLock().lock();
        try {
            return this.sections[...].get(pos);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public void setBlockState(BlockPos pos, BlockState state) {
        lock.writeLock().lock();
        try {
            this.sections[...].set(pos, state);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
```

## Real-World Examples to Study

### 1. **Folia** (Regional Multi-threading)
```bash
git clone https://github.com/PaperMC/Folia.git
```
Look at: `io.papermc.paper.threadedregions`

### 2. **Petal** (Multithreaded Flower)
An abandoned project but good learning material

### 3. **Carpet Mod's Parallel Chunk Ticking**
Shows simple parallelization

## Critical Pitfalls to Avoid

### 1. **ConcurrentModificationException**
```java
// BAD
for (Entity e : entities) {
    if (e.isDead()) entities.remove(e); // Concurrent modification!
}

// GOOD
entities.removeIf(Entity::isDead);
```

### 2. **Race Conditions**
```java
// BAD
if (chunk.getEntities().size() < 100) {  // Check on thread A
    chunk.addEntity(entity);              // Add on thread B - race!
}

// GOOD
synchronized (chunk) {
    if (chunk.getEntities().size() < 100) {
        chunk.addEntity(entity);
    }
}
```

### 3. **Deadlocks**
```java
// BAD
Thread 1: lock(chunkA); lock(chunkB);
Thread 2: lock(chunkB); lock(chunkA); // Deadlock!

// GOOD - Always lock in consistent order
lock(min(chunkA, chunkB));
lock(max(chunkA, chunkB));
```

## Performance Expectations

Realistic improvements:
- **Entity ticking parallelization:** 30-50% improvement with 8+ cores
- **Dimension parallelization:** 2x improvement if multiple dimensions active
- **Regional threading (Folia-style):** 3-4x with proper implementation

**But:** Diminishing returns after 8-12 threads due to synchronization overhead.

## My Recommendation: Start Small

**Week 1:** Profile vanilla MC, identify top 3 bottlenecks  
**Week 2:** Implement parallel entity ticking with spatial partitioning  
**Week 3:** Add thread-safe chunk access  
**Week 4:** Test and fix race conditions  
**Month 2:** Tackle block entities or pathfinding  

Would you like me to create a working example of a parallel entity ticker that you can drop into a Forge/Fabric mod, or dive deeper into any specific aspect?
