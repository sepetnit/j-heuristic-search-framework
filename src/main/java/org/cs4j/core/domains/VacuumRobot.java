package org.cs4j.core.domains;

import com.sun.istack.internal.NotNull;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.collections.PackedElement;
import org.cs4j.core.collections.PairInt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

public class VacuumRobot implements SearchDomain {

    public static final char ROBOT_START_MARKER = 'V';
    public static final char ROBOT_END_MARKER = 'E';

    // The start location of the robot
    private int startX = -1;
    private int startY = -1;

    // 8 is the maximum count of Vacuum Robot operators
    // (4 for regular moves and more 4 for diagonals)
    private VacuumRobotOperator[] reverseOperators = new VacuumRobotOperator[8];

    private Vector<PairInt> dirtyLocations = new Vector<>();
    // Will be size of dirtyLocations (dirtyLocations.size())
    private int maximumDirtyLocationsCount;
    // Mapping between locations and indexes in the dirtyLocations vector
    private int[] dirt;

    private static final int NUM_MOVES = 4;

    private GridMap map;
    private boolean heavy = false;

    private int robotLocationBits;
    private long robotLocationBitMask;
    private long singleBitMask;

    public enum COST_FUNCTION {HEAVY, LITE, UNIT};

    // An array of pre-computed {h, d} pairs, each one based on some combination of dirty
    // locations (any combination is defined by a binary vector)
    // NOTE: The location of the robot is not considered while building the array
    double [][] lookupMST_heavy;

    /**
     * Whether the i'th location (among all the dirty initials) is dirty
     *
     * @param dirt A bit vector of locations
     * @param i The index of the location to check
     *
     * @return True if the location is dirty and False otherwise
     */
    private boolean dirt(long dirt, int i) {
        return (dirt & (1<<i)) != 0;
    }

    /**
     * Init the possible operators for the given state
     *
     * @param state The state whose operators should be initialized
     */
    private void initOps(VacuumRobotState state) {
        // An empty vector of operators
        Vector<VacuumRobotOperator> possibleOperators = new Vector<>();
        // Get the dirty status on the location of the robot
        // (in case it is dirty - the value will be the index in the dirtLocations array)
        int dirt = this.dirt[state.robotLocation];
        // Add a SUCK operator if the current location of the robot is dirty
        // TODO: dirt can't be lower than 0 - it is an index of an array!
        if (dirt >= 0 && state.isDirty(dirt)) {
            possibleOperators.add(new VacuumRobotOperator(VacuumRobotOperator.SUCK));
            // Otherwise, let's add all the possible moves that the robot can perform
        } else {
            // Go ovr all the possible moves
            for (int i = 0; i < VacuumRobot.NUM_MOVES; ++i) {
                if (this._isValidMove(state.robotLocation, this.map.possibleMoves[i])) {
                    possibleOperators.add(new VacuumRobotOperator(i));
                }
            }
        }
        // Finally, create the possible operators array
        state.ops = possibleOperators.toArray(new VacuumRobotOperator[possibleOperators.size()]);
    }

    /**
     * Initializes the reverse operators array: For each operator, set its reverse operator
     *
     * NOTE: This function is called only once (by the constructor)
     */
    private void _initializeReverseOperatorsArray() {
        int reversedMovesCount = 0;
        // Go over all the possible moves
        for (int i = 0; i < this.map.possibleMovesCount; i++) {
            // Go over all the possible moves
            for (int j = 0; j < this.map.possibleMovesCount; j++) {
                // In case the two operators are not reverse - ignore them
                if ((this.map.possibleMoves[i].dx != -this.map.possibleMoves[j].dx) ||
                        (this.map.possibleMoves[i].dy != -this.map.possibleMoves[j].dy)) {
                    continue;
                }
                // Define operator j to be reverse of operator i
                this.reverseOperators[i] = new VacuumRobotOperator(j);
                // Count the number of found 'reverse pairs'
                ++reversedMovesCount;
                break;
            }
        }
        // The number of reverse pairs must be equal to the total number of operators
        assert (reversedMovesCount == map.possibleMovesCount);
        // Finally, add a reverse operator for SUCK: NOP
        this.reverseOperators[VacuumRobotOperator.SUCK] =
                new VacuumRobotOperator(VacuumRobotOperator.NOP);
    }

    /**
     * The function calculates h and d values for every possible combination of the dirty
     * vectors which allows to quickly calculate the actual h and d values for heavy Vacuum
     * Robot problems
     */
    private void _preComputeMSTHeavy() {
        // The number of all possible permutations of dirty locations:
        // This can be easily calculated - since the dirty locations is a binary vector we can
        // just pre-compute the values for every possible binary vector of the specified length
        // e.g. Assume that we have 15 dirty locations - then, we have 2^15=32768 possible options
        // of dirty combinations
        // For each of the combinations we can compute an MST and calculate h and d values
        // Then, during the search we can just get the h and d values from the table
        int perm = (int)Math.pow(2, this.maximumDirtyLocationsCount);
        // The data structure for saving all the h and d values is a 2-dimensional array of
        // size perm * 2 (for h and d)
        this.lookupMST_heavy = new double[perm][2];
        // The first value (all the locations are clean) is 0 and 0 => means we reached a goal!
        this.lookupMST_heavy[0] = new double[] {0, 0};
        // Go over all the possible binary vectors of dirt and compute h and d values
        for (int i = 1; i < perm; ++i) {
            double[] hd = computeHD_MST(i);
            this.lookupMST_heavy[i] = hd;
        }
    }

