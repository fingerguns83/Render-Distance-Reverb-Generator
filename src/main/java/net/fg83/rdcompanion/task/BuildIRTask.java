package net.fg83.rdcompanion.task;

import net.fg83.rdcompanion.Ray;
import net.fg83.rdcompanion.client.RDRCompanionClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;

/**
 * The BuildIRTask class is responsible for processing ray tracing results and generating an impulse response (IR)
 * for the RDRCompanionClient. It continuously monitors the state of a ray tracing operation and, upon completion,
 * aggregates the results to perform further processing.
 *
 * This class implements the Runnable interface, allowing it to be executed in a separate thread to handle
 * asynchronous processing without blocking the main application flow.
 */
public class BuildIRTask implements Runnable {
    MinecraftClient client;
    RDRCompanionClient companionClient;

    public BuildIRTask(MinecraftClient client, RDRCompanionClient companionClient) {
        this.client = client;
        this.companionClient = companionClient;
    }

    /**
     * Runs this operation.
     */
    @Override
    public void run() {
        while (!companionClient.rayPool.isTerminated()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("BuildIRTask has proceeded to run.");
        double percentHit = (float) (Math.round(((float) companionClient.tracedRayQueue.size() / RDRCompanionClient.processedRays) * 10000)) / 100;
        System.out.println(RDRCompanionClient.processedRays + " rays processed | " + companionClient.tracedRayQueue.size() + " rays hit target (" + percentHit + "%)");
        if (!companionClient.isCastingRays.compareAndSet(true, false)) {
            RDRCompanionClient.sendPlayerMessage(client, "Error building waveform. Please try again.", new Formatting[]{Formatting.RED});
            return;
        }

        if (companionClient.isGeneratingIR.compareAndSet(false, true)) {
            assert client.player != null;

            while (!companionClient.tracedRayQueue.isEmpty()) {
                Ray ray = companionClient.tracedRayQueue.poll();
                if (ray == null) {
                    continue;
                }
                if (ray.didHitTarget()) {
                    companionClient.addEnergyToIR(ray.getDelayTime(), ray.getEnergy());
                }
            }

            companionClient.generateIR(client);
            companionClient.isGeneratingIR.set(false);
        }
    }
}
