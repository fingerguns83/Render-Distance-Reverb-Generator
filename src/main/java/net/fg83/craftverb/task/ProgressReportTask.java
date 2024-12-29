package net.fg83.craftverb.task;

import net.fg83.craftverb.client.CraftverbClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ProgressReportTask implements Runnable{

    MinecraftClient client;
    CraftverbClient craftverbClient;

    public ProgressReportTask(MinecraftClient client, CraftverbClient craftverbClient) {
        this.client = client;
        this.craftverbClient = craftverbClient;
    }

    /**
     * Runs this operation.
     */
    @Override
    public void run() {
        while (!craftverbClient.isCastingRays.get()){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        while (craftverbClient.isCastingRays.get()){
            CraftverbClient.reportProgress(craftverbClient, client);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
