package net.fg83.rdrgen.task;

import net.fg83.rdrgen.client.RDRGClient;
import net.minecraft.client.MinecraftClient;

/**
 * ProgressReportTask is responsible for periodically reporting the progress
 * of ray casting operations from the companion client to the Minecraft client.
 *
 * This task runs in a separate thread and monitors the state of the companion client
 * to determine when to begin and stop reporting progress. It relies on the
 * `isCastingRays` flag in the companion client to control the reporting process.
 *
 * If the `isCastingRays` flag is set to false, the task waits until it is set to
 * true. Once the flag is true, the task continuously sends progress updates until
 * the flag is set back to false, at which point it sends one final progress report
 * and terminates.
 *
 * Thread sleeping is used to introduce a delay between progress updates to prevent
 * excessive reporting.
 */
public class ProgressReportTask implements Runnable{

    MinecraftClient client;
    RDRGClient companionClient;

    public ProgressReportTask(MinecraftClient client, RDRGClient companionClient) {
        this.client = client;
        this.companionClient = companionClient;
    }

    /**
     * Runs this operation.
     */
    @Override
    public void run() {
        while (!companionClient.isCastingRays.get()){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        while (companionClient.isCastingRays.get()){
            RDRGClient.reportProgress(client);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        RDRGClient.reportProgress(client);
    }
}
