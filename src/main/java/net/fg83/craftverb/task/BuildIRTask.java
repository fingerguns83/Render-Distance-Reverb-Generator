package net.fg83.craftverb.task;

import net.fg83.craftverb.Ray;
import net.fg83.craftverb.client.CraftverbClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Map;

public class BuildIRTask implements Runnable {
    MinecraftClient client;
    CraftverbClient craftverbClient;

    public BuildIRTask(MinecraftClient client, CraftverbClient craftverbClient) {
        this.client = client;
        this.craftverbClient = craftverbClient;
    }

    /**
     * Runs this operation.
     */
    @Override
    public void run() {
        while (!craftverbClient.rayPool.isTerminated()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (!craftverbClient.isCastingRays.compareAndSet(true, false)) {
            client.player.sendMessage(Text.of("Error building waveform. Please try again."), false);
            return;
        }

        if (craftverbClient.isGeneratingIR.compareAndSet(false, true)) {
            assert client.player != null;

            while (!craftverbClient.tracedRayQueue.isEmpty()) {
                Ray ray = craftverbClient.tracedRayQueue.poll();
                if (ray == null) {
                    continue;
                }
                if (ray.didHitTarget()) {
                    craftverbClient.addEnergyToIR(ray.getDelayTime(), ray.getEnergy());
                }
            }

            craftverbClient.generateIR(client);
            craftverbClient.isGeneratingIR.set(false);
        }
    }
}
