package net.fg83.rdcompanion.task;

import net.fg83.rdcompanion.Ray;
import net.fg83.rdcompanion.client.RDRCompanionClient;
import net.minecraft.client.MinecraftClient;

/**
 * The CastRayTask class is responsible for executing a ray tracing operation
 * in the context of a Minecraft mod. This task implements the Runnable
 * interface, allowing it to be executed as a separate thread or by a task scheduler.
 *
 * This task performs the following:
 * 1. Ensures that the player instance in the Minecraft client is not null.
 * 2. Checks and sets a flag in the companion client to indicate that ray casting
 *    is in progress.
 * 3. Validates the ray instance and performs the ray tracing operation.
 * 4. Increments a global counter for processed rays in the companion client.
 * 5. Adds the ray to a queue in the companion client if it successfully hits a target.
 */
public class CastRayTask implements Runnable{
    MinecraftClient client;
    RDRCompanionClient companionClient;
    Ray ray;

    public CastRayTask(Ray ray, MinecraftClient client, RDRCompanionClient companionClient) {
        this.ray = ray;
        this.client = client;
        this.companionClient = companionClient;
    }

    /**
     * Runs this operation.
     */
    @Override
    public void run() {

        assert client.player != null;
        companionClient.isCastingRays.compareAndSet(false, true);

        if (ray == null){
            System.out.println("Ray is null");
            return;
        }
        ray.trace();
        RDRCompanionClient.processedRays++;
        if (ray.didHitTarget()){
            companionClient.tracedRayQueue.add(ray);
        }
    }

}
