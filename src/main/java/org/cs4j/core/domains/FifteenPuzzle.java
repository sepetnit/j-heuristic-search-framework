/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cs4j.core.domains;

import org.cs4j.core.SearchDomain;
import org.cs4j.core.collections.PackedElement;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;


import com.carrotsearch.hppc.LongByteHashMap;

/**
 * The 4x4 sliding-tiles domain class.
 *
 * @author Matthew Hatem
 */
public final class FifteenPuzzle implements SearchDomain {

    private final int width = 4;
    private final int height = 4;
    private final int tilesNumber = this.width * this.height;
    // TODO?
    private int init[] = new int[this.tilesNumber]; // 16
    // Reflection via the diagonal
    private int reflectedIndexes[] = new int[this.tilesNumber]; // 16
    // Pre-computed Manhattan distance between each pair of tiles
    private double md[][] = new double[this.tilesNumber][this.tilesNumber]; // 4x4 array
    // The difference in the Manhattan Distance when applying any kind of operator on any tile
    private double mdAddends[][][] = new double[tilesNumber][tilesNumber][tilesNumber];
    // The unit cost (pure) Manhattan Distance (no cost function)
    private int mdUnit[][] = new int[this.tilesNumber][this.tilesNumber];
    // The pure difference in the Manhattan Distance when applying any kind of operator on any tile
    private int mdAddendsUnit[][][] = new int[this.tilesNumber][this.tilesNumber][this.tilesNumber];
    // The number of possible operators on any possible tile position
    private int operatorsCount[] = new int[tilesNumber];
    // The next tile we get after applying any possible operator on any tile
    private int operatorsNextTiles[][] = new int[tilesNumber][4];
    // The possible operators (each one is represented by the REACHED tile)
    private Operator possibleOperators[] = new Operator[this.tilesNumber];

    private enum HeuristicType {
        MD,
        PDB78,
        PDB555
    }

    private HeuristicType heuristicType;
    private boolean useReflection;

    public enum COST_FUNCTION {
        UNIT,
        SQRT,
        INVR,
        HEAVY
    }

    private COST_FUNCTION costFunction;

    // PDBs for 7-8 partitioning
    private LongByteHashMap pdb7;
    private LongByteHashMap pdb8;

    // PDBs for 5-5-5 partitioning
    private LongByteHashMap pdb5_1;
    private LongByteHashMap pdb5_2;
    private LongByteHashMap pdb5_3;

    private static final Map<String, Class> FifteenPuzzlePossibleParameters;

    // Declare the parameters that can be tunes before running the search
    static
    {
        FifteenPuzzlePossibleParameters = new HashMap<>();
        FifteenPuzzlePossibleParameters.put("heuristic", String.class);
        FifteenPuzzlePossibleParameters.put("pdb-78-files", String.class);
        FifteenPuzzlePossibleParameters.put("pdb-555-files", String.class);
        FifteenPuzzlePossibleParameters.put("use-reflection", Boolean.class);
    }

    /**
     * The function calculates Manhattan distance between two given tiles
     *
     * @param firstTile The first tile
     * @param secondTile The second tile
     *
     * @return The calculated distance
     */
    private int _computeManhattanDistance(int firstTile, int secondTile) {
        // Get Width and Height of the tile
        int firstTileRow = firstTile / this.width;
        int firstTileCol = firstTile % this.width;
        int secondTileRow = secondTile / this.width;
        int secondTileCol = secondTile % this.width;
        return Math.abs(firstTileCol - secondTileCol) + Math.abs(firstTileRow - secondTileRow);
    }

    /**
     * Computes the _getTileCost of moving the tile to the goal
     *
     * @param tile The tile for which the _getTileCost should be computed
     *
     * @return The computed _getTileCost
     */
    private double _getTileCost(int tile) {
        double value = 1.0;
        // Compute the _getTileCost according to the type of the _getTileCost function used in the search
        switch (this.costFunction) {
            case HEAVY:
                value = tile;
                break;
            case SQRT:
                value = Math.sqrt(tile);
                break;
            case INVR:
                value = 1.0d / tile;
                break;
            case UNIT:
                break;
        }
        return value;
    }

