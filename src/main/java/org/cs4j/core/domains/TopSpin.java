package org.cs4j.core.domains;

import com.carrotsearch.hppc.LongByteHashMap;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.collections.PackedElement;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


/**
 * Created by user on 17/12/2015.
 *
 */
public class TopSpin implements SearchDomain {

    private static final double PDB_ENTRIES_INCREASE = 1.1;

    private static final boolean SHOULD_AVOID_OPERATOR_LOOPS = false;
    private static final int MAXIMUM_SIZE_OF_PATTERN = 10;

    private static final int INDEX_OF_PDB_INDEX = 0;
    private static final int INDEX_OF_PDB_ENTRIES_COUNT = 1;
    private static final int INDEX_OF_PDB_FILENAME = 2;
    private static final int INDEX_OF_PDB_TOKENS_ARRAY = 3;

    private int init[];
    private Operator[] possibleOperators;

    // TODO: Make configurable
    private int tokensNumber = 10;
    private int spinSize = 4;

    private boolean operatorsMatrix[][];

    private int numberOfHeuristics;
    private Map<Integer, SinglePDB> pdbs;

    private int [] reflectedIndexes;
    private int[] tokensForGoalCheck;
    private int[] reflectedTokens;


    private enum HeuristicType {
        LOCATION_BASED,
        MAXING,
        DUAL
    }

    private HeuristicType heuristicType;

    private static final Map<String, Class> TopSpinPossibleParameters;

    // Declare the parameters that can be tunes before running the search
    static
    {
        TopSpinPossibleParameters = new HashMap<>();
        TopSpinPossibleParameters.put("heuristic", String.class);
        TopSpinPossibleParameters.put("pdb-data", String.class);
    }

    @Override
    public Map<String, Class> getPossibleParameters() {
        return TopSpin.TopSpinPossibleParameters;
    }

    @Override
    public void setAdditionalParameter(String parameterName, String value) {
        switch (parameterName) {
            /*
            case "pdbs-count": {
                this.pdbsCount = Integer.parseInt(value);
                if (this.pdbsCount <= 0 || this.pdbsCount > 2) {
                    System.out.println("[ERROR] Invalid PDBs count: must be between 1 and 2");
                    throw new IllegalArgumentException();
                }
                break;
            }
            */
            // The data for a single PDB in the following format: "<index>,<entries-count>,<tokens-array>,<filename>"
            case "pdb-data": {
                String[] splittedPDBData = value.split(",");
                assert splittedPDBData.length == 4;
                int index = Integer.parseInt(splittedPDBData[TopSpin.INDEX_OF_PDB_INDEX]);
                // Check if a PDB for the given index was already read
                if (this.pdbs.containsKey(index)) {
                    SinglePDB previous = this.pdbs.get(index);
                    System.out.println("[WARNING] A PDB with index " + index + " was already read from " +
                            previous.getPdbFileName());
                }
                long entriesCount = Long.parseLong(splittedPDBData[TopSpin.INDEX_OF_PDB_ENTRIES_COUNT]);
                int[] tokensArray = Utils.stringToIntegerArray(splittedPDBData[TopSpin.INDEX_OF_PDB_TOKENS_ARRAY]);
                String pdbFileName = splittedPDBData[TopSpin.INDEX_OF_PDB_FILENAME];
                SinglePDB currentPDB = new SinglePDB(entriesCount, tokensArray, pdbFileName, true);
                this.pdbs.put(index, currentPDB);
                break;
            }
            default: {
                System.out.println("No such parameter: " + parameterName + " (value: " + value + ")");
                throw new IllegalArgumentException();
            }
        }
    }


    /**
     * Initialize an auxiliary data structure intended for avoidance of duplicate or unnecessary operators applying
     * (which lead to nothing)
     */
    private void __initShouldSkipOperatorsMatrix() {
        this.operatorsMatrix = new boolean[this.tokensNumber][this.tokensNumber];
        for (int oldMove = 0; oldMove < this.tokensNumber; ++oldMove) {
            for (int newMove = 0; newMove < this.tokensNumber; ++newMove) {
                this.operatorsMatrix[oldMove][newMove] = true;
            }
        }
        if (TopSpin.SHOULD_AVOID_OPERATOR_LOOPS) {
            for (int oldMove = 0; oldMove < this.tokensNumber; ++oldMove) {
                int acc = 0;
                for (int newMove = oldMove; newMove < this.tokensNumber; ++newMove) {
                    if (newMove == oldMove || (acc >= this.spinSize && acc <= this.tokensNumber - this.spinSize)) {
                        this.operatorsMatrix[newMove][oldMove] = false;
                    }
                    ++acc;
                }
            }
        }
    }

