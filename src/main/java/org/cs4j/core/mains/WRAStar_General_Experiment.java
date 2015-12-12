package org.cs4j.core.mains;

import org.cs4j.core.OutputResult;
import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchResult;
import org.cs4j.core.algorithms.WRAStar;
import org.cs4j.core.data.Weights;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by user on 20/11/2015.
 *
 */
public class WRAStar_General_Experiment {

    /*******************************************************************************************************************
     * Private  fields
     ******************************************************************************************************************/

    private Weights weights = new Weights();

    /*******************************************************************************************************************
     * Private methods
     ******************************************************************************************************************/

    /**
     * Creates a header line for writing into output
     *
     * @return The created header line
     */
    private String _getHeader() {
        return "InstanceID,Wg,Wh,Weight," +
                "WR-Slv,WR-Dep,WR-Cst,WR-Gen,WR-FExp,WR-Exp,WR-Dup,WR-Oup,WR-Rep";
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
     * @param result The search result data structure
     *
     * @return A double array which contains default values to write in case no solution was found
     */
    private double[] _getNoSolutionResult(SearchResult result) {
        return new double[]{
                // solution-not-found
                0,
                // no-length
                -1,
                // no-cost
                -1,
                result.getGenerated(),
                result.getFirstIterationExpanded(),
                result.getExpanded(),
                result.getDuplicates(),
                result.getUpdatedInOpen(),
                result.getReopened(),
        };
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
                result.getFirstIterationExpanded(),
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
        return new double[]{-1, -1, -1, 0, 0, 0, 0, 0, 0};
    }


    /**
     * Returns an initialized outputResult object
     *
     * @param outputPath The output path
     * @param prefix A prefix to add to the created random file
     * @param writeHeader Whether to add a header line immediately
     *
     * @return The created output result
     */
    private OutputResult getOutputResult(String outputPath, String prefix, boolean writeHeader) throws IOException {
        OutputResult output = null;
        if (prefix == null) {
            prefix = "";
        }

        try {
            output = new OutputResult(outputPath, prefix, true);
        } catch (FileAlreadyExistsException e) {
            System.err.println("Output file " + outputPath + " already exists");
            System.exit(-1);
        }
        // Add the header immediately if needed
        if (writeHeader) {
            this._writeHeaderLineToOutput(output);
        }
        return output;
    }

    /**
     * Runs an experiment using the WRAStar and EES algorithms in a SINGLE THREAD!
     *
     * @param domain The domain to run with (can be null)
     * @param firstInstance The id of the first instance to solve
     * @param instancesCount The number of instances to solve
     * @param outputPath The name of the output file (can be null : in this case a random path will be chosen)
     * @param optimalCosts The optimal costs of the instances
     * @param needHeader Whether to add the header line to list
     *
     * @return Name of all the created output files
     *
     * @throws java.io.IOException
     */
    public String[] runGridPathFindingWithPivotsExperimentWRAStarSingleThreaded(
            SearchDomain domain, int firstInstance, int instancesCount,
            String outputPath,
            Map<Integer, Double> optimalCosts,
            boolean needHeader) throws IOException {

        Weights.SingleWeight[] weights = this.weights.VERY_LOW_WEIGHTS;
        int[] pivotsCounts = new int[]{10};
        List<String> allOutputFiles = new ArrayList<>(pivotsCounts.length);

        if (domain == null) {
            // Create the domain by reading the first instance (the pivots DB is read once!)
            domain =
                    DomainsCreation.createGridPathFindingInstanceFromAutomaticallyGeneratedWithTDH(
                            firstInstance + ".in",
                            10);
        }

        for (int pivotsCount : pivotsCounts) {
            System.out.println("[INFO] Runs experiment with " + pivotsCount + " pivots");
            OutputResult output = this.getOutputResult(outputPath + "-" + pivotsCount, null, needHeader);
            // Go over all the possible combinations and solve!
            for (int i = firstInstance; i <= instancesCount; ++i) {
                // Create the domain by reading the relevant instance file
                domain =
                        DomainsCreation.createGridPathFindingInstanceFromAutomaticallyGeneratedWithTDH(
                                domain,
                                i + ".in",
                                pivotsCount);
                // Bypass not found files
                if (domain == null) {
                    continue;
                }
                // Set the optimal cost if required
                if (optimalCosts != null) {
                    double optimalCost = optimalCosts.get(i);
                    domain.setOptimalSolutionCost(optimalCost);
                    System.out.println("[INFO] Instance " + i + " optimal cost is " + optimalCost);
                }

                for (Weights.SingleWeight w : weights) {
                    double weight = w.getWeight();
                    output.write(i + "," + w.wg + "," + w.wh + "," + weight + ",");
                    SearchAlgorithm alg = new WRAStar();
                    alg.setAdditionalParameter("weight", weight + "");
                    alg.setAdditionalParameter("bpmx", true + "");
                    alg.setAdditionalParameter("iteration-to-start-reopening", "300" + "");
                    //alg.setAdditionalParameter("w-admissibility-deviation-percentage", 20.0d + "");
                    alg.setAdditionalParameter("restart-closed-list", false + "");
                    System.out.println("[INFO] Algorithm: " + alg.getName() + ", Instance: " + i + ", Weight: " + weight);
                    try {
                        SearchResult result = alg.search(domain);
                        // No solution
                        if (!result.hasSolution()) {
                            output.appendNewResult(this._getNoSolutionResult(result));
                            System.out.println("[INFO] Done: NoSolution");
                        } else {
                            double[] resultData = this._getSolutionResult(result);
                            System.out.println("[INFO] Done: " + Arrays.toString(resultData));
                            output.appendNewResult(resultData);
                        }
                    } catch (OutOfMemoryError e) {
                        System.out.println("[INFO] Done: OutOfMemory");
                        output.appendNewResult(this._getOutOfMemoryResult());
                    }
                    output.newline();
                }
            }
            output.close();
            allOutputFiles.add(output.getFname());
        }
        return allOutputFiles.toArray(new String[allOutputFiles.size()]);
    }

    /**
     * Runs an experiment using the WRAStar and EES algorithms in a SINGLE THREAD!
     *
     * @param firstInstance The id of the first instance to solve
     * @param instancesCount The number of instances to solve
     * @param outputPath The name of the output file (can be null : in this case a random path will be chosen)
     * @param optimalCosts The optimal costs of the instances
     * @param needHeader Whether to add the header line to list
     *
     * @return Name of all the created output files
     *
     * @throws java.io.IOException
     */
    public String[] runGridPathFindingWithPivotsExperimentWRAStarSingleThreaded(
            int firstInstance, int instancesCount, String outputPath,
            Map<Integer, Double> optimalCosts,
            boolean needHeader) throws IOException {
        return this.runGridPathFindingWithPivotsExperimentWRAStarSingleThreaded(
                null, firstInstance, instancesCount,
                outputPath,
                optimalCosts,
                needHeader);
    }

    /**
     * The function reads the optimal costs of all the instances, stored in the input file
     *
     * @param path The path of the file that contains the optimal costs
     *
     * NOTE: The file must be of the following format:
     *             <instance-i1-id>,<i1-optimal-cost>
     *             <instance-i2-id>,<i2-optimal-cost>
     *             ...
     *             ...
     * @return An mapping from instance id to the optimal cost of this instance
     */
    public static Map<Integer, Double> readOptimalCosts(String path) throws IOException {
        System.out.println("[INFO] Reading optimal costs");
        Map<Integer, Double> toReturn = new HashMap<>();
        InputStream is = new FileInputStream(new File(path));
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        String currentLine;
        while ((currentLine = in.readLine()) != null) {
            String[] split = currentLine.split(",");
            assert split.length == 2;
            Integer instanceID = Integer.parseInt(split[0]);
            assert !toReturn.containsKey(instanceID);
            Double cost = Double.parseDouble(split[1]);
            assert cost >= 0;
            toReturn.put(instanceID, cost);
        }
        System.out.println("[INFO] Done reading optimal costs, read " + toReturn.size() + " costs in total");
        return toReturn;
    }

    /*******************************************************************************************************************
     * Different Main definitions
     ******************************************************************************************************************/

    /**
     * For GridPathFinding with pivots
     */
    public static void mainGridPathFindingExperimentWithPivotsSingleThreaded() {
        // Solve with 100 instances
        try {
            WRAStar_General_Experiment experiment = new WRAStar_General_Experiment();
            experiment.runGridPathFindingWithPivotsExperimentWRAStarSingleThreaded(
                    // First instance ID
                    1,
                    // Instances Count
                    100,
                    // Output Path
                    //"results/gridpathfinding/generated/den400d.map/Inconsistent/generated+wrastar+extended-random-pivot-10",
                    "results/gridpathfinding/generated/brc202d.map/Inconsistent/generated+wrastar+extended-random-pivot-10",
                    //"results/gridpathfinding/generated/brc202d.map/Inconsistent/generated+wrastar+extended-random-pivot-10-bpmx",
                    //"results/gridpathfinding/generated/brc202d.map/Inconsistent/RANDOM_PIVOT_10/generated+wastar+extended-random-pivot-10-ar-at-iteration-1",
                    //"results/gridpathfinding/generated/maze512-1-6.map/generated+wrastar+extended",
                    null,
                    //WRAStar_General_Experiment.readOptimalCosts("input/gridpathfinding/generated/brc202d.map/optimal.raw"),
                    //WRAStar_General_Experiment.readOptimalCosts("input/gridpathfinding/generated/ost003d.map/optimal.raw"),
                    //WRAStar_General_Experiment.readOptimalCosts("input/gridpathfinding/generated/den400d.map/optimal.raw"),
                    // Add header
                    true);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    /*******************************************************************************************************************
     * Main :)
     ******************************************************************************************************************/

    public static void main(String[] args) {
        //WAStar_EES_GeneralExperiment.cleanAllSearchFiles();
        //WAStar_EES_GeneralExperiment.mainGeneralExperimentSingleThreaded();
        WRAStar_General_Experiment.mainGridPathFindingExperimentWithPivotsSingleThreaded();
        //WAStar_EES_GeneralExperiment.mainGeneralExperimentMultiThreaded();
    }
}
