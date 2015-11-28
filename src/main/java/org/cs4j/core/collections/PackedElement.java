package org.cs4j.core.collections;

/**
 * Created by sepetnit on 11/8/2015.
 *
 */
public class PackedElement {
    private long[] internal;

    public PackedElement(long internal) {
        this.internal = new long[]{internal};
    }

    public PackedElement(long[] internal) {
        this.internal = new long[internal.length];
        System.arraycopy(internal, 0, this.internal, 0, internal.length);
    }

    public long[] getInternal() {
        return this.internal;
    }

    public int getLongsCount() {
        return this.internal.length;
    }

    public long getFirst() {
        return this.internal[0];
    }

    public long getLong(int index) {
        return this.internal[index];
    }

    /**
     * Calculates the summary of the longs that the packed element holds
     * (can be used for relying on the packed value, e.g. when randomizing heuristic calculation)
     *
     * @return The calculated sum
     */
    public long getLongsSum() {
        long toReturn = 0;
        for (long current : this.internal) {
            toReturn += current;
        }
        return toReturn;
    }

    @Override
    public int hashCode() {
        int result = 0;
        // Allow overflow
        for (long current : this.internal) {
            result += current;
        }
        return result;
    }

    @Override
    public boolean equals(Object object) {
        try {
            PackedElement other = (PackedElement)object;
            if (other.internal.length != this.internal.length) {
                return false;
            }
            for (int i = 0; i < this.internal.length; ++i) {
                if (this.internal[i] != other.internal[i]) {
                    return false;
                }
            }
            return true;
        } catch (ClassCastException e) {
            return false;
        }
    }
}
