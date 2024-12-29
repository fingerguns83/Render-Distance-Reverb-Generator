package net.fg83.craftverb.task;

import net.fg83.craftverb.Ray;
import net.fg83.craftverb.client.CraftverbClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class PopulateRaysTask implements Runnable{

    MinecraftClient client;
    CraftverbClient craftverbClient;

    public PopulateRaysTask(MinecraftClient client, CraftverbClient craftverbClient) {
        this.client = client;
        this.craftverbClient = craftverbClient;
    }
    /**
     * Runs this operation.
     */
    @Override
    public void run() {
        assert client.player != null;

        Entity camera = client.cameraEntity;

        if (camera == null) {
            return;
        }
        Vec3d startPos = camera.getEyePos();


        Thread progressReportThread = new Thread(new ProgressReportTask(client, craftverbClient));
        progressReportThread.start();

        try {
            client.player.sendMessage(Text.of("Running acoustic simulation..."), false);
            for (float pitch = -90.0F; pitch <= 90.0F; pitch += 0.1F) {
                while(craftverbClient.rayPool.hasQueuedSubmissions()){
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                for (float yaw = -180.0F; yaw <= 180.0F; yaw += 0.1F) {
                    Vec3d currentDir = camera.getRotationVector(pitch, yaw); // Modify yaw/pitch here
                    craftverbClient.rayPool.submit(() -> new CastRayTask(new Ray(startPos, currentDir, camera), client, craftverbClient).run());
                }
            }
        } finally {
            // Shut down the pool to release resources
            craftverbClient.rayPool.shutdown();
            new BuildIRTask(client, craftverbClient).run();
        }

        /*

        int startSize = craftverbClient.rayQueue.size();
        client.player.sendMessage(Text.of("Casting " + startSize + " rays..."), false);

        int rayCount = 0;

        while (!craftverbClient.rayQueue.isEmpty()){
            craftverbClient.isCastingRays.compareAndSet(false, true);

            Ray ray = craftverbClient.rayQueue.poll();
            if (ray == null){
                System.out.println("Ray is null");
                continue;
            }
            ray.trace();
            craftverbClient.addEnergyToIR(ray.getDelayTime(), ray.getEnergy());

            if (rayCount % ((float) startSize / 10) == 0){
                int percentage = Math.round(((float) rayCount / startSize) * 100);
                String message = "[";
                for (int i = 0; i <= 10; i++){
                    if (i < Math.floor((double) percentage / 10)){
                        message += "◆";
                    }
                    else {
                        message += "◇";
                    }
                }
                message += "] " + percentage + "%";
                client.player.sendMessage(Text.of(message), false);
            }
            rayCount++;
        }
        client.player.sendMessage(Text.of("[◆◆◆◆◆◆◆◆◆◆] 100%"), false);
        client.player.sendMessage(Text.of("Finished casting rays."), false);


        client.player.sendMessage(Text.of("Building IR waveform..."), false);
        craftverbClient.isCastingRays.set(false);
        craftverbClient.generateIR();
        craftverbClient.isGeneratingIR.set(false);

         */
    }
}
