package java;// ThreadSafeEcsTick.java
// Single-file prototype. Compile with: javac ThreadSafeEcsTick.java && java ThreadSafeEcsTick

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class TheoryECS {
    /* ---------- Simple component types ---------- */
    static class Position { double x, y, z; }
    static class Velocity { double vx, vy, vz; }
    static class Health { int hp; }
    static class Intent { // write-buffer for a single entity for this tick
        double moveDx, moveDy, moveDz;
        int damage;
        boolean hasMove = false;
        boolean hasDamage = false;
        void clear() { moveDx = moveDy = moveDz = 0; damage = 0; hasMove = hasDamage = false; }
    }

    /* ---------- Entity id management ---------- */
    static class EntityId {
        final int id;
        EntityId(int id){this.id=id;}
        public String toString(){return "E"+id;}
    }

    /* ---------- ECS storage ---------- */
    static class ComponentStorage {
        final List<EntityId> entities = new ArrayList<>();
        final List<Position> pos = new ArrayList<>();
        final List<Velocity> vel = new ArrayList<>();
        final List<Health> hp = new ArrayList<>();
        final List<Intent> intent = new ArrayList<>();

        synchronized EntityId create(Position p, Velocity v, Health h){
            int idx = entities.size();
            entities.add(new EntityId(idx));
            pos.add(p);
            vel.add(v);
            hp.add(h);
            intent.add(new Intent());
            return entities.get(idx);
        }
        int size(){ return entities.size(); }
    }

    /* ---------- Legacy entity (for adapter example) ---------- */
    static class LegacyEntity {
        double x,y,z;
        double vx,vy,vz;
        int hp;
        String name;
        LegacyEntity(String name,double x,double y,double z,double vx,double vy,double vz,int hp){
            this.name=name; this.x=x; this.y=y; this.z=z; this.vx=vx; this.vy=vy; this.vz=vz; this.hp=hp;
        }
        @Override public String toString(){
            return name + " pos=(" + x + ","+y+"," + z + ") vel=(" + vx + ","+vy+"," + vz + ") hp=" + hp;
        }
    }

    /* ---------- Systems (read snapshot, write intents) ---------- */
    interface SystemTask { void run(int start, int end, ComponentStorage snap); }
    static class MovementSystem implements SystemTask {
        final double dt;
        MovementSystem(double dt){ this.dt = dt; }
        public void run(int start,int end,ComponentStorage s){
            for(int i=start;i<end;i++){
                Position p = s.pos.get(i);
                Velocity v = s.vel.get(i);
                Intent it = s.intent.get(i);
                // compute movement intent based on velocity and dt
                double dx = v.vx * dt;
                double dy = v.vy * dt;
                double dz = v.vz * dt;
                it.moveDx += dx; it.moveDy += dy; it.moveDz += dz; it.hasMove = true;
            }
        }
    }
    static class DamageSystem implements SystemTask {
        // example: apply damage if below Y=0 (fell into void)
        public void run(int start,int end,ComponentStorage s){
            for(int i=start;i<end;i++){
                Position p = s.pos.get(i);
                Intent it = s.intent.get(i);
                if(p.y < 0){
                    it.damage += 5;
                    it.hasDamage = true;
                }
            }
        }
    }

    /* ---------- Tick scheduler ---------- */
    static class ParallelScheduler {
        private final ForkJoinPool pool;
        ParallelScheduler(int threads){ pool = new ForkJoinPool(threads); }
        void runSystems(ComponentStorage snapshot, List<SystemTask> systems) throws Exception {
            int n = snapshot.size();
            // clear all intents before running systems
            for(int i=0;i<n;i++) snapshot.intent.get(i).clear();

            // execute each system in parallel over chunks
            int chunkSize = Math.max(1, n / (pool.getParallelism()*4));
            List<Callable<Void>> systemJobs = new ArrayList<>();
            for(SystemTask sys : systems){
                // create jobs per-chunk for this system
                for(int start=0; start<n; start += chunkSize){
                    final int s = start;
                    final int e = Math.min(n, start+chunkSize);
                    systemJobs.add(() -> { sys.run(s,e,snapshot); return null; });
                }
            }
            // invoke all
            pool.invokeAll(systemJobs);
        }
        void shutdown(){ pool.shutdown(); }
    }

    /* ---------- Merge phase: apply intents deterministically ---------- */
    static class Merger {
        // single-threaded deterministic merge to authoritative state
        void merge(ComponentStorage storage){
            for(int i=0;i<storage.size();i++){
                Intent it = storage.intent.get(i);
                if(it.hasMove){
                    Position p = storage.pos.get(i);
                    p.x += it.moveDx;
                    p.y += it.moveDy;
                    p.z += it.moveDz;
                }
                if(it.hasDamage){
                    Health h = storage.hp.get(i);
                    h.hp -= it.damage;
                }
                it.clear();
            }
        }
    }

    /* ---------- Adapters to/from legacy entity ---------- */
    static void snapshotFromLegacy(LegacyEntity le, ComponentStorage cs, int idx){
        Position p = cs.pos.get(idx);
        Velocity v = cs.vel.get(idx);
        Health h = cs.hp.get(idx);
        p.x = le.x; p.y = le.y; p.z = le.z;
        v.vx = le.vx; v.vy = le.vy; v.vz = le.vz;
        h.hp = le.hp;
    }
    static void applyToLegacy(LegacyEntity le, ComponentStorage cs, int idx){
        Position p = cs.pos.get(idx);
        Velocity v = cs.vel.get(idx);
        Health h = cs.hp.get(idx);
        le.x = p.x; le.y = p.y; le.z = p.z;
        le.vx = v.vx; le.vy = v.vy; le.vz = v.vz;
        le.hp = h.hp;
    }

    /* ---------- Demo main loop ---------- */
    public static void main(String[] args) throws Exception {
        ComponentStorage storage = new ComponentStorage();
        // create some entities
        for(int i=0;i<2000;i++){
            Position p = new Position(); p.x = i%50; p.y = (i%3==0)?-1:5; p.z = i/50;
            Velocity v = new Velocity(); v.vx = 0.1*(i%5); v.vy = 0; v.vz = 0.02*(i%3);
            Health hp = new Health(); hp.hp = 20;
            storage.create(p,v,hp);
        }

        // example legacy entities array to show adapter usage
        List<LegacyEntity> legacy = new ArrayList<>();
        for(int i=0;i<50;i++){
            legacy.add(new LegacyEntity("User"+i, i, 10, 0, 0.5, 0, 0.1, 20));
            // map first N legacy entities into ECS storage positions
            if(i < storage.size()){
                snapshotFromLegacy(legacy.get(i), storage, i);
            }
        }

        // systems
        List<SystemTask> systems = Arrays.asList(new MovementSystem(1.0), new DamageSystem());
        ParallelScheduler scheduler = new ParallelScheduler(Math.max(1, Runtime.getRuntime().availableProcessors()-1));
        Merger merger = new Merger();

        // run ticks
        final int TICKS = 5;
        for(int tick=0; tick<TICKS; tick++){
            long t0 = System.nanoTime();
            // create snapshot view — in this simple prototype storage *is* the snapshot (no copy),
            // but in a real engine you would copy or keep read-only structures / versioned buffers.
            ComponentStorage snapshot = storage;

            // run systems in parallel which write into per-entity intents
            scheduler.runSystems(snapshot, systems);

            // merge (single-threaded)
            merger.merge(storage);

            // writeback to legacy objects for compatibility (for demonstration)
            for(int i=0;i<legacy.size() && i<storage.size(); i++){
                applyToLegacy(legacy.get(i), storage, i);
            }

            long dt = (System.nanoTime()-t0)/1_000_000;
            System.out.println("Tick " + tick + " done in " + dt + " ms; sample legacy[0]=" + legacy.get(0));
        }

        scheduler.shutdown();
    }
}