    /**
     * The constructor of the general VacuumRobot World domain
     *
     * @param stream The input stream for parsing the instance
     * @param costFunction The type of the cost function
     */
    public VacuumRobot(InputStream stream, COST_FUNCTION costFunction) {
        this.heavy = (costFunction == COST_FUNCTION.HEAVY);
        // Initialize the input-reader to allow parsing the state
        BufferedReader in = new BufferedReader(new InputStreamReader(stream));
        try {
            // First, read the size of the grid
            String sz[] = in.readLine().trim().split(" ");
            int w = Integer.parseInt(sz[0]);
            int h = Integer.parseInt(sz[1]);

            // Now, let's read the board
            in.readLine();

            // Create the map
            this.map = new GridMap(w, h);
            // Initialize the dirty locations vector
            this.dirt = new int[this.map.mapSize];
            // Initially, all the locations are clean
            for (int i = 0; i < this.dirt.length; ++i) {
                this.dirt[i] = -1;
            }

            // Now, read all the locations
            for (int y = 0; y < h; ++y) {
                String line = in.readLine();
                char[] chars = line.toCharArray();
                int ci = 0;
                // Horizontal
                for (int x = 0; x < w; ++x) {
                    char c = chars[ci++];
                    switch (c) {
                        // An obstacle
                        case '#': {
                            this.map.setBlocked(this.map.index(x, y));
                            break;
                        // Dirty location
                        } case '*': {
                            // Map between the dirty locations vector and the dirty locations index
                            this.dirt[map.index(x, y)] = this.dirtyLocations.size();
                            this.dirtyLocations.add(new PairInt(x, y));
                            break;
                        }
                        // The start location of the robot
                        case '@':
                        case 'V': {
                            this.startX = x;
                            this.startY = y;
                            break;
                        // End of line
                        }
                        case '.':
                        case '_':
                        case ' ': {
                            break;
                        // Net line
                        } case '\n': {
                            assert x == chars.length;
                            break;
                        // Something strange
                        } default: {
                            Utils.fatal("Unknown character" + c);
                        }
                    }
                }
            }
            // Assure there is a start location
            if (this.startX < 0 || this.startY < 0) {
                Utils.fatal("No start location");
            }
        } catch(IOException e) {
            e.printStackTrace();
            Utils.fatal("Error reading input file");
        }

        // Set the number of the dirty locations (according to the read value)
        this.maximumDirtyLocationsCount = this.dirtyLocations.size();

        // Compute bit masks for bit twiddling states in pack/unpack

        // The number of bits required in order to store all the locations of the grid map
        this.robotLocationBits = (int) Math.ceil(Utils.log2(map.mapSize));
        // The bit-mask required in order to access the locations bit-vector
        this.robotLocationBitMask = Utils.mask(this.robotLocationBits);
        // The bit-mask required in order to access the dirty state of a single location
        this.singleBitMask = Utils.mask(1);
        // All the required bits : locations and the dirty locations (a single bit for each one)
        int totalRequiredBits = this.robotLocationBits + this.maximumDirtyLocationsCount;
        // Assure there is no overflow : at most 64 bits can be used in order to store the state
        if (totalRequiredBits > 64) {
            System.err.println("Too many bits required: " + totalRequiredBits);
            System.exit(1);
        }
        System.out.println("[Init] Initializes reverse operators");
        // Initialize the array of reverse operators
        this._initializeReverseOperatorsArray();
        System.out.println("[Done] (Initializes reverse operators)");
        System.out.println("[Init] Initializes MST for heavy calculation");
        // Pre-compute the {h, d} pairs for all the possible combinations of dirty vectors
        this._preComputeMSTHeavy();
        System.out.println("[Done] (Initializes MST for heavy calculation)");
    }

    /**
     * The constructor of the general VacuumRobot World domain
     *
     * @param stream The input stream for parsing the instance
     */
    public VacuumRobot(InputStream stream) {
        this(stream, COST_FUNCTION.UNIT);
    }

    /**
     * A node in a Maximum-Spanning-Tree (MST)
     */
    private class MSTNode {
        // The parent node
        private MSTNode p;
        private int id;
        private int rank;

        /**
         * A constructor of the class
         *
         * @param id An identifier of the node
         */
        private MSTNode(int id) {
            this.id = id;
            this.p = this;
            this.rank = 0;
        }

        /**
         * Initializes the state - no parent states and rank is 0
         */
        void reset() {
            this.p = this;
            this.rank = 0;
        }

        /**
         * Connects two nodes - x and y
         *
         * @param x The first node
         * @param y The second node
         */
        private void link(MSTNode x, MSTNode y) {
            // Connect the states according to their rank
            if (x.rank > y.rank) {
                y.p = x;
            } else {
                x.p = y;
                // Increase the second rank if they are equal
                if (x.rank == y.rank) {
                    ++y.rank;
                }
            }
        }

        /**
         * A recursive function that returns a ROOT state that actually represents a set of states
         * (The returned state must not have a parent - should be a parent of itself)
         *
         * NOTE: Actually, this function could be defined as static
         *
         * @param x The state whose representative state should be found
         *
         * @return The found state
         */
        private MSTNode findSet(MSTNode x) {
            if (x != x.p) {
                return this.findSet(x.p);
            }
            return x.p;
        }

        /**
         * Connects the set of state in which this state recedes, to the set of states in which
         * state y recedes
         *
         * @param y The state which should be connected to this state
         */
        private void union(MSTNode y) {
            this.link(
                    this.findSet(this),
                    this.findSet(y)
            );
        }
    }

    /**
     * An Edge that connects two MST nodes
     */
    private class MSTEdge implements Comparable<MSTEdge> {
        private MSTNode u;
        private MSTNode v;

        private int weight;

        /**
         * A constructor of the class : constructs an edge that connects two given nodes
         * @param u The first node
         * @param v The second node
         * @param weight The weight of the edge
         */
        private MSTEdge(MSTNode u, MSTNode v, int weight) {
            this.u = u;
            this.v = v;
            this.weight = weight;
        }

        /**
         * Compares the edge to other one - the one with the smallest weight is the 'winner'
         *
         * @param other The edge to which this edge is compared
         *
         * @return A negative number if the weight of the current edge is smaller,
         * a positive number if the weight of the current edge is higher
         * and 0 if the weights are equal
         */
        @Override
        public int compareTo(@NotNull MSTEdge other) {
            return this.weight - other.weight;
        }
    }

    /**
     * This class represents a grid on which the robot is moving and sucking garbage
     */
    private class GridMap {
        private int mapWidth;
        private int mapHeight;
        private int mapSize;

        private char map[];

        private Move possibleMoves[];
        private int possibleMovesCount;

        /**
         * The constructor of the class - constructs a Map with a pre-defined width and height
         *
         * @param mapWidth The width of the map
         * @param mapHeight The height of the map
         */
        private GridMap(int mapWidth, int mapHeight) {
            this.mapWidth = mapWidth;
            this.mapHeight = mapHeight;
            // The total size of the map
            this.mapSize = this.mapWidth * this.mapHeight;
            // The locations of the map : (mapWidth * mapHeight)
            this.map = new char[mapSize];
            // All the possible moves (can't move diagonally)
            this.possibleMoves = new Move[4];
            this.possibleMovesCount = possibleMoves.length;
            // Initialize all the moves according to the real directions to perform
            this.possibleMoves[0] = new Move(this, 'S',  0,  1);
            this.possibleMoves[1] = new Move(this, 'N',  0, -1);
            this.possibleMoves[2] = new Move(this, 'W', -1,  0);
            this.possibleMoves[3] = new Move(this, 'E',  1,  0);
        }

        /**
         * Make some location to tbe 'blocked': The robot can't be at this location
         *
         * @param loc The location to block
         */
        void setBlocked(int loc) {
            this.map[loc] = '#';
        }

        /**
         * Whether the queried location is blocked
         *
         * @param loc The location to check
         *
         * @return True if the location is blocked and False otherwise
         */
        boolean isBlocked(int loc) {
            return map[loc] == '#';
        }

        /**
         * Calculate the index of the location in a one-dimensional array
         *
         * @param x The horizontal location
         * @param y The vertical location
         *
         * @return The calculated index
         */
        int index(int x, int y) {
            return y * this.mapWidth + x;
        }

        /**
         * Creates a Pair object with the dimensions of the given location
         *
         * @param loc The location of the object
         * @return The calculated Pair
         */
        PairInt getPosition(int loc) {
            return new PairInt(loc % mapWidth, loc / mapWidth);
        }
    }

    /**
     * A specific Move performed on the grid
     */
    private class Move {
        // An identification of the move
        private char id;
        // The x and y deltas between this move and the previous move
        private int dx;
        private int dy;
        private int delta;

