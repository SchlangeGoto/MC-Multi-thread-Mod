package dev.kkazi.mcMultiThreadFabric.client.multithreading.entityTicking;


import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Die Method ist ein readonly snapshot der dafür da ist
 * daten aus der entity class zu speichern damit nicht immer die große Klasse gereferenced werden muss
 */
public class EntitySnapshot {
    UUID uuid;
    Vec3d entityPos;
    Vec3d velocity;
    boolean onGround;
    float health;

    public EntitySnapshot(UUID uuid, Vec3d entityPos, Vec3d velocity, boolean onGround, float health) {
        this.entityPos = entityPos;
        this.uuid = uuid;
        this.health = health;
        this.onGround = onGround;
        this.velocity = velocity;
    }

    public EntitySnapshot(UUID uuid, Vec3d entityPos, Vec3d velocity, boolean onGround){
        this.entityPos = entityPos;
        this.uuid = uuid;
        this.onGround = onGround;
        this.velocity = velocity;
    }
}
