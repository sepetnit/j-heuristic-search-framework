package org.cs4j.core.domains;

import org.cs4j.core.collections.PairInt;

/**
 * This class contains general util functions that are required in more than a single domain
 *
 * All the functions in this class are defined as static
 *
 */
public class Utils {

    /**
     * Calculates Manhattan distance between two locations
     *
     * @param xy The first location
     * @param ij The second location
     *
     * @return The calculated Manhattan distance
     */
    public static int calcManhattanDistance(PairInt xy, PairInt ij) {
        // Calculate Manhattan distance to the location
        int xDistance = Math.abs(xy.first - ij.first);
        int yDistance = Math.abs(xy.second - ij.second);
        return xDistance + yDistance;
    }

    /*
     * Calculates the log2 value of the given number
     *
     * @param x The input number for the calculation
     * @return The calculated value
     */
    public static double log2(int x) {
        return Math.log(x) / Math.log(2);
    }

    /**
     * An auxiliary function that computes the bitmask of the required number of bits
     *
     * @param bits The number of bits whose bit-mask should be computed
     * @return The calculated bit-mask
     */
    public static long mask(int bits) {
        return ~((~0) << bits);
    }

    /**
     * An auxiliary function for throwing some fatal error and exit
     *
     * @param msg The message to print before exiting
     */
    public static void fatal(String msg) {
        System.err.println(msg);
        System.exit(1);
    }
}
