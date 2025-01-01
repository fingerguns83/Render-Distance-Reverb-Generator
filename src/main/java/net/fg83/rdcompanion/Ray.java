package net.fg83.rdcompanion;

import net.fg83.rdcompanion.client.RDRCompanionClient;
import net.minecraft.block.BlockState;
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

/**
 * Represents a class that simulates the properties and behavior of a ray traveling through
 * a 3D environment. The ray can interact with blocks and entities, attenuate energy over
 * distance, and reflect or transmit when hitting materials, based on their properties.
 *
 * The Ray class maintains state information about its energy across different frequency bands,
 * total traveled distance, and whether it has reached its target. It provides methods to
 * trace the ray's path and process interactions along its trajectory.
 *
 * Core features include:
 * - Initialization of energy values for specified frequency bands.
 * - Dynamic tracing to process collisions and energy dissipation.
 * - Interaction with material properties of intersected blocks.
 * - Determination of successful ray delivery to a target.
 *
 * This class is suitable for applications that require ray simulations for environmental interactions,
 * such as optics, acoustics, or other ray-tracing systems.
 */
public class Ray {
    private Vec3d currentPosition;
    private Vec3d currentDirection;
    private final Entity castingEntity;
    private final Entity targetEntity;
    private boolean hasHitTarget = false;
    private final Map<Integer, Double> energy;  // Map frequency (Hz) to energy
    private double totalTraveledDistance = 0;  // Total distance the ray has traveled

    // Constants for frequency bands
    public static final int[] FREQUENCY_BANDS = {125, 250, 500, 1000, 2000, 4000};
    public static final int DEFAULT_MAX_DISTANCE = 1700;

