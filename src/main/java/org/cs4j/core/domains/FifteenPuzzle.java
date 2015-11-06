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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import org.cs4j.core.SearchDomain;

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

    public enum COST_FUNCTION {
        UNIT,
        SQRT,
        INVR,
        HEAVY
    }
    private COST_FUNCTION costFunction;

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
        for (int i = 0; i < this.tilesNumber; i++) {
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
                this.init[p] = t;
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
        // Call the init function and initialize the domain and the instance, according to the read
        // values
        this._init(costFunction);
        // Create a new operator for each possible tile - i is the position of blank
        for (int i = 0; i < this.possibleOperators.length; ++i) {
            this.possibleOperators[i] = new FifteenPuzzleOperator(i);
        }
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

    @Override
    public State initialState() {
        int blank = -1;
        // Initialize the tiles
        int tiles[] = new int[this.tilesNumber];
        for (int i = 0; i < this.tilesNumber; ++i) {
            // Find the blank
            if (this.init[i] == 0) {
                blank = i;
            }
            // In any case, initialize the tiles array with the initial state
            // (read from the input file)
            tiles[i] = this.init[i];
        }
        // Blank must be found!
        if (blank < 0) {
            throw new IllegalArgumentException("No blank tile");
        }

        // Finally, initialize the start state
        TileState s = new TileState();
        s.tiles = tiles;
        s.blank = blank;
        // Let's calculate the heuristic values (h and d)
        s.h = this._computeTotalMD(s.blank, s.tiles, costFunction);
        s.d = this._computeTotalMD(s.blank, s.tiles, COST_FUNCTION.UNIT);
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
        // Copy the position of 0 (blank) and the tile numbered as 1
        copy.blank = ts.blank;
        copy.h = ts.h;
        copy.d = ts.d;
        return copy;
    }

    @Override
    public State applyOperator(State s, Operator op) {
        TileState ts = (TileState) copy(s);
        FifteenPuzzleOperator fop = (FifteenPuzzleOperator) op;
        // Get the updated position of the blank
        int futureBlankPosition = fop.value;
        // Get the tile located at the future blank position
        int tileAtFutureBlankPosition = ts.tiles[fop.value];
        // Move that tile to the current position of blank
        ts.tiles[ts.blank] = tileAtFutureBlankPosition;
        // Update the h and d according to the result deltas
        ts.h += this.mdAddends[tileAtFutureBlankPosition][futureBlankPosition][ts.blank];
        ts.d += this.mdAddendsUnit[tileAtFutureBlankPosition][futureBlankPosition][ts.blank];
        // Update the current blank value to the requested one (MUST be AFTER updating h and d)
        ts.blank = futureBlankPosition;
        return ts;
    }

    @Override
    public long pack(State s) {
        TileState ts = (TileState) s;
        long result = 0;
        // TODO: Sounds that the value of blank is unnecessary
        ts.tiles[ts.blank] = 0;
        // We need at most 4 bits in order to pack a single Fifteen-Puzzle tile: (0b1111 is 15)
        // Thus, we need at most 4 * 16 = 64 bits to pack the full state (64 bits = a lonng number)
        for (int i = 0; i < this.tilesNumber; ++i) {
            result = (result << 4) | ts.tiles[i];
        }
        return result;
    }

    @Override
    public State unpack(long packed) {
        TileState ts = new TileState();
        ts.blank = -1;
        // Start from end and go to start
        for (int i = this.tilesNumber - 1; i >= 0; --i) {
            // Each time, extract a single tile
            int t = (int) packed & 0xF;
            // Initialize this tile
            ts.tiles[i] = t;
            // Mark the blank (in this case there is no need to update the distance between the tile
            // and its required position (in the goal)
            if (t == 0) {
                ts.blank = i;
            } else {
                ts.h += this.md[t][i];
                ts.d += this.mdUnit[t][i];
            }
            // Update the word so that the next tile can be now extracted
            packed >>= 4;
        }
        return ts;
    }

    /**
     * The tile state class
     */
    private final class TileState implements State {

        private int tiles[] = new int[FifteenPuzzle.this.tilesNumber];
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
            this.blank = state.blank;
            // Copy the parent state
            this.parent = state.parent;
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
            return h;
        }

        @Override
        public double getD() {
            return d;
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
        public double getCost(State s) {
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
