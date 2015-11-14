package org.cs4j.core.generators;

import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchResult;
import org.cs4j.core.algorithms.WAStar;
import org.cs4j.core.collections.PairInt;
import org.cs4j.core.domains.GridPathFinding;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Created by sepetnit on 11/12/2015.
 *
 */
public class PivotsCalculator {

    private static final double NO_SOLUTION = -2.0d;

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

        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < this.mapSize; ++i) {
                if (this.map[i] == 0) {
                    sb.append('.');
                } else {
                    sb.append(this.map[i]);
                }
                if (i % this.mapWidth == 0) {
                    sb.append("\n");
                }
            }
            return sb.toString();
        }

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
         * A constructor of the class
         *
         * @param mapWidth Width of the map
         * @param mapHeight Height of the map
         *
         * @param grid The grid to initialize from
         */
        private GridMap(int mapWidth, int mapHeight, char[] grid) {
            this(mapWidth, mapHeight);
            assert mapWidth * mapHeight == grid.length;
            System.arraycopy(grid, 0, this.map, 0, grid.length);
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
         * Creates a Pair object with the dimensions of the given location
         *
         * @param location The required location
         * @return The calculated Pair
         */
        private PairInt getPosition(int location) {
            return new PairInt(location % this.mapWidth, location / this.mapWidth);
        }

        @Override
        public boolean equals(Object other) {
            try {
                GridMap otherGridMap = (GridMap)other;
                return ((this.mapWidth == otherGridMap.mapWidth) &&
                        (this.mapHeight == otherGridMap.mapHeight) &&
                        (this.mapSize == otherGridMap.mapSize) &&
                        Arrays.equals(this.map, otherGridMap.map));
            } catch (ClassCastException e) {
                return false;
            }
        }
    }

    /**
     * Chooses the first pivot by taking a random location that is not blocked
     *
     * @param grid The grid from which the pivot should be chosen
     * @param rand The random object to use for generating random locations
     *
     * @return The chosen pivot (in a 1-dimensional representation)
     */
    /*
    private int _chooseFirstPivotRandomly(GridMap grid, Random rand) {
        if (rand == null) {
            rand = new Random();
        }
        int location = -1;
        while (location == -1 || grid.isBlocked(location)) {
            location = rand.nextInt(grid.mapSize);
        }
        return location;

    }
    */

    /**
     * Chooses the first pivot by taking the left-most, top-most not-blocked location
     *
     * @return The chosen pivot (in a 1-dimensional representation)
     */
    private int _chooseFirstPivotByMostLeftTop(GridMap grid) {
        for (int i = 0; i < grid.mapSize; ++i) {
            if (!grid.isBlocked(i)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds a returns the minimum cost of reaching goal location from start location (on the given grid)
     * The cost is found by running an optimal-cost search algorithm (e.g. AStar)
     *
     * @param grid The grid to find distances on
     * @param startLocation The start location on the grid
     * @param goalLocation The goal location on the grid
     *
     * @return The found distance
     */
    private double _minDistance(GridMap grid, int startLocation, int goalLocation) {
        SearchAlgorithm alg = new WAStar();
        SearchDomain domain =
                new GridPathFinding(
                        grid.mapWidth,
                        grid.mapHeight,
                        grid.map,
                        startLocation, goalLocation);
        SearchResult result = alg.search(domain);
        if (result.hasSolution()) {
            return result.getSolutions().get(0).getCost();
        }
        return PivotsCalculator.NO_SOLUTION;
    }

    /**
     * Finds whether the given value is inside the given array
     *
     * @param arr The array to look in
     * @param value The value to look for
     *
     * @return Whether value is inside arr
     */
    private boolean contains(int[] arr, int value) {
        for (double current : arr) {
            if (current == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * The functions computes pivots for a given grid
     *
     * @param width The width of the grid
     * @param height The height of the grid
     * @param grid The grid for which pivots should be computed
     * @param pivotsCount The computed pivots count
     *
     * @return The computed pivots count
     */
    public int[] computePivots(int width, int height, char[] grid, int pivotsCount) {
        // Copy the grid, in order to avoid unwanted changes during the process of finding the pivots
        GridMap gridCopy = new GridMap(width, height, grid);

        int pivots[] = new int[pivotsCount];
        // Choose the first pivot
        pivots[0] = this._chooseFirstPivotByMostLeftTop(gridCopy);
        assert pivots[0] != -1;
        System.out.println("[INFO] First pivot is : " + gridCopy.getPosition(pivots[0]));

        // For each pivot to look for
        for (int currentPivotIndex = 1; currentPivotIndex < pivotsCount; ++currentPivotIndex) {
            System.out.println("[INFO] Looking for pivot " + currentPivotIndex);
            double maxSumOfDistances = 0.0d;
            int locationWithMaxSunOfDistances = -1;
            // Go over all the possible locations
            for (int i = 0; i < gridCopy.mapSize; ++i) {
                if (i % 1000 == 0) {
                    System.out.print("\r[INFO] Searched over " + (i + 1) + "/" + gridCopy.mapSize +
                            " locations");
                }
                if (this.contains(pivots, i)) {
                    continue;
                }
                double currentSumOfDistances = 0.0d;
                for (int k = 0; k < pivotsCount - currentPivotIndex; ++k) {
                    double currentValue = 0;
                    if (i != pivots[k] && !gridCopy.isBlocked(i)) {
                        currentValue = this._minDistance(gridCopy, pivots[k], i);
                    }
                    // Here, we of course have the value of distances[pivots[k]][i] correctly set
                    if (currentValue > 0) {
                        currentSumOfDistances += currentValue;
                    }
                }
                if (currentSumOfDistances > maxSumOfDistances) {
                    maxSumOfDistances = currentSumOfDistances;
                    locationWithMaxSunOfDistances = i;
                }
            }
            System.out.println();
            // Location was found
            assert locationWithMaxSunOfDistances != -1;
            pivots[currentPivotIndex] = locationWithMaxSunOfDistances;
            System.out.println("[INFO] Pivot " + currentPivotIndex + " is : " +
                    gridCopy.getPosition(pivots[currentPivotIndex]));
            //gridCopy.map[pivots[currentPivotIndex]] = 'Y';
        }
        //System.out.println(gridCopy.toString());
        return pivots;
    }



    public static void main(String[] args) {
        int pivotsCount = 10;

        String[] mapFiles =
                new String[]{
                        //"input/gridpathfinding/raw/mine/test.map",
                        "input/gridpathfinding/raw/maps/brc202d.map",
                        "input/gridpathfinding/raw/maps/den400d.map",
                        "input/gridpathfinding/raw/maps/ost003d.map"
                };
        PivotsCalculator calculator = new PivotsCalculator();
        for (String mapFile : mapFiles) {
            // Read the map file
            if (!(new File(mapFile).exists())) {
                System.err.println("[ERROR] Map file " + mapFile + " doesn't exist");
                System.exit(-1);
            }
            // Create a fake GridPathFinding problem (just for reading the map)
            try {
                InputStream is = new FileInputStream(new File(mapFile));
                System.out.println("INFO] Computing " + pivotsCount + " pivots for " + mapFile);
                // Create a fake map
                GridPathFinding gridPathFindingProblem = new GridPathFinding(is, 0, 0);
                int[] pivots =
                        calculator.computePivots(
                                gridPathFindingProblem.getGridWidth(), gridPathFindingProblem.getGridHeight(),
                                gridPathFindingProblem.getGridMap(),
                                pivotsCount);
                System.out.println("[INFO] All pivots found: " + Arrays.toString(pivots));
            } catch (IOException e) {
                System.err.println("[ERROR] For " + mapFile + " " + e.getMessage());
                // continue to next problem
            }
        }
    }
}