    /**
     * Initialize all the data structures relevant to the domain
     */
    private void _initDataStructures() {
        // Initialize the reflection array
        this.reflectedIndexes = new int[this.tokensNumber];
        for (int token = 0; token < this.tokensNumber; ++token) {
            this.reflectedIndexes[token] = token;
        }
        this.possibleOperators = new Operator[this.tokensNumber];
        // Initialize the operators (according to the updated position of the pancake)
        for (int i = 0; i < this.tokensNumber; ++i) {
            this.possibleOperators[i] = new TopSpinOperator(i + 1);
        }
        this.__initShouldSkipOperatorsMatrix();
        this.tokensForGoalCheck = new int[this.tokensNumber];
        this.reflectedTokens = new int[this.tokensNumber];
    }

    /**
     * The constructor reads an instance of TopSpin problem from the specified input stream
     *
     * @param stream The input stream to read the problem from
     */
    public TopSpin(InputStream stream) {
        String line;
        String[] split;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            // First, read the size of the ring and the number of reversible tokens (and ignore them: assume they are constant)
            line = reader.readLine();
            split = line.split(" ");
            assert split.length == 2;
            assert Integer.parseInt(split[0]) == 10; // ring of size 10
            assert Integer.parseInt(split[1]) == 4;  // reverse part of size 4
            // Read the text line
            reader.readLine();

            // Read the rest
            this.tokensNumber = 10;
            this.spinSize = 4;

            this.init = new int[this.tokensNumber];

            // Now, start reading the ring itself (Each token in a single line)
            for (int i = 0; i < this.tokensNumber; ++i) {
                // Read a value of a token and place in t
                int t = Integer.parseInt(reader.readLine().trim());
                // Insert the tile value into the storage
                this.init[i] = t;
            }
            reader.close();
        } catch(IOException e) {
            e.printStackTrace();
            Utils.fatal("Error reading input file");
        }
        // Call the init function and initialize all other data structures
        this._initDataStructures();

