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
