package org.cs4j.core.generators;

import org.cs4j.core.domains.Utils;

import java.io.*;
import java.util.*;

/**
 * This class generated instances of the Pancakes domain
 */
public class PancakesGenerator extends GeneralInstancesGenerator {
    private static final int RANDOM_FLIP_COUNT_FOR_PROBLEM = 10000;


    /**
     * Generates a random valid possible operator for flipping the top portion of the pancakes stack
     *
     * e.g. If the size of the problem is 14 :
     *  *) all operators 1-13 are valid
     *  *) rand.nextDouble() returns a number in a range [0, 1)
     *  *) We use size - 2 in order to generate some operator 0-12
     *  *) Adding 1 gives us the required range of operators: 1-13
     *
     * @param size Size of the pancakes problem
     * @param rand A random object, from which random numbers are generated
     *
     * @return The generated random index
     */
    private int _getRandomOperatorIndex(int size, Random rand) {
        return (int)Math.floor((size - 2) * rand.nextDouble()) + 1;
    }

    /**
     * Lift a top portion of the stack and reverse it
     *
     * @param pancakes The array of pancakes to flip
     * @param op The operator for the operation (represented by an integer number)
     */
    private void _flipTopStackPortion(int[] pancakes, int op) {
        System.out.println("[INFO] Flipping top " + op + " pancakes");
        // Assert the operator is valid
        assert (op > 0);
        assert (op < pancakes.length);
        // Flip the first half (and the second will be flipped automatically)
        for (int n = 0; n <= op / 2; ++n) {
            int tmp = pancakes[n];
            pancakes[n] = pancakes[op - n];
            pancakes[op - n] = tmp;
        }
    }

    /**
     * Returns an initial state of the pancakes domain (where all the pancakes are at the true place)
     *
     * @param size The size of the stack of pancakes
     *
     * @return The created initial state
     */
    private int[] _generateInitialState(int size) {
        assert size >= 2;
        int[] toReturn = new int[size];
        // Initialize with the final state
        for (int i = 0; i < toReturn.length; ++i) {
            toReturn[i] = i;
        }
        return toReturn;
    }

    /**
     * Generates a single instance of Pancakes domain by performing a constant count of random flips of a goal state
     *
     * @param size The size of the stack of pancakes
     *
     * @return An array that represents the initial state of the pancakes stack
     */
    private int[] _generateInstanceByRandomFlips(int size) {
        assert size >= 2;
        int[] toReturn = this._generateInitialState(size);
        // Initialize a new random for generating random operators
        Random rand = new Random();
        // Perform a constant number of random flips
        for (int i = 0; i < PancakesGenerator.RANDOM_FLIP_COUNT_FOR_PROBLEM; ++i) {
            this._flipTopStackPortion(toReturn, this._getRandomOperatorIndex(size, rand));
        }
        // Finally, return the result
        return toReturn;
    }

    /**
     * Generates a single instance of Pancakes domain by performing a random shuffle of the pancakes in a goal state
     *
     * @param size The size of the stack of pancakes
     *
     * @return An array that represents the initial state of the pancakes stack
     */
    private int[] _generateInstanceByRandomShuffle(int size) {
        int[] toReturn = this._generateInitialState(size);
        List<Integer> toReturnAsList = new ArrayList<>(toReturn.length);
        for (int current : toReturn) {
            toReturnAsList.add(current);
        }
        Collections.shuffle(toReturnAsList);
        for (int i = 0; i < toReturn.length; ++i) {
            toReturn[i] = toReturnAsList.get(i);
        }
        return toReturn;
    }

    /**
     * Generates a single instance of Pancakes domain and returns its string representation
     *
     * @param size The size of the stack of pancakes
     ** @return An string representation of the instance, which can be written to file
     */
    public String generateInstance(int size) {
        int[] instance = this._generateInstanceByRandomFlips(size);
        StringBuilder sb = new StringBuilder();
        sb.append(instance.length);
        this._appendNewLine(sb);
        // Append all the pancakes with a following space
        for (int i = 0; i < instance.length - 1; ++i) {
            sb.append(instance[i]);
            sb.append(" ");
        }
        // Append the last pancake
        sb.append(instance[instance.length - 1]);
        // Finally, return the created instance
        return sb.toString();
    }

    public static void main(String args[]) throws IOException {
        int instancesCount;
        int size;
        String previousInstancesDir = null;
        int previousInstancesCount = 0;

        // In case no arguments were given - let's specify them here
        if (args.length == 0) {
            args = new String[4];
            System.out.println("[WARNING] Using local arguments");
            // Output directory
            args[0] = "input\\pancakes\\generated-new";
            // Count of pancakes
            args[1] = "1";
            // Size of problem
            args[2] = "40";
            args[3] = "input\\pancakes\\generated";
        }

        if (args.length == 4) {
            previousInstancesDir = args[3];
        }

        if (args.length < 3) {
            System.out.println("Usage: <OutputPath> <Count> <Size>");
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
        // Size of each problem
        try {
            size = Integer.parseInt(args[2]);
            // The size must be at least 2
            if (size < 2) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            throw new IOException("Invalid size of Pancakes problem (must be >= 2): " + args[1]);
        }

        // This set is used in order to avoid duplicates
        Set<String> instances = new HashSet<>();

        // Fill in previous instances (in order to avoid duplicates)
        if (previousInstancesDir != null) {
            File prev = new File(previousInstancesDir);
            if (prev.exists()) {
                for(File current : prev.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return pathname.getName().endsWith("in");
                    }
                })) {
                    instances.add(Utils.fileToString(current));
                    ++previousInstancesCount;
                }
            }
        }

        // Now, create the problems
        PancakesGenerator generator = new PancakesGenerator();
        // Loop over the required number of instances
        for (int i = 0; i < instancesCount; ++i) {
            int pancakeNumber = i + 1;
            System.out.println("[INFO] Generating instance # " + pancakeNumber + " ...");
            FileWriter fw = new FileWriter(new File(outputDirectory, pancakeNumber + ".in"));
            String instance = null;
            while (instance == null || instances.contains(instance)) {
                instance = generator.generateInstance(size);
            }
            instances.add(instance);
            fw.write(instance);
            fw.close();
            System.out.println(" Done.");
        }
        assert instances.size() == instancesCount + previousInstancesCount;
    }
}
