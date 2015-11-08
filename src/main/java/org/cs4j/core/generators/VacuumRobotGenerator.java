package org.cs4j.core.generators;

import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchResult;
import org.cs4j.core.algorithms.AStar;
import org.cs4j.core.algorithms.IDAstar;
import org.cs4j.core.algorithms.SearchResultImpl;
import org.cs4j.core.collections.PairInt;
import org.cs4j.core.domains.GridPathFinding;
import org.cs4j.core.domains.Utils;

import java.io.*;
import java.util.Arrays;
import java.util.Random;

/**
 * This class generated instances of the VacuumRobot domain
 */
public class VacuumRobotGenerator {
    private static final char FREE_CHARACTER = '.';
    private static final char OBSTACLE_CHARACTER = '#';
    private static final char ROBOT_CHARACTER = 'V';
    private static final char DIRTY_CHARACTER = '*';
    private static final char IMPOSSIBLE_DIRTY_CHARACTER = '$';

    private static final int TOO_MUCH_ERRORS_RESTART_INSTANCE_GENERATION = 1000;

    private static final int[][] POSSIBLE_DELTAS =
            new int[][] {
                    {0, 1},
                    {0, -1},
                    {-1, 0},
                    {1, 0}
            };

    /**
     * Calculate the index of the location in a one-dimensional array
     *
     * @param x The horizontal location
     * @param y The vertical location
     *
     * @return The calculated index
     */
    private int index(int x, int y, int mapWidth) {
        return y * mapWidth + x;
    }

    /**
     * Calculate the index of the location in a one-dimensional array
     *
     * @param location A location represented as a pair of integers
     *
     * @return The calculated index
     */
    private int index(PairInt location, int mapWidth) {
        return this.index(location.first, location.second, mapWidth);
    }

    /**
     * Creates a Pair object with the dimensions of the given location
     *
     * @param locationIndex The index of the location in a 1-dimensional array
     *
     * @return The calculated Pair
     */
    private PairInt location2d(int locationIndex, int mapWidth) {
        return new PairInt(locationIndex % mapWidth, locationIndex / mapWidth);
    }

    private boolean _isObstacle(char[] map, int index) {
        assert index >= 0 && index < map.length;
        return map[index] == VacuumRobotGenerator.OBSTACLE_CHARACTER;
    }

    /**
     * Checks whether the given location is valid on the map
     *
     * @param map The map to check on
     * @param index The index to check (1-dimensional)
     *
     * NOTE: The function doesn't check the robot is not located in the given location
     *
     * @return Whether the location is free
     */
    private boolean _isFree(char[] map, int index) {
        return index >= 0 && index < map.length && !this._isObstacle(map, index);
    }

    /**
     * Checks whether the robot is located in the given location
     *
     * @param map The map to check on
     * @param index The index to check (1-dimensional)
     *
     * @return Whether the robot is located at the given location
     */
    private boolean _isRobotHere(char[] map, int index) {
        assert index >= 0 && index < map.length;
        return map[index] == VacuumRobotGenerator.ROBOT_CHARACTER;
    }

    /**
     * Return whether the given location contains garbage (is dirty)
     *
     * @param map The map to check on
     * @param index The index to check (1-dimensional)
     *
     * @return Whether the given location is dirty
     */
    // private boolean _isDirty(char[] map, int index) {
    //     assert index >= 0 && index < map.length;
    //     return map[index] == VacuumRobotGenerator.DIRTY_CHARACTER;
    // }

    /**
     * Return whether the given location is dirty (contained in the given dirty locations array)
     *
     * @param possibleDirtyLocations The array of dirty locations to search in
     * @param locationIndex The index to check (1-dimensional)
     * @param mapCopy (OPTIONAL) A copy of the map which is checked for impossible places for dirty locations
     *
     * @return Whether the given location is dirty
     */
    private boolean _isDirty(int[] possibleDirtyLocations, int locationIndex, char[] mapCopy) {
        for (int possibleDirtyLocation : possibleDirtyLocations) {
            if (possibleDirtyLocation == locationIndex) {
                return true;
            // If we have the copy of the map here, check if that dirty location was already checked
            } else if (mapCopy != null && mapCopy[locationIndex] == VacuumRobotGenerator.IMPOSSIBLE_DIRTY_CHARACTER) {
                return true;
            }
        }
        return false;
    }

