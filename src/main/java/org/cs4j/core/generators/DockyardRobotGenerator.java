package org.cs4j.core.generators;

import org.cs4j.core.domains.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Created by sepetnit on 11/8/2015.
 *
 */
public class DockyardRobotGenerator extends GeneralInstancesGenerator {
    private static final int BOXES_OUT_OF_PLACE_PERCENTAGE = 30  ;
    private static final int MAX_DISTANCE = 2;
    private static final int AVERAGE_DISTANCE = DockyardRobotGenerator.MAX_DISTANCE / 2;

    /**
     * Generates a single instance of Dockyard Robot domain and returns its string representation
     *
     * @param locationsCount The number of locations in dockyard
     * @param cranesCount The total number of cranes that are located in the dockyard
     * @param boxesCount The total number of boxes that are located in the dockyard
     * @param pilesCount The total number of piles that can be created in the dockyard
     * @param robotsCount The total number of robots that are working
     *
     * @return An string representation of the instance, which can be written to file - or null if failed
     */
    public String generateInstance(int locationsCount, int cranesCount, int boxesCount, int pilesCount, int robotsCount) {
        int[][] adjacencyMatrix = new int[locationsCount][];
        for (int i = 0; i < locationsCount; ++i) {
            int[] adjacent = new int[locationsCount];
            adjacencyMatrix[i] = adjacent;
            for (int j = 0; j < locationsCount; ++j) {
                if (i == j) {
                    adjacencyMatrix[i][j] = 0;
                } else {
                    adjacencyMatrix[i][j] = DockyardRobotGenerator.AVERAGE_DISTANCE;
                    if (this.rand.nextDouble() > 0.5) {
                        adjacencyMatrix[i][j] = this.rand.nextInt(DockyardRobotGenerator.MAX_DISTANCE) + 1;
                    }
                }
            }
        }

        // A list that defines a pile for each location
        List<Integer> pilesToLocationsList = new ArrayList<>();
        for (int i = 0; i < locationsCount; ++i) {
            pilesToLocationsList.add(i);
        }
        Collections.shuffle(pilesToLocationsList, this.rand);
        // The final pile->location array
        int[] pilesToLocations = Utils.integerListToArray(pilesToLocationsList);

        // For each box, determine the initial pile
        int[] boxesToPiles = Utils.getRandomIntegerListArray(boxesCount, pilesCount, this.rand);
        // The map is the reverse  of boxesToPiles
        Map<Integer, List<Integer>> pilesToBoxes = new HashMap<>();
        for (int i = 0; i < boxesToPiles.length; ++i) {
            int pile = boxesToPiles[i];
            List<Integer> currentBoxes = pilesToBoxes.get(pile);
            if (currentBoxes == null) {
                currentBoxes = new ArrayList<>(boxesCount);
                pilesToBoxes.put(pile, currentBoxes);
            }
            currentBoxes.add(i);
        }

        int[] boxesToLocations = new int[boxesCount];
        // Now, let's determine the initial location of each box -
        for (int i = 0; i < boxesCount; ++i) {
            // The id of the pile
            int pileNumber = boxesToPiles[i];
            // Now, let's determine the location of the pile
            boxesToLocations[i] = -1;
            for (int j = 0; j < pilesToLocations.length; ++j) {
                if (pilesToLocations[j] == pileNumber) {
                    boxesToLocations[i] = j;
                    break;
                }
            }
            assert boxesToLocations[i] != -1;
        }

        // Here we know the initial location of each box
        int requiredOutOfPlaceLocationsCount = (int)Math.ceil((boxesCount *
                DockyardRobotGenerator.BOXES_OUT_OF_PLACE_PERCENTAGE) / 100.0);
        assert requiredOutOfPlaceLocationsCount <= boxesCount;


        int[] boxesToGoals = new int[boxesCount];
        for (int i = 0; i < boxesCount; ++i) {
            boxesToGoals[i] = this.rand.nextInt(locationsCount);
        }

        // Now, go over the goals and correct some of the locations
        List<Integer> outOfPlaceLocations = new ArrayList<>();
        for (int i = 0; i < boxesCount; ++i) {
            if (boxesToGoals[i] != boxesToLocations[i]) {
                outOfPlaceLocations.add(i);
            }
        }

        // Correct some locations if required
        if (outOfPlaceLocations.size() > requiredOutOfPlaceLocationsCount) {
            Collections.shuffle(outOfPlaceLocations);
            for (int i = 0; i < outOfPlaceLocations.size() - requiredOutOfPlaceLocationsCount; ++i) {
                int locationToCorrect = outOfPlaceLocations.get(i);
                // Make the goal be the initial location of the box
                boxesToGoals[locationToCorrect] = boxesToLocations[locationToCorrect];
            }
        }

        StringBuilder sb = new StringBuilder();
        // locations count
        this._appendIntFieldToStringBuilder("locs", locationsCount, sb);
        // cranes count
        this._appendIntFieldToStringBuilder("cranes", cranesCount, sb);
        // boxes count
        this._appendIntFieldToStringBuilder("boxes", boxesCount, sb);
        // piles count
        this._appendIntFieldToStringBuilder("piles", pilesCount, sb);
        // robots count
        this._appendIntFieldToStringBuilder("robots", robotsCount, sb);

        this._appendNewLine(sb);

        // Append all the locations
        for (int i = 0; i < locationsCount; ++i) {
            sb.append("location ");
            sb.append(i);
            this._appendNewLine(sb);
            sb.append("adjacent:");
            for (int j = 0; j < locationsCount; ++j) {
                sb.append(" ");
                if (i == j) {
                    sb.append("0");
                } else {
                    sb.append(adjacencyMatrix[i][j]);
                }
            }
            this._appendNewLine(sb);
            this._appendIntFieldToStringBuilder("cranes", 1, sb);
            this._appendIntFieldToStringBuilder("piles", pilesToLocations[i], sb);
            this._appendNewLine(sb);
        }

        // Now, append all the piles

        for (int key : pilesToBoxes.keySet()) {
            sb.append("pile ");
            sb.append(key);
            this._appendNewLine(sb);
            List<Integer> currentBoxes = pilesToBoxes.get(key);
            for (int currentBox : currentBoxes) {
                sb.append(currentBox);
                sb.append(' ');
            }
            this._appendNewLine(sb);
            this._appendNewLine(sb);
        }

        // Finally, append goals
        for (int i = 0; i < boxesToGoals.length; ++i) {
            sb.append("container ");
            sb.append(i);
            sb.append(' ');
            sb.append(boxesToGoals[i]);
            this._appendNewLine(sb);
        }

        return sb.toString();
    }

