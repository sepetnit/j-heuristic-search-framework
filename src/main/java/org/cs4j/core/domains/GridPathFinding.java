package org.cs4j.core.domains;

import org.cs4j.core.SearchDomain;
import org.cs4j.core.collections.PackedElement;
import org.cs4j.core.collections.Pair;
import org.cs4j.core.collections.PairInt;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * Represents some grid (not a full problem!!!, only the grid!!!
 * <p/>
 * Note that the internal grid is represented as an array of integers
 * <p/>
 * <p>
 * Note: The grid is 1-based
 * </p>
 */
public class GridPathFinding implements SearchDomain {

    private static final int NUM_MOVES = 4;

    public static final char OBSTACLE_MARKER = '@';
    public static final char START_MARKER = 'S';
    public static final char GOAL_MARKER = 'G';

    private static final Map<String, Class> GridPathFindingPossibleParameters;

    // Declare the parameters that can be tunes before running the search
    static
    {
        GridPathFindingPossibleParameters = new HashMap<>();
        GridPathFinding.GridPathFindingPossibleParameters.put("heuristic", String.class);
        GridPathFinding.GridPathFindingPossibleParameters.put("pivots-distances-db-file", String.class);
        GridPathFinding.GridPathFindingPossibleParameters.put("pivots-count", Integer.class);
    }

    // The start location of the agent
    private int startX = -1;
    private int startY = -1;

    public enum COST_FUNCTION {
        HEAVY,
        UNIT
    }

    private boolean heavy = false;

    private long agentLocationBitMask;

    private GridMap map;
    private List<Integer> goals;
    private List<PairInt> goalsPairs;

    private enum HeuristicType {
        // Manhattan distance
        MD,
        // TDH with furthest k pivots
        TDH_FURTHEST
    }

    private HeuristicType heuristicType;
    // The number of pivots in case TDH_FURTHEST is used
    private int pivotsCount;
    // Required for the TDH heuristic
    private int[] orderedPivots;
    private Map<Integer, Map<Integer, Double>> distancesFromPivots;

    private GridPathFindingOperator[] reverseOperators;

    /**
     * A specific Move performed on the grid
     */
    private class Move {
        // An identification of the move
        private char id;
        // The x and y deltas between this move and the previous move
        private int dx;
        private int dy;
        // Delta in the GridMap internal data structure
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
     * This class represents a grid on which the agent is moving
     * The grid must be a rectangle (and can contain obstacles)
     */
    private class GridMap {
        private int mapWidth;
        private int mapHeight;
        // Size of the rectangle
        private int mapSize;

        // The internal data of the grid is represented as a character array
        private char map[];

        private Move possibleMoves[];
        private int possibleMovesCount;

        private int obstaclesCount;

        private int _countObstacles() {
            int toReturn = 0;
            for (int i = 0; i < this.mapSize; ++i) {
                if (GridPathFinding.OBSTACLE_MARKER == this.map[i]) {
                    ++toReturn;
                }
            }
            return toReturn;
        }

        /**
         * The constructor of the class - constructs a Map with a pre-defined width and height
         *
         * @param mapWidth The width of the map
         * @param mapHeight The height of the map
         */
        private GridMap(int mapWidth, int mapHeight, int obstaclesCount) {
            if (obstaclesCount == -1) {
                obstaclesCount = this._countObstacles();
            }
            // Obstacles count not given - so let's count it
            this.obstaclesCount = obstaclesCount;
            this.mapWidth = mapWidth;
            this.mapHeight = mapHeight;

            // The total size of the map
            this.mapSize = this.mapWidth * this.mapHeight;
            // The locations of the map : (mapWidth * mapHeight)
            this.map = new char[this.mapSize];
            // All the possible moves (currently, can't move diagonally)
            this.possibleMoves = new Move[4];
            this.possibleMovesCount = this.possibleMoves.length;
            // Initialize all the moves according to the real directions to perform
            this.possibleMoves[0] = new Move(this, 'S',  0,  1);
            this.possibleMoves[1] = new Move(this, 'N',  0, -1);
            this.possibleMoves[2] = new Move(this, 'W', -1,  0);
            this.possibleMoves[3] = new Move(this, 'E',  1,  0);
        }

        /**
         * A constructor of the class that also counts obstacles
         *
         * @param mapWidth The width of the map
         * @param mapHeight The height of the map
         */
        private GridMap(int mapWidth, int mapHeight) {
            this(mapWidth, mapHeight, -1);
        }

        /**
         * Make some location to tbe 'blocked': The agent can't be placed at this location
         *
         * @param location The location to block
         */
        private void setBlocked(int location) {
            this.map[location] = '#';
        }

        /**
         * Whether the queried location is blocked
         *
         * @param location The location to check
         *
         * @return True if the location is blocked and False otherwise
         */
        private boolean isBlocked(int location) {
            return this.map[location] == '#' ||
                    this.map[location] == 'T' ||
                    this.map[location] == GridPathFinding.OBSTACLE_MARKER;
        }

        /**
         * Calculate the index of the location in a one-dimensional array
         *
         * @param x The horizontal location
         * @param y The vertical location
         *
         * @return The calculated index
         */
        private int getLocationIndex(int x, int y) {
            return y * this.mapWidth + x;
        }

        /**
         * Calculate the index of the location in a one-dimensional array, given a pair of indexes
         *
         * @param location A pair whose first part represents the horizontal location and whose second part represents
         *                 the vertical location
         *
         * @return The calculated index
         */
        int getLocationIndex(PairInt location) {
            return this.getLocationIndex(location.first, location.second);
        }

        /**
         * Creates a Pair object with the dimensions of the given location
         *
         * @param location The required location
         * @return The calculated Pair
         */
        private PairInt getPosition(int location) {
            return new PairInt(location % this.mapWidth, location / this.mapWidth);
        }

        /**
         * @return The total number of obstacles on that Grid
         */
        public int getObstaclesCount() {
            return this.obstaclesCount;
        }

        /**
         * @return The probability of a single location to contain an obstacle
         */
        public double getObstaclesProbability() {
            return this.getObstaclesCount() / (double) (this.mapSize);
        }

        /**
         * @return The percentage of obstacles on the map
         */
        public int getObstaclesPercentage() {
            double prob = this.getObstaclesProbability();
            return (int) (prob * 100);
        }
    }

    /**
     * Returns the internal grid data for using in other contexts
     *
     * @return A character array which represents the grid
     */
    public char[] getGridMap() {
        return this.map.map;
    }

    /**
     * @return The width of the grid
     */
    public int getGridWidth() {
        return this.map.mapWidth;
    }

    /**
     * @return The height of the grid
     */
    public int getGridHeight() {
        return this.map.mapHeight;
    }

    /**
     * Initializes the reverse operators array: For each operator, set its reverse operator
     *
     * NOTE: This function is called only once (by the constructor)
     */
    private void _initializeReverseOperatorsArray() {
        // 8 is the maximum count of Vacuum Robot operators
        // (4 for regular moves and more 4 for diagonals)
        this.reverseOperators = new GridPathFindingOperator[8];
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
                this.reverseOperators[i] = new GridPathFindingOperator(j);
                // Count the number of found 'reverse pairs'
                ++reversedMovesCount;
                break;
            }
        }
        // The number of reverse pairs must be equal to the total number of operators
        assert (reversedMovesCount == this.map.possibleMovesCount);
    }

    /**
     * Completes the initialization steps of the domain
     */
    private void _completeInit(boolean log) {

        // MD is used by default
        this.heuristicType = HeuristicType.MD;
        // No need for this
        this.pivotsCount = -1;
        this.orderedPivots = null;
        this.distancesFromPivots = null;

        // Compute bit masks for bit twiddling states in pack/unpack

        // The number of bits required in order to store all the locations of the grid map
        int locationBits = (int) Math.ceil(Utils.log2(this.map.mapSize));
        // The bit-mask required in order to access the locations bit-vector
        this.agentLocationBitMask = Utils.mask(locationBits);

        // Assure there is no overflow : at most 64 bits can be used in order to store the state
        if (locationBits > 64) {
            Utils.fatal("Too many bits required: " + locationBits);
        }
        if (log) {
            System.out.println("[INFO] Initializes reverse operators");
        }
        // Initialize the array of reverse operators
        this._initializeReverseOperatorsArray();
        if (log) {
            System.out.println("[INFO] Finished initializing reverse operators");
        }
    }

    /**
     * This constructor is used in order to generate a simple instance of the domain - with a single agent and a
     * single goal
     *
     * The constructor is used by some generators of instances, which want to check that the generated instance is
     * valid
     *
     * Note: Either start1Dim or start can be given, and also, either goal1Dim or goal can be given
     *
     * @param width The width of the grid
     * @param height The height of the grid
     * @param map The grid itself (with obstacles filled)
     * @param start1Dim Start position (1-dimensional)
     * @param start The start position on the grid
     * @param goal1Dim Goal position (1-dimensional)
     * @param goal The SINGLE goal on the grid
     */
    private GridPathFinding(int width, int height, char[] map,
                            int start1Dim, PairInt start,
                            int goal1Dim, PairInt goal) {
        // Either 1-dimensional or 2-dimensional input can be given for start and goal locations
        assert (((start1Dim == -1) ^ (start == null)) && ((goal1Dim == -1) ^ (start == null)));

        this.heavy = false;
        this.map = new GridMap(width, height);
        // Set the map explicitly
        this.map.map = map;
        if (start1Dim != -1) {
            start = this.map.getPosition(start1Dim);
        }
        this.startX = start.first;
        this.startY = start.second;
        this.goals = new ArrayList<>();
        if (goal1Dim != -1) {
            goal = this.map.getPosition(goal1Dim);
        } else {
            goal1Dim = this.map.getLocationIndex(goal);
        }
        this.goals.add(goal1Dim);
        this.goalsPairs = new ArrayList<>();
        this.goalsPairs.add(goal);
        // System.out.println("[INFO] Start: " + start.toString());
        // System.out.println("[INFO] Goal: " + goal.toString());
        // Now, complete the initialization by initializing other parameters
        this._completeInit(false);
    }

    /**
     * This constructor is used in order to generate a simple instance of the domain - with a single agent and a
     * single goal - start and goal positions are given in 1-dimensional format
     *
     * The constructor is used by some generators of instances, which want to check that the generated instance is
     * valid
     *
     * @param width The width of the grid
     * @param height The height of the grid
     * @param map The grid itself (with obstacles filled)
     * @param start The start position on the grid (in a 1-dimensional format)
     * @param goal The SINGLE goal on the grid (in a 1-dimensional format)
     */
    public GridPathFinding(int width, int height, char[] map, PairInt start, PairInt goal) {
        this(width, height, map, -1, start, -1, goal);
    }

    /**
     * This constructor is used in order to generate a simple instance of the domain - with a single agent and a
     * single goal - start and goal positions are given in 1-dimensional format
     *
     * The constructor is used by some generators of instances, which want to check that the generated instance is
     * valid
     *
     * @param width The width of the grid
     * @param height The height of the grid
     * @param map The grid itself (with obstacles filled)
     * @param start The start position on the grid (in a 1-dimensional format)
     * @param goal The SINGLE goal on the grid (in a 1-dimensional format)
     */
    public GridPathFinding(int width, int height, char[] map, int start, int goal) {
        this(width, height, map, start, null, goal, null);
    }

    /**
     * Reads and initializes map from the given the (pre-)initialized buffered reader
     *
     * @param width The width of the map
     * @param height The height of the map
     * @param in The reader to read from
     *
     * @throws IOException In case the read operation failed
     */
    private void _readMap(int width, int height, BufferedReader in) throws IOException {
        // Create the map
        this.map = new GridMap(width, height);
        // Now, read all the locations
        for (int y = 0; y < height; ++y) {
            String line = in.readLine();
            char[] chars = line.toCharArray();
            int ci = 0;
            // Horizontal
            for (int x = 0; x < width; ++x) {
                char c = chars[ci++];
                switch (c) {
                    // An obstacle
                    case GridPathFinding.OBSTACLE_MARKER:
                    case '#':
                    case 'T': {
                        this.map.setBlocked(this.map.getLocationIndex(x, y));
                        break;
                        // The start location
                    } case GridPathFinding.START_MARKER:
                    case 's':
                    case 'V': {
                        this.startX = x;
                        this.startY = y;
                        break;
                        // The end location
                    } case 'g':
                    case GridPathFinding.GOAL_MARKER: {
                        this.goals.add(this.map.getLocationIndex(x, y));
                        this.goalsPairs.add(new PairInt(x, y));
                        break;
                        // Empty location
                    } case '.':
                    case '_':
                    case ' ': {
                        break;
                        // End of line
                    } case '\n': {
                        assert x == chars.length;
                        break;
                        // Something strange
                    } default: {
                        Utils.fatal("Unknown character: " + c);
                    }
                }
            }
        }
    }

    /**
     * Reads a value of some field from the given reader
     *
     * @param in The reader to read from
     * @param fieldName The name of the field to check
     *
     * @return The read value
     *
     * @throws IOException If something wrong occurred
     */
    private int _readSingleIntValueFromLine(BufferedReader in, String fieldName) throws IOException {
        String[] sz = in.readLine().trim().split(" ");
        if (fieldName != null) {
            assert sz.length == 2 && sz[0].equals(fieldName);
        }
        return Integer.parseInt(sz[1]);
    }

    /**
     * Reads a map of the moving AI format
     *
     * @param mapReader The reader from which the map should be read
     *
     * @throws IOException If something wrong occurred
     */
    private void _readMovingAIMap(BufferedReader mapReader) throws IOException {
        // First, read the first line (should be ignored)
        String sz[] = mapReader.readLine().trim().split(" ");
        assert sz.length == 2 && sz[0].equals("type");
        // Now, read the height of the map
        int height = this._readSingleIntValueFromLine(mapReader, "height");
        // Now, read the height of the map
        int width = this._readSingleIntValueFromLine(mapReader, "width");
        sz = mapReader.readLine().trim().split(" ");
        assert sz.length == 1 && sz[0].equals("map");
        // Now, read the map itself by calling the relevant function
        this._readMap(width, height, mapReader);
    }

    /**
     * The function reads start locations and goal locations from an initialized BufferedReader
     *
     * @param problemReader The reader to read the data from
     *
     * @throws IOException If something wrong occurred
     */
    private void _readStartAndGoalsFromProblemFile(BufferedReader problemReader) throws IOException {
        // Read start location
        String[] sz = problemReader.readLine().trim().split(" ");
        assert sz.length == 2 && sz[0].equals("start:");
        String start[] = sz[1].split(",");
        assert start.length == 2;
        this.startX = Integer.parseInt(start[0]);
        this.startY = Integer.parseInt(start[1]);
        // Read goal locations
        sz = problemReader.readLine().trim().split(" ");
        assert sz.length >= 2 && sz[0].equals("goals:");
        for (int i = 1; i < sz.length; ++i) {
            String goal[] = sz[i].split(",");
            assert goal.length == 2;
            int goalX = Integer.parseInt(goal[0]);
            int goalY = Integer.parseInt(goal[1]);
            this.goals.add(this.map.getLocationIndex(goalX, goalY));
            this.goalsPairs.add(new PairInt(goalX, goalY));
        }
    }

    /**
     * Reads a map file of the following format:
     *
     * <link to map (.map file)
     * [start <start location>]
     * [goal <goal location>]
     *
     * @param mapFilePath A path to a .map file
     * @param in A buffered reader for reading the rest of the file
     */
    private void _initMapFormat2(String mapFilePath, BufferedReader in) throws IOException {
        try {
            BufferedReader mapReader =
                    new BufferedReader(
                            new InputStreamReader(
                                    new FileInputStream(mapFilePath)));
            // Read the map
            this._readMovingAIMap(mapReader);
            System.out.println("[INFO] Map read from " + mapFilePath);
            // Read start and goal locations
            this._readStartAndGoalsFromProblemFile(in);
        } catch (FileNotFoundException e) {
            System.err.println("[ERROR] Can't find reference map file: " + mapFilePath);
            throw new IOException();
        }
    }

    /**
     * The constructor of the general GridPathFinding domain
     *
     * @param stream The input stream for parsing the instance
     * @param costFunction The type of the cost function
     */
    public GridPathFinding(InputStream stream, COST_FUNCTION costFunction) {
        // TODO:
        // this.heavy = (cost == COST.HEAVY);
        // Initialize the input-reader to allow parsing the state
        BufferedReader in = new BufferedReader(new InputStreamReader(stream));
        try {
            this.goals = new ArrayList<>(1);
            this.goalsPairs = new ArrayList<>(1);
            // First, read the size of the grid
            String sz[] = in.readLine().trim().split(" ");
            assert sz.length == 2;
            if (sz[0].equals("map:")) {
                // Read the start and goal locations from the file
                this._initMapFormat2(sz[1], in);
            } else {
                int width = Integer.parseInt(sz[0]);
                int height = Integer.parseInt(sz[1]);
                // Read the map itself
                this._readMap(width, height, in);
            }
            // Assure there is a start location
            if (this.startX < 0 || this.startY < 0) {
                Utils.fatal("No start location");
            }
        } catch(IOException e) {
            e.printStackTrace();
            Utils.fatal("Error reading input file");
        }

        // Now, complete the initialization by initializing other parameters
        this._completeInit(true);
    }

    /**
     * A constructor of the class - start and end are given explicitly here
     * The map is assumed to be in the format of the Moving AI lab:
     *
     * type -type-
     * height -height-
     * width -width-
     * map
     * -map-data-
     *
     * @param stream The input stream for parsing the instance
     * @param start The start location
     * @param goal The goal location
     */
    public GridPathFinding(InputStream stream, int start, int goal) {
        // TODO:
        // this.heavy = (cost == COST.HEAVY);
        // Initialize the input-reader to allow parsing the state
        BufferedReader in = new BufferedReader(new InputStreamReader(stream));
        try {
            // Read the map (without start and goal locations)
            this._readMovingAIMap(in);
            // Read start
            PairInt startPair = this.map.getPosition(start);
            this.startX = startPair.first;
            this.startY = startPair.second;
            // Read goals
            this.goals = new ArrayList<>(1);
            this.goalsPairs = new ArrayList<>(1);
            PairInt goalPair = this.map.getPosition(goal);
            this.goals.add(goal);
            this.goalsPairs.add(goalPair);
            // Assure there is a start location
            if (this.startX < 0 || this.startY < 0) {
                Utils.fatal("No start location");
            }
        } catch(IOException e) {
            e.printStackTrace();
            Utils.fatal("[ERROR] Error reading input file ");
        }

        // Now, complete the initialization by initializing other parameters
        this._completeInit(true);

    }

    /**
     * This constructor initializes a GridPathFinding problem by copying all the parameters from other given problem
     * and initializing start and goal locations from the given input stream
     *
     * @param other The GridPathFinding problem to copy from
     * @param stream The stream to read start and goal locations from
     */
    public GridPathFinding(GridPathFinding other, InputStream stream) {
        // TODO:
        // this.heavy = (cost == COST.HEAVY);
        // Initialize the input-reader to allow parsing the state
        BufferedReader in = new BufferedReader(new InputStreamReader(stream));
        // We need the map in order to read start and goals
        this.map = other.map;
        try {
            this.goals = new ArrayList<>(1);
            this.goalsPairs = new ArrayList<>(1);
            // First, read the size of the grid
            String sz[] = in.readLine().trim().split(" ");
            if (!sz[0].equals("map:")) {
                System.out.println("[ERROR] Copying GridPathFinding problem isn't allowed in this case");
                throw new IOException();
            }
            // Read start and goals locations
            this._readStartAndGoalsFromProblemFile(in);
            // Assure there is a start location
            if (this.startX < 0 || this.startY < 0) {
                Utils.fatal("No start location");
            }
        } catch(IOException e) {
            e.printStackTrace();
            Utils.fatal("Error reading input file");
        }

        this.heavy = other.heavy;
        this.agentLocationBitMask = other.agentLocationBitMask;
        this.reverseOperators = other.reverseOperators;

        this.heuristicType = other.heuristicType;
        this.pivotsCount = other.pivotsCount;
        this.orderedPivots = other.orderedPivots;
        this.distancesFromPivots = other.distancesFromPivots;
    }

    /**
     * The constructor of the general GridPathFinding domain (with the UNIT cost f
     * unction)
     *
     * @param stream The input stream for parsing the instance
     */
    public GridPathFinding(InputStream stream) {
        this(stream, COST_FUNCTION.UNIT);
    }

    /**
     * Compute the heuristic value of a given state
     *
     * @param s The state whose heuristic value should be computed
     * @return The computed value
     */
    private double[] computeHD(GridPathFindingState s) {
        assert this.goalsPairs.size() == 1;
        switch (this.heuristicType) {
            case MD: {
                int md = Utils.calcManhattanDistance(
                        this.map.getPosition(s.agentLocation),
                        // TODO: Deals with a single goal only!
                        this.goalsPairs.get(0));
                return new double[]{md, md};
            } case TDH_FURTHEST: {
                int currentGoal = this.goals.get(0);
                double maxDistance = 0;
                for (int i = 0; i < this.pivotsCount; ++i) {
                    int currentPivot = this.orderedPivots[i];
                    double distanceFromAgentToPivot = this.distancesFromPivots.get(currentPivot).get(s.agentLocation);
                    if (distanceFromAgentToPivot <= 0) {
                        continue;
                    }
                    double distanceFromPivotToGoal = this.distancesFromPivots.get(currentPivot).get(currentGoal);
                    if (distanceFromPivotToGoal < 0) {
                        continue;
                    }
                    double diff = Math.abs(distanceFromAgentToPivot - distanceFromPivotToGoal);
                    if (diff > maxDistance) {
                        maxDistance = diff;
                    }
                }
                return new double[]{maxDistance, maxDistance};
            }
        }
        return new double[]{0, 0};
    }

    /**
     * A GridPathFinding State
     */
    private final class GridPathFindingState implements State {
        private double h = -1;
        private double d = -1;

        //private double hHat;
        //private double dHat;
        //private double sseD;
        //private double sseH;

        // The location of the agent
        private int agentLocation;
        // The depth of the search
        private int depth;

        // All the possible operators
        private GridPathFindingOperator[] ops = null;

        private GridPathFindingState parent = null;

        /**
         * A default constructor of the class
         */
        private GridPathFindingState() { }

        /**
         * A copy constructor
         *
         * @param state The state to copy
         */
        private GridPathFindingState(GridPathFindingState state) {
            this.h = state.h;
            this.d = state.d;
            this.depth = state.depth;
            // Copy the location of the robot
            this.agentLocation = state.agentLocation;
            // Copy the parent state
            this.parent = state.parent;
        }

        @Override
        public boolean equals(Object obj) {
            try {
                GridPathFindingState o = (GridPathFindingState)obj;
                // Assure the location of the agent is the same
                return this.agentLocation != o.agentLocation;
            } catch (ClassCastException e) {
                return false;
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
            double[] p = GridPathFinding.this.computeHD(this);
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
            return GridPathFinding.this.dumpState(this);
        }

        @Override
        public String dumpStateShort() {
            return GridPathFinding.this.map.getPosition(this.agentLocation).toString();
        }

    }

    /**
     * The function performs a dump of the grid map used in the search and puts the positions of the agent on each
     * of the given state on the map
     *
     * @param states The states array (can be null)
     * @param obstaclesCountArray The obstacles counter (an OUTPUT parameter)
     *
     * @return A string representation of the map (with all agents located on it)
     */
    private String _dumpMap(State states[], int[] obstaclesCountArray) {
        StringBuilder sb = new StringBuilder();
        int obstaclesCount = 0;
        // Now, dump the Map with the location of the agent and the goals
        for (int y = 0; y < this.map.mapHeight; ++y, sb.append('\n')) {
            // Horizontal
            for (int x = 0; x < this.map.mapWidth; ++x) {
                // Get the index of the current location
                int locationIndex = this.map.getLocationIndex(x, y);
                // Check if the location contains an obstacle
                if (this.map.isBlocked(locationIndex)) {
                    sb.append(GridPathFinding.OBSTACLE_MARKER);
                    ++obstaclesCount;
                } else {
                    boolean agentLocation = false;
                    if (states != null) {
                        // Check if the robot is at this location
                        for (int k = 0; k < states.length; ++k) {
                            if (((GridPathFindingState)states[k]).agentLocation == locationIndex) {
                                if (k == 0) {
                                    sb.append(GridPathFinding.START_MARKER);
                                } else if (k == states.length - 1) {
                                    sb.append(GridPathFinding.GOAL_MARKER);
                                } else {
                                    sb.append('X');
                                }
                                agentLocation = true;
                                break;
                            }
                        }
                    }
                    if (!agentLocation) {
                        sb.append('.');
                    }
                }
            }
        }
        // Set the output parameter
        if (obstaclesCountArray != null) {
            obstaclesCountArray[0] = obstaclesCount;
        }
        return sb.toString();
    }

    @Override
    public String dumpStatesCollection(State[] states) {
        StringBuilder sb = new StringBuilder();
        // All the data regarding a single state refers to the last state of the collection
        GridPathFindingState lastState = (GridPathFindingState)states[states.length - 1];
        sb.append("********************************\n");
        // h
        sb.append("h: ");
        sb.append(lastState.getH());
        sb.append("\n");
        // d
        sb.append("d: ");
        sb.append(lastState.getD());
        sb.append("\n");

        // Output parameter of the _dumpMap function
        int[] obstaclesCountArray = new int[1];
        sb.append(this._dumpMap(states, obstaclesCountArray));

        // Additional newline
        sb.append('\n');
        PairInt agentLocation = this.map.getPosition(lastState.agentLocation);
        sb.append("Agent location: ");
        sb.append(agentLocation.toString());
        sb.append("\n");
        sb.append("Goals:");
        for (PairInt goal: this.goalsPairs) {
            sb.append(" ");
            sb.append(goal.toString());
        }
        sb.append("\n");
        sb.append("obstacles count: ");
        sb.append(obstaclesCountArray[0]);
        sb.append("\n");
        sb.append("********************************\n\n");
        return sb.toString();
    }

    @Override
    public Map<String, Class> getPossibleParameters() {
        return GridPathFinding.GridPathFindingPossibleParameters;
    }

    /**
     * The function reads the distances from all pivots of the TDH heuristic from the given pivots PDB file
     *
     * The file is assumed to be of the following formats:
     *
     *       <pivots-count>
     *       <pivot-1>
     *       <pivot-2>
     *       ...
     *       <pivot-n>
     *       <all-distances-from-pivot-1>
     *       <all-distances-from-pivot-2>
     *       ...
     *       <all-distances-from-pivot-n>
     *
     * @param pivotsPDBFile The input file which contains the pivots
     *
     * @return The created map of pivots : pivot => map : location => distance
     *
     * @throws IOException In something wrong occurred
     */
    private Pair<int[], Map<Integer, Map<Integer, Double>>> _readPivotsDB(String pivotsPDBFile) throws IOException {
        System.out.println("[INFO] Reading pivots DB from " + pivotsPDBFile);
        DataInputStream inputStream = new DataInputStream(new FileInputStream(pivotsPDBFile));
        // First, read count of pivots
        int pivotsCount = inputStream.readInt();
        // Next read the pivots
        int[] pivots = new int[pivotsCount];
        for (int i = 0; i < pivotsCount; ++i) {
            pivots[i] = inputStream.readInt();
        }
        Map<Integer, Map<Integer, Double>> distancesMap = new HashMap<>();
        // Finally, read the distances
        for (int pivot : pivots) {
            Map<Integer, Double> currentDistancesMap = new HashMap<>();
            for (int i = 0; i < this.map.mapSize; ++i) {
                currentDistancesMap.put(i, inputStream.readDouble());
            }
            distancesMap.put(pivot, currentDistancesMap);
        }
        System.out.println("[INFO] Finished reading pivots DB from " + pivotsPDBFile);
        return new Pair<>(pivots, distancesMap);
    }

    @Override
    public void setAdditionalParameter(String parameterName, String value) {
        switch (parameterName) {
            case "heuristic": {
                switch (value) {
                    case "tdh-furthest": {
                        this.heuristicType = HeuristicType.TDH_FURTHEST;
                        break;
                    }
                    case "md": {
                        this.heuristicType = HeuristicType.MD;
                        break;
                    }
                    default: {
                        System.err.println("Illegal heuristic type for GridPathfinding domain: " + value);
                        throw new IllegalArgumentException();
                    }
                }
                break;
            }
            case "pivots-distances-db-file": {
                try {
                    Pair<int[], Map<Integer, Map<Integer, Double>>> readData = this._readPivotsDB(value);
                    this.orderedPivots = readData.getKey();
                    this.distancesFromPivots = readData.getValue();
                    // Debug:
                    //for (int p : this.orderedPivots) {
                    //    String formattedP = String.format("%7d", p);
                    //    System.out.println("Pivot: " + formattedP + " - " + this.map.getPosition(p));
                    //}
                } catch (IOException e) {
                    System.out.println("[ERROR] Reading pivots failed" +
                            (e.getMessage() != null ? " : " + e.getMessage() : ""));
                    throw new IllegalArgumentException();
                }
                break;
            } case "pivots-count": {
                if (this.heuristicType != HeuristicType.TDH_FURTHEST) {
                    System.out.println("[ERROR] Heuristic type isn't TDH - can't set pivots count");
                    throw new IllegalArgumentException();
                } else if (this.orderedPivots == null) {
                    System.out.println("[ERROR] Please specify pivots file");
                    throw new IllegalArgumentException();
                } else {
                    int pivotsCount = Integer.parseInt(value);
                    if (pivotsCount > this.orderedPivots.length) {
                        System.out.println("[ERROR] Insufficient pivots number (currently " +
                                this.orderedPivots.length + " but required " + pivotsCount + ")");
                        throw new IllegalArgumentException();
                    }
                    this.pivotsCount = pivotsCount;
                }
                break;
            } default: {
                System.out.println("No such parameter: " + parameterName + " (value: " + value + ")");
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * An auxiliary function for dumping a single state of the GridPathFinding domain instance
     *
     * @param state The state to dump
     *
     * @return A string representation of the state
     */
    private String dumpState(GridPathFindingState state) {
        return this.dumpStatesCollection(new GridPathFindingState[]{state});
    }

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

        // (next = y * this.mapWidth + x)

        // Assure the move doesn't cause the state to exceed the grid and also that the move
        // doesn't cause the state to reach a blocked location

        // Moving West/East && y changed => invalid!
        if (move.dx != 0 &&
                (next / this.map.mapWidth != location / this.map.mapWidth)) {
            return false;
            // Moving South/North && x changed => invalid
        } else if (move.dy != 0 &&
                (next % this.map.mapWidth != location % this.map.mapWidth)) {
            return false;
        }

        return (next > 0 && next < this.map.mapSize && !this.map.isBlocked(next));
    }

    /**
     * Checks settings of the domain and returns true if all is Ok and false otherwise
     *
     * @return Whether the domain settings are Ok
     */
    private boolean _checkSettings() {
        return this.heuristicType != HeuristicType.TDH_FURTHEST || this.pivotsCount >= 1;
    }

    @Override
    public GridPathFindingState initialState() {
        assert this.startX != -1 && this.startY != -1;
        // Assert settings are ok
        assert this._checkSettings();
        GridPathFindingState state = new GridPathFindingState();
        state.agentLocation = this.map.getLocationIndex(this.startX, this.startY);
        // Compute the initial mapHeight and d values and fill the state with that values
        double hd[] = this.computeHD(state);
        state.h = hd[0];
        state.d = hd[1];
        // System.out.println(this.dumpState(state));
        // Return the created state
        return state;
    }

    @Override
    public boolean isGoal(State state) {
        GridPathFindingState grs = (GridPathFindingState)state;
        return this.goals.contains(grs.agentLocation);
    }

    /**
     * Init the possible operators for the given state
     *
     * @param state The state whose operators should be initialized
     */
    private void _initOps(GridPathFindingState state) {
        // An empty vector of operators
        Vector<GridPathFindingOperator> possibleOperators = new Vector<>();
        // Go ovr all the possible moves
        for (int i = 0; i < GridPathFinding.NUM_MOVES; ++i) {
            if (this._isValidMove(state.agentLocation, this.map.possibleMoves[i])) {
                possibleOperators.add(new GridPathFindingOperator(i));
            }
        }
        // Finally, create the possible operators array
        state.ops = possibleOperators.toArray(new GridPathFindingOperator[possibleOperators.size()]);
    }

    @Override
    public int getNumOperators(State state) {
        GridPathFindingState grs = (GridPathFindingState) state;
        if (grs.ops == null) {
            this._initOps(grs);
        }
        return grs.ops.length;
    }

    @Override
    public Operator getOperator(State state, int index) {
        GridPathFindingState grs = (GridPathFindingState)state;
        if (grs.ops == null) {
            this._initOps(grs);
        }
        return grs.ops[index];
    }

    @Override
    public State copy(State state) {
        return new GridPathFindingState((GridPathFindingState)state);
    }

    /**
     * Packs a state into a long number
     *
     * The packed state is a 64 bit (long) number which stores (currently) the location of the
     * agent
     */
    @Override
    public PackedElement pack(State s) {
        GridPathFindingState state = (GridPathFindingState)s;
        long packed = 0L;
        // pack the location of the robot
        packed |= state.agentLocation & this.agentLocationBitMask;
        /*
         * VacuumRobotState test = unpack(packed);
         * assert(test.equals(state));
         */
        return new PackedElement(packed);
    }

    /**
     * An auxiliary function for unpacking Vacuum Robot state from a long number.
     * This function performs the actual unpacking
     *
     * @param packed The packed state
     * @param dst The destination state which is filled from the unpacked value
     */
    private void _unpackLite(long packed, GridPathFindingState dst) {
        dst.ops = null;
        // Finally, unpack the location of the robot
        dst.agentLocation = (int) (packed & this.agentLocationBitMask);
    }

    /**
     * An auxiliary function for unpacking Vacuum Robot state from a long number
     *
     * @param packed The packed state
     * @param dst The destination state which is filled from the unpacked value
     */
    private void unpack(long packed, GridPathFindingState dst) {
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
    public GridPathFindingState unpack(PackedElement packed) {
        assert packed.getLongsCount() == 1;
        GridPathFindingState dst = new GridPathFindingState();
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
        GridPathFindingState s = (GridPathFindingState)state;
        GridPathFindingState grs = (GridPathFindingState)copy(s);
        GridPathFindingOperator o = (GridPathFindingOperator)op;

        grs.ops = null; // reset operators

        // Assure the type of the operator is actually a move
        if (o.type < 0 || o.type > 3) {
            System.err.println("Unknown operator type " + o.type);
            System.exit(1);
        }
        // Update the location of the robot
        grs.agentLocation += this.map.possibleMoves[o.type].delta;

        grs.depth++;

        double p[] = this.computeHD(grs);
        grs.h = p[0];
        grs.d = p[1];
        grs.parent = s;

        //dumpState(s);
        //dumpState(vrs);
        return grs;
    }

    private final class GridPathFindingOperator implements Operator {
        // UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3
        public static final int NOP = -1;
        // Initially, the type of the operator is NOP
        private int type = GridPathFindingOperator.NOP;

        /**
         * The constructor of the class: initializes an operator with the given type
         *
         * @param type The type of the operator
         */
        private GridPathFindingOperator(int type) {
            this.type = type;
        }

        @Override
        public boolean equals(Object obj) {
            try {
                if (obj == null) {
                    return false;
                }
                GridPathFindingOperator o = (GridPathFindingOperator) obj;
                return type == o.type;
            } catch (ClassCastException e) {
                return false;
            }
        }

        @Override
        public double getCost(State s, State parent) {
            GridPathFindingState grs = (GridPathFindingState) s;
            double cost = 1.0d;
            // TODO: Heavy???
            return cost;
        }

        /**
         * Finds the reverse operator that applying it will reverse the state caused by this
         * operator
         */
        @Override
        public SearchDomain.Operator reverse(State state) {
            return GridPathFinding.this.reverseOperators[this.type];
        }
    }
}
