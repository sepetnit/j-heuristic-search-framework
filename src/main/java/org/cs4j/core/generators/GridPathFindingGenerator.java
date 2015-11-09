package org.cs4j.core.generators;

import org.cs4j.core.collections.GeneralPair;
import org.cs4j.core.collections.PairInt;
import org.cs4j.core.domains.GridPathFinding;
import org.cs4j.core.domains.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;


/**
 * Converts from .map files to data_structures.grids
 */
public class GridPathFindingGenerator extends GeneralInstancesGenerator {

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
        private GridMap(int mapWidth, int mapHeight, int obstaclesCount) {
            this.mapWidth = mapWidth;
            this.mapHeight = mapHeight;
            // The total size of the map
            this.mapSize = this.mapWidth * this.mapHeight;
            // The locations of the map : (mapWidth * mapHeight)
            this.map = new char[this.mapSize];
        }

        /**
         * A constructor of the class that also counts obstacles
         *
         * @param mapWidth  The width of the map
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
         * Creates a Pair object with the dimensions of the given location
         *
         * @param location The required location
         * @return The calculated Pair
         */
        private PairInt getPosition(int location) {
            return new PairInt(location % this.mapWidth, location / this.mapWidth);
        }

    }

    /**
     * Reads and initializes map from the given the (pre-)initialized buffered reader
     *
     * @param width  The width of the map
     * @param height The height of the map
     * @param in     The reader to read from
     * @throws IOException In case the read operation failed
     */
    private GridMap _readMap(int width, int height, BufferedReader in) throws IOException {
        // Create the map
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

    public static void main(String args[]) throws IOException {
        if (args.length != 3) {
            System.out.println("Usage: <OutputPath> <MapFile> <Count>");
            System.exit(-1);
        }
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



    }
}
