package net.fg83.craftverb;

import net.fg83.craftverb.client.CraftverbClient;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Ray {
    private final Vec3d startPos;
    private Vec3d currentPos;
    private Vec3d currentDir;
    private Entity castEntity;
    private boolean traced = false;
    private AtomicBoolean tracing = new AtomicBoolean(false);

    private Map<Integer, Double> energy;  // Map frequency (Hz) to energy
    private double totalDistance = 0;  // Total distance the ray has traveled

    // Constants for frequency bands (fixed for your 6 bands)
    public static final int[] FREQUENCY_BANDS = {125, 250, 500, 1000, 2000, 4000};

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

    public void trace(){
        tracing.set(true);
        while (totalDistance < 1700) {
            HitResult hitResult = performRaycast(currentPos, currentDir, 128 - (totalDistance / 2), castEntity);

            if (totalDistance > 1 && VectorUtils.doesVectorPassThroughPoint(currentPos, castEntity.getEyePos(), currentDir, 1)){
                totalDistance += currentPos.distanceTo(castEntity.getEyePos());

                //System.out.println("Hit origin!");  // Log the block type
                //System.out.println("Energy: " + energy.toString());
                //System.out.println("Delay: " + calculateDelaySamples() + " (" + totalDistance + " meters)");
                break;
            }

            if (hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                Vec3d hitPos = blockHitResult.getPos();
                double correctedX = (double) Math.round(hitPos.getX() * 100) / 100;
                double correctedY = (double) Math.round(hitPos.getY() * 100) / 100;
                double correctedZ = (double) Math.round(hitPos.getZ() * 100) / 100;
                Vec3d correctedHit = new Vec3d(correctedX, correctedY, correctedZ);

                BlockState blockState = castEntity.getWorld().getBlockState(blockHitResult.getBlockPos());  // Get the block state at the position

                //System.out.println("Hit block " + blockState.getBlock().toString() + " at " + correctedHit.toString() + " (Total Distance: " + totalDistance + ")");  // Log the block type

                String coefficientKey = fetchCoefficientKey(blockState.getBlock().toString());
                List<AbsorptionCoefficient> absorptionCoefficients = getAbsorptionCoefficients(coefficientKey);
                if (absorptionCoefficients == null){
                    System.out.println("Missing absorption coefficients for: " + coefficientKey);
                    break;
                }

                double castDistance = currentDir.distanceTo(correctedHit);

                applyDistanceAttenuation(castDistance);
                applyMaterialAttenuation(absorptionCoefficients);

                totalDistance += castDistance;
                /*if (castEntity.getPos().distanceTo(new Vec3d(correctedHit.getX(), correctedHit.getY(), correctedHit.getZ())) <= 1) {
                    System.out.println("Hit origin!");  // Log the block type
                    System.out.println("Energy: " + energy.toString());
                    System.out.println("Delay: " + calculateDelaySamples() + " (" + totalDistance + " meters)");
                    break;  // If the ray hits the entity
                }*/

                Vec3d hitNormal = new Vec3d(blockHitResult.getSide().getUnitVector().normalize());
                currentDir = Ray.asVec3d(Ray.asVector3d(currentDir).reflect(Ray.asVector3d(hitNormal)));
                currentPos = correctedHit;  // Update starting point for the next ray
            }
            else {
                break;
            }
        }
        //System.out.println("MISSED");
        tracing.set(false);
        traced = true;
    }

    private HitResult performRaycast(Vec3d startPos, Vec3d direction, double maxDistance, Entity entity) {

        RaycastContext context = new RaycastContext(startPos, startPos.add(direction.multiply(maxDistance)), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity);
        return entity.getWorld().raycast(context);  // Perform the raycast
    }

    private boolean onEdge(Vec3d hitPos){
        List<Integer> integerBlockBounds = new ArrayList<>(List.of(0, 0, 0));
        if (hitPos.getX() % 1 == 0){
            integerBlockBounds.set(0, 1);
        }
        if (hitPos.getY() % 1 == 0){
            integerBlockBounds.set(1, 1);
        }
        if (hitPos.getZ() % 1 == 0){
            integerBlockBounds.set(2, 1);
        }

        AtomicInteger edgeCount = new AtomicInteger();
        integerBlockBounds.forEach(edgeCount::addAndGet);
        if (edgeCount.get() > 1) {
            return true;
        }
        else {
            return false;
        }
    }

    private String fetchCoefficientKey(String blockNameRaw){
        String blockName = blockNameRaw.replace("Block{", "").replace("}", "");
        String coefficientKey = CraftverbClient.blockCoefficientKeys.get(blockName);
        return coefficientKey;
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
                double newLevel = current - (castDistance * (current * coef));
            //    newLevel = (newLevel < 0) ? 0 : newLevel;
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
                double newLevel = current - (current * coef);
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