        /**
         * The constructor of the class
         *
         * @param map The grid on which the move is performed
         * @param id The identification of the move
         * @param dx The x delta of the move
         * @param dy The y delta of the move
         */
        private Move(GridMap map, char id, int dx, int dy) {
            this.id = id;
            this.dx = dx;
            this.dy = dy;
            this.delta = dx + map.mapWidth * dy;
        }
    }

    /**
     * This function creates a single MSTEdge which connects a state u and a state v
     * The function (is auxiliary) is called by the following functions:
     *      1. {@see _computeMSTForSingleState}
     *      2. {@see _computeMSTForSingleStateOnlyDirty}
     *
     * @param u The index of the node in the nodes array
     * @param xy A pair of doubles, which contains the location of the node u
     * @param v An index in the nodes array, from which we start to build the edges
     * @param nodes An array which contains all the MST nodes
     *
     * @return The calculated edge
     */
    private MSTEdge __computeMSTForSingleState(int u, PairInt xy,
                                               int v,
                                               MSTNode[] nodes) {
        // Add the computed edge to the list of edges
        return new MSTEdge(
                nodes[u], nodes[v],
                // The weight of the edge is the Manhattan distance between the locations
                Utils.calcManhattanDistance(
                        xy,
                        this.dirtyLocations.get(v)
                )
        );
    }

    /**
     * An auxiliary function for computing all the edges of the MST whose first side is equal to
     * some specific state, starting from a pre-given index in the states array
     *
     * NOTE: Only states that ARE ACTUALLY DIRTY are considered in this function
     * NOTE: The function assumes that the location at index u, located at (x, y) is DIRTY
     *
     * @param u The index of the node in the nodes array
     * @param xy A pair of doubles, which contains the location of the node u
     * @param v An index in the nodes array, from which we start to build the edges
     * @param nodes An array which contains all the MST nodes
     * @param edges The edges list into which the calculated edges are added
     */
    private void _computeMSTForSingleStateOnlyDirty(int dirtyVector,
                                                    int u, PairInt xy,
                                                    int v,
                                                    MSTNode[] nodes,
                                                    List<MSTEdge> edges) {
        // Go over all the NEXT dirty locations
        for (; v < this.dirtyLocations.size(); ++v) {
            // Again, treat only dirty locations
            if (!this.dirt(dirtyVector, v)) {
                continue;
            }
            edges.add(this.__computeMSTForSingleState(u, xy, v, nodes));
        }
    }

    /**
     * Initializes an array of MSTNodes of a given count
     *
     * @param count The number of nodes to create
     *
     * @return The created array of nodes
     */
    private MSTNode[] _initializeNodesArray(int count) {
        // All the dirty locations + location for the robot?
        MSTNode mstNodes[] = new MSTNode[count];
        // An initialization step
        for (int i = 0; i < mstNodes.length; ++i) {
            mstNodes[i] = new MSTNode(i);
        }
        return mstNodes;
    }

    /**
     * Used for DEBUG issues:
     * verifies a given MST by checking that all the states have the same root
     *
     * @param mstEdges An array of all the edges of the MST
     */
    // public void verifyMST(MSTEdge[] mstEdges) {
    //     int setId = -1;
    //     for (MSTEdge e : mstEdges) {
    //        if (setId == -1) {
    //            setId = e.u.findSet(e.u).id;
    //        }
    //        assert e.u.findSet(e.u).id == setId;
    //        assert e.v.findSet(e.v).id == setId;
    //    }
    // }

    /**
     * Given a state, computes a maximum spanning tree whose nodes are formed all the dirty
     * locations WHICH ARE ACTUALLY MARKED AS DIRTY
     *
     * @return The computed MST (set of edges)
     */
    private List<MSTEdge> computeMSTOnlyDirty(int dirt) {
        // Initialize a MSTNodes array of size dirtyLocations
        MSTNode mstNodes[] = this._initializeNodesArray(this.dirtyLocations.size());
        // Compute an MST of only the dirty locations (which are marked as actually dirty)
        List<MSTEdge> mstEdges = new ArrayList<>();
        for (int u = 0; u < this.dirtyLocations.size(); ++u) {
            // Treat only dirty locations
            if (!this.dirt(dirt, u)) {
                continue;
            }
            // Find all the edges which start from the node with index u,
            // and start the computation at index v (treat only actually dirty locations)
            this._computeMSTForSingleStateOnlyDirty(
                    dirt,
                    u, this.dirtyLocations.get(u),
                    u + 1,
                    mstNodes, mstEdges);
        }
        Collections.sort(mstEdges);
        return mstEdges;
    }

    /**
     * Computes the heuristic values (h and d) of the CURRENT state,
     * based on an MST that are built from the ACTUALLY DIRTY locations
     *
     * @param dirt The binary vector which states for each location of the grid, whether it is dirty
     *
     * @return An array of the form {h, d}
     */
    private double[] computeHD_MST(int dirt) {
        int remainingDirt = 0;
        // Count the dirty locations
        for (int u = 0; u < this.dirtyLocations.size(); ++u) {
            if (this.dirt(dirt, u)) {
                ++remainingDirt;
            }
        }
        // At least two remaining dirty locations should exist, otherwise, the heuristic value is 0
        // TODO: Why???
        if (remainingDirt <= 1) {
            return new double[] {0, 0};
        }
        // Compute an MST of only the dirty locations (which are marked as actually dirty)
        List<MSTEdge> mstEdges = this.computeMSTOnlyDirty(dirt);
        // Since currently the tree is rooted at a 'random' state - let's make it be rooted at a
        // state whose rank is the highest
        // (remember that the edges are sorted by weight - the edge with the lowest weight is first)
        // Also, define the <i>p</i> and <i>rank</i> values for each state
        MSTEdge mst[] = new MSTEdge[remainingDirt - 1];
        int j = mst.length - 1;
        // Go over all the built edges
        for (MSTEdge e : mstEdges) {
            // Bypass that pair of edges {e, v} if they have the same root state
            if (e.u.findSet(e.u) == e.v.findSet(e.v)) {
                continue;
            }
            // Add the edge to the list of edges
            // (the array will be sorted from highest to lowest weight values)
            // (don't forget to decrease the value of j after adding the edge to the array)
            mst[j--] = e;
            e.u.union(e.v);
        }

        // DEBUG: verify the tree: All the MST states must have the same root!
        // verifyMST(mst);

        return this._computeHDAccordingToDirtyLocationsMST(mst, remainingDirt);
    }

    /**
     * The function calculates the distance between a given location and the closest dirty location
     *
     * @param xy The location whose distance (to the closest dirty location) should be calculated
     * @param ignoreIndexes An array of location indexes that should be ignored while traversing all
     *                      the locations (optional)
     * NOTE: The size of ignoreIndexes (ignoreIndexes.length) must be equal to the total number of
     *       the possible dirty locations (dirtLocations.size())
     * @param s The state in which the dirty locations recede
     *
     * @return A pair of :
     *      {
     *          DIRT:       index of the dirty location closest to the robot,
     *          DISTANCE:   the calculated closest (minimum) distance,
     *      }
     */
    private int[] __getMinimumDirtAndDistanceToDirty(PairInt xy, boolean[] ignoreIndexes,
                                                     VacuumRobotState s) {
        // The index of the dirty location, closest to the robot
        int minDirtyIndex = -1;
        // Initially, the minimum distance is the lowest possible value
        int minDirtyDist = Integer.MAX_VALUE;
        // Go over all the (possible) dirty locations
        for (int n = 0; n < this.maximumDirtyLocationsCount; ++n) {
            // If the location should be ignored or was already cleaned - bypass it
            if ((ignoreIndexes != null && ignoreIndexes[n]) || (!s.isDirty(n))) {
                continue;
            }
            // Get the distance between the robot location and the current dirty location
            int currentDist = Utils.calcManhattanDistance(xy, this.dirtyLocations.get(n));
            // Update the minimum distance if required
            if (currentDist < minDirtyDist) {
                minDirtyIndex = n;
                minDirtyDist = currentDist;
            }
        }
        return new int[]{minDirtyIndex, minDirtyDist};
    }

