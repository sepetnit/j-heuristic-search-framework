package org.cs4j.core.generators;

import javafx.util.Pair;
import org.cs4j.core.collections.PairInt;
import org.cs4j.core.domains.GridPathFinding;
import org.cs4j.core.domains.Utils;

import java.io.*;
import java.util.*;


/**
 * Generates grids
 *
 */
public class GridPathFindingGenerator extends GeneralInstancesGenerator {

    private static final int MIN_START_GOAL_MANHATTAN_DISTANCE = 5000;
    private static final int MAX_TRIES_TO_SINGLE_INSTANCE = 100;

    private final Set<Character> OBSTACLE_CHARACTERS;

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

        /**
         * Counts the obstacles on the map
         *
         * @return The number of obstacles
         */
        public int countObstacles() {
            int toReturn = 0;
            for (int i = 0; i < this.mapSize; ++i) {
                if (GridPathFindingGenerator.this.OBSTACLE_CHARACTERS.contains(this.map[i])) {
                    ++toReturn;
                }
            }
            return toReturn;
        }

        /**
         * The constructor of the class - constructs a GridMap with a pre-defined width and height
         *
         * @param mapWidth  The width of the map
         * @param mapHeight The height of the map
         */
        private GridMap(int mapWidth, int mapHeight) {
            this.mapWidth = mapWidth;
            this.mapHeight = mapHeight;
            // The total size of the map
            this.mapSize = this.mapWidth * this.mapHeight;
            // The locations of the map : (mapWidth * mapHeight)
            this.map = new char[this.mapSize];
        }

        /**
         * A constructor of the class - constructs a GridMap with a pre-defined width and height +
         * counts obstacles (if required)
         *
         * @param mapWidth       The width of the map
         * @param mapHeight      The height of the map
         * @param countObstacles Whether to count the obstacles
         */
        private GridMap(int mapWidth, int mapHeight, boolean countObstacles) {
            this(mapWidth, mapHeight);
            if (countObstacles) {
                this.countObstacles();
            }
        }

        /**
         * Make the blocked state of the some location to be the given value : influences on whether an agent can
         * be placed on this location
         *
         * @param location The location to block/unblock
         * @param value    The value to set
         */
        private void setBlocked(int location, boolean value) {
            if (value) {
                this.map[location] = '#';
            } else {
                this.map[location] = 0;
            }
        }


        /**
         * Make some location to tbe 'blocked': The agent can't be placed at this location
         *
         * @param location The location to block
         */
        private void setBlocked(int location) {
            this.setBlocked(location, true);
        }

        /**
         * Whether the queried location is blocked
         *
         * @param location The location to check
         * @return True if the location is blocked and False otherwise
         */
        private boolean isBlocked(int location) {
            return this.map[location] == '#' ||
                    this.map[location] == 'T' ||
                    this.map[location] == GridPathFinding.OBSTACLE_MARKER;
        }

        /**
         * Whether the queried location is blocked
         *
         * @param location The location to check
         * @return True if the location is blocked and False otherwise
         */
        private boolean isBlocked(PairInt location) {
            return this.isBlocked(this.getLocationIndex(location));
        }

        /**
         * Calculate the index of the location in a one-dimensional array
         *
         * @param x The horizontal location
         * @param y The vertical location
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
         * @return The calculated index
         */
        private int getLocationIndex(PairInt location) {
            return this.getLocationIndex(location.first, location.second);
        }

        /**
         * Dumps the map to a String
         *
         * @return A string representation of the map
         */
        private String _dumpMap() {
            StringBuilder sb = new StringBuilder();
            // Now, dump the Map with the location of the agent and the goals
            for (int y = 0; y < this.mapHeight; ++y, sb.append('\n')) {
                // Horizontal
                for (int x = 0; x < this.mapWidth; ++x) {
                    // Get the index of the current location
                    int locationIndex = this.getLocationIndex(x, y);
                    // Check if the location contains an obstacle
                    if (this.isBlocked(locationIndex)) {
                        sb.append(GridPathFinding.OBSTACLE_MARKER);
                    } else {
                        sb.append('.');
                    }
                }
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            // The returned representation of the Map is according to the Moving AI Lab
            String result = "";
            result += "type octile";
            result += "\n";
            result += "height ";
            result += this.mapHeight;
            result += "\n";
            result += "width ";
            result += this.mapWidth;
            result += "\n";
            result += "map";
            result += "\n";
            result += this._dumpMap();
            return result;
        }
    }

