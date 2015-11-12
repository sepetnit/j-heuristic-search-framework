package org.cs4j.core.mains;

import org.cs4j.core.OutputResult;
import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchResult;
import org.cs4j.core.algorithms.WAStar;
import org.cs4j.core.data.Weights;

import java.io.IOException;
import java.util.Arrays;

/**
 * Created by user on 11/11/2015.
 *
 */
public class WAStarEESPancakesGAPKExperiment {

    /*******************************************************************************************************************
     * Private  fields
     ******************************************************************************************************************/

    private Weights weights = new Weights();
    private boolean reopenPossibilities[] = new boolean[]{true, false};

    /*******************************************************************************************************************
     * Private methods
     ******************************************************************************************************************/

    /**
     * Creates a header line for writing into output
     *
     * @return The created header line
     */
    private String _getHeader() {
        return "InstanceID,Wh,Wg,Weight," +
                "AR-Slv,AR-Dep,AR-Ggl,AR-Gen,AR-Exp,AR-Dup,AR-Oup,AR-Rep," +
                "NR-Slv,NR-Dep,NR-Ggl,NR-Gen,NR-Exp,NR-Dup,NR-Oup,NR-Rep,";
    }

    /***
     * Write a header line into the output
     * @param outputResult The output result which points to a file
     * @throws IOException
     */
    private void _writeHeaderLineToOutput(OutputResult outputResult) throws IOException {
        // Write the header line
        outputResult.writeln(this._getHeader());
    }

    /**
     * Returns an array for NoSolution
     *
     * @return A double array which contains default values to write in case no solution was found
     */
    private double[] _getNoSolutionResult() {
        // Append-  Sol- 0:solution-not-found
        //          Dep- -1,
        //          Ggl- -1,
        //          Gen- 0,
        //          Exp- 0,
        //          Dup- 0,
        //          Oup- 0 (updated in open),
        //          Rep- 0
        return new double[]{0, -1, -1, 0, 0, 0, 0, 0};
    }

    /**
     * Returns an array for a found solution
     *
     * @param result The search result data structure
     *
     * @return A new double array which contains all the fields for the solution
     */
    private double[] _getSolutionResult(SearchResult result) {
        SearchResult.Solution solution = result.getSolutions().get(0);
        return new double[]{
                1,
                solution.getLength(),
                solution.getCost(),
                result.getGenerated(),
                result.getExpanded(),
                result.getDuplicates(),
                result.getUpdatedInOpen(),
                result.getReopened(),
        };
    }

    /**
     * Returns an array for OutOfMemory
     *
     * @return A double array which contains default values to write in case there is no memory for solution
     */
    private double[] _getOutOfMemoryResult() {
        return new double[]{-1, -1, -1, 0, 0, 0, 0, 0};
    }

    /*******************************************************************************************************************
     * Public member definitions
     ******************************************************************************************************************/

    /**
     * Runs an experiment using the WAStar and EES algorithms in a SINGLE THREAD!
     *
     * @param size Size of pancakes problem
     * @param firstInstance The id of the first instance to solve
     * @param instancesCount The number of instances to solve
     * @param gaps The possible k values for GAP-k heuristic
     *
     * @return The name of the output file where all the data recedes
     *
     * @throws java.io.IOException
     */
    public String runExperiment(int size, int firstInstance, int instancesCount, int[] gaps) throws IOException {
        for (int currentGap : gaps) {
            System.out.println("[INFO] GAP-" + currentGap);
            OutputResult output = new OutputResult(
                    "results/pancakes/generated-" + size + "-gaps/generated+wastar+gap-" + currentGap + ".csv", true);
            output.writeln(this._getHeader());

            // Go over all the possible combinations and solve!
            for (int i = firstInstance; i <= instancesCount; ++i) {
                // Create the domain by reading the relevant instance file
                SearchDomain domain =
                        DomainsCreation.createPancakesInstanceFromAutomaticallyGenerated(size, i + ".in", currentGap);
                // Bypass not found files
                if (domain == null) {
                    continue;
                }
                for (Weights.SingleWeight w : this.weights.EXTENDED_WEIGHTS) {
                    double weight = w.getWeight();
                    output.write(i + "," + w.wg + "," + w.wh + "," + weight + ",");
                    for (boolean reopen : this.reopenPossibilities) {
                        SearchAlgorithm alg = new WAStar(weight, reopen);
                        //SearchAlgorithm alg = new EES(weight, reopen);
                        System.out.println("[INFO] Solving : " +
                                "(Instance- " + i + ", Gap-" + currentGap + ", Weight- " + weight + ", Reopen- " + reopen + ")");
                        try {
                            SearchResult result = alg.search(domain);
                            // No solution
                            if (!result.hasSolution()) {
                                output.appendNewResult(this._getNoSolutionResult());
                                System.out.println("[INFO] NoSolution");
                            } else {
                                double[] resultData = this._getSolutionResult(result);
                                System.out.println("[INFO] " + Arrays.toString(resultData));
                                output.appendNewResult(resultData);
                            }
                        } catch (OutOfMemoryError e) {
                            System.out.println("[INFO] OutOfMemory");
                            output.appendNewResult(this._getOutOfMemoryResult());
                        }
                    }
                    output.newline();
                }
            }
            output.close();
        }
        return null;
    }

    /*******************************************************************************************************************
     * Main :)
     ******************************************************************************************************************/

    public static void main(String[] args) {
        // Solve with 100 instances
        try {
            WAStarEESPancakesGAPKExperiment experiment = new WAStarEESPancakesGAPKExperiment();
            experiment.runExperiment(
                    10,
                    // First instance ID
                    74,
                    // Instances Count
                    100,
                    // The gaps
                    new int[]{6, 7, 8, 9}); // 1, 2, 3, 4, 5 - already done
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }
}
