package net.fg83.rdrgen;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Utility class for performing various vector-related operations, especially in the
 * context of 3D space calculations such as block interaction, surface detection, and
 * vector alignment.
 */
public class VectorUtils {

    /**
     * Checks if a vector `v`, starting at point `p`, passes through any point within `marginOfError` of `p1`.
     *
     * @param startPoint             The starting point of the vector (Vec3d).
     * @param testPoint            The point to check against (Vec3d).
     * @param v             The direction vector (Vec3d).
     * @param marginOfError The margin of error (double).
     * @return true if the vector passes within the margin of error of `p1`, false otherwise.
     */
    public static boolean doesVectorPassThroughPoint(Vec3d startPoint, Vec3d testPoint, Vec3d v, double marginOfError) {
        // Calculate the differences between p1 and p
        double dx = testPoint.x - startPoint.x;
        double dy = testPoint.y - startPoint.y;
        double dz = testPoint.z - startPoint.z;

        // Avoid division by zero and calculate t values
        double tx = v.x != 0 ? dx / v.x : Double.NaN;
        double ty = v.y != 0 ? dy / v.y : Double.NaN;
        double tz = v.z != 0 ? dz / v.z : Double.NaN;

        // Check consistency of t values
        Double consistentT = null; // Track the consistent t value, if any
        if (!Double.isNaN(tx)) consistentT = tx;
        if (!Double.isNaN(ty)) {
            if (consistentT == null) consistentT = ty;
            else if (Math.abs(ty - consistentT) > marginOfError) return false;
        }
        if (!Double.isNaN(tz)) {
            if (consistentT == null) consistentT = tz;
            else if (Math.abs(tz - consistentT) > marginOfError) return false;
        }

        // If a consistent t exists, calculate the point along the vector at t
        if (consistentT != null) {
            Vec3d pointOnVector = startPoint.add(v.multiply(consistentT));
            return pointOnVector.isInRange(testPoint, marginOfError); // Check if it's within margin of error
        }

        return false; // No consistent t found
    }

    /**
     * Determines the type of surface a point lies on, given its position.
     * The types are classified as FLAT, SEAM, or CORNER based on how many coordinates
     * of the position are integers.
     *
     * @param collisionPos The 3D position vector to check, represented as a Vec3d.
     * @return A SurfaceType indicating whether the point lies on a flat, seam, or corner surface.
     *         Returns null if none of the conditions are met.
     */
    public static SurfaceType isPointOnEdge(Vec3d collisionPos){
        int integerValues = 0;
        if (isInteger(collisionPos.x)) integerValues++;
        if (isInteger(collisionPos.y)) integerValues++;
        if (isInteger(collisionPos.z)) integerValues++;
        if (integerValues == 1){
            return SurfaceType.FLAT;
        }
        if (integerValues == 2){
            return SurfaceType.SEAM;
        }
        if (integerValues == 3){
            return SurfaceType.CORNER;
        }
        return null;
    }

    /**
     * Determines and retrieves the list of block positions that are shared or intersected
     * by the given block hit result and, if provided, a corrected hit position.
     * The method calculates which blocks are shared based on the hit position and checks
     * if the position lies on a flat surface, seam, or corner.
     *
     * @param blockHitResult The result of a block hit, containing information about the block position and hit location.
     * @param correctedHitPos An optional corrected hit position (Vec3d) that overrides the hit position in blockHitResult.
     *                        If null, the position from blockHitResult is used.
     * @return A list of BlockPos objects representing the block positions that are shared or intersected based on the hit.
     */
    public static List<BlockPos> getSharedBlocks(BlockHitResult blockHitResult, @Nullable Vec3d correctedHitPos){
        List<BlockPos> sharedBlocks = new ArrayList<>();
        Vec3d hitPos;

        if (correctedHitPos != null){
            hitPos = correctedHitPos;
        }
        else {
             hitPos = blockHitResult.getPos();
        }

        double x = hitPos.x;
        double y = hitPos.y;
        double z = hitPos.z;

        // Check which coordinates are integers
        boolean isXInt = isInteger(x);
        boolean isYInt = isInteger(y);
        boolean isZInt = isInteger(z);

        SurfaceType surfaceType = isPointOnEdge(hitPos);

        BlockPos hitBlockPos = blockHitResult.getBlockPos();
        sharedBlocks.add(hitBlockPos);

        if (surfaceType == SurfaceType.CORNER || surfaceType == SurfaceType.SEAM){
            List<Direction> offsets = new ArrayList<>();

            if (isXInt){
                if (Math.round(x) == hitBlockPos.getX()){
                    offsets.add(Direction.WEST);
                }
                else {
                    offsets.add(Direction.EAST);
                }
            }
            if (isYInt){
                if (Math.round(y) == hitBlockPos.getZ()){
                    offsets.add(Direction.NORTH);
                }
                else {
                    offsets.add(Direction.SOUTH);
                }
            }
            if (isZInt){
                if (Math.round(z) == hitBlockPos.getY()){
                    offsets.add(Direction.DOWN);
                }
                else {
                    offsets.add(Direction.UP);
                }
            }

            if (offsets.size() == 2){
                sharedBlocks.add(hitBlockPos.offset(offsets.get(0)).offset(offsets.get(1)));
            }
            else if (offsets.size() == 3){
                sharedBlocks.add(hitBlockPos.offset(offsets.get(0)).offset(offsets.get(1)));
                sharedBlocks.add(hitBlockPos.offset(offsets.get(0)).offset(offsets.get(1)).offset(offsets.get(2)));
            }
            else {
                throw new RuntimeException("Something went wrong with seam/corner calculation. Expected 2 or 3 offsets, got " + offsets.size() + " instead.");
            }
        }

        return sharedBlocks;
    }