    /**
     * The function checks whether the location at the given map is blocked by obstacles, such that the robot
     * can't move
     *
     * Blocked location means something like that (# means obstacle):
     *
     *      # # #
     *      # V #
     *      # # #
     *
     * NOTE: In case there are no diagonal moves, the following state is also blocked:
     *      . # .
     *      # V #
     *      . # .
     *
     * @param w The width of the map
     * @param h The height of the map
     * @param map The map on which all the locations recede
     * @param index The index of the location to check
     *
     * @return Whether the location os blocked
     */
    private boolean _isLocationBlocked(int w, int h, char[] map, int index) {
        assert w * h == map.length;
        // Get the location on the map
        PairInt location = this.location2d(index, w);
        // Let's try each possible direction and if we can move there - the state isn't blocked
        for (int deltaIndex = 0; deltaIndex < VacuumRobotGenerator.POSSIBLE_DELTAS.length; ++deltaIndex) {
            int[] currentDeltas = VacuumRobotGenerator.POSSIBLE_DELTAS[deltaIndex];
            int updatedIndex = this.index(location.first + currentDeltas[0], location.second + currentDeltas[1], w);
            if (this._isFree(map, updatedIndex)) {
                return false;
            }
        }
        return true;
    }

    private boolean _isDirtyLocationReachableByRobot(int w, int h, char[] map,
                                                     PairInt robotLocationPair,
                                                     int dirtyLocation) {
        // System.out.println(this.location2d(dirtyLocation, w).toString());
        // System.out.println(robotLocationPair.toString());
        // System.out.println("[INFO] Checking if the dirty location " + dirtyLocation + " is reachable");
        PairInt dirtyLocationPair = this.location2d(dirtyLocation, w);
        char[] mapCopy = new char[map.length];
        System.arraycopy(map, 0, mapCopy, 0, map.length);
        mapCopy[this.index(robotLocationPair, w)] = GridPathFinding.START_MARKER;
        mapCopy[dirtyLocation] = GridPathFinding.GOAL_MARKER;
        SearchDomain domain = new GridPathFinding(w, h, mapCopy, robotLocationPair, dirtyLocationPair);
        SearchAlgorithm searchAlgorithm = new AStar();
        SearchResult result = searchAlgorithm.search(domain);
        // System.out.println("[INFO] Partial Search - Found: " + (result.getSolutions().size() > 0));
        return result.getSolutions().size() > 0;
    }

    /**
     * Generates a single instance of VacuumRobot domain
     *
     * @param w Required width of the grid
     * @param h Required height of the grid
     * @param obstaclesPercentage Required percentage of obstacles on the grid
     * @param dirtyCount The required count of dirty locations
     *
     * @return An array which represents a grid, with the required parameters (size, obstacles percentage and dirty)
     */
    private char[] _generateInstance(int w, int h, double obstaclesPercentage, int dirtyCount) {
        int mapSize = w * h;
        assert obstaclesPercentage >= 0.0d && obstaclesPercentage < 100.0d;
        int obstaclesCount = (int)Math.ceil(mapSize * (obstaclesPercentage / 100));
        int freeLocationsCount = mapSize - obstaclesCount;
        // Assure count of dirty cells isn't too big (at most the number of free cells - 1 (for robot start position)
        assert dirtyCount <= freeLocationsCount - 1;
        // Create the map
        char[] map = new char[mapSize];

        // Decide for each location - whether it is an obstacle or free
        for (int i = 0; i < mapSize; ++i) {
            // Initially, all the locations are free
            map[i] = VacuumRobotGenerator.FREE_CHARACTER;
        }

        System.out.println("[INFO] Map initialized with empty locations");

        // Now, fill the obstacles
        Random obstaclesRandom = new Random();
        int foundObstaclesCount = 0;
        while (foundObstaclesCount < obstaclesCount) {
            int obstacleIndex = (int)Math.ceil(obstaclesRandom.nextDouble() * (mapSize - 1));
            // If not already filled by an obstacle
            if (!this._isObstacle(map, obstacleIndex)) {
                map[obstacleIndex] = VacuumRobotGenerator.OBSTACLE_CHARACTER;
                ++foundObstaclesCount;
            }
        }

        System.out.println("[INFO] Map filled with obstacles");

        Random robotLocationRandom = new Random();
        // Locate the robot
        boolean robotPositionFound = false;
        PairInt robotLocation = null;
        while (!robotPositionFound) {
            int robotPositionIndex = (int)Math.ceil(robotLocationRandom.nextDouble() * (mapSize - 1));
            if (!this._isObstacle(map, robotPositionIndex) && !this._isLocationBlocked(w, h, map, robotPositionIndex)) {
                map[robotPositionIndex] = VacuumRobotGenerator.ROBOT_CHARACTER;
                robotLocation = this.location2d(robotPositionIndex, w);
                // Required one free location for the robot
                freeLocationsCount -= 1;
                robotPositionFound = true;
            }
        }

        System.out.println("[INFO] Map filled with robot location");

        // First, let's store all the dirtyIndexes in a separate array
        int dirtyLocations[] = new int[dirtyCount];
        // Initialize the array with an invalid value
        Arrays.fill(dirtyLocations, -1);

        // Let's prepare a copy of the map and each time a dirty location fails, fill its place, such that it will
        // be never generated next time
        char [] mapCopy = new char[map.length];
        System.arraycopy(map, 0, mapCopy, 0, map.length);

        // Used to break on too much random errors
        int errorRandoms = 0;
        Random dirtyLocationRandom = new Random();
        // Now, fill the dirty locations
        int foundDirtyCount = 0;
        while (foundDirtyCount < dirtyCount && freeLocationsCount > 0) {
            int dirtyLocation = (int)Math.ceil(dirtyLocationRandom.nextDouble() * (mapSize - 1));
            // If not already filled by an obstacle
            if (!this._isObstacle(map, dirtyLocation) &&
                    !this._isRobotHere(map, dirtyLocation) &&
                    !this._isDirty(dirtyLocations, dirtyLocation, mapCopy) &&
                    !this._isLocationBlocked(w, h, map, dirtyLocation)) {
                // However, we still need to check that the location of the garbage is accessible by the robot, thus
                // let's perform a single AStar search from the start to goal
                if (this._isDirtyLocationReachableByRobot(w, h, map, robotLocation, dirtyLocation)) {
                    dirtyLocations[foundDirtyCount] = dirtyLocation;
                    ++foundDirtyCount;
                } else {
                    mapCopy[dirtyLocation] = VacuumRobotGenerator.IMPOSSIBLE_DIRTY_CHARACTER;
                    freeLocationsCount -= 1;
                    // Break if there are no freeLocations anymore
                    if (freeLocationsCount == 0) {
                        break;
                    }
                }
            } else {
                ++errorRandoms;
                if (errorRandoms >= VacuumRobotGenerator.TOO_MUCH_ERRORS_RESTART_INSTANCE_GENERATION) {
                    return null;
                }
            }
        }

        // If we didn't found required number of dirty locations - give up and return null
        if (foundDirtyCount < dirtyCount) {
            return null;
        }

        // Finally, fill the map with the dirty locations
        for (int i = 0; i < dirtyCount; ++i) {
            map[dirtyLocations[i]] = VacuumRobotGenerator.DIRTY_CHARACTER;
        }

        System.out.println("[INFO] Map filled with dirty locations");

        return map;
    }