    /**
     * Reads a value of some field from the given reader
     *
     * @param in        The reader to read from
     * @param fieldName The name of the field to check
     * @return The read value
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
     * Reads and initializes map from the given the (pre-)initialized buffered reader
     *
     * @param in The reader to read from
     * @throws IOException In case the read operation failed
     */
    public GridMap readMap(BufferedReader in) throws IOException {
        // Create the map
        // First, read the first line (should be ignored)
        String sz[] = in.readLine().trim().split(" ");
        assert sz.length == 2 && sz[0].equals("type");
        // Now, read the height of the map
        int height = this._readSingleIntValueFromLine(in, "height");
        // Now, read the height of the map
        int width = this._readSingleIntValueFromLine(in, "width");
        sz = in.readLine().trim().split(" ");
        assert sz.length == 1 && sz[0].equals("map");
        GridMap map = new GridMap(width, height);
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
                        map.setBlocked(map.getLocationIndex(x, y));
                        break;
                        // Empty location
                    }
                    case '.':
                    case '_':
                    case ' ': {
                        break;
                        // End of line
                    }
                    case '\n': {
                        assert x == chars.length;
                        break;
                        // Something strange
                    }
                    default: {
                        Utils.fatal("Unknown character: " + c);
                    }
                }
            }
        }
        return map;
    }

    /**
     * Takes an initialized GridMap and decreases its obstacles count to the required number
     *
     * @param map                    The GridMao to adapt
     * @param obstaclesCount         The current number of obstacles on the grid
     * @param requiredObstaclesCount The required number of obstacles on the grid
     * @param rand                   [OPTIONAL] A random generator to use (if null - will be created internally)
     */
    public void fitObstaclesRandomly(GridMap map, int obstaclesCount, int requiredObstaclesCount, Random rand) {
        if (rand == null) {
            rand = new Random();
        }
        while (requiredObstaclesCount < obstaclesCount) {
            while (true) {
                int randomLocation = rand.nextInt(map.mapSize);
                // We need a blocked location!
                if (map.isBlocked(randomLocation)) {
                    map.setBlocked(randomLocation, false);
                    // Decrease the actual number of obstacles
                    --obstaclesCount;
                    break;
                }
            }
        }

    }

    private PairInt _generateLocationOnMap(GridMap map, Set<PairInt> existingLocations) {
        PairInt toReturn = null;
        while (toReturn == null ||
                existingLocations.contains(toReturn) ||
                map.isBlocked(toReturn)) {
            int x = this.rand.nextInt(map.mapWidth);
            int y = this.rand.nextInt(map.mapHeight);
            toReturn = new PairInt(x, y);
        }
        return toReturn;
    }

    public String generateInstance(String path, GridMap map) {
        int triesNumber = 0;

        StringBuilder sb = new StringBuilder();
        sb.append("map: ");
        sb.append(path);
        this._appendNewLine(sb);

        int minDistance = Math.min(
                (int) ((2 / 3.0) * Math.max(map.mapWidth, map.mapHeight)),
                MIN_START_GOAL_MANHATTAN_DISTANCE);
        Set<PairInt> existing = new HashSet<>();

        PairInt start = this._generateLocationOnMap(map, existing);
        existing.add(start);

        sb.append("start: ");
        sb.append(start.toStringNoParen());
        this._appendNewLine(sb);

        PairInt goal;
        while (true) {
            goal = this._generateLocationOnMap(map, existing);
            if (Utils.calcManhattanDistance(start, goal) < minDistance) {
                ++triesNumber;
            } else {
                existing.add(goal);
                break;
            }
        }
        if (triesNumber == GridPathFindingGenerator.MAX_TRIES_TO_SINGLE_INSTANCE) {
            System.err.println("[ERROR] Max Tries reached for instance");
            return null;
        }
        sb.append("goals: ");
        sb.append(goal.toStringNoParen());
        this._appendNewLine(sb);

        return sb.toString();
    }

    public GridPathFindingGenerator() {
        this.OBSTACLE_CHARACTERS = new HashSet<>();
        this.OBSTACLE_CHARACTERS.add('@');
        this.OBSTACLE_CHARACTERS.add('#');
    }

    /**
     * A main function that takes raw maps from the Moving AI lab and tunes their obstacles count by randomly removing
     * obstacles, such that the percentages of obstacles are some constant value
     *
     * @throws IOException If something wrong occurred
     */
    public static void mainGenerateMazesFromExistingMazesWithObstaclesTuning() throws IOException {
        Random rand = new Random();

        String[] mapFiles =
                new String[]{
                        "input/gridpathfinding/raw/mazes/maze1/maze512-1-6-100.map",
                        //"input/gridpathfinding/raw/mazes/maze512-2-2.map",
                        //"input/gridpathfinding/raw/mazes/maze512-8-6.map",
                        //"input/gridpathfinding/raw/mazes/maze512-32-8.map",
                };
        double obstaclesPercentages[] = new double[]{98};

        GridPathFindingGenerator generator = new GridPathFindingGenerator();

        for (String mapFile : mapFiles) {
            for (double obstaclesPercentage : obstaclesPercentages) {

                // Read the map file
                if (!(new File(mapFile).exists())) {
                    System.out.println("[WARNING] Map file " + mapFile + " doesn't exist");
                    continue;
                }

                // Read the map
                GridMap map = generator.readMap(new BufferedReader(new InputStreamReader(new FileInputStream(mapFile))));

                int obstaclesCount = map.countObstacles();
                int requiredObstaclesCount = (int) Math.ceil((obstaclesCount * obstaclesPercentage) / 100.0d);
                if (requiredObstaclesCount > obstaclesCount) {
                    System.out.println("[ERROR] Too many obstacles required (" + requiredObstaclesCount +
                            " but only " + obstaclesCount + " exist)");
                    throw new IOException();
                } else if (requiredObstaclesCount < obstaclesCount) {
                    System.out.println("[INFO] Working on map " + mapFile + "(decreases from " + obstaclesCount +
                            " to " + requiredObstaclesCount + " obstacles)");
                    generator.fitObstaclesRandomly(map, obstaclesCount, requiredObstaclesCount, rand);
                    // Now we have the map ready
                    String outputFileName = mapFile.replace(".map", "") + "-" + (int) obstaclesPercentage + ".map";
                    FileWriter fw = new FileWriter(new File(outputFileName));
                    fw.write(map.toString());
                    fw.close();
                    System.out.println("[INFO] Finished working on map " + mapFile);
                } else {
                    // Here - the map already fits the required number of obstacles
                    System.out.println("[INFO] Map " + mapFile + " already fits the required percentage of obstacles (" +
                            obstaclesPercentage + ")");
                }
            }
        }
    }

    /**
     * Creates a string representation of a pre-defined instance
     *
     * @param path   The path of the map file
     * @param startX The start X location
     * @param startY The start Y location
     * @param goalX  The goal X location
     * @param goalY  The goal Y location
     * @return The created instance
     */
    public static String _stringifyInstance(String path, String startX, String startY, String goalX, String goalY) {
        StringBuilder sb = new StringBuilder();
        sb.append("map: ");
        sb.append(path);
        sb.append("\n");
        sb.append("start: ");
        sb.append(startX);
        sb.append(",");
        sb.append(startY);
        sb.append("\n");
        sb.append("goals: ");
        sb.append(goalX);
        sb.append(",");
        sb.append(goalY);
        sb.append("\n");
        return sb.toString();
    }

    /**
     * A main function that generates GridPathFinding instances by reading them from .scen files (of the Moving AI)
     *
     * @throws IOException If something wrong occurred
     */
    public static void mainGenerateInstancesFromMovingAISceneFiles() throws IOException {
        Random rand = new Random();
        Map<String, String> inputOutput = new HashMap<>();
        inputOutput.put("input/gridpathfinding/moving-ai/maze512-1-6.map.scen",
                "input/gridpathfinding/generated/maze512-1-6");
        inputOutput.put("input/gridpathfinding/moving-ai/maze512-2-2.map.scen",
                "input/gridpathfinding/generated/maze512-2-2");
        inputOutput.put("input/gridpathfinding/moving-ai/maze512-8-6.map.scen",
                "input/gridpathfinding/generated/maze512-8-6");
        inputOutput.put("input/gridpathfinding/moving-ai/maze512-32-8.map.scen",
                "input/gridpathfinding/generated/maze512-32-8");

        // The number of instances to generate from each map
        int instancesCount = 100;

        for (Map.Entry<String, String> sceneAndOutputDir : inputOutput.entrySet()) {
            // Get the scene file
            String sceneFile = sceneAndOutputDir.getKey();
            if (!(new File(sceneFile).exists())) {
                System.out.println("[WARNING] Scene file " + sceneFile + " doesn't exist");
                continue;
            }
            // Get the output directory
            String outputDir = sceneAndOutputDir.getValue();
            // Read the output directory
            File outputDirectory = new File(outputDir);
            if (!outputDirectory.isDirectory()) {
                System.out.println("[WARNING] Invalid directory: " + outputDir);
                continue;
            }
            System.out.println("[INFO] Generating instances for " + sceneFile);
            System.out.println("[INFO] Instances for " + sceneFile + " will be written to " + outputDir);
            // Read the whole file
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(sceneFile)));
            List<String[]> instances = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("version")) {
                    continue;
                }
                String[] split = line.trim().split("\t");
                //                              0     1     2      3       4        5         6      7      8
                // According to the format: [Bucket, Map, Width, Height, Start-X, Start-Y, Goal-X, Goal-Y, Opt]
                assert split.length == 9;
                // Add the map, start and goal locations and the optimal length
                //                           0          1         2         3         4         5
                instances.add(new String[]{split[1], split[4], split[5], split[6], split[7], split[8]});
            }
            if (instances.size() < instancesCount) {
                System.out.println("[WARNING] Insufficient number of instances (" + instances.size() +
                        " but required " + instancesCount + ")");
                continue;
            }
            // This list will store the optimal lengths of the solutions to the problems
            List<Pair<Integer, String>> optimal = new ArrayList<>();
            // Loop over the required number of instances and take randomly
            for (int j = 0; j < instancesCount; ++j) {
                int problemNumber = j + 1;
                System.out.println("[INFO] Generating instance # " + problemNumber + " ...");
                int instanceID = rand.nextInt(instances.size());
                String[] randomInstance = instances.get(instanceID);
                // Remove the instance from the list
                instances.remove(randomInstance);
                FileWriter fw = new FileWriter(new File(outputDirectory, problemNumber + ".in"));
                String instance = GridPathFindingGenerator._stringifyInstance(
                        // Map path
                        randomInstance[0],
                        // Start X
                        randomInstance[1],
                        // Start Y
                        randomInstance[2],
                        // Goal X
                        randomInstance[3],
                        // Goal Y
                        randomInstance[4]);
                fw.write(instance);
                fw.close();
                optimal.add(new Pair<>(j + 1, randomInstance[5]));
            }
            // Now, let's write the optimal solutions to a file
            FileWriter fw = new FileWriter(new File(outputDirectory, "optimal"));
            for (Pair<Integer, String> current : optimal) {
                fw.write(current.getKey() + "");
                fw.write(",");
                fw.write(current.getValue());
                fw.write("\n");
            }
            fw.close();
        }
    }


    public static void mainGenerateInstaceFromPreparedMap(String args[]) throws IOException {
        String[] outputPaths =
                new String[]{
                        "input/gridpathfinding/generated/brc202d.map",
                        "input/gridpathfinding/generated/den400d.map",
                        "input/gridpathfinding/generated/ost003d.map"
                };
        String[] mapFiles =
                new String[]{
                        "input/gridpathfinding/raw/maps/brc202d.map",
                        "input/gridpathfinding/raw/maps/den400d.map",
                        "input/gridpathfinding/raw/maps/ost003d.map"
                };

        /*
        if (args.length != 3) {
            System.out.println("Usage: <OutputPath> <MapFile> <Count>");
            System.exit(-1);
        }
        */

        for (int i = 0; i < outputPaths.length; ++i) {
            args[0] = outputPaths[i];
            args[1] = mapFiles[i];
            args[2] = "100";

            // Read the output directory
            File outputDirectory = new File(args[0]);
            if (!outputDirectory.isDirectory()) {
                throw new IOException("Invalid directory: " + args[0]);
            }
            // Read the map file
            String mapFile = args[1];
            if (!(new File(mapFile).exists())) {
                System.out.println("Map file " + mapFile + " doesn't exist");
            }
            // Read required count of instances
            // Required number of instances
            int instancesCount = GridPathFindingGenerator.readIntNumber(args[2], 1, -1, "# of instances");

            GridPathFindingGenerator generator = new GridPathFindingGenerator();

            // Read the map
            GridMap map = generator.readMap(new BufferedReader(new InputStreamReader(new FileInputStream(mapFile))));
            // This set is used in order to avoid duplicates
            Set<String> instances = new HashSet<>();

            // Loop over the required number of instances
            for (int j = 0; j < instancesCount; ++j) {
                int problemNumber = j + 1;
                System.out.println("[INFO] Generating instance # " + problemNumber + " ...");
                FileWriter fw = new FileWriter(new File(outputDirectory, problemNumber + ".in"));
                String instance = null;
                while (instance == null || instances.contains(instance)) {
                    instance = generator.generateInstance(mapFile, map);
                }
                instances.add(instance);
                fw.write(instance);
                fw.close();
                System.out.println(" Done.");
            }
            assert instances.size() == instancesCount;
        }
    }

    public static void main(String[] args) {
        try {
            //GridPathFindingGenerator.mainGenerateInstancesFromMovingAISceneFiles();
            GridPathFindingGenerator.mainGenerateMazesFromExistingMazesWithObstaclesTuning();
        } catch (IOException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }
}
