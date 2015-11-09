package org.cs4j.core.domains;

import org.cs4j.core.collections.PairInt;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This class contains general util functions that are required in more than a single domain
 *
 * All the functions in this class are defined as static
 *
 */
public class Utils {

    /**
     * Recursive implementation of factorial
     *
     * @param n Compute fact(n)
     *
     * @return Value of n! calculation
     */
    public static int fact(int n) {
        if (n == 1) {
            return 1;
        }
        return n * fact(n -1);
    }

    /**
     * Converts a given list that contains Integer objects, to an array of ints
     *
     * @param list The input list
     *
     * @return An array of integers (int) that contains all the elements in list
     */
    public static int[] integerListToArray(List<Integer> list) {
        int[] toReturn = new int[list.size()];
        int index = 0;
        for (int element : list) {
            toReturn[index++] = element;
        }
        return toReturn;
    }

    /**
     * An opposite function of {@see integerListToArray} - converts an array of integers to a list
     *
     * @param array The array to convert
     *
     * @return The result list of integers
     */
    public static List<Integer> intArrayToIntegerList(int[] array) {
        List<Integer> toReturn = new ArrayList<>(array.length);
        for (int current : array) {
            toReturn.add(current);
        }
        return toReturn;
    }

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

    /**
     * Calculates the log2 value of the given number
     *
     * @param x The input number for the calculation
     * @return The calculated value
     */
    public static double log2(int x) {
        return Math.log(x) / Math.log(2);
    }

    /**
     * Calculates the required number of bits in order to store a given number
     *
     * @param number The number to store
     *
     * @return The calculated bit count
     */
    public static int bits(int number) {
        return (int) Math.ceil(Utils.log2(number));
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

    /**
     * Reads the whole given file into a string
     *
     * @param file The file to read
     * @return The read bytes of the file
     *
     * @throws IOException If something wrong occurred
     */
    public static String fileToString(File file) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    /**
     * Reads the whole given file into a string
     *
     * @param filename The file to read
     * @return The read bytes of the file
     *
     * @throws IOException If something wrong occurred
     */
    public static String fileToString(String filename) throws IOException {
        return Utils.fileToString(new File(filename));
    }
}