    public static void main(String args[]) throws IOException {
        int instancesCount;
        int locationsCount;
        int cranesCount;
        int boxesCount;
        int pilesCount;
        // A single robot is enforced!
        int robotsCount;

        if (args.length == 0) {
            args = new String[7];
            args[0] = "input\\dockyardrobot\\generated";
            // Count of Problems
            args[1] = 100 + "";
            // Count of Locations
            args[2] = 5 + "";
            // Count of Cranes
            args[3] = 5 + "";
            // Count of Containers (Boxes)
            args[4] = 8 + "";
            // Count of Piles
            args[5] = 5 + "";
            // Count of Robots
            args[6] = 1 + "";
        }

        if (args.length != 7) {
            System.out.println("Usage: <OutputPath> <Count> <Locations> <Cranes> <Boxes> <Piles> <Robots>");
            System.exit(-1);
        }

        File outputDirectory = new File(args[0]);
        if (!outputDirectory.isDirectory()) {
            throw new IOException("Invalid directory: " + args[0]);
        }
        // Required number of instances
        instancesCount = DockyardRobotGenerator.readIntNumber(args[1], -1, -1, "# of instances");
        // Locations count
        locationsCount = DockyardRobotGenerator.readIntNumber(args[2], 2, -1, "locations count");
        // Cranes count
        cranesCount = DockyardRobotGenerator.readIntNumber(args[3], 2, -1, "cranes count");
        // Boxes count
        boxesCount = DockyardRobotGenerator.readIntNumber(args[4], 1, -1, "boxes count");
        // Piles count
        pilesCount = DockyardRobotGenerator.readIntNumber(args[5], 1, -1, "piles count");
        // Robots count
        robotsCount = DockyardRobotGenerator.readIntNumber(args[6], 1, 1, "robots count");

        // Some assertions for current implementation
        assert cranesCount == locationsCount;
        assert pilesCount == locationsCount;
        assert robotsCount == 1;


        // This set is used in order to avoid duplicates
        Set<String> instances = new HashSet<>();
        // Now, create the problems
        DockyardRobotGenerator generator = new DockyardRobotGenerator();
        // Loop over the required number of instances
        for (int i = 0; i < instancesCount; ++i) {
            int problemNumber = i + 1;
            System.out.println("[INFO] Generating instance # " + problemNumber + " ...");
            FileWriter fw = new FileWriter(new File(outputDirectory, problemNumber + ".in"));
            String instance = null;
            while (instance == null || instances.contains(instance)) {
                instance = generator.generateInstance(locationsCount, cranesCount, boxesCount, pilesCount, robotsCount);
            }
            instances.add(instance);
            fw.write(instance);
            fw.close();
            System.out.println(" Done.");
        }
        assert instances.size() == instancesCount;
    }
}