    // Constructor to initialize ray with energy values for each frequency band
    public Ray(Vec3d position, Vec3d direction, Entity castingEntity, Entity targetEntity) {
        this.currentPosition = position;
        this.currentDirection = direction;
        this.castingEntity = castingEntity;
        this.targetEntity = targetEntity;
        this.energy = initializeEnergyValues();
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

    /**
     * Continuously traces the ray's path in the given direction, iterating through
     * potential collisions and interactions with blocks or entities until a specific stopping
     * condition is reached.
     *
     * The trace operates as follows:
     * - Performs raycasting to detect intersections along the ray's path.
     * - Checks if the ray passes through the target entity. If so, it handles the target hit
     *   and terminates further tracing.
     * - Processes collisions with blocks by computing the hit position and applying relevant
     *   effects based on the block's properties, including energy attenuation and direction reflection.
     * - Stops tracing if the ray's energy is fully dissipated or if there are no blocks/entities
     *   to further interact with along the path.
     *
     * This method is essential for simulating the behavior of rays traveling through the environment,
     * accounting for interactions with targets, block materials, and energy dissipation.
     */
    public void trace() {
        while (true) {
            HitResult hitResult = performRaycast(currentPosition, currentDirection, castingEntity);
            if (isPassingThroughTarget()) {
                handleTargetHit(castingEntity.getEyePos());
                return;
            }

            if (hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                Vec3d roundedHitPos = roundHitPosition(blockHitResult.getPos());
                handleBlockHit(blockHitResult, roundedHitPos);
            }
            else {
                return;
            }

            if (isDissipated()) {
                return;
            }
        }
    }

    /**
     * Determines whether the ray is passing through the target entity. This is evaluated by checking
     * if the ray's total traveled distance is greater than zero and if its current direction, starting
     * from the ray's current position, intersects with the target entity's eye position based on the given precision.
     *
     * @return true if the ray's path passes through the target entity, false otherwise.
     */
    private boolean isPassingThroughTarget() {
        return totalTraveledDistance > 0 && VectorUtils.doesVectorPassThroughPoint(currentPosition, targetEntity.getEyePos(), currentDirection, 1);
    }

    /**
     * Handles the event when the ray hits its target.
     * Updates the total distance traveled by the ray using the position of the target's eye
     * and marks the ray as having hit the target.
     *
     * @param eyePosition The eye position of the target entity that the ray intersects with.
     */
    private void handleTargetHit(Vec3d eyePosition) {
        totalTraveledDistance += currentPosition.distanceTo(eyePosition);
        this.hasHitTarget = true;
    }

    /**
     * Rounds the coordinates of a given 3D vector to a specified precision.
     *
     * @param hitPos the original position represented as a 3D vector (Vec3d) to be rounded.
     * @return a new Vec3d object with its coordinates rounded to the defined precision.
     */
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

    /**
     * Handles the event when the ray intersects with a block along its path.
     * This method processes the block hit by calculating the distance,
     * applying attenuation effects, identifying intersected blocks, and reflecting the ray's direction.
     *
     * @param blockHitResult The result of the block hit, containing details about the hit location
     *                       and normal data.
     * @param roundedHitPos  The rounded position of the hit intersection, used for calculations
     *                       and updates to the ray's state.
     */
    private void handleBlockHit(BlockHitResult blockHitResult, Vec3d roundedHitPos) {
        double castDistance = currentPosition.distanceTo(roundedHitPos);
        applyDistanceAttenuation(castDistance);
        totalTraveledDistance += castDistance;

        List<BlockPos> intersectedBlocks = VectorUtils.getSharedBlocks(blockHitResult, roundedHitPos);
        processBlockMaterials(intersectedBlocks);

        Vec3d summedNormal = VectorUtils.getSummedNormal(blockHitResult, intersectedBlocks, castingEntity.getWorld());

        currentDirection = Ray.asVec3d(Ray.asVector3d(currentDirection).reflect(Ray.asVector3d(summedNormal)));
        currentPosition = roundedHitPos;
    }

    /**
     * Processes a list of block positions to evaluate their material properties and apply
     * relevant attenuation effects based on predefined absorption coefficients.
     * For each block in the provided list, its block state is evaluated. If the block is not air,
     * its material's attenuation coefficients are retrieved and applied to modify the energy levels.
     *
     * @param intersectedBlocks a list of block positions (BlockPos objects) representing
     *                           intersected blocks along the ray's path that will be processed
     *                           for material attenuation effects.
     */
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

    /**
     * Performs a raycast from a starting position in a specific direction, considering potential collisions
     * with blocks or entities in the world. The raycast uses the provided entity as the source of the context.
     *
     * @param startPos The starting position of the raycast, represented as a 3D vector.
     * @param direction The direction vector of the raycast, representing the direction in which the ray is cast.
     * @param entity The entity associated with the raycast, used for determining the context and exclusions.
     * @return A HitResult object containing details about the nearest collision point,
     *         or null if no collision occurs within the maximum ray distance.
     */
    private HitResult performRaycast(Vec3d startPos, Vec3d direction, Entity entity) {

        RaycastContext context = new RaycastContext(startPos, startPos.add(direction.multiply(Ray.DEFAULT_MAX_DISTANCE)), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity);
        return entity.getWorld().raycast(context);  // Perform the raycast
    }

    /**
     * Retrieves the coefficient key associated with a specific block name by
     * processing the raw block name format and mapping it to the corresponding key.
     * The method removes unnecessary syntax from the raw block name and fetches
     * the coefficient key from a predefined mapping.
     *
     * @param blockNameRaw the raw string representation of the block name, typically
     *                     containing additional syntax that needs to be stripped.
     * @return the associated coefficient key as a string if found in the mapping;
     *         null otherwise.
     */
    private String fetchCoefficientKey(String blockNameRaw){
        String blockName = blockNameRaw.replace("Block{", "").replace("}", "");
        return RDRCompanionClient.blockCoefficientKeys.get(blockName);
    }

    /**
     * Retrieves a list of absorption coefficients associated with the specified key.
     * The absorption coefficients determine the attenuation properties for different frequency bands.
     * If the provided key is not found, the method returns null.
     *
     * @param key the key used to fetch the corresponding absorption coefficients from the internal mapping.
     * @return a list of AbsorptionCoefficient objects corresponding to the specified key,
     *         or null if the key does not exist in the mapping.
     */
    private List<AbsorptionCoefficient> getAbsorptionCoefficients(String key){
        return RDRCompanionClient.absorptionCoefficients.getOrDefault(key, null);
    }

    /**
     * Applies distance-based energy attenuation to the ray based on the absorption coefficients
     * of the surrounding medium. For each frequency band, the energy is reduced proportionally
     * to the provided distance and the corresponding attenuation coefficient.
     *
     * @param castDistance The distance the ray has traveled, used to calculate the attenuation effect on energy.
     */
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
    /**
     * Applies material-based attenuation to the energy levels of each frequency band
     * by reducing the energy according to the specified absorption coefficients.
     * For each absorption coefficient, the frequency and its corresponding attenuation
     * factor are used to calculate the new energy level of the respective frequency band.
     *
     * @param coefficients a list of {@code AbsorptionCoefficient} objects,
     *                     each specifying the frequency band and its associated
     *                     attenuation coefficient to be applied.
     */
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

    /**
     * Calculates the delay of a signal in terms of samples based on the total distance
     * traveled by the ray and a constant scaling factor. The result is rounded to the
     * nearest whole number.
     *
     * @return the calculated delay in samples as a double.
     */
    private double calculateDelaySamples(){
        return Math.round(totalTraveledDistance * 140.16);
    }

    /**
     * Converts a {@code Vector3d} object into a {@code Vec3d} object.
     *
     * @param vector the {@code Vector3d} object to be converted, containing
     *               the x, y, and z coordinates to initialize the new {@code Vec3d}.
     * @return a new {@code Vec3d} object with the same x, y, and z values
     *         as the provided {@code Vector3d}.
     */
    public static Vec3d asVec3d(Vector3d vector) {
        return new Vec3d(vector.x, vector.y, vector.z);
    }
    /**
     * Converts a {@code Vec3d} object into a {@code Vector3d} object.
     *
     * @param vector the {@code Vec3d} object to be converted, containing
     *               the x, y, and z coordinates to initialize the new {@code Vector3d}.
     * @return a new {@code Vector3d} object with the same x, y, and z values
     *         as the provided {@code Vec3d}.
     */
    public static Vector3d asVector3d(Vec3d vector) {
        return new Vector3d(vector.x, vector.y, vector.z);
    }

    /**
     * Retrieves the calculated delay of a signal in terms of samples.
     * This is determined based on the total distance traveled by the ray and
     * a constant scaling factor, as computed by the {@code calculateDelaySamples} method.
     *
     * @return the delay in samples as a Double.
     */
    public Double getDelayTime() {
        return calculateDelaySamples();
    }

    public Map<Integer, Double> getEnergy() {
        return energy;
    }

    public boolean isDissipated(){
        return energy.values().stream().allMatch(e -> e <= 0.00000001);
    }
}
