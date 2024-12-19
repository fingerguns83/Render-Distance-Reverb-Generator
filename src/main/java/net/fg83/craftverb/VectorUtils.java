package net.fg83.craftverb;

import net.minecraft.util.math.Vec3d;

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

    public static boolean isPointOnEdge(Vec3d collisionPos){
        int integerValues = 0;
        if (collisionPos.x % 1 == 0) integerValues++;
        if (collisionPos.y % 1 == 0) integerValues++;
        if (collisionPos.z % 1 == 0) integerValues++;
        return integerValues > 1;
    }
}
