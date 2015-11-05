package org.cs4j.core.mains;

import org.cs4j.core.OutputResult;
import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchResult;
import org.cs4j.core.algorithms.EES;
import org.cs4j.core.data.Weights;
import org.cs4j.core.domains.VacuumRobot;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.util.List;

/**
 * Created by sepetnit on 11/5/2015.
 *
 */
public class EESVacuumRobot {

    public static SearchDomain createVacuumRobotInstanceFromAutomaticallyGenerated(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/vacuumrobot/generated/" + instance));
        return new VacuumRobot(is);
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
            output = new OutputResult("results/vacuumrobot/generated+ees", true);


            // Write the header line
            output.writeln("InstanceID,Wh,Wg,Weight,AR-Slv,AR-Dep,AR-Gen,AR-Exp,AR-Rep,NR-Slv,NR-Dep,NR-Gen,NR-Exp,NR-Rep");

            // Go over all the possible combinations and solve!
            for (int i = 1; i <= instancesCount; ++i) {
                // Create the domain by reading the relevant instance file
                SearchDomain domain = EESVacuumRobot.createVacuumRobotInstanceFromAutomaticallyGenerated(i + ".in");
                for (Weights.SingleWeight w : weights.BASIC_WEIGHTS) {
                    double weight = w.getWeight();
                    output.write(i + "," + w.wg + "," + w.wh + "," + weight + ",");
                    for (boolean reopen : reopenPossibilities) {
                        SearchAlgorithm alg = new EES(weight, reopen);
                        System.out.println("[INFO] Instance: " + i + ", Weight: " + weight + ", Reopen: " + reopen);

                        try {
                            SearchResult result = alg.search(domain);
                            List<SearchResult.Solution> solutions = result.getSolutions();
                            // No solution
                            if (solutions.size() == 0) {
                                // Append-  Sol- 0:solution-not-found
                                //          Dep- 0,
                                //          Gen- 0,
                                //          Exp- 0,
                                //          Rep- 0
                                output.appendNewResult(new double[]{0, -1, 0, 0, 0});
                            } else {
                                int solutionLength = solutions.get(0).getLength();
                                double[] resultData = new double[]{
                                        1,
                                        solutionLength,
                                        result.getGenerated(),
                                        result.getExpanded(),
                                        result.getReopened()
                                };
                                output.appendNewResult(resultData);
                            }
                        } catch (OutOfMemoryError e) {
                            // The first -1 is for marking out-of-memory
                            output.appendNewResult(new double[]{-1, -1, 0, 0, 0});
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
            EESVacuumRobot.mainEESExperiment(50);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }
}
