package org.cs4j.core.generators;

import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchResult;
import org.cs4j.core.algorithms.WAStar;
import org.cs4j.core.collections.Pair;
import org.cs4j.core.collections.PairInt;
import org.cs4j.core.domains.GridPathFinding;
import org.cs4j.core.domains.Utils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by sepetnit on 11/12/2015.
 *
 */
public class PivotsPDBGenerator {

    private static final double NO_SOLUTION = -2.0d;

    private static double DOUBLE_SIZE_IN_BYTES = 8.0d;
    private static double MB_SIZE_IN_BYTES = 1024.0d * 1024.0d;

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
         * Checks whether the given location is valid for the map (fits inside)
         *
         * @param location The location to check
         *
         * @return True if the location is valid and False otherwise
         */
        private boolean isValidLocation(int location) {
            return location >= 0 && location < this.map.length;
        }

        /**
         * Checks whether the given location is valid for the map (fits inside)
         *
         * @param location The location to check
         * @return True if the location is valid and False otherwise
         */
        private boolean isValidLocation(PairInt location) {
            int locationAsInt = this.getLocationIndex(location);
            return this.isValidLocation(locationAsInt);
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
        return PivotsPDBGenerator.NO_SOLUTION;
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
     * The function computes pivots for a given grid and stores them
     *
     * @param grid The grid for which pivots should be computed
     *
     * @param pivotsCount The computed pivots count
     *
     * @return The computed pivots and distances from them
     */
    private Pair<int[], Map<Integer, Map<Integer, Double>>> _computePivotsAndDistances(GridMap grid, int pivotsCount) {
        int pivots[] = new int[pivotsCount];
        System.out.println("[INFO] The file will be at least " +
                this._getPivotsFileSize(grid.mapSize, pivots.length) + " MB");
        Map<Integer, Map<Integer, Double>> allDistances = new HashMap<>();
        // Choose the first pivot - left-most and top-most free location
        pivots[0] = this._chooseFirstPivotByMostLeftTop(grid);
        assert pivots[0] != -1;
        System.out.println("[INFO] First pivot is : " + grid.getPosition(pivots[0]) + " - " + pivots[0]);
        // For each pivot to look for
        for (int currentPivotIndex = 1; currentPivotIndex < pivotsCount; ++currentPivotIndex) {
            Map<Integer, Double> currentDistances = new HashMap<>();
            System.out.println("[INFO] Looking for pivot " + currentPivotIndex);
            double maxSumOfDistances = 0.0d;
            int locationWithMaxSumOfDistances = -1;
            // Go over all the possible locations
            for (int i = 0; i < grid.mapSize; ++i) {
                // Debug
                if (i % 999 == 0) {
                    System.out.print("\r[INFO] Searched over " + (i + 1) + "/" + grid.mapSize + " locations");
                }
                // i can't be a pivot
                if (this.contains(pivots, i)) {
                    currentDistances.put(i, 0.0d);
                    continue;
                }
                if (grid.isBlocked(i)) {
                    currentDistances.put(i, PivotsPDBGenerator.NO_SOLUTION);
                    continue;
                }
                double currentSumOfDistances = 0.0d;
                for (int k = 0; k < currentPivotIndex; ++k) {
                    double currentValue = 0;
                    if (i != pivots[k]) {
                        if (k < currentPivotIndex - 1) {
                            currentValue = allDistances.get(pivots[k]).get(i);
                        // Otherwise, k == currentPivotIndex - 1
                        } else {
                            currentValue = this._minDistance(grid, pivots[k], i);
                            currentDistances.put(i, currentValue);
                        }
                    } else if (k == currentPivotIndex - 1) {
                        currentDistances.put(i, 0.0d);
                    }
                    // Here, we of course have the value of distances[pivots[k]][i] correctly set
                    if (currentValue > 0) {
                        currentSumOfDistances += currentValue;
                    }
                }
                if (currentSumOfDistances > maxSumOfDistances) {
                    maxSumOfDistances = currentSumOfDistances;
                    locationWithMaxSumOfDistances = i;
                }
            }
            System.out.println();
            // Location was found
            assert locationWithMaxSumOfDistances != -1;
            pivots[currentPivotIndex] = locationWithMaxSumOfDistances;
            System.out.println("[INFO] Pivot " + currentPivotIndex + " is : " +
                    grid.getPosition(pivots[currentPivotIndex]) + " - " + pivots[currentPivotIndex]);
            allDistances.put(pivots[currentPivotIndex - 1], currentDistances);
            //gridCopy.map[pivots[currentPivotIndex]] = 'Y';
        }
        //System.out.println(gridCopy.toString());
        return new Pair<>(pivots, allDistances);
    }

    /**
     * The functions computes pivots for a given grid
     *
     * @param grid The grid for which pivots should be computed
     *
     * @param pivotsCount The computed pivots count
     *
     * @return The computed pivots count
     */
    private int[] _computePivots(GridMap grid, int pivotsCount) {
        int pivots[] = new int[pivotsCount];
        // Choose the first pivot - left-most and top-most free location
        pivots[0] = this._chooseFirstPivotByMostLeftTop(grid);
        assert pivots[0] != -1;
        System.out.println("[INFO] First pivot is : " + grid.getPosition(pivots[0]));
        // For each pivot to look for
        for (int currentPivotIndex = 1; currentPivotIndex < pivotsCount; ++currentPivotIndex) {
            System.out.println("[INFO] Looking for pivot " + currentPivotIndex);
            double maxSumOfDistances = 0.0d;
            int locationWithMaxSunOfDistances = -1;
            // Go over all the possible locations
            for (int i = 0; i < grid.mapSize; ++i) {
                // Debug
                if (i % 999 == 0) {
                    System.out.print("\r[INFO] Searched over " + (i + 1) + "/" + grid.mapSize +
                            " locations");
                }
                if (this.contains(pivots, i)) {
                    continue;
                }
                if (grid.isBlocked(i)) {
                    continue;
                }
                double currentSumOfDistances = 0.0d;
                for (int k = 0; k < currentPivotIndex; ++k) {
                    double currentValue = 0;
                    if (i != pivots[k]) {
                        currentValue = this._minDistance(grid, pivots[k], i);
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
                    grid.getPosition(pivots[currentPivotIndex]));
            //gridCopy.map[pivots[currentPivotIndex]] = 'Y';
        }
        //System.out.println(gridCopy.toString());
        return pivots;
    }

    /**
     * Returns the estimated pivots file size in Mbs
     * @param mapSize The size of the map (1-dimensional)
     * @param pivotsCount The number of pivots
     *
     * @return The estimated size
     */
    private double _getPivotsFileSize(int mapSize, int pivotsCount) {
        return ((PivotsPDBGenerator.DOUBLE_SIZE_IN_BYTES * ((pivotsCount + 1) + (mapSize * pivotsCount))) /
                PivotsPDBGenerator.MB_SIZE_IN_BYTES);
    }

    /**
     * Stores all the pivots relevant information inside the given outputFile
     *
     * @param pivots The pivots to build the PDB from
     * @param outputFile The output file to store the pivots in
     *
     * NOTE: The output file is of the following format:
     *       <pivots-count>
     *       <pivot-1>
     *       <pivot-2>
     *       ...
     *       <pivot-n>
     *       <all-distances-from-pivot-1>
     *       <all-distances-from-pivot-2>
     *       ...
     *       <all-distances-from-pivot-n>
     */
    private void _storePivots(GridMap grid, int[] pivots, String outputFile) throws IOException {
        DataOutputStream writer = new DataOutputStream(new FileOutputStream(outputFile));
        System.out.println("[INFO] Creating pivots file " + outputFile);
        System.out.println("[INFO] The file will be at least " +
                this._getPivotsFileSize(grid.mapSize, pivots.length) + " MB");
        // Write pivots count
        writer.writeInt(pivots.length);
        // Write the pivots
        for (int pivot : pivots) {
            writer.writeInt(pivot);
        }
        // Write the distances for each pivot
        for (int pivotIndex = 0; pivotIndex < pivots.length; ++pivotIndex) {
            int pivot = pivots[pivotIndex];
            System.out.println("[INFO] Writing all distances for pivot # " +
                    (pivotIndex + 1) + "/" + pivots.length + " " + grid.getPosition(pivot));
            for (int i = 0; i < grid.mapSize; ++i) {
                if (i % 999 == 0) {
                    System.out.print("\r[INFO] Wrote " + (i + 1) + "/" + grid.mapSize + " distances");
                }
                if (i == pivot) {
                    writer.writeDouble(0.0d);
                } else {
                    writer.writeDouble(this._minDistance(grid, i, pivot));
                }
            }
            // Add a newline
            System.out.println();
        }
        writer.close();
        System.out.println("[INFO] Done creating pivots file " + outputFile);
    }

    /**
     * The function reads the pivots relevant to the TDH heuristic from the given pivots file
     *
     * The file is assumed to be of the one of the following formats:
     *
     * 1. pair pair pair pair ...
     * 2. pair
     *    pair
     *    pair
     *    pair
     *    ...
     * 3. like 1-2 but with 1-dimensional locations
     *
     * @param grid An initialized grid file
     * @param pivotsInputFile The input file which contains the pivots
     *
     * @return The created pivots array
     *
     * @throws IOException In something wrong occurred
     */
    private int[] _readPivots(GridMap grid, String pivotsInputFile) throws IOException {
        BufferedReader pivotsReader =
                new BufferedReader(
                        new InputStreamReader(
                                new FileInputStream(pivotsInputFile)));
        // Initialize some initial data structure for the pivots
        Map<Integer, PairInt> pivots = new HashMap<>(100);
        // For keeping the order of the pivots
        List<Integer> orderedPivots = new ArrayList<>(100);
        Set<String> pivotsStrings = new HashSet<>(100);
        // Now, let's read the pivots
        String line = pivotsReader.readLine();
        if (line == null) {
            System.out.println("[ERROR] Empty pivots file");
            throw new IOException();
        }
        String[] split = line.split(" ");
        // In this case, the line is a single location
        if (split.length == 1) {
            pivotsStrings.add(line);
            while ((line = pivotsReader.readLine()) != null) {
                if (pivotsStrings.contains(line)) {
                    System.out.println("[ERROR] Invalid pivots data: double pivot " + line);
                    throw new IOException();
                }
                pivotsStrings.add(line);
            }
            // Otherwise, the line contains several locations
        } else {
            pivotsStrings = new HashSet<>(Arrays.asList(split));
        }
        // Now, let's parse the pivots
        for (String current : pivotsStrings) {
            int pivot1Dim;
            // Try to treat the string as PairInt
            PairInt pivot2Dim = PairInt.fromString(current);
            // If not PairInt, current must be 1-dimensional location
            if (pivot2Dim == null) {
                try {
                    pivot1Dim = Integer.parseInt(current);
                } catch (NumberFormatException e) {
                    System.out.println("[ERROR] Invalid pivot " + current);
                    throw new IOException();
                }
                // assert that the PairInt is valid for the map
                if (!grid.isValidLocation(pivot1Dim)) {
                    System.out.println("[ERROR] Pivot " + pivot1Dim + " is out of bounds for this map");
                    throw new IOException();
                }
                if (grid.isBlocked(pivot1Dim)) {
                    System.out.println("[ERROR] Pivot " + pivot1Dim + " is blocked on this map");
                    throw new IOException();
                }
                pivot2Dim = grid.getPosition(pivot1Dim);
            } else {
                // assert that the PairInt is valid for the map
                if (!grid.isValidLocation(pivot2Dim)) {
                    System.out.println("[ERROR] Pivot " + pivot2Dim + " is out of bounds for this map");
                    throw new IOException();
                }
                pivot1Dim = grid.getLocationIndex(pivot2Dim);
            }
            // This must be true!
            assert pivot1Dim != -1;
            if (pivots.containsKey(pivot1Dim)) {
                System.out.println("[ERROR] Duplicate pivot " + pivot1Dim + " - " + pivot2Dim + " for this map");
                throw new IOException();
            }
            orderedPivots.add(pivot1Dim);
            // Otherwise, the pivot is valid, so put it inside
            pivots.put(pivot1Dim, pivot2Dim);
        }
        // Finally, close the reader
        pivotsReader.close();
        // And return the result
        return Utils.integerListToArray(orderedPivots);
    }

    /**
     * Reads the input pivots from a given input file and computes a PDB output file of distances from all the pivots
     *
     * This function is used if we already have all the pivots but need to create the PDB
     *
     * @param width Width of the input map
     * @param height Height of the input map
     * @param grid The input map
     * @param inputFile The pivots file
     * @param outputFile The output PDB file
     *
     * @throws IOException If something wrong occurred
     */
    public void computeAndStorePivots(int width, int height, char[] grid,
                                      String inputFile,
                                      String outputFile) throws IOException {
        GridMap gridCopy = new GridMap(width, height, grid);
        int[] pivots = this._readPivots(gridCopy, inputFile);
        this._storePivots(gridCopy, pivots, outputFile);
    }

    public int[] computeAndStorePivots(int width, int height, char[] grid,
                                       int pivotsCount,
                                       String outputFile) throws IOException {
        GridMap gridCopy = new GridMap(width, height, grid);
        int[] pivots = this._computePivots(gridCopy, pivotsCount);
        if (outputFile != null) {
            this._storePivots(gridCopy, pivots, outputFile);
        }
        return pivots;
    }

    public void computeAndStorePivotsEfficiently(int width, int height, char[] grid,
                                                 int pivotsCount,
                                                 String outputFile) throws IOException {
        GridMap gridCopy = new GridMap(width, height, grid);
        Pair<int[], Map<Integer, Map<Integer, Double>>> pivotsAndDistances =
                this._computePivotsAndDistances(gridCopy, pivotsCount);
        int[] pivots = pivotsAndDistances.getKey();
        Map<Integer, Map<Integer, Double>> allDistances = pivotsAndDistances.getValue();
        System.out.println(Arrays.toString(allDistances.keySet().toArray()));
        DataOutputStream writer = new DataOutputStream(new FileOutputStream(outputFile));
        // Write pivots count
        writer.writeInt(pivots.length);
        // Write the pivots
        for (int pivot : pivots) {
            writer.writeInt(pivot);
        }
        // Write the distances for each pivot
        for (int pivotIndex = 0; pivotIndex < pivots.length; ++pivotIndex) {
            int pivot = pivots[pivotIndex];
            Map<Integer, Double> currentDistances = allDistances.get(pivot);
            System.out.println("[INFO] Writing all distances for pivot # " +
                    (pivotIndex + 1) + "/" + pivots.length + " " + gridCopy.getPosition(pivot));
            for (int i = 0; i < gridCopy.mapSize; ++i) {
                if (i % 999 == 0) {
                    System.out.print("\r[INFO] Wrote " + (i + 1) + "/" + gridCopy.mapSize + " distances");
                }
                if (currentDistances != null) {
                    writer.writeDouble(currentDistances.get(i));
                } else  if (i == pivot) {
                    writer.writeDouble(0.0d);
                } else {
                    writer.writeDouble(this._minDistance(gridCopy, i, pivot));
                }
            }
            // Add a newline
            System.out.println();
        }
        writer.close();
    }

    /**
     * Creates PDBs of pivots that contain distances from all locations on the map, to all pivot points
     */
    public static void mainCreateAllPivotsPDBs(Map<String, String> mapToPivots) {
        if (mapToPivots == null) {
            mapToPivots = new HashMap<>();
            /*
            mapToPivots.put(
                    "input/gridpathfinding/raw/maps/brc202d.map",
                    "input/gridpathfinding/raw/maps/brc202d.map.pdb");
            */

            // Debug:
            // mapToPivots.put(
            //        "input/gridpathfinding/raw/mine/test.map",
            //        "input/gridpathfinding/raw/mine/test.map.pdb");


            mapToPivots.put(
                    "input/gridpathfinding/raw/mazes/maze1/maze512-1-6.map",
                    "input/gridpathfinding/raw/mazes/maze1/maze512-1-6.map.pivots.pdb");
        }

        int pivotsCount = 10;

        PivotsPDBGenerator calculator = new PivotsPDBGenerator();

        for (Map.Entry<String, String> pivotEntry : mapToPivots.entrySet()) {
            // Create a fake GridPathFinding problem (just for having the map)
            try {
                InputStream is = new FileInputStream(new File(pivotEntry.getKey()));
                System.out.println("INFO] Creating pivots PDB for " + pivotEntry.getKey());
                // Create a fake map
                GridPathFinding gridPathFindingProblem = new GridPathFinding(is, 0, 0);
                // Copy the grid, in order to avoid unwanted changes during the process of finding the pivots
                calculator.computeAndStorePivotsEfficiently(
                        gridPathFindingProblem.getGridWidth(), gridPathFindingProblem.getGridHeight(),
                        gridPathFindingProblem.getGridMap(),
                        pivotsCount,
                        pivotEntry.getValue());
                System.out.println("[INFO] Done creating pivots PDB for " + pivotEntry.getKey());
                // Now, let' calculate the distance to all the pivots and save the PDB
            } catch (IOException e) {
                System.err.println("[ERROR] For " + pivotEntry.getKey() + " " + e.getMessage());
                // continue to next problem
            }
        }
    }

    /**
     * Creates pivots files which contain only the farthest pivots that can be found on the map (without distances)
     *
     * NOTE: The function returns nothing (just creates the pivots)
     */
    public static void mainCreateAllPivots() {
        int pivotsCount = 10;
        String[] mapFiles =
                new String[]{
                        //"input/gridpathfinding/raw/mazes/maze32/maze512-32-8-80.map"
                        "input/gridpathfinding/raw/mazes/maze1/maze512-1-6.map"
                        //"input/gridpathfinding/raw/maps/brc202d.map",
                        //"input/gridpathfinding/raw/maps/den400d.map",
                        //"input/gridpathfinding/raw/maps/ost003d.map"
                };
        PivotsPDBGenerator calculator = new PivotsPDBGenerator();
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
                // Copy the grid, in order to avoid unwanted changes during the process of finding the pivots

                int[] pivots =
                        calculator.computeAndStorePivots(
                                gridPathFindingProblem.getGridWidth(), gridPathFindingProblem.getGridHeight(),
                                gridPathFindingProblem.getGridMap(),
                                pivotsCount,
                                mapFile + ".pivots");
                System.out.println("[INFO] All pivots found: " + Arrays.toString(pivots));
                // Now, let' calculate the distance to all the pivots and save the PDB
            } catch (IOException e) {
                System.err.println("[ERROR] For " + mapFile + " " + e.getMessage());
                // continue to next problem
            }
        }
    }

    /**
     * Main function that is ran
     *
     * @param args Arguments to main (ignored)
     */
    public static void main(String[] args) {
        // PivotsPDBGenerator.mainCreateAllPivots();
        PivotsPDBGenerator.mainCreateAllPivotsPDBs(null);
    }

}
