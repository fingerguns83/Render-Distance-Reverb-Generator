package net.fg83.craftverb;

import net.fg83.craftverb.client.CraftverbClient;
import net.fg83.craftverb.task.CastRayTask;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Ray {
    private final Vec3d startPos;
    private Vec3d currentPos;
    private Vec3d currentDir;
    private final Entity castEntity;
    private boolean hitTarget = false;
    private boolean traced = false;
    private final AtomicBoolean tracing = new AtomicBoolean(false);

    private final Map<Integer, Double> energy;  // Map frequency (Hz) to energy
    private double totalDistance = 0;  // Total distance the ray has traveled

    // Constants for frequency bands (fixed for your 6 bands)
    public static final int[] FREQUENCY_BANDS = {125, 250, 500, 1000, 2000, 4000};
    public final int MAX_DISTANCE = 512;

    // Constructor to initialize ray with energy values for each frequency band
    public Ray(Vec3d pos, Vec3d dir, Entity castEntity) {
        this.startPos = pos;
        this.currentPos = pos;
        this.currentDir = dir;
        this.castEntity = castEntity;

        this.energy = new HashMap<>();
        for (int frequencyBand : FREQUENCY_BANDS) {
            this.energy.put(frequencyBand, 1.0);
        }
    }

    public Ray(Vec3d pos, Vec3d dir, Entity castEntity, double totalDistance, Map<Integer, Double> energy) {
        this.startPos = pos;
        this.currentPos = pos;
        this.currentDir = dir;
        this.castEntity = castEntity;
        this.totalDistance = totalDistance;
        this.energy = energy;
    }

    public boolean didHitTarget() {
        return hitTarget;
    }

    public void trace(){
        tracing.set(true);
        while (totalDistance < MAX_DISTANCE) {
            HitResult hitResult = performRaycast(currentPos, currentDir, (MAX_DISTANCE - totalDistance) / 2, castEntity);

            if (totalDistance > 0 && VectorUtils.doesVectorPassThroughPoint(currentPos, castEntity.getEyePos(), currentDir, 1)){
                totalDistance += currentPos.distanceTo(castEntity.getEyePos());

                //System.out.println("Hit origin!");  // Log the block type
                //System.out.println("Energy: " + energy.toString());
                //System.out.println("Delay: " + calculateDelaySamples() + " (" + totalDistance + " meters)");
                this.hitTarget = true;
                break;
            }

            if (hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                Vec3d hitPos = blockHitResult.getPos();
                double correctedX = (double) Math.round(hitPos.getX() * 10000) / 10000;
                double correctedY = (double) Math.round(hitPos.getY() * 10000) / 10000;
                double correctedZ = (double) Math.round(hitPos.getZ() * 10000) / 10000;
                Vec3d correctedHit = new Vec3d(correctedX, correctedY, correctedZ);


                double castDistance = currentDir.distanceTo(correctedHit);
                applyDistanceAttenuation(castDistance);
                totalDistance += castDistance;

                List<BlockPos> sharedBlocks = VectorUtils.getSharedBlocks(blockHitResult, correctedHit);

                sharedBlocks.forEach(blockPos -> {
                    BlockState blockState = castEntity.getWorld().getBlockState(blockPos);  // Get the block state at the position
                    if (!blockState.isAir()){
                        String coefficientKey = fetchCoefficientKey(blockState.getBlock().toString());
                        List<AbsorptionCoefficient> absorptionCoefficients = getAbsorptionCoefficients(coefficientKey);
                        if (absorptionCoefficients != null){
                            applyMaterialAttenuation(absorptionCoefficients);
                        }
                    }
                });

                Vec3d summedNormal = VectorUtils.getSummedNormal(blockHitResult, sharedBlocks, castEntity.getWorld());
                currentDir = Ray.asVec3d(Ray.asVector3d(currentDir).reflect(Ray.asVector3d(summedNormal)));
                currentPos = correctedHit;  // Update starting point for the next ray
            }
            else {
                break;
            }
        }
        tracing.set(false);
        traced = true;
    }

    private HitResult performRaycast(Vec3d startPos, Vec3d direction, double maxDistance, Entity entity) {

        RaycastContext context = new RaycastContext(startPos, startPos.add(direction.multiply(maxDistance)), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity);
        return entity.getWorld().raycast(context);  // Perform the raycast
    }

    private String fetchCoefficientKey(String blockNameRaw){
        String blockName = blockNameRaw.replace("Block{", "").replace("}", "");
        return CraftverbClient.blockCoefficientKeys.get(blockName);
    }

    private List<AbsorptionCoefficient> getAbsorptionCoefficients(String key){
        return CraftverbClient.absorptionCoefficients.getOrDefault(key, null);
    }

    private void applyDistanceAttenuation(double castDistance){
        Medium.AIR.forEach(absorptionCoefficient -> {
            int freq = absorptionCoefficient.getFrequency();
            double coef = absorptionCoefficient.getCoefficient();
            double current = energy.get(freq);
            //if (current > 0){
                double newLevel = current * (Math.pow((1 - coef), castDistance));
                energy.put(freq, newLevel);
            //}
        });
    }
    private void applyMaterialAttenuation(List<AbsorptionCoefficient> coefficients){
        coefficients.forEach(coefficient -> {
            int freq = coefficient.getFrequency();
            double coef = coefficient.getCoefficient();
            double current = energy.get(freq);
            //if (current > 0){
                double newLevel = current * (1 - coef);
              //  newLevel = (newLevel < 0) ? 0 : newLevel;
                energy.put(freq, newLevel);
            //}
        });
    }

    private double calculateDelaySamples(){
        return Math.round(totalDistance * 140.16);
    }

    public static Vec3d asVec3d(Vector3d vector) {
        return new Vec3d(vector.x, vector.y, vector.z);
    }
    public static Vector3d asVector3d(Vec3d vector) {
        return new Vector3d(vector.x, vector.y, vector.z);
    }

    public boolean isFinishedTracing() {
        return traced;
    }

    public void setFinished(boolean traced) {
        this.traced = traced;
    }

    public boolean isTracing() {
        return tracing.get();
    }

    public void setTracing(boolean tracing) {
        this.tracing.compareAndSet(false, tracing);
    }

    public Double getDelayTime() {
        return calculateDelaySamples();
    }

    public Map<Integer, Double> getEnergy() {
        return energy;
    }
}
