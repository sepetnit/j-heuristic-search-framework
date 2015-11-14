package org.cs4j.core.generators;

import org.cs4j.core.collections.PairInt;
import org.cs4j.core.domains.GridPathFinding;
import org.cs4j.core.domains.Utils;

import java.io.*;
import java.util.HashSet;
import java.util.Set;


/**
 * Generates grids
 *
 */
public class GridPathFindingGenerator extends GeneralInstancesGenerator {

    private static final int MIN_START_GOAL_MANHATTAN_DISTANCE = 5000;
    private static final int MAX_TRIES_TO_SINGLE_INSTANCE = 100;

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
         * The constructor of the class - constructs a Map with a pre-defined width and height
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
     * Reads and initializes map from the given the (pre-)initialized buffered reader
     *
     * @param in     The reader to read from
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


    public static void main(String args[]) throws IOException {
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
}
