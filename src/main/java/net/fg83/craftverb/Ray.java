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

public class Ray {
    private final Vec3d startPosition;
    private final Vec3d currentPosition;
    private final Vec3d currentDirection;
    private final Entity castingEntity;
    private boolean hasHitTarget = false;
    private final Map<Integer, Double> energy;  // Map frequency (Hz) to energy
    private double totalTraveledDistance = 0;  // Total distance the ray has traveled

    // Constants for frequency bands
    public static final int[] FREQUENCY_BANDS = {125, 250, 500, 1000, 2000, 4000};
    public static final int DEFAULT_MAX_DISTANCE = 512;

    // Constructor to initialize ray with energy values for each frequency band
    public Ray(Vec3d position, Vec3d direction, Entity castingEntity) {
        this.startPosition = position;
        this.currentPosition = position;
        this.currentDirection = direction;
        this.castingEntity = castingEntity;
        this.energy = initializeEnergyValues();
    }

    public Ray(Vec3d position, Vec3d direction, Entity castingEntity, double totalTraveledDistance, Map<Integer, Double> initialEnergy) {
        this.startPosition = position;
        this.currentPosition = position;
        this.currentDirection = direction;
        this.castingEntity = castingEntity;
        this.totalTraveledDistance = totalTraveledDistance;
        this.energy = new HashMap<>(initialEnergy);
    }

    private Map<Integer, Double> initializeEnergyValues() {
        Map<Integer, Double> energyMap = new HashMap<>();
        for (int frequency : FREQUENCY_BANDS) {
            energyMap.put(frequency, 1.0);
        }
        return energyMap;
    }

    public boolean didHitTarget() {
        return hasHitTarget;
    }

    public void trace(CraftverbClient craftverbClient, MinecraftClient client) {
        if (totalTraveledDistance >= DEFAULT_MAX_DISTANCE) {
            return;
        }

        HitResult hitResult = performRaycast(currentPosition, currentDirection, (DEFAULT_MAX_DISTANCE - totalTraveledDistance), castingEntity);
        if (isPassingThroughOrigin()) {
            handleTargetHit(castingEntity.getEyePos());
            return;
        }

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult) hitResult;
            Vec3d roundedHitPos = roundHitPosition(blockHitResult.getPos());
            handleBlockHit(blockHitResult, roundedHitPos, craftverbClient, client);
        }
    }

    private boolean isPassingThroughOrigin() {
        return totalTraveledDistance > 0 && VectorUtils.doesVectorPassThroughPoint(currentPosition, castingEntity.getEyePos(), currentDirection, 1);
    }

    private void handleTargetHit(Vec3d eyePosition) {
        totalTraveledDistance += currentPosition.distanceTo(eyePosition);
        this.hasHitTarget = true;
    }

    private Vec3d roundHitPosition(Vec3d hitPos) {
        return new Vec3d(
                roundToPrecision(hitPos.getX()),
                roundToPrecision(hitPos.getY()),
                roundToPrecision(hitPos.getZ())
        );
    }

    private double roundToPrecision(double value) {
        return (double) Math.round(value * (double) 10000) / (double) 10000;
    }

    private void handleBlockHit(BlockHitResult blockHitResult, Vec3d roundedHitPos, CraftverbClient craftverbClient, MinecraftClient client) {
        double castDistance = currentPosition.distanceTo(roundedHitPos);
        applyDistanceAttenuation(castDistance);
        totalTraveledDistance += castDistance;

        List<BlockPos> intersectedBlocks = VectorUtils.getSharedBlocks(blockHitResult, roundedHitPos);
        processBlockMaterials(intersectedBlocks);

        if (isDissipated()) {
            return;
        }

        Vec3d summedNormal = VectorUtils.getSummedNormal(blockHitResult, intersectedBlocks, castingEntity.getWorld());
        SurfaceType surfaceType = VectorUtils.isPointOnEdge(roundedHitPos);
        submitDiffuseRays(craftverbClient, client, roundedHitPos, summedNormal, surfaceType);
    }

    private void processBlockMaterials(List<BlockPos> intersectedBlocks) {
        intersectedBlocks.forEach(blockPos -> {
            BlockState blockState = castingEntity.getWorld().getBlockState(blockPos);
            if (!blockState.isAir()) {
                String coefficientKey = fetchCoefficientKey(blockState.getBlock().toString());
                List<AbsorptionCoefficient> absorptionCoefficients = getAbsorptionCoefficients(coefficientKey);
                if (absorptionCoefficients != null) {
                    applyMaterialAttenuation(absorptionCoefficients);
                }
            }
        });
    }

    private void submitDiffuseRays(CraftverbClient craftverbClient, MinecraftClient client, Vec3d startPos, Vec3d summedNormal, SurfaceType surfaceType) {
        int diffuseRays = calculateDiffuseRays(surfaceType);
        Random random = new Random();

        for (int i = 0; i < diffuseRays; i++) {
            Vec3d direction = generateDirectionForSurfaceType(surfaceType, summedNormal, random);
            craftverbClient.rayPool.submit(() -> new CastRayTask(
                    new Ray(startPos, direction, castingEntity, totalTraveledDistance, energy), client, craftverbClient).run());
        }
    }

    private int calculateDiffuseRays(SurfaceType surfaceType) {
        final int BASE_DIFFUSE_RAYS = 64;
        return switch (surfaceType) {
            case FLAT -> BASE_DIFFUSE_RAYS;
            case SEAM -> BASE_DIFFUSE_RAYS / 2;
            case CORNER -> BASE_DIFFUSE_RAYS / 4;
        };
    }

    private Vec3d generateDirectionForSurfaceType(SurfaceType surfaceType, Vec3d normal, Random random) {
        return switch (surfaceType) {
            case FLAT -> VectorUtils.generateRandomHemisphereDirection(normal, random);
            case SEAM -> VectorUtils.generateRandomQuarterSphereDirection(normal, random);
            case CORNER -> VectorUtils.generateRandomEighthSphereDirection(normal, random);
        };
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
        return Math.round(totalTraveledDistance * 140.16);
    }

    public static Vec3d asVec3d(Vector3d vector) {
        return new Vec3d(vector.x, vector.y, vector.z);
    }
    public static Vector3d asVector3d(Vec3d vector) {
        return new Vector3d(vector.x, vector.y, vector.z);
    }

    public Double getDelayTime() {
        return calculateDelaySamples();
    }

    public Map<Integer, Double> getEnergy() {
        return energy;
    }

    public boolean isDissipated(){
        return energy.values().stream().allMatch(e -> e <= 0.000001);
    }
}