    /**
     * Initializes the Manhattan distance heuristic table.
     */
    private void _initMD() {

        // First, calculate Manhattan distance between each pair of tiles
        for (int currentTile = 1; currentTile < this.tilesNumber; ++currentTile) {
            // Calculate the _getTileCost of the tile (important for 'heavy' state)
            double cost = this._getTileCost(currentTile);
            for (int otherTile = 0; otherTile < this.tilesNumber; ++otherTile) {
                // Calculate the Manhattan distance between the tiles
                this.mdUnit[currentTile][otherTile] =
                        this._computeManhattanDistance(currentTile, otherTile);
                this.md[currentTile][otherTile] = this.mdUnit[currentTile][otherTile] * cost;
            }
        }

        // Go over all pairs of tiles and compute the increase/decrease in the Manhattan distance
        // when applying some operator on the puzzle (for any possible pair of tiles and for any
        // possible operator)
        for (int t = 1; t < this.tilesNumber; t++) {
            for (int d = 0; d < this.tilesNumber; d++) {
                double previousDistance = this.md[t][d];
                for (int s = 0; s < this.tilesNumber; s++) {
                    this.mdAddends[t][d][s] = -100; // some invalid value.
                    // Moving Up
                } if (d >= this.width) {
                    // d-width is the index of tile we are on if moving UP
                    this.mdAddends[t][d][d - this.width] =
                            this.md[t][d - this.width] - previousDistance;
                    this.mdAddendsUnit[t][d][d - this.width] = (int)this.mdAddends[t][d][d - this.width];
                    // Moving Left
                } if (d % this.width > 0) {
                    // d-1 is the index of the tile we are on if moving Left
                    this.mdAddends[t][d][d - 1] = this.md[t][d - 1] - previousDistance;
                    this.mdAddendsUnit[t][d][d - 1] = (int)this.mdAddends[t][d][d - 1];
                    // Moving Right
                } if (d % this.width < this.width - 1) {
                    // d+1 is the index of the tile we are on if moving right
                    this.mdAddends[t][d][d + 1] = this.md[t][d + 1] - previousDistance;
                    this.mdAddendsUnit[t][d][d + 1] = (int)this.mdAddends[t][d][d + 1];
                    // Moving Down
                } if (d < this.tilesNumber - this.width) {
                    // d+width is the index of the tile we are on if moving down
                    this.mdAddends[t][d][d + this.width] =
                            this.md[t][d + this.width] - previousDistance;
                    this.mdAddendsUnit[t][d][d + this.width] = (int)this.mdAddends[t][d][d + this.width];
                }
            }
        }
    }


    /**
     * Initializes the operators and their count for each state:
     * This functions initializes an array which specifies for each tile, the tile that is reached
     * after applying each possible operator
     *
     * Note that each time we add an optional operator, we increase the operators counter by 1
     *
     * The tile-puzzle board looks like this:
     *
     * 0  1  2  3
     * 4  5  6  7
     * 8  9  10 11
     * 12 13 14 15
     */
    private void _initOperators() {
        for (int i = 0; i < this.tilesNumber; ++i) {
            // Initially, there is no optional operators, so initialize the counter to 0
            this.operatorsCount[i] = 0;
            // Move up
            if (i >= this.width) {
                this.operatorsNextTiles[i][this.operatorsCount[i]++] = i - this.width;
            }
            // Move left
            if (i % this.width > 0) {
                this.operatorsNextTiles[i][this.operatorsCount[i]++] = i - 1;
            }
            // Move right
            if (i % this.width < this.width - 1) {
                this.operatorsNextTiles[i][this.operatorsCount[i]++] = i + 1;
            }
            // Move down
            if (i < this.tilesNumber - this.width) {
                this.operatorsNextTiles[i][this.operatorsCount[i]++] = i + this.width;
            }
            // In any case there cannot be more than 4 possible operators - so assert this
            assert (this.operatorsCount[i] <= 4);
        }
    }

    private void _init(COST_FUNCTION cost) {
        this.costFunction = cost;
        this._initMD();
        this._initOperators();
        // Create a new operator for each possible tile - i is the position of blank
        for (int i = 0; i < this.possibleOperators.length; ++i) {
            this.possibleOperators[i] = new FifteenPuzzleOperator(i);
        }
        // Manhattan Distance is the default heuristic type
        this.heuristicType = HeuristicType.MD;
        // By default reflection is not used!
        this.useReflection = false;
        // Initially, no pdb-7-8
        this.pdb7 = null;
        this.pdb8 = null;
        // Initially, no pdb-555
        this.pdb5_1 = null;
        this.pdb5_2 = null;
        this.pdb5_3 = null;
    }

    @Override
    public boolean isCurrentHeuristicConsistent() {
        return (this.heuristicType == HeuristicType.MD);
    }

    /**
     * A default constructor of the class:
     *
     * Initialize a default instance of the FifteenPuzzle such that all the tiles are at their place
     */
    public FifteenPuzzle() {
        for (int t = 0; t < this.tilesNumber; ++t) {
            this.init[t] = t;
        }
        // Initialize the rest
        this._init(COST_FUNCTION.UNIT);
    }

