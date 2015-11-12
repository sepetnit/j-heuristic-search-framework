package org.cs4j.core.generators;

import org.cs4j.core.collections.PairInt;
import org.cs4j.core.domains.GridPathFinding;

import java.util.Random;

/**
 * Created by sepetnit on 11/12/2015.
 *
 */
public class PivotsCalculator {

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

    public void computePivot(GridMap grid) {
        Random rand = new Random();


        int [][] distances = new int[grid.mapSize][];
        for (int i = 0; i < distances.length; ++i) {
            distances[i] = new int[grid.mapSize];
        }

        int location = -1;
        while (location == -1 || grid.isBlocked(location)) {
            location = rand.nextInt(grid.mapSize);
        }

        // Here we have the first pivot

    }
}