    /**
     * The function calculates the distance between a given location and the closest dirty location
     *
     * @param xy The location whose distance (to the closest dirty location) should be calculated
     * @param s The state in which the dirty locations recede
     *
     * {@see __getMinimumDirtAndDistanceToDirty}
     *
     * @return A pair of :
     *      {
     *          DIRT:       index of the dirty location closest to the robot,
     *          DISTANCE:   the calculated closest (minimum) distance,
     *      }
     */
    /*
    private int[] _getMinimumDirtAndDistanceToDirty(PairInt xy, VacuumRobotState s) {
        // We don't have locations to ignore - so pass null as a parameter
        return this.__getMinimumDirtAndDistanceToDirty(xy, null, s);
    }
    */

    /**
     * The function calculates the distance between a given location and the closest dirty location
     *
     * @param xy The location whose distance (to the closest dirty location) should be calculated
     * @param s The state in which the dirty locations recede
     *
     * @return The calculated minimum distance
     */
    /*
    private int _getMinimumDistanceToDirty(PairInt xy, VacuumRobotState s) {
        return this._getMinimumDirtAndDistanceToDirty(xy, s)[1];
    }
    */

    /**
     * The function calculates h and d values given a pre-computed MST of all the remaining
     * dirty locations
     *
     * @param mstEdgesDirty An MST of all the locations that are dirty
     * @param remainingDirt The number of remaining dirty locations
     *
     * @return A pair of calculated h and d values {h, d}
     */
    private double[] _computeHDAccordingToDirtyLocationsMST(MSTEdge mstEdgesDirty[],
                                                            int remainingDirt) {
        // Initial values
        double h = 0.0d;
        double d = 0.0d;

        // Calculate the number of locations that are CLEAN
        // This value is used for the heavy operation - it is calculated by applying some function
        // on the number of the locations cleaned by the robot

        // The idea behind that calculation is that the robot is consuming fuel during the clean
        // operations, so each operation must cost more than the previous
        // TODO: +1? Why?
        int robotHeavyAddend = (this.maximumDirtyLocationsCount - (remainingDirt)) + 1;
        // Here we define the cost of a single operation that the robot should perform
        // (either MOVE or VACUUM) which normally costs 1.0
        // In case heavy mode is applied, the operation will cost 1 + some function applied on the
        // number of required operations
        double robotOperationCost = (heavy) ? (this.heavy(robotHeavyAddend) + 1.0d) : 1.0d;

        // Now, let's calculate the h and d values
        for (MSTEdge edge : mstEdgesDirty) {
            // The cost of the operations to perform: MOVing and VACUUM
            h += (edge.weight * robotOperationCost + robotOperationCost);
            // Number of operations to perform: MOVing and VACUUM
            d += (edge.weight + 1);
            // Increase the number of locations passed by robot and update the cost of its single
            // operation
            ++robotHeavyAddend;
            robotOperationCost = (heavy) ? heavy(robotHeavyAddend) + 1.0d : 1.0d;
        }
        // Finally, we got the h and d values, so, let's return them
        return new double[]{h, d};
    }

    /**
     * Calculates h and d addend values created by considering a single edge formed by
     * moving the robot to closest dirty locations
     *
     * @param s The current VacuumRobot state (required in order to know the current number of
     *          dirty locations - for calculating the heavy value)
     * @param ignoreIndexes An array of location indexes that should be ignored while traversing all
     *                      the locations (optional)
     * NOTE: The size of ignoreIndexes (ignoreIndexes.length) must be equal to the total number of
     *       the possible dirty locations (dirtLocations.size())
     * @param closestLocationIndex [OUTPUT parameter] The index of the closest location (optional)
     *
     * @return An pair of h and d addends (in a form of a double array) - {h, d}
     */
    private double[] __getHDAddendToClosestDirtyLocation(VacuumRobotState s,
                                                         boolean[] ignoreIndexes,
                                                         int[] closestLocationIndex){
        int[] closestDirtAndDistance =
            this.__getMinimumDirtAndDistanceToDirty(
                // Location of the robot
                this.map.getPosition(s.robotLocation),
                ignoreIndexes,
                s
        );
        // Fill the output parameter with the index of the location closest to the robot
        // (if the output parameter is not null)
        if (closestLocationIndex != null) {
            closestLocationIndex[0] = closestDirtAndDistance[0];
        }
        int shortestDistance = closestDirtAndDistance[1];
        // In case of heavy calculation - the cost of robot operation is
        // 1 + number of edges already considered (number of cleaned states)
        double robotOperationCost = (this.heavy) ? (heavy(s) + 1.0d) : 1.0d;
        // Cost of operations: MOVE * FUEL + SUCK
        double hAddend = (shortestDistance * robotOperationCost + robotOperationCost);
        // Number of operations: MOVE + SUCK
        double dAddend = (shortestDistance + 1);
        return new double[]{hAddend, dAddend};
    }

    /**
     * Calculates h and d addend values created by considering a single edge formed by
     * moving the robot to closest dirty locations
     *
     * {@see __getHDAddendToClosestDirtyLocation}
     *
     * @param s The current VacuumRobot state (required in order to know the current number of
     *          dirty locations - for calculating the heavy value)
     *
     * @return An pair of h and d addends (in a form of a double array) - {h, d}
     */
    private double[] _getHDAddendToClosestDirtyLocation(VacuumRobotState s) {
        // No locations to ignore and not output index of the closest distance to the robot location
        return this.__getHDAddendToClosestDirtyLocation(s, null, null);
    }

    /**
     * An auxiliary function for computing all the edges of the MST whose first side is equal to
     * some specific state, starting from a pre-given index in the states array
     *
     * @param u The index of the node in the nodes array
     * @param xy A pair of doubles, which contains the location of the node u
     * @param v An index in the nodes array, from which we start to build the edges
     * @param nodes An array which contains all the MST nodes
     * @param edges The edges list into which the calculated edges are added
     */
    /*
    private void _computeMSTForSingleState(int u, PairInt xy,
                                           int v,
                                           MSTNode[] nodes,
                                           List<MSTEdge> edges) {
        // Go over all the NEXT dirty locations
        for (; v < this.dirtyLocations.size(); ++v) {
            edges.add(this.__computeMSTForSingleState(u, xy, v, nodes));
        }
    }
    */

