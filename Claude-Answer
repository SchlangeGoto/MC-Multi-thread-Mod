# What Actually Kills Minecraft Performance

Let me break down the real bottlenecks based on profiling data and what major optimization mods target.

## The Two Worlds: Client vs Server

### Client-Side Bottlenecks (FPS)

**1. Rendering (60-80% of frame time)**

The biggest performance killer by far:

```
Frame Budget (16.6ms for 60 FPS):
├─ Chunk Rendering: 6-10ms
├─ Entity Rendering: 2-4ms  
├─ Particles: 1-3ms
├─ Lighting Updates: 2-5ms
├─ GUI/HUD: 0.5-1ms
└─ Everything else: 2-3ms
```

**Why it's slow:**
- Vanilla rebuilds entire chunk meshes when ONE block changes
- No occlusion culling (renders chunks you can't see)
- Inefficient vertex buffer updates
- Poor batching of draw calls

**2. Chunk Meshing/Rebuilding**

When you place a block, Minecraft rebuilds the entire 16x16x16 chunk section mesh:
```java
// Vanilla approach - SLOW
public void rebuildChunk() {
    for (BlockState state : allBlocksInChunk) {  // 4,096 blocks
        addQuadsToMesh(state);  // Checks neighbors, lighting, etc.
    }
    uploadToGPU();  // Expensive!
}
```

This can take 5-20ms per chunk section.

**3. Lighting Engine**

Vanilla's lighting is notoriously broken and slow:
- Cascading light updates across chunks
- Recalculates unnecessarily 
- Causes lighting bugs that persist

### Server-Side Bottlenecks (TPS)

**1. Entity Ticking (30-40% of tick time)**

```
20 TPS = 50ms per tick budget:
├─ Entity AI: 15-20ms (pathfinding, behavior trees)
├─ Entity Physics: 5-8ms (collision detection, movement)
├─ Block Entity Updates: 5-10ms (hoppers, furnaces)
├─ Chunk Ticking: 3-5ms (random ticks, scheduled ticks)
└─ Everything else: 7-12ms
```

**Worst offenders:**
- **Pathfinding:** A* algorithm runs every tick for every mob
- **Hoppers:** Check for items multiple times per tick
- **Collision detection:** O(n²) checks between nearby entities

**2. Redstone**

Redstone is essentially unoptimizable without breaking it:
```java
// Every redstone component updates neighbors
public void updateNeighbors() {
    for (Direction dir : Direction.values()) {
        BlockPos neighbor = pos.relative(dir);
        neighbor.neighborChanged();  // Cascades...
            └─> neighbor.updateNeighbors()  // More cascading...
```

Can cause 100+ block updates from one lever flip.

**3. Chunk Loading/Generation**

- World generation is CPU-bound
- Terrain generation, decoration, lighting all sequential
- Disk I/O for loading chunks from disk

## What Major Optimization Mods Focus On

### **Sodium** (Client - Rendering)

**Target:** Chunk rendering (the #1 client bottleneck)

**Key optimizations:**

1. **Chunk Mesh Caching**
```java
// Sodium: Only rebuild changed sections
if (chunk.isDirty(sectionY)) {
    rebuildSection(sectionY);  // 16x16x16 only, not whole chunk
}
```

2. **Occlusion Culling**
```java
// Don't render chunks behind other chunks
if (!isVisible(chunk, cameraPos)) {
    skip(); // Saves 40-60% of draw calls
}
```

3. **Greedy Meshing**
```java
// Vanilla: 6 quads per block face (even if adjacent)
// Sodium: Merge adjacent faces into one large quad
//
// Before: ████ = 24 quads (4 blocks × 6 faces)
// After:  ████ = 6 quads (1 merged mesh)
```

4. **Batch Rendering**
- Groups similar blocks together
- Reduces draw calls from ~5,000 to ~500 per frame

5. **Multi-threaded Chunk Building**
```java
// Build chunk meshes on worker threads
CompletableFuture.supplyAsync(() -> {
    return buildChunkMesh(chunk);
}, chunkBuilderPool)
.thenAccept(mesh -> uploadToGPU(mesh)); // Main thread
```

**Performance gain:** 3-5x FPS improvement, especially on integrated graphics

---

### **Lithium** (Server - Game Logic)

**Target:** Game tick optimizations (entity AI, physics, block updates)

**Key optimizations:**

1. **Entity AI Optimization**
```java
// Vanilla: Recalculates pathfinding every tick
// Lithium: Caches paths, only recalculates when needed

if (!path.isStale() && !targetMoved()) {
    return cachedPath;  // Saves 60% of pathfinding calls
}
```

2. **Fast Block Access**
```java
// Vanilla: Bounds checks, chunk lookups every access
// Lithium: Direct array access with cached chunk references

// ~30% faster block access
return chunkSection.states[index];  // vs multiple method calls
```

3. **Smarter Collision Detection**
```java
// Vanilla: Checks every entity against every other entity
// Lithium: Spatial hashing

Map<ChunkPos, List<Entity>> spatialGrid;
// Only check entities in same/adjacent chunks
// O(n²) → O(n) for sparse entities
```

4. **Hopper Optimization**
```java
// Vanilla: Hoppers try to pull items even when inventory is full
// Lithium: Cache "no items available" state
if (lastCheckFoundNothing && !inventoryChanged) {
    return;  // Skip expensive item search
}
```

**Performance gain:** 1.5-2x TPS on entity-heavy servers

---

### **Starlight** (Both - Lighting)

**Target:** Lighting engine (causes stuttering and lag spikes)

**The problem with vanilla:**
```java
// Vanilla propagates light one block at a time
public void updateLight(BlockPos pos) {
    for (BlockPos neighbor : neighbors) {
        if (shouldUpdateLight(neighbor)) {
            updateLight(neighbor);  // Recursive, cascades forever
        }
    }
}
```

**Starlight's approach:**
```java
// Flood-fill algorithm with priority queue
PriorityQueue<LightNode> queue;

while (!queue.isEmpty()) {
    LightNode node = queue.poll();
    for (LightNode neighbor : getNeighbors(node)) {
        if (shouldPropagate(neighbor)) {
            queue.add(neighbor);  // Process in optimal order
        }
    }
}
```

**Key improvements:**
- Processes light updates in batches
- Better algorithm complexity
- Multi-threaded light updates

**Performance gain:** Eliminates lighting lag spikes, 10-50x faster light updates

---

### **C2ME** (Both - Chunk System)

**Target:** Chunk loading/generation/I/O

**Key optimizations:**

1. **Parallel Chunk Generation**
```java
// Generate multiple chunks simultaneously
List<CompletableFuture<Chunk>> futures = 
    chunksToGenerate.stream()
        .map(pos -> CompletableFuture.supplyAsync(
            () -> generateChunk(pos),
            generationPool
        ))
        .collect(Collectors.toList());
```

2. **I/O Thread Pool**
```java
// Vanilla: Single I/O thread bottleneck
// C2ME: Multiple threads for disk operations
ExecutorService ioPool = Executors.newFixedThreadPool(4);
```

3. **Async Chunk Loading**
- Loads chunks without blocking game thread
- Prioritizes chunks near players

**Performance gain:** 2-3x faster chunk loading

---

### **FerriteCore** (Both - Memory)

**Target:** Memory usage (causes GC pauses)

**Optimizations:**

1. **BlockState Deduplication**
```java
// Vanilla: Each chunk stores BlockState objects
// FerriteCore: Use shared palette + indices

// Memory: ~40% reduction for block states
```

2. **Smaller Block Palettes**
- Optimized data structures for chunk storage
- Less memory = less GC pressure

**Performance gain:** 50-70% memory reduction, fewer GC pauses

---

### **Phosphor** (Replaced by Starlight)

Earlier lighting optimization mod, superseded by Starlight.

---

## Performance Impact Summary

| Bottleneck | % of Performance | Best Mod | Improvement |
|------------|------------------|----------|-------------|
| **Chunk Rendering** | 60-80% (client) | Sodium | 3-5x FPS |
| **Entity AI** | 30-40% (server) | Lithium | 1.5-2x TPS |
| **Lighting** | 10-20% (both) | Starlight | 10-50x faster updates |
| **Chunk Gen/Load** | 10-15% (both) | C2ME | 2-3x faster |
| **Memory/GC** | 5-15% (both) | FerriteCore | 50% less memory |

## What Should YOU Optimize?

Based on use case:

### **For Single-Player/Client:**
1. **Chunk rendering** (Sodium-style optimization) - Biggest bang for buck
2. **Lighting updates** (Starlight-style) - Eliminates stuttering
3. **Chunk meshing** (Multi-threading) - Smoother block placement

### **For Multiplayer Servers:**
1. **Entity AI/pathfinding** (Lithium-style) - Most CPU time
2. **Hoppers/tile entities** (Caching, smarter updates)
3. **Chunk loading** (Parallel generation)

### **The Low-Hanging Fruit:**

If you're starting out, focus on:

1. **Parallel entity ticking** - Easy to implement, 30-50% gain
2. **Hopper optimization** - Cache checks, huge impact
3. **Pathfinding caching** - Massive savings with simple caching

## Profiling Example

Here's what a typical Spark profile shows:

```
Tick Time Breakdown (Server):
├─ 38.2% Entity Ticking
│   ├─ 18.4% Pathfinding (Mob.goalSelector)
│   ├─ 12.1% Entity AI (Mob.updateBehavior)  
│   └─ 7.7% Physics (Entity.move)
├─ 24.1% Block Entity Updates
│   ├─ 15.2% Hoppers (HopperBlockEntity.tick)
│   └─ 8.9% Other (Furnaces, etc.)
├─ 18.6% Chunk Ticking
│   ├─ 10.2% Random Ticks (Crop growth, etc.)
│   └─ 8.4% Scheduled Ticks (Redstone, fluids)
├─ 11.3% Player Ticking
└─ 7.8% Other (Networking, world save, etc.)
```

**Translation:** Fix pathfinding and hoppers first!

## Concrete Starting Point

Want to make an impact fast? Here's what I'd implement first:

### 1. **Hopper Optimization** (Easy, Big Impact)### 2. **Entity AI Caching** (Medium Difficulty, Great Impact)## Recommended Reading/Resources

1. **Sodium Source Code:** https://github.com/CaffeineMC/sodium-fabric
   - Focus on: `ChunkRenderManager`, `ChunkBuilder`, `TerrainRenderPass`

2. **Lithium Source Code:** https://github.com/CaffeineMC/lithium-fabric
   - Focus on: AI optimizations, entity ticking, collision

3. **Paper's Timings Reports:** Shows real server bottlenecks
   - Command: `/timings paste`

4. **Spark Profiler:** Best profiling tool
   - https://spark.lucko.me/

5. **Folia Regional Threading:** Future of server optimization
   - https://github.com/PaperMC/Folia

## TL;DR - Start Here

**Client optimization priority:**
1. Chunk rendering (Sodium approach)
2. Lighting (Starlight approach)
3. Chunk meshing parallelization

**Server optimization priority:**
1. Hopper caching (easy win)
2. Entity AI/pathfinding caching
3. Parallel entity ticking

Want me to dive deeper into any specific optimization technique, or create a working implementation of any of these systems?
