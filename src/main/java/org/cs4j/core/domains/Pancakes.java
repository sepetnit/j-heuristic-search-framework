package org.cs4j.core.domains;

import org.cs4j.core.SearchDomain;
import org.cs4j.core.collections.PackedElement;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The pancake problem is a famous search problem where the objective is to sort a sequence of
 * objects (pancakes) through a minimal number of prefix reversals (flips).
 */
public class Pancakes implements SearchDomain {

    private COST_FUNCTION costFunction;
    // The parameter k for GAP-k heuristic (means that k pancakes are ignored during heuristic calculation
    // [starting from 0])
    private int k;

    private static final Map<String, Class> PancakesPossibleParameters;

    // Declare the parameters that can be tunes before running the search
    static
    {
        PancakesPossibleParameters = new HashMap<String, Class>();
        PancakesPossibleParameters.put("GAP-k", Integer.class);
    }

    // The possible cost functions
    public enum COST_FUNCTION {
        UNIT,
        HEAVY
    }

    protected int numCakes = 0;

    // The initial given state
    private int[] init;
    private Operator[] possibleOperators;

    private int bitsForSinglePancake;
    private long maskForSinglePancake;
    // The number of cakes that can be stored in a single long
    private int packedCakesInSingleLong;
    // The number of long numbers to store the packed state
    private int packedLongsCount;

    // This static variable defines the minimum pancake index that is checked when checking for goal -
    // in case we use the domain to compute PDB, we refer to partial portion of the pancakes
    public static int MIN_PANCAKE_FOR_PDB = 0;
    public static int MAX_PANCAKE_FOR_PDB = -1;
    // This can be set only according to the number of pancakes
    private int maxPancakeForPDB;

    /**
     * Initialize all the data structures relevant to the domain
     */
    private void _initializeDataStructures() {
        this.possibleOperators = new Operator[this.numCakes];
        // Initialize the operators (according to the updated position of the pancake)
        for (int i = 0; i < this.numCakes; ++i) {
            this.possibleOperators[i] = new PancakeOperator(i + 1);
        }
        // Set the maximum index (if it is not already defined)
        if (Pancakes.MAX_PANCAKE_FOR_PDB == -1) {
            this.maxPancakeForPDB = this.numCakes - 1;
        } else {
            this.maxPancakeForPDB = Pancakes.MAX_PANCAKE_FOR_PDB;
        }
        // calculate the bits and bitmask to store single pancake
        this.bitsForSinglePancake = Utils.bits(this.numCakes);
        this.maskForSinglePancake = Utils.mask(this.bitsForSinglePancake);
        //System.out.println("mask: " + this.maskForSinglePancake);
        this.packedCakesInSingleLong = Math.min(this.numCakes, (int)Math.floor(64.0d / this.bitsForSinglePancake));
        //System.out.println("packedCakesInSingleLong: " + this.packedCakesInSingleLong);
        this.packedLongsCount = (int)Math.ceil(this.numCakes * this.bitsForSinglePancake / 64.0d);
        //System.out.println("packedLongsCount: " + this.packedLongsCount);

        // Current debugs ..
        //assert(this.bitsForSinglePancake == 5);
        //assert this.packedLongsCount == 2;
    }

