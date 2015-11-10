package org.cs4j.core.mains;

import org.cs4j.core.OutputResult;
import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchResult;
import org.cs4j.core.algorithms.PTS;
import org.cs4j.core.domains.GridPathFinding;
import org.cs4j.core.domains.Utils;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.util.*;

/**
 * Created by sepetnit on 11/5/2015.
 *
 */
public class PTSGeneralExperiment {


    public static SearchDomain createGridPathFindingInstanceFromAutomaticallyGenerated(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/gridpathfinding/generated/ost003d.map/" + instance));
        return new GridPathFinding(is);
    }

    private static class MaxCostsCreationElement {
        private int min;
        private int jump;
        private int max;

        private MaxCostsCreationElement(int min, int jump, int max) {
            this.min = min;
            this.jump = jump;
            this.max = max;
        }
    }

    private static int[] createMaxCosts(MaxCostsCreationElement[] parts) {
        Set<Integer> maxCosts = new HashSet<>();
        for (MaxCostsCreationElement part : parts) {
            for (int i = part.min; i <= part.max; i += part.jump) {
                maxCosts.add(i);
            }
        }
        List<Integer> maxCostsAsList = new ArrayList<Integer>(maxCosts);
        Collections.sort(maxCostsAsList);
        return Utils.integerListToArray(maxCostsAsList);
    }

    /**
     * Runs an experiment using the PTS algorithm
     *
     * @param instancesCount The number of instances to solve
     *
     * @throws java.io.IOException
     */
    public static void mainPTSExperiment(int instancesCount) throws IOException {
        boolean reopenPossibilities[] = new boolean[]{true, false};
        int[] maxCosts = PTSGeneralExperiment.createMaxCosts(
                new MaxCostsCreationElement[] {
                        new MaxCostsCreationElement(100, 10, 400)
                }
        );
        OutputResult output;

        try {
            output = new OutputResult("results/gridpathfinding/generated/ost003d.map/generated+pts", true);

            // Write the header line
            output.writeln(
                    "InstanceID,MaxCost," +
                            "AR-Slv,AR-Dep,AR-Ggl,AR-Gen,AR-Exp,AR-Dup,AR-Oup,AR-Rep," +
                            "NR-Slv,NR-Dep,NR-Ggl,NR-Gen,NR-Exp,NR-Dup,NR-Oup,NR-Rep,"
            );

            SearchAlgorithm alg = new PTS();

            // Go over all the possible combinations and solve!
            for (int i = 1; i <= instancesCount; ++i) {
                // Create the domain by reading the relevant instance file
                SearchDomain domain = PTSGeneralExperiment.createGridPathFindingInstanceFromAutomaticallyGenerated(i + ".in");
                // Bypass not found files
                if (domain == null) {
                    continue;
                }
                for (int maxCost : maxCosts) {
                    output.write(i + "," + maxCost + ",");
                    for (boolean reopen : reopenPossibilities) {
                        System.out.println("[INFO] Instance: " + i + ", maxCost: " + maxCost + ", Reopen: " + reopen);
                        // Set parameters
                        alg.setAdditionalParameter("maxCost", maxCost + "");
                        alg.setAdditionalParameter("reopen", reopen + "");
                        // Run
                        try {
                            SearchResult result = alg.search(domain);
                            List<SearchResult.Solution> solutions = result.getSolutions();
                            // No solution
                            if (solutions.size() == 0) {
                                // Append-  Sol- 0:solution-not-found
                                //          Dep- -1,
                                //          Ggl- -1,
                                //          Gen- 0,
                                //          Exp- 0,
                                //          Dup- 0,
                                //          Oup- 0 (updated in open),
                                //          Rep- 0
                                output.appendNewResult(new double[]{0, -1, -1, 0, 0, 0, 0, 0});
                            } else {
                                int solutionLength = solutions.get(0).getLength();
                                double[] resultData = new double[]{
                                        1,
                                        solutionLength,
                                        // Put here the G value of the goal
                                        solutions.get(0).getCost(),
                                        result.getGenerated(),
                                        result.getExpanded(),
                                        result.getDuplicates(),
                                        result.getUpdatedInOpen(),
                                        result.getReopened(),
                                };
                                System.out.println(Arrays.toString(resultData));
                                //System.out.println(solutions.get(0).dumpSolution());
                                output.appendNewResult(resultData);
                            }
                        } catch (OutOfMemoryError e) {
                            System.out.println("Got out of memory :(");
                            // The first -1 is for marking out-of-memory
                            output.appendNewResult(new double[]{-1, -1, -1, 0, 0, 0, 0, 0});
                        }
                    }
                    output.newline();
                }
            }
            output.close();
        } catch (FileAlreadyExistsException e) {
            System.err.println("File already found for PTS result!");
            System.exit(-1);
        }
    }

    public static void main(String[] args) {
        // Solve with 100 instances
        try {
            PTSGeneralExperiment.mainPTSExperiment(100);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }
}
