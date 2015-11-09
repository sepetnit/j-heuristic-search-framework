package org.cs4j.core.mains;

import org.cs4j.core.OutputResult;
import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchResult;
import org.cs4j.core.algorithms.AStar;
import org.cs4j.core.algorithms.EES;
import org.cs4j.core.data.Weights;
import org.cs4j.core.domains.FifteenPuzzle;
import org.cs4j.core.domains.GridPathFinding;
import org.cs4j.core.domains.Pancakes;
import org.cs4j.core.domains.VacuumRobot;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.util.Arrays;
import java.util.List;

/**
 * Created by sepetnit on 11/5/2015.
 *
 */
public class EESGeneralExperiment {


    public static SearchDomain createGridPathFindingInstanceFromAutomaticallyGenerated(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/gridpathfinding/generated/ost003d.map/" + instance));
        return new GridPathFinding(is);
    }

    public static SearchDomain createPancakesInstanceFromAutomaticallyGenerated(String instance) throws FileNotFoundException {
        String filename = "input/pancakes/generated/" + instance;
        try {
            InputStream is = new FileInputStream(new File(filename));
            return new Pancakes(is);
        } catch (FileNotFoundException e) {
            System.out.println("[WARNING] File " + filename + " not found");
            return null;
        }
    }

    public static SearchDomain createVacuumRobotInstanceFromAutomaticallyGenerated(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/vacuumrobot/generated/" + instance));
        return new VacuumRobot(is);
    }

    public static SearchDomain create15PuzzleInstanceFromKorfInstances(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/fifteenpuzzle/korf100/" + instance));
        return new FifteenPuzzle(is);
    }

    /**
     * Runs an experiment using the EES algorithm, the generated VacuumRobot domains, and Weights.BASIC_WEIGHTS
     *
     * @param instancesCount The number of instances to solve
     *
     * @throws java.io.IOException
     */
    public static void mainEESExperiment(int instancesCount) throws IOException {
        Weights weights = new Weights();
        boolean reopenPossibilities[] = new boolean[]{true, false};

        OutputResult output;
        try {
            // Create the result file + overwrite if exists!
            output = new OutputResult("results/gridpathfinding/generated/ost003d.map/generated+wastar+extended", true);

            //output = new OutputResult("results/vacuumrobot/generated+ees+extended-test", true);

            //output = new OutputResult("results/fifteenpuzzle/korf100-new", true);

            //output = new OutputResult("results/pancakes/generated-40/generated+ees+extended", true);

            // Write the header line
            output.writeln(
                    "InstanceID,Wh,Wg,Weight," +
                    "AR-Slv,AR-Dep,AR-Ggl,AR-Gen,AR-Exp,AR-Dup,AR-Oup,AR-Rep," +
                    "NR-Slv,NR-Dep,NR-Ggl,NR-Gen,NR-Exp,NR-Dup,NR-Oup,NR-Rep,"
            );

            // Go over all the possible combinations and solve!
            for (int i = 1; i <= instancesCount; ++i) {
                // Create the domain by reading the relevant instance file
                SearchDomain domain = EESGeneralExperiment.createGridPathFindingInstanceFromAutomaticallyGenerated(i + ".in");
                // Bypass not found files
                if (domain == null) {
                    continue;
                }
                for (Weights.SingleWeight w : weights.EXTENDED_WEIGHTS) {
                    double weight = w.getWeight();
                    output.write(i + "," + w.wg + "," + w.wh + "," + weight + ",");
                    for (boolean reopen : reopenPossibilities) {
                        SearchAlgorithm alg = new AStar(weight, reopen);
                        //SearchAlgorithm alg = new EES(weight, reopen);
                        System.out.println("[INFO] Instance: " + i + ", Weight: " + weight + ", Reopen: " + reopen);

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
                                // System.out.println(Arrays.toString(resultData));
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
            System.err.println("File already found for EES result!");
            System.exit(-1);
        }
    }

    public static void main(String[] args) {
        // Solve with 100 instances
        try {
            EESGeneralExperiment.mainEESExperiment(100);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }
}
