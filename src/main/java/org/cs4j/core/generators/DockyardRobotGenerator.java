package org.cs4j.core.generators;

import org.cs4j.core.domains.DockyardRobot;
import org.cs4j.core.domains.Pancakes;
import org.cs4j.core.domains.Utils;

import javax.print.Doc;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Created by sepetnit on 11/8/2015.
 *
 */
public class DockyardRobotGenerator {
    private static final int MAX_DISTANCE = 1000;
    private static final int AVERAGE_DISTANCE = DockyardRobotGenerator.MAX_DISTANCE / 2;

    private Random rand;

    public DockyardRobotGenerator() {
        this.rand = new Random();
    }

    /**
     * Appends a newline to the given StringBuild
     *
     * @param sb A StringBuilder object to update
     */
    public void _appendNewLine(StringBuilder sb) {
        sb.append("\n");
    }

    private void _appendIntFieldToStringBuilder(String fieldName, int value, StringBuilder sb) {
        sb.append(fieldName);
        sb.append(": ");
        sb.append(value);
        this._appendNewLine(sb);
    }

    /**
     * Generates a single instance of Dockyard Robot domain and returns its string representation
     *
     * @param locationsCount The number of locations in dockyard
     * @param cranesCount The total number of cranes that are located in the dockyard
     * @param boxesCount The total number of boxes that are located in the dockyard
     * @param pilesCount The total number of piles that can be created in the dockyard
     * @param robotsCount The total number of robots that are working
     *
     * @return An string representation of the instance, which can be written to file
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
        List<Integer> pilesOnLocationsList = new ArrayList<>();
        for (int i = 0; i < locationsCount; ++i) {
            pilesOnLocationsList.add(i);
        }

        Collections.shuffle(pilesOnLocationsList, this.rand);
        int[] pilesOnLocations = Utils.integerListToArray(pilesOnLocationsList);

        // For each box, determine the initial pile
        int[] boxesInPiles = new int[boxesCount];
        for (int i = 0; i < boxesInPiles.length; ++i) {
            boxesInPiles[i] = this.rand.nextInt(pilesCount);
        }
        Map<Integer, List<Integer>> pilesToBoxes = new HashMap<>();
        for (int i = 0; i < boxesInPiles.length; ++i) {
            int pile = boxesInPiles[i];
            List<Integer> currentBoxes = pilesToBoxes.get(pile);
            if (currentBoxes == null) {
                currentBoxes = new ArrayList<>(boxesCount);
                pilesToBoxes.put(pile, currentBoxes);
            }
            currentBoxes.add(i);
        }

        int[] goalsOfBoxes = new int[boxesCount];
        for (int i = 0; i < boxesCount; ++i) {
            goalsOfBoxes[i] = this.rand.nextInt(locationsCount);
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
            this._appendIntFieldToStringBuilder("piles", pilesOnLocations[i], sb);
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
        for (int i = 0; i < goalsOfBoxes.length; ++i) {
            sb.append("container ");
            sb.append(i);
            sb.append(' ');
            sb.append(goalsOfBoxes[i]);
            this._appendNewLine(sb);
        }

        return sb.toString();
    }

    /**
     * Reads an integer from the input argument and assures its validity
     *
     * @param input The input to parse and read an integer from
     * @param min The minimum enforced value of the integer (-1 if irrelevant)
     * @param max The maximum enforced value of the integer (-1 if irrelevant)
     * @param type What the integer represents (a string representation)
     *
     * @return The parsed integer
     *
     * @throws IOException If something wrong occurred
     */
    private static int readIntNumber(String input, int min, int max, String type) throws IOException {
        try {
            // Parse the input
            int toReturn = Integer.parseInt(input);
            if (min != -1 && toReturn < min) {
                throw new IOException("Too low " + type + " (must be >= " + min + "): " + input);
            } else if (max != -1 && toReturn > max) {
                throw new IOException("Too high " + type + " (must be <= " + max + "): " + input);
            }
            return toReturn;
        } catch (NumberFormatException e) {
            throw new IOException("Invalid " + type + ": " + input);
        }
    }

    public static void main(String args[]) throws IOException {
        int instancesCount;
        int locationsCount;
        int cranesCount;
        int boxesCount;
        int pilesCount;
        // A single robot is enforced!
        int robotsCount;

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