    /**
     * The function generates a single instance of the VacuumRobot domain and returns its string representation
     *
     * @param w Required width of the grid
     * @param h Required height of the grid
     * @param obstaclesPercentage Required percentage of obstacles on the grid
     * @param dirtyCount The required count of dirty locations
     *
     * @return A string representation of the generated instance
     */
    public String generateInstance(int w, int h, double obstaclesPercentage, int dirtyCount) {
        char[] grid = null;
        // Try more than a single time (if required)
        while (grid == null) {
            grid = this._generateInstance(w, h, obstaclesPercentage, dirtyCount);
            if (grid == null) {
                System.out.println("[INFO] Restarting");
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(w);
        sb.append(" ");
        sb.append(h);
        sb.append("\n");
        // Now, dump the Map with all the dirty locations and the location of the robot
        for (int y = 0; y < h; ++y) {
            // Add newline after end of a single characters line
            sb.append("\n");
            // Horizontal
            for (int x = 0; x < w; ++x) {
                // Get the index of the current location
                sb.append(grid[this.index(x, y, w)]);
            }
        }
        return sb.toString();
    }

    public static void main(String args[]) throws IOException {
        int instancesCount;
        int mapWidth;
        int mapHeight;
        double obstaclesPercentage;
        int dirtyCount;

        // TODO: Read all the input parameters from a single file
        if (args.length != 6) {
            System.out.println("Usage: <OutputPath> <Count> <Width> <Height> <ObstaclesPercentage> <DirtyCount>");
            System.exit(-1);
        }
        File outputDirectory = new File(args[0]);
        if (!outputDirectory.isDirectory()) {
            throw new IOException("Invalid directory: " + args[0]);
        }
        // Required number of instances
        try {
            instancesCount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid # of instances: " + args[1]);
        }
        // Required width of the map
        try {
            mapWidth = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid map width: " + args[2]);
        }
        // Required height of the map
        try {
            mapHeight = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid map height: " + args[3]);
        }
        // Required height of the map
        try {
            obstaclesPercentage = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid percentage of obstacles: " + args[4]);
        }
        // Required count of dirty locations
        try {
            dirtyCount = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid count of dirty locations: " + args[5]);
        }

        VacuumRobotGenerator generator = new VacuumRobotGenerator();

        for (int i = 0; i < instancesCount; ++i) {
            System.out.println("[INFO] Generating instance # " + (i + 1) + " ...");
            FileWriter fw = new FileWriter(new File(outputDirectory, (i + 1) + ".in"));
            fw.write(generator.generateInstance(mapWidth, mapHeight, obstaclesPercentage, dirtyCount));
            fw.close();
            System.out.println(" Done.");
        }
    }
}
