package org.cs4j.core.collections;

/**
 * This class represents a Pair of integers.
 *
 * The class is useful for many situations
 * e.g. Required for storing all the dirty locations that should be cleaned by the Robot in a
 * VacuumRobot domain
 */
public class PairInt {
    public int first;
    public int second;

    /**
     * The constructor of the class
     *
     * @param first X value of the location
     * @param second Y value of the location
     */
    public PairInt(int first, int second) {
        this.first = first;
        this.second = second;
    }

    /**
     * Creates a new instance of PairInt by parsing a given string
     * The string can be of the following formats:
     *  1. (a, b)
     *  2. a,b
     *
     * @param pairIntAsString The given string
     *
     * @return The created PairInt object or null if the creation failed
     */
    public static PairInt fromString(String pairIntAsString) {
        // If the format of the pair is "(a,b" or "a,b)" - it is considered as illegal - so return null
        if (pairIntAsString.startsWith("(") ^ pairIntAsString.endsWith(")")) {
            return null;
        }
        String[] split = pairIntAsString.trim().split(",");
        if (split.length != 2) {
            return null;
        }
        int firstInt = -1;
        int secondInt = -1;
        try {
            String first = split[0];
            // Ignore the first paren
            if (first.startsWith("(")) {
                first = first.substring(1);
            }
            firstInt = Integer.parseInt(first);
            String second = split[1];
            // Ignore the last paren
            if (second.endsWith(")")) {
                second = second.substring(0, second.lastIndexOf(")"));
            }
            secondInt = Integer.parseInt(second);
        } catch (NumberFormatException e) {
            return null;
        }
        // If we are here this must be true!
        assert firstInt != -1 && secondInt != -1;
        // Finally, return the created pair
        return new PairInt(firstInt, secondInt);
    }

    @Override
    public int hashCode() {
        return (int)(Math.pow(2, this.first) * Math.pow(3, this.second));
    }

    @Override
    public boolean equals(Object other) {
        try {
            PairInt otherPair = (PairInt) other;
            return otherPair.first == this.first && otherPair.second == this.second;
        } catch (ClassCastException e) {
            return false;
        }
    }

    public String toStringNoParen() {
        return this.first + "," + this.second;
    }

    @Override
    public String toString() {
        return "(" + this.toStringNoParen() + ")";
    }


}
