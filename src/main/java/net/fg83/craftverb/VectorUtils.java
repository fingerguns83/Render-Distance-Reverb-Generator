package net.fg83.craftverb;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

        SurfaceType unspecificSurfaceType = isPointOnEdge(hitPos);

        BlockPos hitBlockPos = blockHitResult.getBlockPos();
        sharedBlocks.add(hitBlockPos);

        if (unspecificSurfaceType == SurfaceType.CORNER || unspecificSurfaceType == SurfaceType.SEAM){
            List<Direction> offsets = new ArrayList<>();

            if (isXInt && Math.round(x) == hitBlockPos.getX())      {offsets.add(Direction.WEST);}
            else if (isXInt && Math.round(x) == hitBlockPos.getX() + 1)  {offsets.add(Direction.EAST);}

            if (isYInt && Math.round(y) == hitBlockPos.getZ())      {offsets.add(Direction.NORTH);}
            else if (isYInt && Math.round(y) == hitBlockPos.getZ() + 1)  {offsets.add(Direction.SOUTH);}

            if (isZInt && Math.round(z) == hitBlockPos.getY())      {offsets.add(Direction.DOWN);}
            else if (isZInt && Math.round(z) == hitBlockPos.getY() + 1)  {offsets.add(Direction.UP);}



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

    public static Vec3d getSummedNormal(BlockHitResult blockHitResult, List<BlockPos> sharedBlocks, World world){
        Vec3d summedNormal = new Vec3d(blockHitResult.getSide().getUnitVector().normalize());;

        if (sharedBlocks.size() == 1){
            return summedNormal;
        }
        else if (sharedBlocks.size() == 2){
            if (world.getBlockState(sharedBlocks.get(1)).isAir()) {
                //System.out.println("Hit false seam at " + blockHitResult.getPos().x + ", " + blockHitResult.getPos().y + ", " + blockHitResult.getPos().z);
                return summedNormal;
            }

            //System.out.println("Hit seam at " + blockHitResult.getPos().x + ", " + blockHitResult.getPos().y + ", " + blockHitResult.getPos().z);

            if (sharedBlocks.get(1).getX() < blockHitResult.getBlockPos().getX()){
                summedNormal.add(new Vec3d(Direction.EAST.getUnitVector().normalize()));
            }
            else if (sharedBlocks.get(1).getX() > blockHitResult.getBlockPos().getX()){
                summedNormal.add(new Vec3d(Direction.WEST.getUnitVector().normalize()));
            }
            else if (sharedBlocks.get(1).getY() < blockHitResult.getBlockPos().getY()){
                summedNormal.add(new Vec3d(Direction.UP.getUnitVector().normalize()));
            }
            else if (sharedBlocks.get(1).getY() > blockHitResult.getBlockPos().getY()){
                summedNormal.add(new Vec3d(Direction.DOWN.getUnitVector().normalize()));
            }
            else if (sharedBlocks.get(1).getZ() < blockHitResult.getBlockPos().getZ()){
                summedNormal.add(new Vec3d(Direction.NORTH.getUnitVector().normalize()));
            }
            else if (sharedBlocks.get(1).getZ() > blockHitResult.getBlockPos().getZ()) {
                summedNormal.add(new Vec3d(Direction.SOUTH.getUnitVector().normalize()));
            }
            else {
                return summedNormal;
            }
        }
        else if (sharedBlocks.size() == 3){
            if (world.getBlockState(sharedBlocks.get(1)).isAir() && world.getBlockState(sharedBlocks.get(2)).isAir()) {
                //System.out.println("Hit convex corner at " + blockHitResult.getPos().x + ", " + blockHitResult.getPos().y + ", " + blockHitResult.getPos().z);
                return summedNormal;
            }
            if (!world.getBlockState(sharedBlocks.get(1)).isAir() && !world.getBlockState(sharedBlocks.get(2)).isAir()){
                //System.out.println("Hit concave corner at " + blockHitResult.getPos().x + ", " + blockHitResult.getPos().y + ", " + blockHitResult.getPos().z);
            }
            if (!world.getBlockState(sharedBlocks.get(1)).isAir() || !world.getBlockState(sharedBlocks.get(2)).isAir()){
                //System.out.println("Hit seam corner at " + blockHitResult.getPos().x + ", " + blockHitResult.getPos().y + ", " + blockHitResult.getPos().z);
            }

            if (!world.getBlockState(sharedBlocks.get(1)).isAir()){
                if (sharedBlocks.get(1).getX() < blockHitResult.getBlockPos().getX()){
                    summedNormal.add(new Vec3d(Direction.EAST.getUnitVector().normalize()));
                }
                else if (sharedBlocks.get(1).getX() > blockHitResult.getBlockPos().getX()){
                    summedNormal.add(new Vec3d(Direction.WEST.getUnitVector().normalize()));
                }
                else if (sharedBlocks.get(1).getY() < blockHitResult.getBlockPos().getY()){
                    summedNormal.add(new Vec3d(Direction.UP.getUnitVector().normalize()));
                }
                else if (sharedBlocks.get(1).getY() > blockHitResult.getBlockPos().getY()){
                    summedNormal.add(new Vec3d(Direction.DOWN.getUnitVector().normalize()));
                }
                else if (sharedBlocks.get(1).getZ() < blockHitResult.getBlockPos().getZ()){
                    summedNormal.add(new Vec3d(Direction.NORTH.getUnitVector().normalize()));
                }
                else if (sharedBlocks.get(1).getZ() > blockHitResult.getBlockPos().getZ()) {
                    summedNormal.add(new Vec3d(Direction.SOUTH.getUnitVector().normalize()));
                }
            }
            if (!world.getBlockState(sharedBlocks.get(2)).isAir()){
                if (sharedBlocks.get(2).getX() < blockHitResult.getBlockPos().getX()){
                    summedNormal.add(new Vec3d(Direction.EAST.getUnitVector().normalize()));
                }
                else if (sharedBlocks.get(2).getX() > blockHitResult.getBlockPos().getX()){
                    summedNormal.add(new Vec3d(Direction.WEST.getUnitVector().normalize()));
                }
                else if (sharedBlocks.get(2).getY() < blockHitResult.getBlockPos().getY()){
                    summedNormal.add(new Vec3d(Direction.UP.getUnitVector().normalize()));
                }
                else if (sharedBlocks.get(2).getY() > blockHitResult.getBlockPos().getY()){
                    summedNormal.add(new Vec3d(Direction.DOWN.getUnitVector().normalize()));
                }
                else if (sharedBlocks.get(2).getZ() < blockHitResult.getBlockPos().getZ()){
                    summedNormal.add(new Vec3d(Direction.NORTH.getUnitVector().normalize()));
                }
                else if (sharedBlocks.get(2).getZ() > blockHitResult.getBlockPos().getZ()) {
                    summedNormal.add(new Vec3d(Direction.SOUTH.getUnitVector().normalize()));
                }
            }

        }
        else {
            throw new RuntimeException("Something went wrong with seam/corner calculation. Expected 3 or fewer sharedBlocks, got " + sharedBlocks.size() + " instead.");
        }

        return summedNormal.normalize();
    }

    public static boolean isInteger(double value) {
        return Math.abs(value - Math.round(value)) < 1e-6; // Adjust tolerance for floating-point precision
    }


    public static Vec3d generateRandomHemisphereDirection(Vec3d normal, Random random) {
        // Generate a random direction in the +z hemisphere
        double theta = 2 * Math.PI * random.nextDouble(); // Azimuth
        double phi = Math.acos(random.nextDouble()); // Elevation

        double x = Math.sin(phi) * Math.cos(theta);
        double y = Math.sin(phi) * Math.sin(theta);
        double z = Math.cos(phi);

        Vec3d localDirection = new Vec3d(x, y, z);

        // Align the local hemisphere with the given normal
        return alignWithNormal(localDirection, normal);
    }

    public static Vec3d generateRandomQuarterSphereDirection(Vec3d normal, Random random) {
        // Generate a random direction within the quarter-sphere
        double theta = Math.PI / 2 * random.nextDouble(); // Restrict azimuth to a quarter
        double phi = Math.acos(random.nextDouble()); // Elevation

        double x = Math.sin(phi) * Math.cos(theta);
        double y = Math.sin(phi) * Math.sin(theta);
        double z = Math.cos(phi);

        Vec3d localDirection = new Vec3d(x, y, z);

        // Align the local quarter-sphere with the given normal
        return alignWithNormal(localDirection, normal);
    }

    public static Vec3d generateRandomEighthSphereDirection(Vec3d normal, Random random) {
        // Generate a random direction within the eighth-sphere
        double theta = Math.PI / 4 * random.nextDouble(); // Restrict azimuth to an eighth
        double phi = Math.acos(random.nextDouble()); // Elevation

        double x = Math.sin(phi) * Math.cos(theta);
        double y = Math.sin(phi) * Math.sin(theta);
        double z = Math.cos(phi);

        Vec3d localDirection = new Vec3d(x, y, z);

        // Align the local eighth-sphere with the given normal
        return alignWithNormal(localDirection, normal);
    }

    private static Vec3d alignWithNormal(Vec3d localDirection, Vec3d normal) {
        Vec3d up = new Vec3d(0, 0, 1); // Default "up" vector

        // If the normal is aligned with the +z axis, return the local direction as-is
        if (normal.equals(up)) {
            return localDirection;
        }

        // Calculate rotation axis and angle
        Vec3d rotationAxis = up.crossProduct(normal).normalize();
        double angle = Math.acos(up.dotProduct(normal));

        // Rotate the local direction to align with the normal
        return rotateVector(localDirection, rotationAxis, angle);
    }

    private static Vec3d rotateVector(Vec3d vec, Vec3d axis, double angle) {
        // Rodrigues' rotation formula
        double cosTheta = Math.cos(angle);
        double sinTheta = Math.sin(angle);

        return vec.multiply(cosTheta)
                .add(axis.crossProduct(vec).multiply(sinTheta))
                .add(axis.multiply(axis.dotProduct(vec) * (1 - cosTheta)));
    }


}