    /**
     * Calculates and returns a normalized vector that represents the summed normal direction
     * based on the block hit result, the provided list of shared blocks, and the world state.
     * The method takes into account air blocks and the direction of neighboring blocks.
     *
     * @param blockHitResult The result of the block hit, used to determine the initial direction and position.
     * @param sharedBlocks A list of block positions that are shared or intersected by the hit.
     *                     The size of the list determines how the summed normal is calculated.
     * @param world The world object, used to verify the state of the blocks.
     * @return A normalized Vec3d representing the summed normal direction.
     * @throws RuntimeException If the size of sharedBlocks is not 1, 2, or 3.
     */
    public static Vec3d getSummedNormal(BlockHitResult blockHitResult, List<BlockPos> sharedBlocks, World world) {
        final int SINGLE_BLOCK = 1;
        final int TWO_BLOCKS = 2;
        final int THREE_BLOCKS = 3;

        Vec3d summedNormal = new Vec3d(blockHitResult.getSide().getUnitVector());

        if (sharedBlocks.size() == SINGLE_BLOCK) {
            return summedNormal;
        }

        if (sharedBlocks.size() == TWO_BLOCKS) {
            if (world.getBlockState(sharedBlocks.get(1)).isAir()) {
                return summedNormal;
            }
            addDirectionToSummedNormal(blockHitResult, sharedBlocks.get(1), summedNormal);
        } else if (sharedBlocks.size() == THREE_BLOCKS) {
            boolean secondBlockIsAir = world.getBlockState(sharedBlocks.get(1)).isAir();
            boolean thirdBlockIsAir = world.getBlockState(sharedBlocks.get(2)).isAir();
            if (secondBlockIsAir && thirdBlockIsAir) {
                return summedNormal;
            }
            if (!secondBlockIsAir) {
                addDirectionToSummedNormal(blockHitResult, sharedBlocks.get(1), summedNormal);
            }
            if (!thirdBlockIsAir) {
                addDirectionToSummedNormal(blockHitResult, sharedBlocks.get(2), summedNormal);
            }
        } else {
            throw new RuntimeException("Unexpected number of sharedBlocks: " + sharedBlocks.size());
        }

        return summedNormal.normalize();
    }

    /**
     * Adds a directional unit vector to the given summedNormal based on
     * the positional relationship between blockPos and the block position in blockHitResult.
     * The direction added corresponds to one of the six cardinal directions (NORTH, SOUTH, EAST, WEST, UP, DOWN).
     *
     * @param blockHitResult The result of a block hit, containing information about the block position.
     * @param blockPos       The position of the block being compared.
     * @param summedNormal   The vector to which the directional unit vector is added.
     */
    private static void addDirectionToSummedNormal(BlockHitResult blockHitResult, BlockPos blockPos, Vec3d summedNormal) {
        if (blockPos.getX() < blockHitResult.getBlockPos().getX()) {
            summedNormal.add(new Vec3d(Direction.EAST.getUnitVector()));
        } else if (blockPos.getX() > blockHitResult.getBlockPos().getX()) {
            summedNormal.add(new Vec3d(Direction.WEST.getUnitVector()));
        } else if (blockPos.getY() < blockHitResult.getBlockPos().getY()) {
            summedNormal.add(new Vec3d(Direction.UP.getUnitVector()));
        } else if (blockPos.getY() > blockHitResult.getBlockPos().getY()) {
            summedNormal.add(new Vec3d(Direction.DOWN.getUnitVector()));
        } else if (blockPos.getZ() < blockHitResult.getBlockPos().getZ()) {
            summedNormal.add(new Vec3d(Direction.NORTH.getUnitVector()));
        } else if (blockPos.getZ() > blockHitResult.getBlockPos().getZ()) {
            summedNormal.add(new Vec3d(Direction.SOUTH.getUnitVector()));
        }
    }

    public static boolean isInteger(double value) {
        return Math.abs(value - Math.round(value)) < 1e-6; // Adjust tolerance for floating-point precision
    }

    /**
     * Calculates a scaled value based on the input pitch using an exponential scaling formula.
     *
     * @param pitch The pitch value, typically in degrees, used to compute the scaled result.
     * @return A scaled double value derived from the given pitch.
     */
    public static double getScaledRaysForPitch(float pitch) {
        double b = Math.log(36000) / 90;
        return Math.exp(b * pitch) + 1080;
    }


}