    /**
     * Given a state, computes a maximum spanning tree whose nodes are formed all the dirty
     * locations
     *
     * NOTE: The locations from which the MST is formed ARE NOT CHECKED OF BEING DIRTY
     *
     * @return The computed MST (set of edges)
     */
    /*
    private List<MSTEdge> _computeMST() {
        // Create an array of nodes (without the location of the robot)
        // All the dirty locations + location for the robot?
        MSTNode mstNodes[] = this._initializeNodesArray(this.dirtyLocations.size());
        // Initial set of mstEdges (initially empty)
        List<MSTEdge> mstEdges = new ArrayList<>();
        // Go over all the dirty locations
        for (int u = 0; u < this.dirtyLocations.size(); ++u) {
            // Find all the edges which start from the node with index u,
            // and start the computation at index v
            this._computeMSTForSingleState(
                    u, this.dirtyLocations.get(u),
                    u + 1,
                    mstNodes, mstEdges);
        }
        // Sort the collection of edges by weight
        Collections.sort(mstEdges);
        return mstEdges;
    }
    */

    /**
     * Given a state, computes a maximum spanning tree whose nodes are formed all the dirty
     * locations AND THE LOCATION OF THE ROBOT OF THAT STATE
     *
     * NOTE: The locations from which the MST is formed ARE NOT CHECKED OF BEING DIRTY
     *
     * @param state The state from which the maximum spanning tree should be computed
     *
     * @return The computed MST (set of edges)
     */
    /*
    private List<MSTEdge> _computeMST(VacuumRobotState state) {
        // All the dirty locations + location for the robot?
        MSTNode mstNodes[] = this._initializeNodesArray(this.dirtyLocations.size() + 1);
        // Initial set of mstEdges (initially empty)
        List<MSTEdge> mstEdges = new ArrayList<>();
        for (int u = 0; u < this.dirtyLocations.size() + 1; ++u) {
            // Get the location (one from the dirty locations or the location of the robot)
            PairInt xy = (u < this.dirtyLocations.size()) ?
                    this.dirtyLocations.get(u) :
                    this.map.getPosition(state.robotLocation);
            // Next dirty location
            int v = (u < this.dirtyLocations.size()) ? u + 1 : 0;
            // Find all the edges which start from the node with index u, and start the computation
            // at index v
            this._computeMSTForSingleState(u, xy, v, mstNodes, mstEdges);
        }
        // Sort the collection of edges by weight
        Collections.sort(mstEdges);
        return mstEdges;
    }
    */

    /**
     * Calculate the h and d values for heavy vacuum problems:
     *  1. Compute the minimum spanning tree (MST) of the isDirty piles
     *  2. Order the edges by greatest length first
     *  3. Multiply the edge weights by the current weight of the robot plus the number of edges
     *     already considered
     *  (Add same calculation for the robot:
     *   Reaching the closest dirty position from the current location of the robot)
     *
     * @param s The state whose heuristic values should be calculated
     *
     * @return An array of the form {h, d}
     */
    /*
    private double[] computeHD_chris(VacuumRobotState s) {
        // Initial values
        double h;
        double d;

        // In case we are at goal - return 0 values
        if (s.remainingDirtyLocationsCount == 0) {
            return new double[]{0.0d, 0.0d};
        }

        // Build mst of all the POTENTIALLY DIRTY locations
        List<MSTEdge> mstEdges = this._computeMST();

        // Compute an MST of only the dirty locations (which are marked as actually dirty)

        // Number of edges of the MST (also mstEdges.size())
        MSTEdge mst[] = new MSTEdge[s.remainingDirtyLocationsCount - 1];
        // The last index in the array
        int j = mst.length - 1;
        for (MSTEdge e : mstEdges) {
            // If one of the nodes that form the edge is clean - bypass it
            if ((e.u.id < this.maximumDirtyLocationsCount && !s.isDirty(e.u.id)) ||
                    (e.v.id < this.maximumDirtyLocationsCount && !s.isDirty(e.v.id))) {
                continue;
            }
            // Nodes at both sides of the edge are dirty - let's define their parent
            // (if not identical)
            if (e.u.findSet(e.u) == e.v.findSet(e.v)) {
                continue;
            }
            // Add the edge to the final list of edges
            mst[j] = e;
            e.u.union(e.v);
            j--;
        }

        // DEBUG: verify the tree: All the MST states must have the same root!
        // verifyMST();
        double[] hd = this._computeHDAccordingToDirtyLocationsMST(mst,
                                                                  s.remainingDirtyLocationsCount);
        // Extract the h and d values
        h = hd[0];
        d = hd[1];
        // now include edge for shortest isDirty to the current location of the robot
        double[] hdAddend = _getHDAddendToClosestDirtyLocation(s);
        // Finally, return the final h and d values
        return new double[]{h + hdAddend[0], d + hdAddend[1]};
    }
    */

    /**
     * This function is equivalent to the {@see computeHD_chris} function.
     * However, it uses pre-computed values of h and d which are calculated based on the
     * dirty locations binary vector - of the current state
     *
     * @param s The state whose heuristic values should be calculated
     *
     * @return An array of the form {h, d}
     */
    private double[] computeHD_chris_fast(VacuumRobotState s) {
        // In case we are at goal - return 0 immediately
        if (s.remainingDirtyLocationsCount == 0) {
            return new double[]{0.0d, 0.0d};
        }
        // Get the {h, d} pair for the current dirty locations binary vector
        double[] hd = this.lookupMST_heavy[s.dirt];
        double h = hd[0];
        double d = hd[1];
        // Now, include edge for shortest dirt to the current location of the robot
        double[] hdAddend = _getHDAddendToClosestDirtyLocation(s);
        // Finally, return the final h and d values
        return new double[]{h + hdAddend[0], d + hdAddend[1]};
    }

    /**
     * The function computes the h and d values in a greedy manner, which means that the
     * cost of moving the robot to the closest dirty location is calculated, then, moving it to the
     * next closest location etc.
     *
     * For heavy Vacuum Robot problems, the weight of each edge is multiplied by the current weight
     * of the robot plus the number of edges already considered
     *
     * @param s The state whose heuristic values should be calculated
     *
     * @return An array of the form {h, d}
     */
    private double[] computeHD_greedy(VacuumRobotState s) {
        double h = 0.0d;
        double d = 0.0d;

        // In case we are at goal - return 0 immediately
        if (s.remainingDirtyLocationsCount == 0) {
            return new double[]{0.0d, 0.0d};
        }

        int[] minDistDirtyIndexArray = new int[1];
        double[] hdAddend = this.__getHDAddendToClosestDirtyLocation(
                // The state
                s,
                // No locations to ignore
                null,
                // OUTPUT parameter: The index of the location closest to the robot
                minDistDirtyIndexArray
        );

        // Increase h and d values by moving the robot to the closest dirty location
        h += hdAddend[0];
        d += hdAddend[1];
        // Extract the output values: the index of the dirty location, closest to the robot
        int minDirtyIndex = minDistDirtyIndexArray[0];

        // Number of cleaned locations
        // The last +1 is because the robot has been already moved to the closest location
        int numberOfCleanLocations =
                (this.maximumDirtyLocationsCount - s.remainingDirtyLocationsCount) + 1;

        // Sum the greedy traversal of remaining dirty locations
        boolean used[] = new boolean[this.maximumDirtyLocationsCount];

        for (int rem = s.remainingDirtyLocationsCount - 1; rem > 0; --rem) {
            assert (s.isDirty(minDirtyIndex));
            used[minDirtyIndex] = true;
            // Calculate the distance to the next closest location
            // (starting from the dirty location the robot is currently on)
            int[] minDirtyDirtAndDistance =
                    this.__getMinimumDirtAndDistanceToDirty(
                            // Current dirty location
                            this.dirtyLocations.get(minDirtyIndex),
                            // Indexes of locations to ignore
                            used,
                            s
                    );
            minDirtyIndex = minDirtyDirtAndDistance[0];
            int minDirtyDistance = minDirtyDirtAndDistance[1];

            double robotOperationCost = this.heavy(numberOfCleanLocations) + 1.0d;
            // Cost of current operation: MOVE * FUEL + SUCK
            h += (minDirtyDistance * robotOperationCost + robotOperationCost);
            // Number of operations: MOVE + SUCK
            d += (minDirtyDistance + 1);
            // One more location has been cleaned
            ++numberOfCleanLocations;
        }

        // For h on the heavy vacuum problems, multiply the edge weights by the current
        // weight of the robot plus the number of edges already considered
        if (this.heavy) {
            return new double[]{h, d};
        } else {
            // Estimate the cost of a greedy traversal of the dirt piles: Calculate the number of
            // actions required to send the robot to the nearest pile of dirt, then the nearest after
            // that, and so on
            return new double[]{d, d};
        }
    }

