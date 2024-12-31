package net.fg83.craftverb.task;

import net.fg83.craftverb.Ray;
import net.fg83.craftverb.VectorUtils;
import net.fg83.craftverb.client.CraftverbClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ForkJoinTask;

public class PopulateRaysTask implements Runnable{

    MinecraftClient client;
    CraftverbClient craftverbClient;

    Entity transmitter;
    Entity receiver;

    public PopulateRaysTask(Entity transmitter, Entity receiver, MinecraftClient client, CraftverbClient craftverbClient) {
        this.transmitter = transmitter;
        this.receiver = receiver;
        this.client = client;
        this.craftverbClient = craftverbClient;
    }
    /**
     * Runs this operation.
     */
    @Override
    public void run() {
        assert client.player != null;

        if (receiver == null || transmitter == null) {
            return;
        }

        Vec3d startPos = receiver.getEyePos();

        Thread progressReportThread = new Thread(new ProgressReportTask(client, craftverbClient));
        progressReportThread.start();

        try {
            CraftverbClient.sendPlayerMessage(client, "Running acoustic simulation...", new Formatting[]{Formatting.GOLD, Formatting.BOLD});
            CraftverbClient.sendPlayerMessage(client, "(Grab some coffee, this is going to take a while.)", new Formatting[]{Formatting.GRAY, Formatting.ITALIC});

            for (float pitch = -90.0F; pitch <= 90.0F; pitch += 0.1F) {
                int numYawRays = (int) Math.round(VectorUtils.getScaledRaysForPitch(90 - Math.abs(pitch)));

                for (int yawRays = 0; yawRays < numYawRays; yawRays++) {
                    float yaw = ((((float) yawRays * 360.0F) / (float) numYawRays) / 10) - 180.0F;
                    Vec3d currentDir = receiver.getRotationVector(pitch, yaw);
                    CraftverbClient.raysSubmitted++;
                    //System.out.println("Submitting ray: " + CraftverbClient.raysSubmitted); 6176443
                    craftverbClient.rayPool.submit(new CastRayTask(new Ray(startPos, currentDir, receiver, transmitter), client, craftverbClient));
                }
                try {
                    Thread.sleep(30);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Finished populating rays.");
        } finally {
            // Shut down the pool to release resources
            craftverbClient.rayPool.shutdown();
            new BuildIRTask(client, craftverbClient).run();
        }
    }
}
