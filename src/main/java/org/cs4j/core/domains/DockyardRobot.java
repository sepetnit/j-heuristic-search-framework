/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cs4j.core.domains;

import com.sun.istack.internal.NotNull;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.collections.PackedElement;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/*
 * DockyardRobot domain
 */
public class DockyardRobot implements SearchDomain {

    private Location[] initialLocation;
    private int[] goals;
    private int[] maximumPilesCountAtPosition;
    private int[] maximumCranesCountAtPosition;

    private double[][] adj;
    private int locationsCount;
    private int boxesCount;
    private int cranesCount;
    private int pilesCount;

    double[][] shortest;

    private long robotLocationBitMask;
    private long positionsBitMask;
    private int robotLocationBitsCount;
    private int positionsBitsCount;

    //LoadCost is the cost of loading and unloading the robot.
    //private static final double LoadCost = 0.01;
    //private static final double LoadCost = 0.01d;
    private static final double LoadCost = 1d;

    //PopCostFact is the cost of pop'ing from a stack, given as a factor of the stack's height.
    //private static final double PopCostFact = 0.05d;
    private static final double PopCostFact = 5d;

    /**
     * Init the possible operators for the given state
     *
     * @param state The state whose operators should be initialized
     */
    private void initOps(DRobotState state) {
        // An empty vector of operators
        Vector<DRobotOperator> possibleOperators = new Vector<>();
        // Get the location of the robot - this is the location we start from
        Location location = state.locations[state.robotLocation];

        // Push cranes onto piles
        int lastPile = location.piles.size();

        // In case the number of piles is greater than available number of piles for the location
        // the robot is on, update it to be the maximum available value
        if (lastPile >= this.maximumPilesCountAtPosition[state.robotLocation]) {
            lastPile = this.maximumPilesCountAtPosition[state.robotLocation] - 1;
        }

        // Go over all the cranes at the location, that contain boxes and add a possible operation
        // of pushing some box from the crane onto a pile
        for (int c = 0; c < location.cranes.size(); c++) {
            // Go over all the possible piles
            for (int p = 0; p <= lastPile; p++)
                // Add an operation of pushing boxes that are on the crane, onto the pile
                possibleOperators.add(new DRobotOperator(DRobotOperator.PUSH, c, p));
        }

        // Whether there is some empty crane that can take a box from some pile?
        if (location.cranes.size() < this.maximumCranesCountAtPosition[state.robotLocation]) {
            // Go over all the piles
            for (int p = 0; p < location.piles.size(); p++) {
                possibleOperators.add(new DRobotOperator(DRobotOperator.POP, p));
            }
            // Unload a container from the robot into a crane
            if (state.boxLoadedByRobot >= 0) {
                possibleOperators.add(new DRobotOperator(DRobotOperator.UNLOAD));
            }
        }

        // Load the robot if it is empty
        if (state.boxLoadedByRobot < 0) {
            // Go over all the cranes that contain boxes and add robot loading operation
            for (int c = 0; c < location.cranes.size(); c++)
                possibleOperators.add(new DRobotOperator(DRobotOperator.LOAD, c));
        }

        // Move the robot (all locations except the current one are possible)
        for (int i = 0; i < this.locationsCount; i++) {
            if (i == state.robotLocation) {
                continue;
            }
            possibleOperators.add(new DRobotOperator(DRobotOperator.MOVE, i));
        }
        // Finally, create the possible operators array
        state.ops = possibleOperators.toArray(new DRobotOperator[possibleOperators.size()]);
    }