    /**
     * The constructor reads an instance of Pancakes problem from the specified input stream
     *
     * @param stream The input stream to read the problem from
     * @param costFunction The computeCost function to use
     */
    public Pancakes(InputStream stream, COST_FUNCTION costFunction) {
        this.costFunction = costFunction;

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            // Read the number of cakes
            String line = reader.readLine();
            this.numCakes = Integer.parseInt(line);
            // The number of pancakes can be 2-15 only (15 in order to allow packing into long - 64 bit number)
            // assert this.numCakes >= 2 && this.numCakes <= 15;
            this.init = new int[this.numCakes];
            // Read the cakes
            line = reader.readLine();
            String[] cakes = line.split(" ");
            for (int i = 0; i < cakes.length; ++i) {
                this.init[i] = Integer.parseInt(cakes[i]);
            }
            this._initializeDataStructures();
        } catch(IOException e) {
            Utils.fatal("Error reading input file");
        }
    }

    /**
     * The constructor reads an instance of Pancakes problem from the specified input stream.
     * In this case, the UNIT cost function is used.
     *
     * @param stream the input stream to read
     */
    public Pancakes(InputStream stream) {
        this(stream, COST_FUNCTION.UNIT);
        // Default value for k
        this.k = 0;
    }

    /**
     * This constructor receives the initial pancakes position
     *
     * @param init The initial position of the pancakes
     * @param costFunction The computeCost function to use
     */
    public Pancakes(int[] init, COST_FUNCTION costFunction) {
        this.costFunction = costFunction;
        this.numCakes = init.length;
        this.init = new int[init.length];
        // Copy the given init array
        System.arraycopy(init, 0, this.init, 0, this.init.length);
        // System.out.println(Arrays.toString(this.init));
        this._initializeDataStructures();
    }

    /**
     * This constructor receives the initial pancakes position
     * In this case, the UNIT cost function is used.
     *
     * @param init The initial position of the pancakes
     */
    public Pancakes(int[] init) {
        this(init, COST_FUNCTION.UNIT);
    }

    /**
     * Check whether there is a gap between the cakes n and n+1? (whether they are following)
     *
     * @param cakes The pancakes array to calculate the data from
     * @param n The pancake to check
     *
     * @return Whether there is a gap between the pancakes at positions n and n+1
     */
    private boolean _hasGap(int cakes[], int n, boolean useK) {
        // A special case for the last cake: simply check if its position is true
        if (n == (this.numCakes - 1)) {
            // The first inequality supports GAP-k heuristic
            return (!useK || cakes[n] >= (this.k - 1)) && cakes[n] != n;
        }
        // Support GAP-k heuristic
        if (useK && (cakes[n] < this.k || cakes[n+1] < this.k)) {
            return false;
        }

        // Whether the difference is different than 1
        return Math.abs(cakes[n] - cakes[n+1]) != 1;
    }

    /**
     * The function calculates the number of 'gaps' between the cakes which forms the heuristic
     * function: Since, at least a single operation should be performed on each gap - in order to
     * remove it
     *
     * @param cakes The pancakes array
     * @param costFunction The cost function to apply
     * @param useK Whether to refer to the value of k during the calculation of gaps count
     *
     * @return The calculated gaps number, which allows to form the heuristic function
     */
    private int _countGaps(int cakes[], COST_FUNCTION costFunction, boolean useK) {
        int gapsCount = 0;
        for (int i = Pancakes.MIN_PANCAKE_FOR_PDB; i <= this.maxPancakeForPDB; ++i) {
            if (this._hasGap(cakes, i, useK)) {
                switch (costFunction) {
                    case UNIT:
                        ++gapsCount;
                        break;
                    case HEAVY:
                        int a = cakes[i];
                        int b = (i != this.numCakes - 1) ? cakes[i + 1] : Integer.MAX_VALUE;
                        // Each gap costs the 1+the minimal cake
                        gapsCount += (1 + Math.min(a, b));
                        break;
                }
            }
        }
        return gapsCount;
    }

    /**
     * Same as the above _countGaps function, but the value of k is used
     *
     * @param cakes The pancakes array
     * @param costFunction The cost function to apply
     *
     * @return The calculated gaps number, which allows to form the heuristic function
     */
    private int _countGaps(int cakes[], COST_FUNCTION costFunction) {
        return this._countGaps(cakes, costFunction, true);
    }

    @Override
    public PancakeState initialState() {
        PancakeState s = new PancakeState(this.numCakes);
        System.arraycopy(this.init, 0, s.cakes, 0, numCakes);
        s.h = this._countGaps(s.cakes, this.costFunction);
        s.d = this._countGaps(s.cakes, COST_FUNCTION.UNIT);
        if (this.k == 0) {
            s.dNoGaps = s.d;
        } else {
            // Calc d without k
            s.dNoGaps = this._countGaps(s.cakes, COST_FUNCTION.UNIT, false);
        }
        return s;
    }

    @Override
    public boolean isCurrentHeuristicConsistent() {
        return true;
    }

    @Override
    public void setOptimalSolutionCost(double cost) { }

    @Override
    public double getOptimalSolutionCost() {
        return -1;
    }

    @Override
    public boolean isGoal(State s) {
        PancakeState state = (PancakeState) s;
        return state.dNoGaps == 0;
    }

    @Override
    // Each operator can be applied on any state
    public int getNumOperators(State state) {
        return this.numCakes - 1;
    }

    @Override
    public Operator getOperator(State state, int nth) {
        return this.possibleOperators[nth];
    }

    @Override
    public State applyOperator(State state, Operator op) {
        PancakeState pancakeState = (PancakeState)copy(state);
        int pancakeOperator = ((PancakeOperator)op).value;
        // Flip the top of the stack
        pancakeState.flipTopStackPortion(pancakeOperator);
        pancakeState.h = this._countGaps(pancakeState.cakes, this.costFunction);
        pancakeState.d = this._countGaps(pancakeState.cakes, COST_FUNCTION.UNIT);
        if (this.k == 0) {
            pancakeState.dNoGaps = pancakeState.d;
        } else {
            // Calc d without k
            pancakeState.dNoGaps = this._countGaps(pancakeState.cakes, COST_FUNCTION.UNIT, false);
        }
        return pancakeState;
    }

    @Override
    public State copy(State state) {
        return new PancakeState((PancakeState)state);
    }

    /**
     * Calculates the cost of the given operator according to the given cost function
     *
     * @param op The operator whose cost should be calculated
     * @return The calculated cost of the operator
     */
    private double computeCost(int op) {
        double value = 1.0;
        switch (this.costFunction) {
            case HEAVY:
                value = 1 + op;
                break;
            case UNIT:
            default:
                break;
        }
        return value;
    }

    @Override
    public PackedElement pack(State s) {
        PancakeState ps = (PancakeState)s;
        long[] packed = new long[this.packedLongsCount];
        int index = 0;
        for (int i = 0; i < this.packedLongsCount; ++i) {
            long word = 0;
            for (int j = 0; (j < this.packedCakesInSingleLong) && (index <= this.numCakes - 1); ++j) {
                word = (word << this.bitsForSinglePancake) | ps.cakes[index++];
            }
            packed[i] = word;
        }
        PackedElement toReturn = new PackedElement(packed);
        /**
         * Debug
         */
        if  (!Arrays.equals(ps.cakes, ((PancakeState)this.unpack(toReturn)).cakes)) {
            System.out.println(Arrays.toString(ps.cakes));
            System.out.println(Arrays.toString(((PancakeState)this.unpack(toReturn)).cakes));
            assert false;
        }
        return toReturn;
    }

    @Override
    public State unpack(PackedElement packed) {
        PancakeState state = new PancakeState(this.numCakes);
        int index = this.numCakes - 1;
        for (int i = packed.getLongsCount() - 1; i >= 0; --i) {
            long current = packed.getLong(i);
            int maxIterationIndex = this.packedCakesInSingleLong;
            // In case of first iteration (starting from the end, maybe only part of the full coverage of pancakes is
            // included inside the packed long - let's calculate this count
            if (i == packed.getLongsCount() - 1) {
                // E.g. if (numCakes=20; packedCakesInSingleLong=12 => maxIterationIndex=8)
                //      if (numCakes=15; packedCakesInSingleLong=15 => maxIterationIndex=0)
                //      if (numCakes=10; packedCakesInSingleLong=10 => maxIterationIndex=0)
                int specificMaxIterationIndex = this.numCakes % this.packedCakesInSingleLong;
                if (specificMaxIterationIndex > 0) {
                    maxIterationIndex = specificMaxIterationIndex;
                }
            }
            for (int j = 0; j < maxIterationIndex && index >= 0; ++j) {
                int p = (int) (current & (int)this.maskForSinglePancake);
                current >>= this.bitsForSinglePancake;
                state.cakes[index--] = p;
            }
        }
        state.h = this._countGaps(state.cakes, this.costFunction);
        state.d = this._countGaps(state.cakes, COST_FUNCTION.UNIT);
        state.dNoGaps = this._countGaps(state.cakes, COST_FUNCTION.UNIT, false);
        return state;
    }

    /**
     * Pancake stat class
     */
    public final class PancakeState implements State {
        public int numCakes;
        public int[] cakes;
        public double h;
        public double d;
        // The value of d ignoring k (required for isGoal)
        public double dNoGaps;
        private PancakeState parent = null;

        /**
         * A default constructor of the class
         */
        private PancakeState() { }

        /**
         * A standard constructor
         *
         * @param numCakes The number of cakes in the state
         */
        public PancakeState(int numCakes) {
            this.numCakes = numCakes;
            this.cakes = new int[numCakes];
        }

        /**
         * A standard constructor
         *
         * @param cakes The pancakes array
         */
        public PancakeState(int[] cakes) {
            this.numCakes = cakes.length;
            this.cakes = new int[numCakes];
            System.arraycopy(this.cakes, 0, cakes, 0, cakes.length);
        }

        /**
         * A copy constructor
         *
         * @param pancake Another pancake state to copy
         */
        public PancakeState(PancakeState pancake) {
            this.numCakes = pancake.numCakes;
            this.cakes = new int[numCakes];
            this.h = pancake.h;
            this.d = pancake.d;
            this.dNoGaps = pancake.dNoGaps;
            System.arraycopy(pancake.cakes, 0, this.cakes, 0, pancake.cakes.length);
        }

        /**
         * Pancake states are compared by comparing all the pancakes in the state
         *
         * @param object The object to compare to the current state
         *
         * @return Whether the states are equal
         */
        @Override
        public boolean equals(Object object) {
            try {
                PancakeState pancakeState = (PancakeState)object;
                // Treat only the places of the specific pancakes
                if (Pancakes.MIN_PANCAKE_FOR_PDB > 0) {
                    for (int i = 0; i < this.numCakes; ++i) {
                        if (this.cakes[i] >= Pancakes.MIN_PANCAKE_FOR_PDB &&
                                this.cakes[i] <= Pancakes.this.maxPancakeForPDB) {
                           if (pancakeState.cakes[i] != this.cakes[i]) {
                               return false;
                           }
                        }
                    }
                    return true;
                }
                // Otherwise, perform a regular comparison operation
                return Arrays.equals(this.cakes, pancakeState.cakes);
            } catch (ClassCastException e) {
                return false;
            }
        }

        @Override
        public double getH() {
            return this.h;
        }

        @Override
        public double getD() {
            return this.d;
        }

        @Override
        public State getParent() {
            return this.parent;
        }

        /**
         * Lift a top portion of the stack and reverse it
         *
         * @param op The operator for the operation (represented by an integer number)
         */
        private void flipTopStackPortion(int op) {
            // Assert the operator is valid
            assert (op > 0);
            assert (op < this.numCakes);
            // Flip the first half (and the second will be flipped automatically)
            for (int n = 0; n <= op / 2; ++n) {
                int tmp = this.cakes[n];
                this.cakes[n] = this.cakes[op - n];
                this.cakes[op - n] = tmp;
            }
        }

        @Override
        public String dumpState() {
            StringBuilder sb = new StringBuilder();
            sb.append("********************************\n");
            // h
            sb.append("h: ");
            sb.append(this.h);
            sb.append("\n");
            // d
            sb.append("d: ");
            sb.append(this.d);
            sb.append("\n");

            for (int i = 0; i < this.cakes.length; ++i) {
                sb.append(cakes[i]);
                sb.append(" ");
            }
            sb.append("\n");
            sb.append("********************************\n\n");
            return sb.toString();
        }

        @Override
        public String dumpStateShort() {
            return null;
        }
    }

    /**
     * An operator class which represents a single operator of the Pancake domain
     */
    private final class PancakeOperator implements Operator {

        private int value;

        private PancakeOperator(int value) {
            this.value = value;
        }

        @Override
        public double getCost(State state, State parent) {
            PancakeState ps = (PancakeState)state;
            return Pancakes.this.computeCost(ps.cakes[value]);
        }

        @Override
        public Operator reverse(State state) {
            // In the Pancakes domain, reversing an operation can be performed easily by applying
            // the same operator on the state
            return this;
        }

    }

    public String dumpStatesCollection(State[] states) {
        return null;
    }

    @Override
    public Map<String, Class> getPossibleParameters() {
        return Pancakes.PancakesPossibleParameters;
    }

    @Override
    public void setAdditionalParameter(String parameterName, String value) {
        switch (parameterName) {
            case "GAP-k": {
                this.k = Integer.parseInt(value);
                assert this.k >= 0 && this.k < this.numCakes;
                break;
            } default: {
              throw new IllegalArgumentException("Invalid parameter: " + parameterName);
            }
        }
    }

}