    /**
     * The constructor reads an instance of Fifteen Puzzle problem from the specified input stream
     *
     * @param stream The input stream to read the problem from
     * @param costFunction The cost function to use
     */
    public FifteenPuzzle(InputStream stream, COST_FUNCTION costFunction) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            // First, read the dimensions of the puzzle (and ignore them: assume they are constant)
            String line = reader.readLine();
            /*
            String dim[] = line.split(" ");
            // Width
            Integer.parseInt(dim[0]);
            // Height
            Integer.parseInt(dim[0]);
            */
            line = reader.readLine();
            // Now, start reading the puzzle itself (Each tile in a single line)
            for (int t = 0; t < this.tilesNumber; ++t) {
                // Read a value of a tile and place p
                int p = Integer.parseInt(reader.readLine());
                // Insert the tile value into the storage
                this.init[t] = p;
            }
            // Now, read the goal positions of the puzzle (Each one in a single line)
            // (actually, reading the goals is redundant - since they must form 1-15 series:
            // goal i at line i)
            line = reader.readLine();
            for (int t = 0; t < this.tilesNumber; ++t) {
                int p = Integer.parseInt(reader.readLine());
                if (p != t) {
                    throw new IllegalArgumentException("Non-canonical goal positions");
                }
            }
        } catch(IOException e) {
            e.printStackTrace();
            Utils.fatal("Error reading input file");
        }
        // Call the init function and initialize the domain and the instance, according to the read values
        this._init(costFunction);
    }

    /**
     * The constructor reads a Fifteen Puzzle problem instance from the specified input stream.
     *
     * @param stream the input stream to read
     */
    public FifteenPuzzle(InputStream stream) {
        this(stream, COST_FUNCTION.UNIT);
    }

    /**
     * A copy constructor of the class - used for cases when heuristic is calculated with PDB -
     *
     * we don't want to read it each time again
     *
     * @param other Instance to copy from
     *
     * @param stream The input stream to read the tiles from
     */
    public FifteenPuzzle(FifteenPuzzle other, InputStream stream) {
        // Call a regular constructor
        this(stream);
        // Copy heuristic related data
        this.heuristicType = other.heuristicType;
        this.useReflection = other.useReflection;
        this.pdb7 = other.pdb7;
        this.pdb8 = other.pdb8;
        this.pdb5_1 = other.pdb5_1;
        this.pdb5_2 = other.pdb5_2;
        this.pdb5_3 = other.pdb5_3;
    }

    /**
     * Computes the TOTAL Manhattan distance for the specified blank and tile configuration.
     *
     * @param blank The position of the blank in the given state
     * @param tiles The tiles configuration
     * @param function The _getTileCost function to apply on the calculated value
     *
     * @return The calculated Manhattan distance
     */
    private double _computeTotalMD(int blank, int tiles[], COST_FUNCTION function) {
        double sum = 0;
        // Go over all the tiles and summarize the distances between them and the goals
        for (int i = 0; i < this.tilesNumber; i++) {
            // No need for this - if all tiles are at their positions that implies that the blank
            // is too
            if (i == blank) {
                continue;
            }
            // The md array already contains the _getTileCost to moving the tile to the goal
            sum += this.md[i][tiles[i]];
        }
        return sum;
    }

    /**
     * This function is called in case the heuristic type is not Manhattan Distance
     *
     * @param state The state for which the values should be computed
     *
     * @return An array of the form {h, d}
     */
    private double[] _computeHDNoMD(TileState state) {
        double h;
        double d;
        switch (this.heuristicType) {
            case PDB78: {
                h = this.pdb7.get(state.getHash7Index()) +
                        this.pdb8.get(state.getHash8Index());
                if (this.useReflection) {
                    int hRef = this.pdb7.get(state.getHash7ReflectionIndex()) +
                            this.pdb8.get(state.getHash8ReflectionIndex());
                    h = Math.max(h, hRef);
                }
                d = h;
                break;
            }
            case PDB555: {
                h = this.pdb5_1.get(state.getHash5_1Index()) +
                        this.pdb5_2.get(state.getHash5_2Index()) +
                        this.pdb5_3.get(state.getHash5_3Index());
                if (this.useReflection) {
                    int hRef = this.pdb5_1.get(state.getHash5_1ReflectIndex()) +
                            this.pdb5_2.get(state.getHash5_2ReflectIndex()) +
                            this.pdb5_3.get(state.getHash5_3ReflectIndex());
                    h = Math.max(h, hRef);
                }
                d = h;
                break;
            }
            default: {
                throw new NotImplementedException();
            }
        }
        return new double[] {h, d};
    }

    /**
     * The function computes the values of h and d for a given state
     *
     * @param state The state for which the values should be computed
     *
     * @return An array of the form {h, d}
     */
    private double[] computeHD(TileState state) {
        double h;
        double d;
        switch (this.heuristicType) {
            case MD: {
                // Let's calculate the heuristic values (h and d)
                h = this._computeTotalMD(state.blank, state.tiles, this.costFunction);
                d = this._computeTotalMD(state.blank, state.tiles, COST_FUNCTION.UNIT);
                break;
            }
            default: {
                return this._computeHDNoMD(state);
            }
        }
        assert h != -1 && d != -1;
        return new double[]{h, d};
    }

    /**
     * Creates an initial state but ignores any heuristic related issues
     *
     * @return The created state
     */
    public TileState initialStateNoHeuristic() {
        int blank = -1;
        // Initialize the tiles
        int tiles[] = new int[this.tilesNumber];
        int positionsOfTiles[] = new int[this.tilesNumber];
        for (int i = 0; i < this.tilesNumber; ++i) {
            // Find the blank
            if (this.init[i] == 0) {
                blank = i;
            }
            int t = this.init[i];
            // In any case, initialize the tiles array with the initial state
            // (read from the input file)
            tiles[i] = t;
            // Mark the position of current tile
            positionsOfTiles[t] = i;
        }
        // Blank must be found!
        if (blank < 0) {
            throw new IllegalArgumentException("No blank tile");
        }

        // Finally, initialize the start state
        TileState s = new TileState();
        s.tiles = tiles;
        s.positionsOfTiles = positionsOfTiles;
        s.blank = blank;
        return s;
    }

    /**
     * Each permutation is mapped to an index corresponding to its position in a lexicographic ordering of all
     * permutations
     *
     * The function computes this value for a given permutation
     *
     * @param permutation The vector to be hashed
     *
     * @return The computed hash value
     */
    public long _getHashNIndex(int[] permutation) {
        int actualLength = permutation.length - 1;
        // initialize hash value to empty
        long hash = 0;
        // for each remaining position in permutation
        for (int i = 0; i < actualLength + 1; ++i) {
            // initially digit is value in permutation
            int digit = permutation[i];
            // for each previous element in permutation
            for (int j = 0; j < i; ++j) {
                // previous position contains smaller value - so decrement digit
                if (permutation[j] < permutation[i]) {
                    --digit;
                }
            }
            // multiply digit by appropriate factor
            hash = hash * (FifteenPuzzle.this.tilesNumber - i) + digit;
        }
        return hash / (FifteenPuzzle.this.tilesNumber - actualLength);
    }

    @Override
    public State initialState() {
        TileState s = this.initialStateNoHeuristic();
        // Let's calculate the heuristic values (h and d)
        double[] computedHD = this.computeHD(s);
        s.h = computedHD[0];
        s.d = computedHD[1];
        //System.out.println(s.dumpState());
        return s;
    }

    @Override
    public boolean isGoal(State state) {
        // The state is a goal if the estimated number of tile shifts, between it and the goal, is 0
        return ((TileState) state).d == 0;
    }

    /*
    public List<Operator> getOperators(State s) {
        TileState ts = (TileState) s;
        // Let's get the number of possible operators for this state - all possible operations are
        // achieved by moving the blank
        int currentOperatorsCount = this.operatorsCount[ts.blank];
        // TODO: Why * 2?
        List<Operator> list = new ArrayList<Operator>(currentOperatorsCount * 2);
        // Go over all the operators in the operators table and add them to the result list
        for (int i = 0; i < currentOperatorsCount; i++) {
            // Get an operator of reaching the this.operatorsNextTiles[ts.blank][i] tile
            Operator op = this.possibleOperators[this.operatorsNextTiles[ts.blank][i]];
            list.add(op);
        }
        return list;
    }
    */

    @Override
    public int getNumOperators(State state) {
        // The number of the operators depends only on the position of the blank
        return this.operatorsCount[((TileState) state).blank];
    }

    @Override
    public Operator getOperator(State s, int index) {
        TileState ts = (TileState) s;
        // Return the operator according to the position of the blank after applying the operator
        // whose index equals to the given one
        return this.possibleOperators[this.operatorsNextTiles[ts.blank][index]];
    }

    @Override
    public State copy(State s) {
        TileState ts = (TileState) s;
        TileState copy = new TileState();
        // Copy the tiles
        System.arraycopy(ts.tiles, 0, copy.tiles, 0, ts.tiles.length);
        // Copy the positions of the tiles
        System.arraycopy(ts.positionsOfTiles, 0, copy.positionsOfTiles, 0, ts.positionsOfTiles.length);
        // Copy the position of 0 (blank) and the tile numbered as 1
        copy.blank = ts.blank;
        copy.h = ts.h;
        copy.d = ts.d;
        return copy;
    }

    /**
     * Apply an operator, but ignore any heuristic related issues
     *
     * @param s The state to apply the operator on
     * @param op The operator to apply
     *
     * @return The result state
     */
    /*public State applyOperatorNoHeuristic(State s, Operator op) {
        TileState ts = (TileState) copy(s);
        FifteenPuzzleOperator fop = (FifteenPuzzleOperator) op;
        // Get the updated position of the blank
        int futureBlankPosition = fop.value;
        // Get the tile located at the future blank position
        int tileAtFutureBlankPosition = ts.tiles[fop.value];
        // Move that tile to the current position of blank
        ts.tiles[ts.blank] = tileAtFutureBlankPosition;
        ts.blank = futureBlankPosition;
        return ts;
    }
    */


    @Override
    public State applyOperator(State s, Operator op) {
        TileState ts = (TileState) copy(s);
        FifteenPuzzleOperator fop = (FifteenPuzzleOperator) op;
        // Get the updated position of the blank
        int futureBlankPosition = fop.value;
        // Get the tile that is currently located at a position that will be converted to blank in the next step
        int currentTileAtFutureBlankPosition = ts.tiles[fop.value];
        // Move that tile to the current position of blank
        ts.tiles[ts.blank] = currentTileAtFutureBlankPosition;
        // Update the h and d according to the result deltas
        if (this.heuristicType == HeuristicType.MD) {
            ts.h += this.mdAddends[currentTileAtFutureBlankPosition][futureBlankPosition][ts.blank];
            ts.d += this.mdAddendsUnit[currentTileAtFutureBlankPosition][futureBlankPosition][ts.blank];
            // Update the current blank value to the requested one (MUST be AFTER updating h and d)
            ts.blank = futureBlankPosition;
            ts.tiles[futureBlankPosition] = 0;
        } else {
            ts.tiles[futureBlankPosition] = 0;
            ts.positionsOfTiles[currentTileAtFutureBlankPosition] = ts.blank;
            ts.positionsOfTiles[0] = futureBlankPosition;
            ts.blank = futureBlankPosition;
            double[] computedHD = this._computeHDNoMD(ts);
            ts.h = computedHD[0];
            ts.d = computedHD[1];
        }
        return ts;
    }

    @Override
    public PackedElement pack(State s) {
        TileState ts = (TileState) s;
        long result = 0;
        // TODO: Sounds that the value of blank is unnecessary
        assert ts.tiles[ts.blank] == 0;
        //ts.tiles[ts.blank] = 0;
        // We need at most 4 bits in order to pack a single Fifteen-Puzzle tile: (0b1111 is 15)
        // Thus, we need at most 4 * 16 = 64 bits to pack the full state (64 bits = a long number)
        for (int i = 0; i < this.tilesNumber; ++i) {
            result = (result << 4) | ts.tiles[i];
        }
        return new PackedElement(result);
    }

    /**
     * Unpacks the state but ignores all the heuristic related issues
     *
     * @param packed The packed state
     *
     * @return The unpacked state
     */
    public State unpackNoHeuristic(PackedElement packed) {
        assert packed.getLongsCount() == 1;
        long firstPacked = packed.getFirst();
        TileState ts = new TileState();
        ts.blank = -1;
        // Start from end and go to start
        for (int i = this.tilesNumber - 1; i >= 0; --i) {
            // Each time, extract a single tile
            int t = (int) firstPacked & 0xF;
            // Initialize this tile
            ts.tiles[i] = t;
            ts.positionsOfTiles[t] = i;
            // Mark the blank (in this case there is no need to update the distance between the tile
            // and its required position (in the goal)
            if (t == 0) {
                ts.blank = i;
            }
            // Update the word so that the next tile can be now extracted
            firstPacked >>= 4;
        }
        return ts;
    }

    @Override
    public State unpack(PackedElement packed) {
        assert packed.getLongsCount() == 1;
        long firstPacked = packed.getFirst();
        TileState ts = new TileState();
        ts.blank = -1;
        // Start from end and go to start
        for (int i = this.tilesNumber - 1; i >= 0; --i) {
            // Each time, extract a single tile
            int t = (int) firstPacked & 0xF;
            // Initialize this tile
            ts.tiles[i] = t;
            ts.positionsOfTiles[t] = i;
            // Mark the blank (in this case there is no need to update the distance between the tile
            // and its required position (in the goal)
            if (t == 0) {
                ts.blank = i;
            } else if (this.heuristicType == HeuristicType.MD) {
                ts.h += this.md[t][i];
                ts.d += this.mdUnit[t][i];
            }
            // Update the word so that the next tile can be now extracted
            firstPacked >>= 4;
        }
        if (this.heuristicType != HeuristicType.MD) {
            double[] computedHD = this.computeHD(ts);
            ts.h = computedHD[0];
            ts.d = computedHD[1];
        }
        return ts;
    }

    /**
     * The tile state class
     */
    private final class TileState implements State {

        private int tiles[] = new int[FifteenPuzzle.this.tilesNumber];
        private int positionsOfTiles[] = new int[FifteenPuzzle.this.tilesNumber];
        private int blank;

        private double h;
        private double d;

        private TileState parent = null;

        /**
         * A default constructor (required for the {@see initialState()} function
         */
        private TileState() { }

        /**
         * A copy constructor
         *
         * @param state The state to copy
         */
        private TileState(TileState state) {
            this.h = state.h;
            this.d = state.d;
            // Copy the tiles
            System.arraycopy(this.tiles, 0, state.tiles, 0,this.tiles.length);
            System.arraycopy(this.positionsOfTiles, 0, state.positionsOfTiles, 0,this.positionsOfTiles.length);
            this.blank = state.blank;
            // Copy the parent state
            this.parent = state.parent;
        }

        /**
         * Creates a permutation array for computing hash indexes in the PDBs
         *
         * @param permutationLengthNoBlank The length of the permutation array
         *                                 (will be actually greater by 1 - for having the blank at the end)
         * @param firstTileIndex The index of the first tile to create the permutation from
         *                       (assumes each PDB is constructed of sequential tiles)
         * @return The computed index in the relevant PDB
         */
        private long _getHashNIndex(int permutationLengthNoBlank, int firstTileIndex) {
            int[] permutation = new int[permutationLengthNoBlank + 1];
            // The last element of the permutation array always contains the blank
            permutation[permutationLengthNoBlank] = this.positionsOfTiles[0];
            System.arraycopy(this.positionsOfTiles, firstTileIndex, permutation, 0, permutationLengthNoBlank);
            return FifteenPuzzle.this._getHashNIndex(permutation);
        }

        /**
         * Creates a permutation array for computing hash indexes in the PDBs but now, uses reflected tiles
         *
         * @param permutationLengthNoBlank The length of the permutation array
         *                                 (will be actually greater by 1 - for having the blank at the end)
         * @param firstTileIndex The index of the first tile to create the permutation from
         *                       (assumes each PDB is constructed of sequential tiles)
         * @return The computed index in the relevant PDB
         *
         * Explanation:
         *  1. reflect[] return the reflected index of the required tiles
         *  2. s2[reflect[]] returns the position of that tile
         *  3. The last reflect[] returns the reflected position, such that now it suits the PDB we currently have
         */
        private long _getHashNReflectionIndex(int permutationLengthNoBlank, int firstTileIndex) {
            int[] permutation = new int[permutationLengthNoBlank + 1];
            // The last element of the permutation array always contains the reflection of blank
            permutation[permutationLengthNoBlank] =
                    FifteenPuzzle.this.reflectedIndexes[
                            this.positionsOfTiles[
                                    FifteenPuzzle.this.reflectedIndexes[0]]];
            // Now, reflect other tiles
            for (int i = 0; i < permutationLengthNoBlank; ++i) {
                permutation[i] =
                        FifteenPuzzle.this.reflectedIndexes[
                                this.positionsOfTiles[
                                        FifteenPuzzle.this.reflectedIndexes[firstTileIndex + i]]];
            }
            return FifteenPuzzle.this._getHashNIndex(permutation);
        }

        private long getHash7Index() {
            return this._getHashNIndex(7, 1);
        }

        private long getHash7ReflectionIndex() {
            return this._getHashNReflectionIndex(7, 1);
        }

        private long getHash8Index() {
            return this._getHashNIndex(8, 8);
        }

        private long getHash8ReflectionIndex() {
            return this._getHashNReflectionIndex(8, 8);
        }

        private long getHash5_1Index() {
            return this._getHashNIndex(5, 1);
        }

        private long getHash5_1ReflectIndex() {
            return this._getHashNReflectionIndex(5, 1);
        }

        private long getHash5_2Index() {
            return this._getHashNIndex(5, 6);
        }

        private long getHash5_2ReflectIndex() {
            return this._getHashNReflectionIndex(5, 6);
        }

        private long getHash5_3Index() {
            return this._getHashNIndex(5, 11);
        }

        private long getHash5_3ReflectIndex() {
            return this._getHashNReflectionIndex(5, 11);
        }

        @Override
        public boolean equals(Object object) {
            try {
                TileState tileState = (TileState)object;
                // Compare all the tiles
                return Arrays.equals(this.tiles, tileState.tiles);
            } catch (ClassCastException e) {
                return false;
            }
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
            sb.append(this.h);
            sb.append("\n");
            // d
            sb.append("d: ");
            sb.append(this.d);
            sb.append("\n");

            int horizontalHyphensCount = FifteenPuzzle.this.width * 2 +
                    FifteenPuzzle.this.width * 2 + (FifteenPuzzle.this.width + 1);
            // Dump the puzzle
            for (int i = 0; i < FifteenPuzzle.this.tilesNumber; ++i) {
                if (i % FifteenPuzzle.this.width == 0) {
                    for (int j = 0; j < horizontalHyphensCount; ++j) {
                        sb.append("-");
                    }
                    sb.append("\n");
                }
                sb.append("| ");
                if (this.tiles[i] > 0 && this.tiles[i] < 10) {
                    sb.append("0");
                }
                if (this.blank == i) {
                    sb.append("   ");
                } else {
                    sb.append(this.tiles[i]);
                    sb.append(" ");
                }
                if (i % FifteenPuzzle.this.width == FifteenPuzzle.this.width - 1) {
                    sb.append("|\n");
                }
            }
            for (int j = 0; j < horizontalHyphensCount; ++j) {
                sb.append("-");
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

    @Override
    public String dumpStatesCollection(State[] states) {
        return null;
    }

    @Override
    public Map<String, Class> getPossibleParameters() {
        return FifteenPuzzle.FifteenPuzzlePossibleParameters;
    }

    // Size of the PDB for 5 tiles
    private static final long TABLE_SIZE_PDB5 = 16 * 15 * 14 * 13 * 12;
    // Size of the PDB for the 7 first tiles
    private static final long TABLE_SIZE_PDB7 = 16 * 15 * 14 * 13 * 12 * 11 * 10;
    // Size of the PDB for the 8 rest tiles
    private static final long TABLE_SIZE_PDB8 = 16 * 15 * 14 * 13 * 12 * 11 * 10 * 9;

    /**
     * Read a permutation index from the DB
     *
     * @param inputStream The DB of permutations
     *
     * @return The read index
     *
     * @throws IOException If something wrong occurred
     */
    private long _readIndex(DataInputStream inputStream) throws IOException {
        return inputStream.readInt() & 0xffffffffl;
    }

    /**
     * Reads a single PDB table from the given file
     *
     * @param pdbFileName The name of the PDB file
     * @param permutationsCount The number of permutations assumed to be in the file
     *
     * @return An initialized map that contains all the distances for the permutations
     *
     * @throws IOException If something wrong occurred
     */
    private LongByteHashMap _readSinglePDB(String pdbFileName, long permutationsCount) throws IOException {
        LongByteHashMap toReturn = new LongByteHashMap();
        // Each permutation index is stored as int
        DataInputStream inputStream = new DataInputStream(new FileInputStream(pdbFileName));
        for (long i = 0; i < permutationsCount; ++i) {
            // Debug
            if (i % 999 == 0) {
                System.out.print("\r[INFO] Read " + (i + 1) + "/" + permutationsCount + " values");
            }
            // First, read the hash value of the permutation
            long hashValue = this._readIndex(inputStream);
            // Now, read the distance
            byte distance = inputStream.readByte();
            if (hashValue >= permutationsCount) {
                System.out.println("[ERROR] Invalid hash value found in PDB " + pdbFileName +
                        "(hash: " + hashValue + ", distance: " + distance + ")");
                throw new IOException();
            }
            // Debug:
            /*
            if (toReturn.containsKey(hashValue)) {
                System.out.println((int)toReturn.get(hashValue));
                System.out.println((int) distance);
                assert toReturn.get(hashValue) == distance;
            } else {
                toReturn.put(hashValue, distance);
            }*/
            toReturn.put(hashValue, distance);
        }
        // Last new line
        System.out.println();
        return toReturn;
    }

    private void _readPDB78(String pdb7FileName, String pdb8FileName) throws IOException {
        // Read PDB 7
        System.out.println("[INFO] Reading PDB from " + pdb7FileName);
        this.pdb7 = this._readSinglePDB(pdb7FileName, FifteenPuzzle.TABLE_SIZE_PDB7);
        System.out.println("[INFO] Finished reading PDB from " + pdb7FileName);
        // Read PDB 8
        System.out.println("[INFO] Reading PDB from " + pdb8FileName);
        this.pdb8 = this._readSinglePDB(pdb8FileName, FifteenPuzzle.TABLE_SIZE_PDB8);
        System.out.println("[INFO] Finished reading PDB from " + pdb8FileName);
    }


    private void _readPDB555(String pdb5_1FileName, String pdb5_2FileName, String pdb5_3FileName) throws IOException {
        // Read PDB 5_1
        System.out.println("[INFO] Reading PDB from " + pdb5_1FileName);
        this.pdb5_1 = this._readSinglePDB(pdb5_1FileName, FifteenPuzzle.TABLE_SIZE_PDB5);
        System.out.println("[INFO] Finished reading PDB from " + pdb5_1FileName);
        // Read PDB 5_2
        System.out.println("[INFO] Reading PDB from " + pdb5_2FileName);
        this.pdb5_2 = this._readSinglePDB(pdb5_2FileName, FifteenPuzzle.TABLE_SIZE_PDB5);
        System.out.println("[INFO] Finished reading PDB from " + pdb5_2FileName);
        // Read PDB 5_3
        System.out.println("[INFO] Reading PDB from " + pdb5_3FileName);
        this.pdb5_3 = this._readSinglePDB(pdb5_3FileName, FifteenPuzzle.TABLE_SIZE_PDB5);
        System.out.println("[INFO] Finished reading PDB from " + pdb5_3FileName);
    }

    /**
     * Calculates the reflected index via the diagonal
     *
     * @param tile The tile (index) to calculate the reflection on
     *
     * @return The calculated reflection index
     */
    private int _getReflectedTile(int tile) {
        return (tile % this.width) * this.width + (tile / this.height);
    }

    @Override
    public void setAdditionalParameter(String parameterName, String value) {
        switch (parameterName) {
            case "heuristic": {
                switch (value) {
                    case "md": {
                        this.heuristicType = HeuristicType.MD;
                        break;
                    }
                    case "pdb-78": {
                        this.heuristicType = HeuristicType.PDB78;
                        break;
                    }
                    case "pdb-555": {
                        this.heuristicType = HeuristicType.PDB555;
                        break;
                    }
                    default: {
                        System.err.println("Illegal heuristic type for FifteenPuzzle domain: " + value);
                        throw new IllegalArgumentException();
                    }
                }
                break;
            }
            case "pdb-555-files": {
                if (heuristicType != HeuristicType.PDB555) {
                    System.out.println("[ERROR] Heuristic type isn't pdb-555 - can't set pdb file");
                    throw new IllegalArgumentException();
                }
                String[] split = value.trim().split(",");
                if (split.length == 1) {
                    split = value.trim().split(", ");
                }
                if (split.length != 3) {
                    System.out.println("[ERROR] Invalid format for pdb-555 file: " +
                            "should be '<file-5_1>, <file-5_2> <file-5_3>'");
                    throw new IllegalArgumentException();
                }
                // Otherwise, read and initialize the files
                try {
                    this._readPDB555(split[0], split[1], split[2]);
                } catch (IOException e) {
                    System.out.println("[ERROR] Failed reading pdb-555 files: " + e.getMessage());
                    throw new IllegalArgumentException();
                }
                break;
            }
            case "pdb-78-files": {
                if (heuristicType != HeuristicType.PDB78) {
                    System.out.println("[ERROR] Heuristic type isn't pdb-78 - can't set pdb file");
                    throw new IllegalArgumentException();
                }
                String[] split = value.trim().split(",");
                if (split.length == 1) {
                    split = value.trim().split(", ");
                }
                if (split.length != 2) {
                    System.out.println("[ERROR] Invalid format for pdb-78 files: should be '<file-7>, <file-8>'");
                    throw new IllegalArgumentException();
                }
                // Otherwise, read and initialize the file
                try {
                    this._readPDB78(split[0], split[1]);
                } catch (IOException e) {
                    System.out.println("[ERROR] Failed reading pdb-78 files: " + e.getMessage());
                    throw new IllegalArgumentException();
                }
                break;
            }
            case "use-reflection": {
                if (this.heuristicType == HeuristicType.MD) {
                    System.out.println("[ERROR] Reflection is only relevant if PDB-555 or PDB-78 heuristics are used");
                    throw new IllegalArgumentException();
                }
                this.useReflection = Boolean.parseBoolean(value);
                // Otherwise, let's initialize the reflection array
                for (int tile = 0; tile < this.tilesNumber; ++tile) {
                    this.reflectedIndexes[tile] = this._getReflectedTile(tile);
                }
                break;
            }
            default: {
                System.out.println("No such parameter: " + parameterName + " (value: " + value + ")");
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * A single operator that can be applied on the FifteenPuzzle
     */
    private final class FifteenPuzzleOperator implements Operator {

        private int value;

        /**
         * The constructor of the class
         *
         * @param value Value of the operator : direction to apply
         */
        private FifteenPuzzleOperator(int value) {
            this.value = value;
        }

        @Override
        public double getCost(State s, State parent) {
            // All the operators have the same cost
            TileState ts = (TileState) s;
            int tile = ts.tiles[value];
            return FifteenPuzzle.this._getTileCost(tile);
        }

        @Override
        public Operator reverse(State s) {
            TileState ts = (TileState) s;
            // Get the reverse operator - it must be operator that cause the blank to be located at
            // its location on the given state
            return FifteenPuzzle.this.possibleOperators[ts.blank];
        }
    }
}
