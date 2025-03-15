package net.fg83.rdrgen.task;

import net.fg83.rdrgen.Ray;
import net.fg83.rdrgen.VectorUtils;
import net.fg83.rdrgen.client.RDRGClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/**
 * A task responsible for populating acoustic simulation rays between a transmitter and a receiver
 * in a Minecraft environment. This task submits rays to an execution pool and manages their computation.
 * The task is designed to be executed in a multithreaded context.
 */
public class PopulateRaysTask implements Runnable{

    MinecraftClient client;
    RDRGClient companionClient;

    Entity transmitter;
    Entity receiver;

    public PopulateRaysTask(Entity transmitter, Entity receiver, MinecraftClient client, RDRGClient companionClient) {
        this.transmitter = transmitter;
        this.receiver = receiver;
        this.client = client;
        this.companionClient = companionClient;
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

        Thread progressReportThread = new Thread(new ProgressReportTask(client, companionClient));
        progressReportThread.start();

        try {
            RDRGClient.sendPlayerMessage(client, "Running acoustic simulation...", new Formatting[]{Formatting.GOLD, Formatting.BOLD});
            RDRGClient.sendPlayerMessage(client, "(Grab some coffee, this is going to take a while.)", new Formatting[]{Formatting.GRAY, Formatting.ITALIC});

            for (float pitch = -90.0F; pitch <= 90.0F; pitch += 0.1F) {
                int numYawRays = (int) Math.round(VectorUtils.getScaledRaysForPitch(90 - Math.abs(pitch)));

                for (int yawRays = 0; yawRays < numYawRays; yawRays++) {
                    float yaw = ((((float) yawRays * 360.0F) / (float) numYawRays) / 10) - 180.0F;
                    Vec3d currentDir = receiver.getRotationVector(pitch, yaw);
                    RDRGClient.raysSubmitted++;
                    companionClient.rayPool.submit(new CastRayTask(new Ray(startPos, currentDir, receiver, transmitter), client, companionClient));
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
            companionClient.rayPool.shutdown();
            new BuildIRTask(client, companionClient).run();
        }
    }
}