    /**
     * Compute the h and d values of a given state of the Vacuum Robot on the HEAVY Vacuum
     * Robot problems
     *
     * @param state The state whose heuristic values should be computed
     *
     * @return The pair of the form {h, d}
     */
    private double[] computeHD_jordan(VacuumRobotState state) {
        // In case we are at goal - return 0 immediately
        if (state.remainingDirtyLocationsCount == 0) {
            return new double[]{0.0d, 0.0d};
        }
        // For h, use the function based on fast calculation because of PRE-COMPUTED MSTs
        double h = computeHD_chris_fast(state)[0];
        // This is an alternative (no fast calculation)
        // double h = computeHD_chris(s)[0];
        // For d, use the standard function for the unit cost domain (which estimates the cost of a
        // greedy traversal of the the dirt piles)
        double d = computeHD_greedy(state)[1];
        return new double[]{h, d};
    }

    /**
     * Compute the heuristic value of a given state
     *
     * @param s The state whose heuristic value should be computed
     * @return The computed value
     */
    private double[] computeHD(VacuumRobotState s) {
        if (this.heavy) {
            return computeHD_jordan(s);
        } else {
            return computeHD_greedy(s);
        }
    }

    /**
     * The heavy function for Explicit Estimation Search
     *
     * @param ndirt The number of dirty location that were ALREADY CLEAN BY THE ROBOT
     *
     * @return The value of the function (currently is linear in number of the locations that were
     * already cleaned)
     */
    private double heavy(int ndirt) {
        return ((double)ndirt);
    }

    /**
     * The heavy function for Explicit Estimation Search
     *
     * @param state The state for which the function should be calculated
     *
     * @return The value of the function (currently is linear in the number of locations that were
     * already cleaned)
     */
    private double heavy(VacuumRobotState state) {
        // Number of cleaned locations: # of all possible dirty - # of still dirty
        return heavy(this.maximumDirtyLocationsCount - state.remainingDirtyLocationsCount);
    }

    /**
     * The function calculates the pair {h, d} by summing the Manhattan distance between the
     * farthest points of the surrounding rectangle of all the dirty locations
     * (+ the number of locations that the robot should clean)
     *
     * @param s The state whose heuristic values should be computed
     *
     * @return The pair of the form {h, d}
     */
    /*
    private double[] computeHD_ethan(VacuumRobotState s) {
        // In case we are at goal - return 0 immediately
        if (s.remainingDirtyLocationsCount == 0) {
            return new double[]{0.0d, 0.0d};
        }

        int i = 0;
        // Find the first dirty location in the vector (such one must exist)
        while (i < this.maximumDirtyLocationsCount && !s.isDirty(i)) { ++i; }
        PairInt dirtyLocation = this.dirtyLocations.get(i);
        // The minimum and maximum found X values are equal to the firstly taken location
        int minX = dirtyLocation.first;
        int maxX = minX;
        // The minimum and maximum found Y values are equal to the firstly taken location
        int minY = dirtyLocation.second;
        int maxY = minY;

        // Continue looking in the dirty locations array (increase i index)
        for (i++; i < maximumDirtyLocationsCount; i++) {

            // Bypass locations that has been already cleaned
            if (!s.isDirty(i)) {
                continue;
            }

            // Get the current dirty location and its X and Y values
            dirtyLocation = this.dirtyLocations.get(i);
            int currentX = dirtyLocation.first;
            int currentY = dirtyLocation.second;

            // Update minX, maxX, minY and maxY values (if required)
            if (currentX < minX) {
                minX = currentX;
            }
            if (currentX > maxX) {
                maxX = currentX;
            }
            if (currentY < minY) {
                minY = currentY;
            }
            if (currentY > maxY) {
                maxY = currentY;
            }
        }
        // Now, calculate the Manhattan distance between the farthest locations of the surrounding
        // square of all the possible dirty locations (+ number of dirty locations to clean)
        double sum = s.remainingDirtyLocationsCount + (maxX - minX) + (maxY - minY);
        return new double[]{sum, sum};
    }
    */

    /**
     * Checks whether the given move is valid for the given location
     *
     * @param location The location on the map, on which the move is applied
     * @param move The move to apply
     *
     * @return True if the move is valid and false otherwise
     */
    private boolean _isValidMove(int location, Move move) {
        // Add the delta of the move and get the next location
        int next = location + move.delta;

        // Assure the move doesn't cause the state to exceed the grid and also that the move
        // doesn't cause the state to reach a blocked location

        // (Moving West/East && y changed) => invalid!
        if (move.dx != 0 &&
                (next / this.map.mapWidth != location / this.map.mapWidth)) {
            return false;
        // Moving (South/North && x changed) => invalid
        } else if (move.dy != 0 &&
                (next % this.map.mapWidth != location % this.map.mapWidth)) {
            return false;
        }
        return (next > 0 && next < this.map.mapSize && !this.map.isBlocked(next));
    }

    @Override
    public VacuumRobotState initialState() {
        VacuumRobotState vrs = new VacuumRobotState();
        vrs.robotLocation = this.map.index(this.startX, this.startY);
        vrs.remainingDirtyLocationsCount = this.maximumDirtyLocationsCount;
        // Initially, all the dirty locations should be marked as dirty (will be cleaned later)
        for (int i = 0; i < this.maximumDirtyLocationsCount; i++) {
            vrs.setDirty(i, true);
        }
        // Compute the initial h and d values and fill the state with that values
        double hd[] = this.computeHD(vrs);
        // Initially, g value equals to -1
        vrs.h = hd[0];
        vrs.d = hd[1];
        // System.out.println(this.dumpState(vrs));
        // System.out.println(this.dumpState(vrs));
        // Return the created state
        return vrs;
    }

    @Override
    public boolean isGoal(State state) {
        VacuumRobotState drs = (VacuumRobotState)state;
        return drs.remainingDirtyLocationsCount == 0;
    }

