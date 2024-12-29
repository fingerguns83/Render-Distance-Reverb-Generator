package net.fg83.craftverb.task;

import net.fg83.craftverb.Ray;
import net.fg83.craftverb.client.CraftverbClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class CastRayTask implements Runnable{
    MinecraftClient client;
    CraftverbClient craftverbClient;
    Ray ray;

    public CastRayTask(Ray ray, MinecraftClient client, CraftverbClient craftverbClient) {
        this.ray = ray;
        this.client = client;
        this.craftverbClient = craftverbClient;
    }

    /**
     * Runs this operation.
     */
    @Override
    public void run() {

        assert client.player != null;
        craftverbClient.isCastingRays.compareAndSet(false, true);

        if (ray == null){
            System.out.println("Ray is null");
            return;
        }
        ray.trace();
        craftverbClient.tracedRayQueue.add(ray);
    }

}