    /**
     * The constructor of the class
     *
     * @param stream The input stream for parsing the instance
     */
    public DockyardRobot(InputStream stream) {

        int nrobots;

        BufferedReader in = new BufferedReader(new InputStreamReader(stream));

        try {
            this.locationsCount = getInt(in.readLine());
            this.cranesCount = getInt(in.readLine());
            this.boxesCount = getInt(in.readLine());
            this.pilesCount = getInt(in.readLine());
            nrobots = getInt(in.readLine());

            if (nrobots != 1) {
                Utils.fatal("Multiple robots are not supported");
            }

            this.adj = new double[locationsCount][locationsCount];
            this.maximumCranesCountAtPosition = new int[locationsCount];
            this.maximumPilesCountAtPosition = new int[locationsCount];

            // Initialize the array (initially, no piles at any location)
            for (int i = 0; i < this.maximumPilesCountAtPosition.length; ++i) {
                this.maximumCranesCountAtPosition[i] = 0;
                this.maximumPilesCountAtPosition[i] = 0;
            }

            // The initial locations (each is represented by a class)
            this.initialLocation = new Location[locationsCount];
            for (int i = 0; i < this.initialLocation.length; ++i) {
                this.initialLocation[i] = new Location();
            }

            // The goals array
            this.goals = new int[boxesCount];
            // Initially, the goals array is empty ...
            for (int i = 0; i < this.goals.length; i++) {
                this.goals[i] = -1;
            }

            // Locations of piles, initially are empty
            int[] pilesLocations = new int[this.pilesCount];

            // Now, read the locations
            int loc = -1;
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                String[] tokens = line.split("\\s+");
                if (tokens.length == 0) {
                    continue;
                }
                // Go over all the possible values of the first token and treat the next tokens
                // accordingly
                switch (tokens[0]) {
                    // Location on the grid
                    case "location": {
                        // Read some location
                        loc = Integer.parseInt(tokens[1]);
                        if (loc < 0 || loc >= this.locationsCount) {
                            Utils.fatal("Invalid location " + loc);
                        }
                        break;
                    // Adjacency list of the last read location
                    }
                    case "adjacent:": {
                        // In this case, the rest of the read tokens are the distances to the
                        // current location
                        if (tokens.length != this.locationsCount + 1) {
                            Utils.fatal("Malformed adjacency list for location " + loc);
                        }
                        // Now, read the distances, which actually all are double numbers
                        // (start from 1 - 0 contains the string 'adjacent')
                        for (int i = 1; i < tokens.length; ++i) {
                            Double d = Double.parseDouble(tokens[i]);
                            this.adj[loc][i - 1] = d;
                        }
                        break;
                        // The maximum number of cranes that can be stored at this location
                    }
                    case "cranes:": {
                        this.maximumCranesCountAtPosition[loc] = tokens.length - 1;
                        break;
                        // piles
                    }
                    case "piles:": {
                        for (int i = 1; i < tokens.length; ++i) {
                            int p = Integer.parseInt(tokens[i]);
                            if (p < 0 || p >= this.pilesCount) {
                                Utils.fatal("Malformed pile list, pile " + p + " is out of bounds");
                            }
                            // Pile numbered p is located at location loc
                            pilesLocations[p] = loc;
                            // How many piles can be located at the current location?
                            this.maximumPilesCountAtPosition[loc]++;
                        }
                        break;
                    // A specific pile
                    }
                    case "pile": {
                        if (tokens.length != 2) {
                            Utils.fatal("Malformed pile descriptor");
                        }
                        int pnum = Integer.parseInt(tokens[1]);
                        if (pnum < 0 || pnum >= this.pilesCount) {
                            Utils.fatal("Malformed pile descriptor, pile " + pnum +
                                    " is out of bounds");
                        }
                        line = in.readLine();
                        if (line == null || line.trim().length() == 0) {
                            continue;
                        }
                        line = line.trim();
                        tokens = line.split("\\s+");
                        // Create a new pile for the read value
                        Pile p = new Pile();
                        for (String tok : tokens) {
                            int box = Integer.parseInt(tok);
                            if (box < 0 || box >= this.boxesCount) {
                                Utils.fatal("Malformed pile, box " + box + " is out of bounds");
                            }
                            p.stack.add(box);
                        }
                        // In case that pile has some boxes inside - add it to the initial locations
                        // array
                        if (p.stack.size() > 0) {
                            this.initialLocation[pilesLocations[pnum]].piles.add(p);
                        }
                        break;
                        // Container should contain boxes ...
                    }
                    case "container": {
                        if (tokens.length != 3) {
                            Utils.fatal("Malformed goals descriptor");
                        }
                        int box = Integer.parseInt(tokens[1]);
                        if (box < 0 || box >= this.boxesCount) {
                            Utils.fatal("Out of bound container " + box + " in a goals descriptor");
                        }
                        int dest = Integer.parseInt(tokens[2]);
                        if (dest < 0 || dest >= this.locationsCount) {
                            Utils.fatal("Out of bound location " + dest + " in goals descriptor");
                        }
                        this.goals[box] = dest;
                        break;
                    }
                    default: {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // compute bit masks for bit twiddling states in pack/unpack
        this.robotLocationBitsCount = (int) Math.ceil(Utils.log2(this.locationsCount));
        this.robotLocationBitMask = Utils.mask(this.robotLocationBitsCount);
        this.positionsBitsCount =
                (int) Math.ceil(Utils.log2(this.pilesCount * this.boxesCount +
                        this.cranesCount + 1));
        this.positionsBitMask = Utils.mask(this.positionsBitsCount);
        int reqbits = this.robotLocationBitsCount + this.positionsBitsCount * this.boxesCount;
        if (reqbits > 64) {
            System.err.println("Too many bits required: "+reqbits);
            System.exit(1);
        }

        this.initHeuristic();
    }

    /**
     * Initializes the heuristics array - allows to calculate heuristic values quickly
     * The heuristic lower bound sums the distance of each container's current location from its
     * goals location.
     */
    private void initHeuristic() {
        // Initialize an array of all pairs of distances
        this.shortest = new double[this.locationsCount * this.locationsCount][2];

        // Initialize the shortest array with the shortest values of shortestPath(i, j, 0)
        for (int i = 0; i < this.locationsCount; i++) {
            for (int j = 0; j < this.locationsCount; j++) {
                // Take the distance between locations i and j
                double cost = this.adj[i][j];
                // Same vertex
                if (i == j) {
                    this.shortest[i * this.locationsCount + j] = new double[]{0, 0};
                // The locations can't be reached one from another
                } else if (Double.MAX_VALUE == cost) {
                    this.shortest[i * this.locationsCount + j] = new double[]{cost, cost};
                // Two standard locations
                } else {
                    this.shortest[i * this.locationsCount + j] = new double[]{cost, 1.0d};
                }
            }
        }
        // An implementation of Floyd-Warshall algorithm
        for (int k = 0; k < this.locationsCount; ++k) {
            for (int i = 0; i < this.locationsCount; ++i) {
                for (int j = 0; j < this.locationsCount; j++) {
                    double c = this.shortest[i * this.locationsCount + k][0] +
                            this.shortest[k * this.locationsCount + j][0];
                    if (c < this.shortest[i * this.locationsCount + j][0]) {
                        this.shortest[i * this.locationsCount + j][0] = c;
                    }
                    double d = this.shortest[i * this.locationsCount + k][1] +
                            this.shortest[k * this.locationsCount + j][1];
                    if (c < this.shortest[i * this.locationsCount + j][1]) {
                        this.shortest[i * this.locationsCount + j][1] = d;
                    }
                }
            }
        }
    }

    @Override
    public DRobotState initialState() {
        // Initialize the first state: The robot is currently on location 0 and no box is loaded
        DRobotState drs = new DRobotState(this, this.initialLocation, -1, 0);
        drs.calcHD();
        // System.out.println(drs.dumpState());
        return drs;
    }

    @Override
    public boolean isGoal(State state) {
        DRobotState drs = (DRobotState)state;
        return drs.leftBoxesNumber == 0;
    }

    @Override
    public int getNumOperators(State state) {
        DRobotState drs = (DRobotState)state;
        if (drs.ops == null) {
            this.initOps(drs);
        }
        return drs.ops.length;
    }

    @Override
    public Operator getOperator(State state, int index) {
        DRobotState drs = (DRobotState)state;
        if (drs.ops == null) {
            initOps(drs);
        }
        return drs.ops[index];
    }

    @Override
    public State copy(State state) {
        return new DRobotState((DRobotState)state);
    }

    /**
     * Pack a state into a long number
     *
     * The packed state is a 64 bit (long) number which stores the following data:
     *
     * First part contains the positions of all the boxes
     * If box is loaded by the robot - its position value will be 0
     * If the box is held by some crane, its position will contain the number of the crane + the
     * number of cranes at the location
     */
    @Override
    public PackedElement pack(State s) {
        DRobotState state = (DRobotState)s;
        // Current index
        int cur = 0;

        // First, we need to locate all the boxes in the relevant locations and then pack this
        // number
        int pos[] = new int[boxesCount + 1];
        // Initially we don't know the positions
        for (int i = 0; i < this.boxesCount + 1; ++i) {
            pos[i] = 0;
        }

        // In case the robot currently is loaded with a box - store that the position of the box
        // is 0
        if (state.boxLoadedByRobot >= 0) {
            pos[state.boxLoadedByRobot] = cur;
        }

        // In any case start counting from 1
        cur++;

        // Go over all the locations of the state and pack their data
        for (int i = 0; i < state.locations.length; i++) {
            Location l = state.locations[i];
            // Go over all the cranes at the location and store the boxes that they currently hold
            for (int c = 0; c < l.cranes.size(); c++) {
                pos[l.cranes.get(c)] = cur + c;
            }
            cur += this.maximumCranesCountAtPosition[i];
            // Now, go over all the piles stored at the locations
            for (int p = 0; p < l.piles.size(); p++) {
                // Store all the boxes that the pile holds
                for (int st = 0; st < l.piles.get(p).stack.size(); st++) {
                    pos[l.piles.get(p).stack.get(st)] = cur + st + p * this.boxesCount;
                }
            }
            // Increase the value by the maximum number of piles at the location, multiplied by the
            // total number of boxes in the domain
            cur += this.maximumPilesCountAtPosition[i] * this.boxesCount;
        }

        // Store the location of the robot at the current position (after storing locations of all
        // the boxes)
        pos[this.boxesCount] = state.robotLocation;

        // Finally, pack positions into 64 bits
        long packed = 0;
        // Go over the array and pack it
        for (int i = pos.length - 2; i >= 0; i--) {
            packed |= pos[i] & this.positionsBitMask;
            if (i > 0) {
                packed <<= this.positionsBitsCount;
                // Finally, shift right enough bits to store the location of the robot
            } else {
                packed <<= this.robotLocationBitsCount;
            }
        }
        // Finally, store the location of the robot (the last value of the positions array)
        packed |= pos[pos.length - 1] & this.robotLocationBitMask;

        //DEBUG
        /*
            DRobotState test = unpack(packed);
            if (!test.equals(state)) {
                System.err.println("pack is not working");
                System.exit(1);
            }
        */

        return new PackedElement(packed);
    }

    /**
     * An auxiliary function for unpacking the dockyard robot state from a long number
     *
     * @param packed The packed state
     * @param dst The destination state which is filled from the unpacked value
     */
    private void unpack(long packed, DRobotState dst) {
        dst.ops = null;
        // First, extract the location of the robot
        int robotLocation = (int) (packed & this.robotLocationBitMask);
        packed >>= this.robotLocationBitsCount;
        // Now, let's extract the position values
        int pos[] = new int[this.boxesCount];
        for (int i = 0; i < this.boxesCount; i++) {
            long p = packed & this.positionsBitMask;
            pos[i] = (int)p;
            packed >>= this.positionsBitsCount;
        }

        int boxHoldByRobot = -1;

        // Create the locations array and initialize it
        Location locations[] = new Location[this.locationsCount];
        for (int i=0; i<locations.length; i++) {
            locations[i] = new Location();
        }

        // Let's put the boxes inside the relevant place (crane/pile) in a relevant location
        for (int b = 0; b < this.boxesCount; b++) {
            // Take the current position value
            int p = pos[b];
            // If it is 0 - this box is currently held by the robot
            if (p == 0) {
                boxHoldByRobot = b;
                continue;
            }
            // All the positions (except the one that represents the robot) are represented by a
            // positive number
            --p;

            for (int l = 0; l < this.locationsCount; l++) {
                Location loc = locations[l];
                // This means that the current box is loaded to one of the cranes at the location
                if (p < this.maximumCranesCountAtPosition[l]) {
                    if (loc.cranes.size() <= p) {
                        // Increase the cranes number by 1
                        loc.cranes.setSize(p + 1);
                    }
                    // Add the current box to one of the cranes at the location
                    loc.cranes.set(p, b);
                    // Break the loop since we found the location of the current box
                    break;
                }
                // Otherwise, p is irrelevant for the current location - so let's remove its maximum
                // values and continue to the next location
                p -= this.maximumCranesCountAtPosition[l];

                // This means, p stores the piles in the current location
                if (p < maximumPilesCountAtPosition[l] * boxesCount) {
                    // Find the id of the pile
                    int pid = p / boxesCount;
                    // Find the location of the box in the pile
                    int ht = p % boxesCount;
                    // Assure that there are enough piles in the location and increase if needed
                    if (loc.piles.size() <= pid) {
                        loc.piles.setSize(pid+1);
                        loc.piles.set(pid, new Pile());
                    }
                    // Get the relevant pile
                    Pile pile = loc.piles.get(pid);
                    // Assure the stack of the pile contains enough boxes
                    if (pile.stack.size() <= ht) {
                        pile.stack.setSize(ht + 1);
                    }
                    // Finally, insert the box into the relevant position inside the pile
                    pile.stack.set(ht, b);
                    break;
                }
                // Otherwise, p is irrelevant for the current location - so let's remove its maximum
                // values and continue to the last location
                p -= maximumPilesCountAtPosition[l]* boxesCount;
            }
        }

        // Finally, let's initialize a state with all the extracted values
        dst.init(this, locations, boxHoldByRobot, robotLocation);

        // Finally, compute the heuristics
        double p[] = hd(dst);
        dst.h = p[0];
        dst.d = p[1];
    }

    /**
     * Unpacks the dockyard robot state from a long number
     *
     * @param packed the packed state
     * @return The unpacked state
     */
    @Override
    public DRobotState unpack(PackedElement packed) {
        assert packed.getLongsCount() == 1;
        DRobotState dst = new DRobotState();
        unpack(packed.getFirst(), dst);
        return dst;
    }

    /**
     * Whether there is a box loaded to the robot, which is not at goals and has a goals
     *
     * @param state The state to check the condition
     *
     * @return Whether the condition is true
     */
    //private int onRobot(DRobotState state) {
    //    if (state.boxLoadedByRobot >= 0 && state.robotLocation != goals[state.boxLoadedByRobot]
    //              && goals[state.boxLoadedByRobot] >= 0) {
    //        return 1;
    //    } else {
    //        return 0;
    //    }
    //}

    /**
     * Counts all the boxes loaded to cranes, that still should be moved to goals
     *
     * @param state The state on which the boxes are counted
     *
     * @return The calculated number
     */
    //private int onCrane(DRobotState state) {
    //    int oncranes = 0;
    //    for (int l = 0; l < state.locations.length; l++) {
    //        Location loc = state.locations[l];
    //        // Go over all the cranes at the location and look for a box
    //        for (int c = 0; c < loc.cranes.size(); c++)  {
    //            int box = loc.cranes.get(c);
    //            // Box has no goals or is at goals
    //            if (this.goals[box] < 0 || this.goals[box] == l) {
    //                continue;
    //            }
    //            oncranes++;
    //        }
    //    }
    //    return oncranes;
    //}

    /**
     * Finds the list of deepest boxes in all the piles of the state
     *
     * @param state The state from which the deepest boxes should be extracted
     *
     * @return The list of deepest boxes
     */
    //private List<Integer> deepest(DRobotState state) {
    //    List<Integer> deepestList = new ArrayList<>();
    //    // Go over all the locations
    //    for (int l = 0; l < state.locations.length; l++) {
    //        Location loc = state.locations[l];
    //        // Go over all the piles at the location
    //        for (int p = 0; p < loc.piles.size(); p++) {
    //            Pile pile = loc.piles.get(p);
    //            int deepest = 0;
    //            // Go over all the boxes in the pile and look for the deepest box that is not at
    //            // its goals
    //            for (int ht = 0; ht < pile.stack.size(); ht++) {
    //                int box = pile.stack.get(ht);
    //                if (goals[box] < 0 || goals[box] == l) {
    //                    continue;
    //                }
    //                deepest = ht+1;
    //            }
    //            // If we found a deepest box for that pile - add it to the list of deepest
    //            if (deepest > 0) {
    //                deepestList.add(deepest);
    //            }
    //        }
    //    }
    //    return deepestList;
    //}

    /**
     * An auxiliary function that calculates the values of h and d of the given DRobotState
     *
     * The values are calculated in the following way:
     *  1. h - sum of distances of all boxes to their goals +
     *         cost of loading each box to a robot +
     *         cost of
     *
     * @param state The state whose heuristic (h) and depth (d) values should be calculated
     *
     * @return A pair of the calculated values in a form of an array
     */
    private double[] hd(DRobotState state) {
        double h = 0;
        double d = 0;

        // A goals state - all the boxes are in their correct place
        if (state.leftBoxesNumber == 0) {
            return new double[]{0, 0};
        }

        // Each out-of-place box must move to its goal
        for (int b = 0; b < this.goals.length; ++b) {
            // No goals for this box?
            if (this.goals[b] < 0) {
                continue;
            }
            // Shortest path between the location of the box and the goals
            double[] p =
                    this.shortest[state.boxesLocations[b] * this.locationsCount + this.goals[b]];
            h += p[0];
            d += p[1];
        }

        // All except for the last out-of-place box must be unloaded from the robot.
        h += (((double)(state.leftBoxesNumber - 1)) * DockyardRobot.LoadCost);
        d += state.leftBoxesNumber - 1;

        for (int l = 0; l < state.locations.length; l++) {
            Location loc = state.locations[l];
            // Each out-of-place box on a crane must be loaded onto the robot.
            for (int c = 0; c < loc.cranes.size(); ++c)  {
                int box = loc.cranes.get(c);
                // No goals or boxes at place
                if (this.goals[box] < 0 || this.goals[box] == l) {
                    continue;
                }
                h += DockyardRobot.LoadCost;
                d += 1;
            }

            // Each out-of-place box on a stack must be popped onto a crane, then moved to the robot.

            // Go over all the piles at the location
            for (int p = 0; p < loc.piles.size(); p++) {
                // Get current pile
                Pile pile = loc.piles.get(p);
                // Go over all the boxes on the pile
                for (int ht = 0; ht < pile.stack.size(); ht++) {
                    // Get current box
                    int box = pile.stack.get(ht);
                    // No goals or box at place - bypass the box
                    if (this.goals[box] < 0 || this.goals[box] == l) {
                        continue;
                    }
                    //FIXME: bug? h += PopCostFact*(ht + 2) + LoadCost;
                    h += DockyardRobot.PopCostFact * (ht + 1) + DockyardRobot.LoadCost;
                    //h += ((PopCostFact*((double)(ht+1.0))) + LoadCost);
                    d += 2;
                }
            }

        }
        // Finally, return a pair of h and d
        return new double[]{h, d};
    }

    /**
     * Another way to calculate the values of h and d of the given DRobotState
     *
     * The values are calculated in the following way:
     *  1. h (heuristic value)
     *         sum of distances of all boxes to their goals +
     *         cost of loading each box to a robot +
     *         cost of
     *  2. d (distance to the goals)
     *
     * @param state The state whose heuristic (h) and depth (d) values should be calculated
     *
     * @return A pair of the calculated values in a form of an array
     */
    //private double[] hd_sumdeepest(DRobotState state) {
    //    double h = 0;
    //    double d = 0;
    //    // Each out-of-place box must move to its goals.
    //    int containersOutOfPlace = state.leftBoxesNumber;
    //
    //        // We reached the goals
    //        if (containersOutOfPlace == 0) {
    //            return new double[]{0, 0};
    //        }
    //
    //        // Go over all the goals and summarize the length of each path
    //        for (int b = 0; b < this.goals.length; b++) {
    //            // Assure there is a goals (in case the state is at goals - the cost will be 0)
    //            if (goals[b] < 0 /*|| goals[b] == state.boxesLocations[b]*/) {
    //                continue;
    //            }
    //            double[] p =
    //                  this.shortest[state.boxesLocations[b] *
    //                      this.locationsCount + this.goals[b]];
    //            h += p[0];
    //            d += p[1];
    //        }
    //
    //        // Find all deepest packages
    //        List<Integer> deepestList = this.deepest(state);
    //
    //        h += (containersOutOfPlace * LoadCost);
    //
    //        // Adjust d for deepest
    //        for (int b : deepestList) {
    //            double bd = (double)b;
    //            // cost to move all boxes above b and b ?
    //            h += (((bd * (bd + 1.0)) / 2.0) * PopCostFact);
    //            // lift, load, drive, unload, return
    //            d += ((b * 5) - 1);
    //        }
    //
    //        // Calculate the number of boxes that are loaded to cranes
    //        int oncrane = onCrane(state) * 2;
    //        // Whether there is a box loaded to the robot
    //        int onrobot = onRobot(state);
    //        d += (oncrane + onrobot);
    //
    //        return new double[]{h, d};
    //    }

    /**
     * Calculates the cost of the given operator according to the given cost function
     *
     * @param operatorType The operator whose cost should be calculated
     * @param operatorInputXValue The first custom parameter for the operator
     * @param operatorInputYValue The second custom parameter for the operator
     * @param state The state on which the operator will be applied
     * @return The calculated cost of the operator
     */
    private double cost(int operatorType, int operatorInputXValue, int operatorInputYValue,
                        DRobotState state) {
        double cost = 0.0d;

        switch (operatorType) {
            // Push the container in the crane onto the top of a pile
            case DRobotOperator.PUSH: {
                // Extract the pile on which the required box will be pushed
                int pileNumber = operatorInputYValue;
                // Get the current location of the robot
                Location l = state.locations[state.robotLocation];
                // In case box is added to a new pile - it will be at the bottom
                int sz = 0;
                // If the pile already exists, let's find its size
                if (pileNumber < l.piles.size()) {
                    // Size of the pile
                    sz = l.piles.get(pileNumber).stack.size();
                }
                // Let's calculate the cost of the operation:
                // Accessing a pile with a crane costs 0.05 times the height of the pile
                // (plus 1 to ensure non-zero-cost actions)
                cost = DockyardRobot.PopCostFact * (sz + 1.0d);  // from the OCaml code
                break;
            }
            // Take the top container from the pile using a crane
            case DRobotOperator.POP: {
                // Extract the pile on which the required box will be pushed
                int pileNumber = operatorInputXValue;
                // Get the current location of the robot
                Location l = state.locations[state.robotLocation];
                // Size of the pile
                int sz = l.piles.get(pileNumber).stack.size();
                cost = DockyardRobot.PopCostFact * (sz + 1.0d);  // from the OCaml code
                break;
            }
            // Load/Unload a container from a crane into the robot
            case DRobotOperator.LOAD:
            case DRobotOperator.UNLOAD: {
                cost = DockyardRobot.LoadCost;
                break;
            }
            // Move the robot to other location
            case DRobotOperator.MOVE: {
                // Get the current location of the robot
                int src = state.robotLocation;
                // Get the destination location (first parameter of the operator)
                int dst = operatorInputXValue;
                cost = this.adj[src][dst];
                break;
            }
            default: {
                System.err.println("Unknown operator type " + operatorType);
                System.exit(1);
            }
        }
        return cost;
    }

    /**
     * Apply the given operator on the given state and generate a new state
     *
     * @param state The state to apply the operator on
     * @param op The operator to apply the state on
     *
     * @return The new generated state
     */
    @Override
    public State applyOperator(State state, Operator op) {
        DRobotState s = (DRobotState)state;
        DRobotState drs = (DRobotState)copy(s);
        DRobotOperator o = (DRobotOperator)op;

        drs.ops = null; // reset ops

        switch (o.type) {
            // Push the container in the crane onto the top of a pile
            case DRobotOperator.PUSH: {
                int c = o.x;
                int p = o.y;
                // Only to be sure ...
                assert(drs.robotLocation < drs.locations.length);
                // Get the current location of the robot
                Location l = drs.locations[drs.robotLocation];
                // Take the box from the crane at the current location
                int box = l.removeCrane(c);
                // In case box is added to a new pile - it will be at the bottom
                int bottom = box;
                // If the pile already exists, we must
                if (p < l.piles.size()) {
                    // This is the current bottom of the pile
                    bottom = l.piles.get(p).stack.get(0);
                }
                // Add the box to the pile
                l.push(box, p);
                // Find the updated pile number
                p = l.findPile(bottom);
                // Just to be sure ...
                assert (p >= 0);
                drs.reverseOperator = new DRobotOperator(DRobotOperator.POP, p);
                break;
            }
            // Take the top container from the pile using a crane
            case DRobotOperator.POP: {
                // Extract the relevant pile
                int p = o.x;
                // Find the location of the robot
                Location l = drs.locations[drs.robotLocation];
                // Just to assure that the number of the pile is correct
                assert (p < l.piles.size());
                // Get the size of the pile
                int sz = l.piles.get(p).stack.size();
                // Store the bottom container of the pile
                int bottom = l.piles.get(p).stack.get(0);
                // Take the top box from the pile
                int box = l.pop(p);
                // Add the box to one of the cranes
                l.addCrane(box);
                // Now, find the crane number that took the box
                int c = l.findCrane(box);
                // Just to make sure that the box was found
                assert (c >= 0);
                // In case the last box was taken from the found pile - there is no updated bottom
                // box - so make p to be invalid
                if (sz == 1) {
                    p = l.piles.size();
                // Otherwise, find the updated place of the pile (after removing the top box)
                } else {
                    p = l.findPile(bottom);
                }
                drs.reverseOperator = new DRobotOperator(DRobotOperator.PUSH, c, p);
                break;
            }
            // Load a container from a crane into the robot
            case DRobotOperator.LOAD: {
                // Assert no other box in loaded into the robot
                assert (drs.boxLoadedByRobot < 0);
                // Find the current location of the robot
                Location l = drs.locations[drs.robotLocation];
                // Get the crane to load the box from
                int c = o.x;
                // Load the box into the robot
                drs.boxLoadedByRobot = l.removeCrane(c);
                drs.reverseOperator = new DRobotOperator(DRobotOperator.UNLOAD);
                break;
            }
            // Unload a container from the robot into a crane
            case DRobotOperator.UNLOAD: {
                // Just to make sure - assert the the robot actually has a box that should be unloaded
                assert (drs.boxLoadedByRobot >= 0);
                // Get the location of the robot
                Location l = drs.locations[drs.robotLocation];
                // Get the number of the box that is currently loaded onto the robot
                int box = drs.boxLoadedByRobot;
                // Add the box to one of the cranes of the current location
                l.addCrane(box);
                // Assure that there is no overflow in the number of cranes that exist in this location
                assert (l.cranes.size() <= maximumCranesCountAtPosition[drs.robotLocation]);
                // Clean the box of the robot
                drs.boxLoadedByRobot = -1;
                // Since the cranes were re-sorted, let's find the crane number that current hold the box
                int c = l.findCrane(box);
                // Assure the crane was found
                assert (c >= 0);
                drs.reverseOperator = new DRobotOperator(DRobotOperator.LOAD, c);
                break;
            }
            // Move the robot to other location
            case DRobotOperator.MOVE: {
                // Get the current location of the robot
                int src = drs.robotLocation;
                // Get the destination location (first parameter of the operator)
                int dst = o.x;
                // Are we moving a box out of its goals location or to its goals location?
                if (drs.boxLoadedByRobot >= 0 && this.goals[drs.boxLoadedByRobot] >= 0) {
                    // In case the goals of the box currently held by the robot equals to the robot location
                    if (this.goals[drs.boxLoadedByRobot] == drs.robotLocation) {
                        // We move the box out of its goals location
                        drs.leftBoxesNumber++;
                    // We move the box to its goals location
                    } else if (goals[drs.boxLoadedByRobot] == dst) {
                        drs.leftBoxesNumber--;
                    }
                    // In any case, update the location of the box that is currently loaded to the
                    // robot
                    drs.boxesLocations[drs.boxLoadedByRobot] = dst;
                }
                // Update the location of the robot
                drs.robotLocation = dst;
                drs.reverseOperator = new DRobotOperator(DRobotOperator.MOVE, src);
                // Calculate the cost of the operation
                break;
            }
            default: {
                System.err.println("Unknown operator type " + o.type);
                System.exit(1);
            }
        }

        drs.depth += 1;

        //dumpState(s);
        //dumpState(drs);

        double p[] = this.hd(drs);
        drs.h = p[0];
        drs.d = p[1];

        // PathMax
        /*
        double costsDiff = drs.g - s.g;
        drs.h = Math.max(s.h, (s.h - costsDiff));
        */
        // In order to reach drs, 'cost' should be payed
        //o.costs.put(drs, cost);

        drs.parent = s;

        //dumpState(drs);
        return drs;
    }

    private final class Pile implements Comparable<Pile> {
        private Vector<Integer> stack;

        /**
         * Default constructor - constructs an empty pile
         */
        private Pile() {
            this.stack = new Vector<>();
        }

        /**
         * Create a pile of boxes with an initial container
         *
         * @param bucket The container to add to the created pile
         */
        private Pile(int bucket) {
            this.stack = new Vector<>();
            this.stack.add(bucket);
        }

        /**
         * Compares that pile to the given one
         * The piles are compared by the bottom box - a pile with a box with a low number will
         * appear before a pile with a pile that contains a high numbered box
         *
         * @param toCompare The pile which is compared to the current one
         * @return A negative number if the current pile should appear before the given pile, in
         * the sorted array and a positive number (or zero) otherwise (0 can't be returned since
         * all the boxes are different)
         */
        @Override
        public int compareTo(@NotNull Pile toCompare) {
            return this.stack.get(0) - toCompare.stack.get(0);
        }

        /**
         * Compares this pile to the given one by comparing this stack to other's stack
         */
        @Override
        public boolean equals(Object obj) {
            try {
                Pile o = (Pile) obj;
                return stack.equals(o.stack);
            } catch (ClassCastException e) {
                return false;
            }
        }

        private Pile copy() {
            Pile copy = new Pile();
            for (int i=0; i<stack.size(); i++)
                copy.stack.add(stack.get(i));
            return copy;
        }
    }

    private final class Location {
        private Vector<Integer> cranes;
        private Vector<Pile> piles;

        /**
         * The default constructor of the class: constructs an empty location
         */
        private Location() {
            this.cranes = new Vector<>();
            this.piles = new Vector<>();
        }

        /**
         * A constructor of the class for constructing a location with cranes and piles vectors of
         * known size
         *
         * @param cranesCount The size of the cranes vector
         * @param pilesCount The size of the piles vector
         */
        private Location(int cranesCount, int pilesCount) {
            this.cranes = new Vector<>(cranesCount);
            this.piles = new Vector<>(pilesCount);
        }

        /**
         * Copies the current Location to a new object
         *
         * A deep copy is performed!
         *
         * @return A new instance of Location class which contains all the values of the current one
         */
        private Location copy() {
            Location copy = new Location(this.cranes.size(), piles.size());
            for (int i = 0; i < this.cranes.size(); i++) {
                copy.cranes.add(i, this.cranes.get(i));
            }
            for (int i = 0; i < this.piles.size(); i++) {
                copy.piles.add(i, this.piles.get(i).copy());
            }
            return copy;
        }

        /**
         * Compares two location objects
         */
        @Override
        public boolean equals(Object toCompare) {
            try {
                if (toCompare == null) {
                    return false;
                }
                Location otherLocation = (Location) toCompare;
                return this.cranes.equals(otherLocation.cranes) &&
                            this.piles.equals(otherLocation.piles);
            } catch (ClassCastException e) {
                return false;
            }
        }

        /**
         * Pop a box from the given pile
         *
         * Note that the function doesn't check if there is a box inside the required pile
         *
         * @param pileNumber The pile from which the box should be pop-ed
         * @return The extracted box
         */
        private int pop(int pileNumber) {
            Pile pile = this.piles.get(pileNumber);
            int box = pile.stack.remove(pile.stack.size() - 1);
            // Finally, if the pile is now empty, let's remove it
            if (pile.stack.isEmpty()) {
                this.piles.remove(pileNumber);
            }
            return box;
        }

        /**
         * Push a box to the required pile
         *
         * Note that the function doesn't check if a pile numbered pileNumber can be added to that
         * location
         *
         * @param boxNumber The box to push
         * @param pileNumber The pile to push the box into
         */
        private void push(int boxNumber, int pileNumber) {
            // Add a new pile if required
            if (pileNumber >= this.piles.size()) {
                // The new pile should contain at least boxNumber boxes
                this.piles.add(new Pile(boxNumber));
                // Sort the piles according to the lowest box in the pile
                Collections.sort(this.piles);  //TODO: is this sorting properly
            } else {
                this.piles.get(pileNumber).stack.add(boxNumber);
            }
        }

        /**
         * Loads a box from the crane at the given location
         *
         * Note: The function doesn't check that the crane actually is loaded with a box
         *
         * @param craneNumber The crane from which a box should be removed
         * @return The removed box
         */
        private int removeCrane(int craneNumber) {
            int box = this.cranes.get(craneNumber);
            this.cranes.remove(craneNumber);
            return box;
        }

        /**
         * Adds a box to one of the cranes at this location
         *
         * Note: The function doesn't check that there is an empty crane at the location
         *
         * @param box The box to load by one of the cranes
         */
        private void addCrane(int box) {
            this.cranes.add(box);
            Collections.sort(this.cranes);  //TODO: is this sorting properly
        }

        /**
         * Finds a pile at the location, that contains the given box
         *
         * @param box The box to look for
         * @return The number of the pile that contains the box
         */
        private int findPile(int box) {
            int l = 0;
            int u = this.piles.size();
            // If there are no piles - there is nothing to do (the box wasn't found)
            if (u == 0) {
                return -1;
            }

            // define: piles[-1] and piles[piles.size]
            // invariant: l - 1 < box and u >= box

            // A kind of binary search
            while (l < u) {
                int m = ((u - l) >> 1) + l;
                if (piles.get(m).stack.get(0) < box) {
                    l = m + 1;
                } else {
                    u = m;
                }
            }
            return (l < piles.size() && piles.get(l).stack.get(0) == box) ? l : -1;
        }

        /**
         * Look for the crane at the location, that held the given box
         *
         * @param box The box to look for
         * @return The number of the crane that held the box or -1 if the box wasn't found
         */
        private int findCrane(int box) {
            int l = 0;
            int u = cranes.size();
            // If there is no cranes - there is nothing to return
            if (u == 0) {
                return -1;
            }
            // A kind of binary search
            while (l < u) {
                // Divide by 2
                int m = ((u - l) >> 1) + l;
                // If current value is too low - update the bottom value
                if (this.cranes.get(m) < box) {
                    l = m + 1;
                    // Otherwise, update the top value
                } else {
                    u = m;
                }
            }
            return (l < this.cranes.size() && this.cranes.get(l) == box) ? l : -1;
        }
    }


    private final class DRobotState implements State {
        private double h;
        private double d;

        private int boxLoadedByRobot;
        private int robotLocation;
        private int leftBoxesNumber;
        private int depth;

        private Location[] locations;
        private int[] boxesLocations;

        private DRobotOperator[] ops = null;
        private DRobotOperator reverseOperator;

        private DRobotState parent = null;

        /**
         * Initialize
         * @param dr The DockyardRobot general domain
         * @param ls The locations array
         * @param rb The box loaded to the robot
         * @param rl The location of the robot
         */
        private void init(DockyardRobot dr, Location[] ls, int rb, int rl) {
            // Copy the locations from the given array
            this.locations = new Location[ls.length];
            System.arraycopy(ls, 0, locations, 0, ls.length);
            this.boxLoadedByRobot = rb;
            this.robotLocation = rl;
            this.h = -1;
            this.d = -1;
            // The number of boxes that aren't located in their true locations
            this.leftBoxesNumber = 0;
            // The current locations of all the boxes
            this.boxesLocations = new int[dr.boxesCount];

            for (int l = 0; l < this.locations.length; l++) {
                for (int c = 0; c < this.locations[l].cranes.size(); ++c) {
                    int box = this.locations[l].cranes.get(c);
                    if (dr.goals[box] >= 0 && dr.goals[box] != l) {
                        this.leftBoxesNumber++;
                    }
                    this.boxesLocations[box] = l;
                }
                for (int p = 0; p < this.locations[l].piles.size(); p++) {
                    for (int s = 0; s < this.locations[l].piles.get(p).stack.size(); s++) {
                        int box = this.locations[l].piles.get(p).stack.get(s);
                        if (dr.goals[box] >= 0 && dr.goals[box] != l) {
                            this.leftBoxesNumber++;
                        }
                        this.boxesLocations[box] = l;
                    }
                }
            }
            if (this.boxLoadedByRobot >= 0) {
                if (dr.goals[boxLoadedByRobot] >= 0 && dr.goals[boxLoadedByRobot] != this.robotLocation) {
                    this.leftBoxesNumber++;
                }
                this.boxesLocations[this.boxLoadedByRobot] = this.robotLocation;
            }
            this.parent = null;
        }

        /**
         * A default constructor (required for unpack operation)
         */
        private DRobotState() { }

        /**
         * Constructs a DRobotState using the initial data
         * @param dr The DockyardRobot data structure that contains all the general information
         *           about the current domain
         * @param ls The locations array
         * @param rb The box that is currently held by the robot
         * @param rl The current location of the robot
         */
        private DRobotState(DockyardRobot dr, Location[] ls, int rb, int rl) {
            this.init(dr, ls, rb, rl);
        }

        /**
         * A copy constructor
         *
         * @param state The state to copy
         */
        private DRobotState(DRobotState state) {
            this.h = state.h;
            this.d = state.d;
            this.depth = state.depth;
            this.boxLoadedByRobot = state.boxLoadedByRobot;
            this.robotLocation = state.robotLocation;
            this.leftBoxesNumber = state.leftBoxesNumber;
            this.locations = new Location[state.locations.length];
            // Deep copy of locations
            for (int i = 0; i < this.locations.length; ++i) {
                this.locations[i] = state.locations[i].copy();
            }
            this.boxesLocations = new int[state.boxesLocations.length];
            // Perform deep copy of boxes locations
            System.arraycopy(state.boxesLocations, 0, this.boxesLocations, 0, this.boxesLocations.length);
            this.parent = state.parent;
        }

        @Override
        public int hashCode() {
            return DockyardRobot.this.pack(this).hashCode();
        }

        @Override
        public boolean equals(Object toCompare) {
            try {
                DRobotState o = (DRobotState) toCompare;
                if (this.robotLocation != o.robotLocation ||
                        this.boxLoadedByRobot != o.boxLoadedByRobot ||
                            this.leftBoxesNumber != o.leftBoxesNumber) {
                    return false;
                }
                // Compare the locations
                for (int i = 0; i < this.locations.length; ++i) {
                    if (!this.locations[i].equals(o.locations[i])) {
                        return false;
                    }
                }
                return true;
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
            if (this.h < 0) {
                this.calcHD();
            }
            return this.h;
        }

        @Override
        public double getD() {
            if (this.d < 0) {
                this.calcHD();
            }
            return this.d;
        }

        /**
         * An auxiliary function for calculating the h and d values of the current state
         */
        public void calcHD() {
            double[] p = DockyardRobot.this.hd(this);
            this.h = p[0];
            this.d = p[1];
        }

        @Override
        public String dumpState() {
            return DockyardRobot.dumpState(this);
        }

        @Override
        public String dumpStateShort() {
            return null;
        }
    }

    private final class DRobotOperator implements Operator {
        private static final int NONE = 0;
        private static final int PUSH = 1;
        private static final int POP = 2;
        private static final int LOAD = 3;
        private static final int UNLOAD = 4;
        private static final int MOVE = 5;

        // Declare the fields with default values
        private int type = NONE;
        private int x = 0;
        private int y = 0;

        private Map<PackedElement, Double> costs;

        private DRobotOperator(int type) {
            //this.costs = new HashMap<>();
            this.type = type;
        }

        private DRobotOperator(int type, int x) {
            //this.costs = new HashMap<>();
            this.type = type;
            this.x = x;
        }

        private DRobotOperator(int type, int x, int y) {
            //this.costs = new HashMap<>();
            this.type = type;
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            try {
                if (obj == null) {
                    return false;
                }
                DRobotOperator o = (DRobotOperator) obj;
                return type == o.type && x == o.x && y == o.y;
            } catch (ClassCastException e) {
                return false;
            }
        }

        @Override
        public double getCost(State s, State parent) {
            assert parent != null;
            DRobotState drs = (DRobotState)parent;
            /*
            DRobotState drs = (DRobotState)s;
            PackedElement packed = DockyardRobot.this.pack(s);
            // In case the cost was calculated once - we can't calculate it again, since the
            //if (this.costs.containsKey(packed)) {
            //    return this.costs.get(packed);
            //}
            */
            return DockyardRobot.this.cost(this.type, this.x, this.y, drs);
        }

        /**
         * Finds the reverse operator that applying it will reverse the state caused by this
         * operator
         */
        @Override
        public Operator reverse(State state) {
            return null;
            //throw new NotImplementedException();
        }

        public String toString() {
            return "Operator(Type: " + this.type + ", X: " + this.x + ", Y: " + this.y + ")";
        }
    }

    /**
     * Print the state for debugging reasons
     *
     * @param state The state to print
     */
    private static String dumpState(DRobotState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("********************************\n");
        // h
        sb.append("h: ");
        sb.append(state.h);
        sb.append("\n");
        // d
        sb.append("d: ");
        sb.append(state.d);
        sb.append("\n");
        // Location of the robot
        sb.append("robot location: ");
        sb.append(state.robotLocation);
        sb.append("\n");
        // Contents of the robot
        sb.append("robot contents: ");
        sb.append(state.boxLoadedByRobot);
        sb.append("\n");
        // Out of place boxes
        sb.append("out of place boxes: ");
        sb.append(state.leftBoxesNumber);
        sb.append("\n");
        // Go over all the locations and print the data inside them
        for (int l = 0; l < state.locations.length; ++l) {
            // Current location
            sb.append("location ");
            sb.append(l);
            sb.append(":\n");
            Location location = state.locations[l];
            // Go over all the cranes in the location and print their contents
            for (int c = 0; c < location.cranes.size(); c++) {
                // Current crane
                sb.append("\tcrane: ");
                sb.append(location.cranes.get(c));
                sb.append("\n");
            }
            // Go over all the piles of boxes at the location and print the boxes stored on them
            for (int p = 0; p < location.piles.size(); p++) {
                Pile pile = location.piles.get(p);
                sb.append("\tpile:");
                // Go over all the boxes stored in the pile and print them - top to bottom
                for (int h = 0; h < pile.stack.size(); h++) {
                    sb.append(" ");
                    sb.append(pile.stack.get(h));
                }
                sb.append("\n");
            }
        }
        sb.append("********************************\n\n");
        return sb.toString();
    }

    @Override
    public String dumpStatesCollection(State[] states) {
        return null;
    }

    @Override
    public Map<String, Class> getPossibleParameters() {
        return null;
    }

    @Override
    public void setAdditionalParameter(String parameterName, String value) {
        throw new NotImplementedException();
    }

    /**
     * An auxiliary function for extracting an integer value from a string of the
     * form '<str>: <int>'
     *
     * @param str The string to the extract the integer from
     *
     * @return The extracted value
     */
    private int getInt(String str) {
        String[] tokens = str.split(":");
        return Integer.parseInt(tokens[1].trim());
    }
}