    @Override
    public int getNumOperators(State state) {
        VacuumRobotState vrs = (VacuumRobotState) state;
        if (vrs.ops == null) {
            this.initOps(vrs);
            // TODO: Is required?
            assert vrs.ops.length < 6;
        }
        return vrs.ops.length;
    }

    @Override
    public Operator getOperator(State state, int index) {
        VacuumRobotState vrs = (VacuumRobotState)state;
        if (vrs.ops == null) {
            this.initOps(vrs);
        }
        return vrs.ops[index];
    }

    @Override
    public State copy(State state) {
        return new VacuumRobotState((VacuumRobotState)state);
    }

    /**
     * Pack a state into a long number
     *
     * The packed state is a 64 bit (long) number which stores the following data:
     *
     * First part stores the location of the robot
     * The next part stores a bit vector which indicated for each possible location, whether it is dirty
     */
    @Override
    public PackedElement pack(State s) {
        VacuumRobotState state = (VacuumRobotState)s;

        long packed = 0L;
        // pack the location of the robot
        packed |= (state.robotLocation & this.robotLocationBitMask);
        // pack 1 bit for each remaining dirt
        for (int i = 0; i < this.maximumDirtyLocationsCount; ++i) {
            packed <<= 1;
            if (state.isDirty(i)) {
                packed |= 1 & this.singleBitMask;
            }
        }

        PackedElement toReturn = new PackedElement(packed);

        /**
         * Debug: perform unpack after packing and assure results are ok
         */
        if ((((VacuumRobotState)this.unpack(toReturn)).robotLocation != state.robotLocation) ||
                (((VacuumRobotState)this.unpack(toReturn)).dirt != state.dirt)) {
            assert false;
        }

        /*
         * VacuumRobotState test = unpack(packed);
         * assert(test.equals(state));
         */
        return toReturn;
    }

    /**
     * An auxiliary function for unpacking Vacuum Robot state from a long number.
     * This function performs the actual unpacking
     *
     * @param packed The packed state
     * @param dst The destination state which is filled from the unpacked value
     */
    private void _unpackLite(long packed, VacuumRobotState dst) {
        // unpack the dirty locations
        dst.ops = null;
        // Initially, there are no dirty locations
        dst.remainingDirtyLocationsCount = 0;
        dst.dirt = 0;
        // For each possible dirty location, check if it is actually dirty
        for (int i = this.maximumDirtyLocationsCount - 1; i >= 0; --i) {
            long d = packed & singleBitMask;
            if (d == 1) {
                // Make the location dirty
                dst.setDirty(i, true);
                // Increase the counter
                ++dst.remainingDirtyLocationsCount;
            } else {
                // Clear the dirty value from the location
                dst.setDirty(i, false);
            }
            packed >>= 1;
        }
        // Finally, unpack the location of the robot
        dst.robotLocation = (int) (packed & this.robotLocationBitMask);
    }

    /**
     * An auxiliary function for unpacking Vacuum Robot state from a long number
     *
     * @param packed The packed state
     * @param dst The destination state which is filled from the unpacked value
     */
    private void unpack(long packed, VacuumRobotState dst) {
        this._unpackLite(packed, dst);
        // Compute the heuristic values
        double hd[] = this.computeHD(dst);
        dst.h = hd[0];
        dst.d = hd[1];
    }

    /**
     * Unpacks the Vacuum Robot state from a long number
     */
    @Override
    public State unpack(PackedElement packed) {
        assert packed.getLongsCount() == 1;
        VacuumRobotState dst = new VacuumRobotState();
        this.unpack(packed.getFirst(), dst);
        return dst;
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
        VacuumRobotState s = (VacuumRobotState)state;
        VacuumRobotState vrs = (VacuumRobotState)copy(s);
        VacuumRobotOperator o = (VacuumRobotOperator)op;

        vrs.ops = null; // reset ops

        switch (o.type) {
            case VacuumRobotOperator.SUCK: {
                // Get the dirty location in the dirty locations vector
                int dirt = this.dirt[s.robotLocation];
                assert (dirt >= 0);
                assert (dirt < this.maximumDirtyLocationsCount);
                // Assure the location is actually dirty
                assert (s.isDirty(dirt));
                // Clean the location
                vrs.setDirty(dirt, false);
                // Decrease the count of dirty locations
                --vrs.remainingDirtyLocationsCount;
                break;
            }
            // All other operators are MOVE
            default: {
                // Assure the type of the operator is actually a move
                if (o.type < 0 || o.type > 3) {
                    System.err.println("Unknown operator type " + o.type);
                    System.exit(1);
                }
                // Update the location of the robot
                vrs.robotLocation += this.map.possibleMoves[o.type].delta;
            }
        }

        vrs.depth++;

        //dumpState(s);
        //dumpState(vrs);

        double p[] = this.computeHD(vrs);
        vrs.h = p[0];
        vrs.d = p[1];

        vrs.parent = s;

        //dumpState(vrs);
        return vrs;
    }

    /**
     * A VacuumRobot Cleaner World state
     */
    private final class VacuumRobotState implements State {
        private double h;
        private double d;

        //private double hHat;
        //private double dHat;
        //private double sseD;
        //private double sseH;

        // The location of the robot
        private int robotLocation;
        // The depth of the search
        private int depth;
        // All the dirty locations
        private int dirt;
        // The number of remaining dirty locations
        private int remainingDirtyLocationsCount;

        // All the possible operators
        private VacuumRobotOperator[] ops;

        private VacuumRobotState parent;

        /**
         * A default constructor of the class
         */
        private VacuumRobotState() {
            this.h = -1;
            this.d = -1;
            this.ops = null;
            this.parent = null;
            this.remainingDirtyLocationsCount = -1;
            this.dirt = -1;
        }

        /**
         * A copy constructor
         *
         * @param state The state to copy
         */
        private VacuumRobotState(VacuumRobotState state) {
            this.h = state.h;
            this.d = state.d;
            this.depth = state.depth;
            // Copy the location of the robot
            this.robotLocation = state.robotLocation;
            // Copy dirty locations
            this.dirt = state.dirt;
            this.remainingDirtyLocationsCount = state.remainingDirtyLocationsCount;
            // Copy the parent state
            this.parent = state.parent;
        }

        @Override
        public boolean equals(Object obj) {
            try {
                VacuumRobotState otherState = (VacuumRobotState)obj;
                // First, compare the basic data : The current location and the number of
                // dirty locations
                if (this.robotLocation != otherState.robotLocation ||
                        this.remainingDirtyLocationsCount != otherState.remainingDirtyLocationsCount) {
                    return false;
                }
                // Compare all the dirty locations
                return this.dirt == otherState.dirt;
            } catch (ClassCastException e) {
                return false;
            }
        }

        /**
         * Whether the i'th location is dirty - perform a bitwise AND with the bit at this location
         * @param i The index of the location to test
         * @return True if the location is dirty and False otherwise
         */
        private boolean isDirty(int i) {
            // TODO: test!
            return (this.dirt & (1<<i)) != 0;
        }