        // Currently the heuristic type is constant
        this.heuristicType = HeuristicType.MAXING;

    }

    @Override
    public State copy(State state) {
        return new TopSpinState(state);
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

    /**
     * Creates an initial state but ignores any heuristic related issues
     *
     * @return The created state
     */
    public TopSpinState initialStateNoHeuristic() {
        // Initialize the tokens
        int tokens[] = Arrays.copyOf(this.init, this.init.length);
        // Finally, initialize the start state
        TopSpinState s = new TopSpinState();
        s.tokens = tokens;
        return s;
    }

    /**
     * The function reflects the given tokens array such that tokens[tokens.length - reflectionIndex] will be positioned
     * at index 0.
     * The function updates the 'reflectedTokens' array (which serves as an output parameter)
     *
     * @param reflectionIndex The index which is added to every token
     *
     * @param tokens The tokens to reflect (taken from the state)
     */
    private void _calculateReflection(int reflectionIndex, int[] tokens) {
        for (int i = 0; i < this.tokensNumber; ++i) {
            this.reflectedTokens[i] = (tokens[i] + reflectionIndex) % this.tokensNumber;
        }
    }

    /**
     * The function computes the values of h and d for a given state
     *
     * @param state The state for which the values should be computed
     *
     * @return An array of the form {h, d}
     */
    private double[] _computeHD(TopSpinState state) {
        double h = -1.0d;
        double d = -1.0d;

        double hMax = 0;

        for (Map.Entry<Integer, SinglePDB> currentPDBEntry : this.pdbs.entrySet()) {
            SinglePDB currentPDB = currentPDBEntry.getValue();
            int zeroToken = currentPDB.getFirstTokenInPattern();
            // If the current token is a part of the pattern
            if (zeroToken >= 0) {
                this._calculateReflection(this.tokensNumber - zeroToken, state.tokens);
                try {
                    hMax = Math.max(hMax, currentPDB.getH(state.tokens));
                } catch (InvalidKeyException e) {
                    // Bypass
                }
            }
        }
        // Finally, calculate the H and D values
        switch (this.heuristicType) {
            case MAXING: {
                h = hMax;
                d = hMax;
                break;
            }
            default: {
                System.out.println("[ERROR] Unsupported heuristic type for TopSpin puzzle: " + this.heuristicType);
            }
        }
        return new double[]{h, d};
    }

    @Override
    public State initialState() {
        TopSpinState s = this.initialStateNoHeuristic();
        // Let's calculate the heuristic values (h and d)
        double[] computedHD = this._computeHD(s);
        s.h = computedHD[0];
        s.d = computedHD[1];
        //System.out.println(s.dumpState());
        return s;
    }

    /**
     * The function rotates the given input array such that the token valued 0 is at index 0
     *
     * @param input The input array
     * @param output The output array
     *
     * TODO: I think it is same like _rotateArray(0, input, output) of SinglePDB
     */
    private void _rotateArrayToZero(int[] input, int[] output) {
        int zeroPositionInPop = 0;
        // Find the position of zero
        while (input[zeroPositionInPop] != 0) {
            ++zeroPositionInPop;
        }
        int location = 0;
        // Initialize the output with dual of the state
        for (int i = zeroPositionInPop; i < TopSpin.this.tokensNumber; ++i, ++location) {
            output[location] = input[i];
        }
        // Initialize the output with the dual of the state
        for (int i = 0; i < zeroPositionInPop; ++i, ++location) {
            output[location] = input[i];
        }
    }

    @Override
    public boolean isGoal(State s) {
        TopSpinState state = (TopSpinState) s;
        this._rotateArrayToZero(state.tokens, this.tokensForGoalCheck);
        for (int i = 0; i < this.tokensNumber - 1; ++i) {
            if (this.tokensForGoalCheck[i] > this.tokensForGoalCheck[i + 1]) {
                return false;
            }
        }
        return true;
    }

    @Override
    // Each operator can be applied on any state
    public int getNumOperators(State state) {
        return this.tokensNumber - 1;
    }

    @Override
    public Operator getOperator(State state, int nth) {
        return this.possibleOperators[nth];
    }

    @Override
    public State applyOperator(State state, Operator op) {
        TopSpinState s = (TopSpinState) state;
        TopSpinState tss = (TopSpinState) copy(s);

        TopSpinOperator o = (TopSpinOperator) op;
        int tmp = (((TopSpin.this.spinSize - 1) + o.index) % this.tokensNumber);
        int aVal = -1;
        // Go over the reverse cicle
        for (int i = 0; i <= (TopSpin.this.spinSize >> 1); ++i) {
            int fromIndex = (o.index + i) % TopSpin.this.tokensNumber;
            int toIndex = tmp;
            tss.tokens[fromIndex] = s.tokens[toIndex];
            tss.tokens[toIndex] = s.tokens[fromIndex];
            if (tmp > TopSpin.this.tokensNumber) {
                aVal = 1;
                tmp = 0;
            } else {
                tmp += aVal;
            }
            if (tmp < 0) {
                tmp = (TopSpin.this.tokensNumber - 1);
            }
        }
        //s.dumpState();
        //tss.dumpState();
        return tss;
    }

    @Override
    public PackedElement pack(State s) {
        TopSpinState tss = (TopSpinState)s;
        long result = 0;
        // We need at most 6 bits in order to pack a single Token: (0b1111 is 15)
        // Thus, we need at most 4 * 16 = 64 bits to pack the full state (64 bits = a long number)
        for (int i = 0; i < this.tokensNumber; ++i) {
            result = (result << 4) | tss.tokens[i];
        }
        return new PackedElement(result);
    }

    @Override
    public State unpack(PackedElement packed) {
        assert packed.getLongsCount() == 1;
        long firstPacked = packed.getFirst();
        TopSpinState tss = new TopSpinState();
        // Start from end and go to start
        for (int i = this.tokensNumber - 1; i >= 0; --i) {
            // Each time, extract a single token
            int t = (int) firstPacked & 0xF;
            // Initialize this token
            tss.tokens[i] = t;
            // Update the word so that the next tile can be now extracted
            firstPacked >>= 4;
        }
        double[] hd = this._computeHD(tss);
        tss.h = hd[0];
        tss.d = hd[1];
        return tss;
    }

    @Override
    public String dumpStatesCollection(State[] states) {
        return null;
    }

    private class TopSpinState implements State {
        private int tokens[] = new int[TopSpin.this.tokensNumber];
        public double h;
        public double d;

        private TopSpinState parent = null;

        /**
         * A default constructor of the state
         */
        private TopSpinState() { }

        /**
         * A copy constructor of the state
         *
         * @param state The state to copy (must be of TopSpinState type)
         */
        private TopSpinState(State state) {
            TopSpinState tss = (TopSpinState)state;
            this.tokens = new int[tss.tokens.length];
            // Copy the tokens
            System.arraycopy(tss.tokens, 0, this.tokens, 0, tss.tokens.length);
        }

        @Override
        public State getParent() {
            return this.parent;
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
        public String dumpState() {
            StringBuilder sb = new StringBuilder();
            sb.append("********************************\n");
            // h
            sb.append("h: ");
            sb.append(this.getH());
            sb.append("\n");
            // d
            sb.append("d: ");
            sb.append(this.getD());
            sb.append("\n");
            sb.append(Arrays.toString(this.tokens));
            sb.append("\n");
            return sb.toString();
        }

        @Override
        public String dumpStateShort() {
            return null;
        }
    }

    private class TopSpinOperator implements Operator {
        private int index;

        private TopSpinOperator(int index) {
            this.index = index;
        }

        @Override
        public double getCost(State state, State parent) {
            return 1.0;
        }

        @Override
        public Operator reverse(State state) {
            return null;
        }
    }

    /**
     * A class that contains all the relevant information for a single PDB of the TopSpin problem
     */
    private class SinglePDB {
        // When reading some PDB create a map with the required size * PDB_ENTRIES_INCREASE
        // (in order to disallow map copying)
        private static final double PDB_ENTRIES_INCREASE = 1.1d;
        // Print some debugging information every DEBUG_PRINT_GAP entries of the PDB
        private static final int DEBUG_PRINT_GAP = 999;

        private long entriesCount;
        private String pdbFileName;

        private int[] tokensInPattern;
        // Determines for each token, whether it belongs to the PDB
        private boolean[] tokenBelongsToPattern;
        // Determines for each token of the pattern, the location is it located on
        private int[] locationOfPatternInTokens;

        // A temporary array of the input state (tokens), rotates such that token with value 0 is at position 0
        private int[] rotatedTokensForHeuristicCalculation;
        // The positions of the tokens for a given state in order to calculate the heuristic value
        private int[] tokensPositionsForHeuristicCalculation;

        private LongByteHashMap pdb;

        /**
         * @return The index of the first token in the pattern this PDB refers to
         */
        public int getFirstTokenInPattern() {
            return this.tokensInPattern[0];
        }

        /**
         * @return The filename this PDB is located in
         */
        public final String getPdbFileName() {
            return this.pdbFileName;
        }

        /**
         * Converts tokens into hash number
         *
         * @param locationsOfTokens The locations of tokens that form the pattern
         *
         * @return The converted index
         */
        public long __getHashNIndex(int[] locationsOfTokens) {
            // initialize hash value to empty
            long hash = 0;
            // for each remaining position in permutation
            for (int i = 0; i < this.tokensInPattern.length - 1; ++i) {
                // initially digit is value in permutation
                int digit = locationsOfTokens[i];
                // for each previous element in permutation
                for (int j = 0; j < i; ++j) {
                    // previous position contains smaller value - so decrement digit
                    if (locationsOfTokens[j] < locationsOfTokens[i]) {
                        --digit;
                    }
                }
                // multiply digit by appropriate factor
                hash = hash * ((TopSpin.this.tokensNumber - 1) - i) + digit;
            }
            return hash;
        }

        /**
         * Given the current state (array of tokens), the function calculates the locations of the tokens relevant to
         * the pattern which is represented by the current heuristic
         *
         * @param tokens The state (array of tokens)
         */
        private void __calculateTokensPositionsForHeuristicCalculation(int[] tokens) {
            for (int i = 0; i < TopSpin.this.tokensNumber; ++i) {
                if (this.tokenBelongsToPattern[tokens[i]]) {
                    this.tokensPositionsForHeuristicCalculation[this.locationOfPatternInTokens[tokens[i]]] = i - 1;
                }
            }
        }

        /**
         * The function rotates the given array of tokens such that the token with the value of 0 is located at the
         * required index (and all tokens are moved respectively)
         *
         * @param newZeroLocation The required
         * @param input The input array
         * @param output The output array
         *
         * @return The previous location of the token with the value of 0
         */
        private int __rotateArray(int newZeroLocation, int[] input, int[] output) {
            int location = newZeroLocation;
            int previousZeroLocation = 0;
            // Look for the location of token with the value of 0
            while (input[previousZeroLocation] != 0) {
                ++previousZeroLocation;
            }
            // Put all the tokens at their required positions
            for (int i = 0; i < TopSpin.this.tokensNumber; ++i, ++location) {
                output[location % TopSpin.this.tokensNumber] =
                        input[(i + previousZeroLocation) % TopSpin.this.tokensNumber];
            }
            return previousZeroLocation;
        }

        private long _getHashIndex(int[] tokens) {
            // First, rotate the state and get the dual representation
            this.__rotateArray(0, tokens, this.rotatedTokensForHeuristicCalculation);
            this.__calculateTokensPositionsForHeuristicCalculation(this.rotatedTokensForHeuristicCalculation);
            // Calculate the hash index
            return this.__getHashNIndex(this.locationOfPatternInTokens);
        }

        public double getH(int[] tokens) throws InvalidKeyException {
            long index = this._getHashIndex(tokens);
            // Assert index is valid
            if (index < 0 || index >= this.entriesCount) {
                System.out.println("[ERROR] Invalid PDB hash index for the state " + Arrays.toString(tokens) +
                        ": should be between 0 and " + (this.entriesCount - 1) + ", given: " + index);
                throw new InvalidKeyException();
            }
            return this.pdb.get(index);
        }

        /**
         * The function reads the PDB data from the file
         *
         * @throws IOException If something wrong occurred
         */
        private void _readPDB() throws IOException {
            this.pdb = new LongByteHashMap((int)(this.entriesCount * SinglePDB.PDB_ENTRIES_INCREASE));
            // Each permutation index is stored as int
            DataInputStream inputStream = new DataInputStream(new FileInputStream(this.pdbFileName));
            long hashValue = 0;
            for (long i = 0; i < this.entriesCount; ++i) {
                // Debug
                if (i % SinglePDB.DEBUG_PRINT_GAP == 0) {
                    System.out.print("\r[INFO] Read " + (i + 1) + "/" + this.entriesCount + " values");
                }
                // Read the distance
                byte distance = inputStream.readByte();
                this.pdb.put(hashValue++, distance);
            }
            // Last new line
            System.out.println();
        }

        /**
         * The constructor of the class - initializes a single PDB
         *
         * @param entriesCount The number of entries in the PDB
         * @param tokensInPattern The pattern which is represented by the PDB (only the relevant tokens)
         * @param pdbFileName The name of the file where the PDB is stored
         * @param readImmediately Whether to read the PDB immediately (or delay its reading to later time)
         */
        private SinglePDB(long entriesCount, int[] tokensInPattern, String pdbFileName, boolean readImmediately) {
            assert readImmediately == true;
            this.entriesCount = entriesCount;
            this.tokensInPattern = tokensInPattern;
            this.pdbFileName = pdbFileName;
            if (readImmediately) {
                try {
                    // Read the PDB values from the file
                    this._readPDB();
                    // Initialize values
                    Arrays.fill(this.tokenBelongsToPattern, false);
                    Arrays.fill(this.locationOfPatternInTokens, -1);
                    // Fill relevant values:
                    // The first symbol gets -1 (Note: Symbol 0 belongs to pattern automatically)
                    // The 2nd symbol get 0
                    // The 3rd get 1
                    // ...
                    int index = 0;
                    for (int currentToken : this.tokensInPattern) {
                        this.tokenBelongsToPattern[currentToken] = true;
                        this.locationOfPatternInTokens[currentToken] = index++;
                    }
                    // This array will be recalculated for each heuristic calculation
                    this.tokensPositionsForHeuristicCalculation = new int[this.tokensInPattern.length + 1];
                } catch (IOException e) {
                    System.out.println("[ERROR] Reading PDB for TopSpin problem failed " +
                            "(file: " + this.pdbFileName + ")");
                }
            }
        }
    }
}