        /**
         * Set the 'DIRTY' value of the i'th location
         * @param i The index of the location to set
         * @param b The value to set: True for 'DIRTY' and False for 'CLEAN'
         */
        private void setDirty(int i, boolean b) {
            // TODO: test!
            if (b) {
                this.dirt |= (1 << i);
            } else {
                this.dirt &= ~(1 << i);
            }
        }

        @Override
        public State getParent() {
            return this.parent;
        }

        /**
         * An auxiliary function for calculating the h and d values of the current state
         */
        private void computeHD() {
            double[] p = VacuumRobot.this.computeHD(this);
            this.h = p[0];
            this.d = p[1];
        }

        @Override
        public double getH() {
            if (this.h < 0) {
                this.computeHD();
            }
            return this.h;
        }

        @Override
        public double getD() {
            if (this.d < 0) {
                this.computeHD();
            }
            return this.d;
        }

        @Override
        public String dumpState() {
            return VacuumRobot.this.dumpState(this);
        }

        @Override
        public String dumpStateShort() {
            return VacuumRobot.this.dumpStateShort(this);
        }

    }

    private final class VacuumRobotOperator implements Operator {
        // UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3
        public static final int SUCK = 4;
        public static final int NOP = -1;

        // Initially, the type of the operator is NOP
        private int type = VacuumRobotOperator.NOP;

        /**
         * The constructor of the class: initializes an operator with the given type
         *
         * @param type The type of the operator
         */
        private VacuumRobotOperator(int type) {
            this.type = type;
        }

        @Override
        public boolean equals(Object obj) {
            try {
                if (obj == null) {
                    return false;
                }
                VacuumRobotOperator o = (VacuumRobotOperator) obj;
                return type == o.type;
            } catch (ClassCastException e) {
                return false;
            }
        }

        @Override
        public double getCost(State s) {
            VacuumRobotState vrs = (VacuumRobotState) s;
            double cost = 1.0d;
            if (VacuumRobot.this.heavy) {
                cost += VacuumRobot.this.heavy(vrs);
            }
            return cost;
        }

        /**
         * Finds the reverse operator that applying it will reverse the state caused by this
         * operator
         */
        @Override
        public Operator reverse(State state) {
            return VacuumRobot.this.reverseOperators[this.type];
        }
    }

    /**
     * The function performs a dump of the grid map used in the search and puts the positions of the robot on each
     * of the given states on the map
     *
     * @param states The states array (can be null)
     * @param markAllDirty Whether to mark all the dirty locations (as given in the initial state) or only the actual
     *                     dirty
     * @param obstaclesAndDirtyCountArray The obstacles counter and dirty locations counters (an OUTPUT parameter)
     *
     * @return A string representation of the map (with all agents located on it)
     */
    private String _dumpMap(State states[], boolean markAllDirty, int[] obstaclesAndDirtyCountArray) {
        assert obstaclesAndDirtyCountArray == null || obstaclesAndDirtyCountArray.length == 2;
        StringBuilder sb = new StringBuilder();
        int obstaclesCount = 0;
        int dirtyCount = 0;
        // Now, dump the Map with the location of the agent and the goals
        for (int y = 0; y < this.map.mapHeight; ++y, sb.append('\n')) {
            // Horizontal
            for (int x = 0; x < this.map.mapWidth; ++x) {
                // Get the index of the current location
                int locationIndex = this.map.index(x, y);
                PairInt locationPair = this.map.getPosition(locationIndex);
                // Check for obstacle
                if (this.map.isBlocked(locationIndex)) {
                    sb.append('#');
                    ++obstaclesCount;
                // Check if the location is dirty
                } else {
                    boolean dirtyLocation = false;
                    if (this.dirtyLocations.contains(locationPair)) {
                        if (markAllDirty) {
                            sb.append('*');
                            dirtyLocation = true;
                        } else if (states != null) {
                            int dirtyIndex = this.dirt[locationIndex];
                            for (State state : states) {
                                if (((VacuumRobotState)state).isDirty(dirtyIndex)) {
                                    // Check not last state and robot there
                                    if (states != null &&
                                            ((VacuumRobotState)states[states.length - 1]).robotLocation ==
                                                    locationIndex) {
                                        sb.append(VacuumRobot.ROBOT_END_MARKER);
                                    } else {
                                        sb.append('*');
                                    }
                                    dirtyLocation = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (dirtyLocation) {
                        ++dirtyCount;
                    } else if (states != null) {
                        boolean robotLocation = false;
                        // Check if the robot is at this location
                        for (int k = 0; k < states.length; ++k) {
                            if (((VacuumRobotState)states[k]).robotLocation == locationIndex) {
                                if (k == 0) {
                                    sb.append(VacuumRobot.ROBOT_START_MARKER);
                                } else if (k == states.length - 1) {
                                    sb.append(VacuumRobot.ROBOT_END_MARKER);
                                } else {
                                    sb.append('X');
                                }
                                robotLocation = true;
                                break;
                            }
                        }
                        if (!robotLocation) {
                            sb.append(".");
                        }
                    }
                }
            }
        }
        // Set the output parameter
        if (obstaclesAndDirtyCountArray != null) {
            obstaclesAndDirtyCountArray[0] = obstaclesCount;
            obstaclesAndDirtyCountArray[1] = dirtyCount;
        }
        return sb.toString();
    }

    /**
     * Print the a short representation of the state for debugging reasons
     *
     * @param state The state to print
     */
    private String dumpStateShort(VacuumRobotState state) {
        StringBuilder sb = new StringBuilder();
        PairInt robotLocation = this.map.getPosition(state.robotLocation);
        sb.append("robot location: ");
        sb.append(robotLocation.toString());
        sb.append(", dirty vector: ");
        sb.append(state.dirt);
        return sb.toString();
    }

    /**
     * Print the state for debugging reasons
     *
     * @param state The state to print
     */
    private String dumpState(VacuumRobotState state) {
        StringBuilder sb = new StringBuilder();
        int obstaclesCount;

        sb.append("********************************\n");
        // h
        sb.append("h: ");
        sb.append(state.h);
        sb.append("\n");
        // d
        sb.append("d: ");
        sb.append(state.d);
        sb.append("\n");

        int obstaclesAndDirtyCountArray[] = new int[2];
        sb.append(this._dumpMap(new State[]{state}, false, obstaclesAndDirtyCountArray));
        obstaclesCount = obstaclesAndDirtyCountArray[0];
        sb.append("\n");
        sb.append(this.dumpStateShort(state));
        sb.append("\n");
        sb.append("obstacles count: ");
        sb.append(obstaclesCount);
        sb.append("\n");
        sb.append("dirty locations count: ");
        sb.append(state.remainingDirtyLocationsCount);
        sb.append("\n");
        sb.append("********************************\n\n");
        return sb.toString();
    }

    public String dumpStatesCollection(State[] states) {
        StringBuilder sb = new StringBuilder();
        // All the data regarding a single state refers to the last state of the collection
        VacuumRobotState lastState = (VacuumRobotState)states[states.length - 1];
        sb.append(this._dumpMap(states, true, null));
        // Additional newline
        sb.append('\n');
        PairInt agentLocation = this.map.getPosition(lastState.robotLocation);
        sb.append("Agent location: ");
        sb.append(agentLocation.toString());
        sb.append("\n");
        return sb.toString();
    }
}