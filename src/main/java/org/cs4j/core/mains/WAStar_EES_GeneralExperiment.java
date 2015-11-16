package org.cs4j.core.mains;

import org.cs4j.core.OutputResult;
import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchResult;
import org.cs4j.core.SearchResult.Solution;
import org.cs4j.core.algorithms.EES;
import org.cs4j.core.algorithms.WAStar;
import org.cs4j.core.data.Weights;
import org.cs4j.core.data.Weights.SingleWeight;
import org.cs4j.core.domains.*;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by sepetnit on 11/5/2015.
 *
 */
public class WAStar_EES_GeneralExperiment {

    /*******************************************************************************************************************
     * Private static fields
     ******************************************************************************************************************/

    private static final String TEMP_DIR = "C:\\Windows\\Temp\\";
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();

    /*******************************************************************************************************************
     * Private  fields
     ******************************************************************************************************************/

    private Weights weights = new Weights();
    private boolean reopenPossibilities[] = new boolean[]{true, false};

    /*******************************************************************************************************************
     * Private methods
     ******************************************************************************************************************/

    /**
     * No need for {true, false} reopen in case of only optimal weight
     *
     * @param weights The weights to run with
     * @param currentReopenPossibilities Current reopen policies array
     *
     * @return The updated array
     */
    private boolean[] _avoidUnnecessaryReopens(SingleWeight[] weights, boolean[] currentReopenPossibilities) {
        if (Arrays.equals(weights, this.weights.OPTIMAL_WEIGHTS)) {
            return new boolean[]{true};
        }
        return currentReopenPossibilities;
    }

    /**
     * Creates a header line for writing into output
     *
     * @return The created header line
     */
    private String _getHeader() {
        return "InstanceID,Wh,Wg,Weight," +
                "AR-Slv,AR-Dep,AR-Cst,AR-Gen,AR-Exp,AR-Dup,AR-Oup,AR-Rep," +
                "NR-Slv,NR-Dep,NR-Cst,NR-Gen,NR-Exp,NR-Dup,NR-Oup,NR-Rep,";
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
        Solution solution = result.getSolutions().get(0);
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

    /**
     * Returns an initialized outputResult object
     *
     * @param outputPath The output path (can be null and in this case a random file is created)
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
        // Temporary
        if (outputPath == null) {
            while (true) {
                try {
                    String tempFileName = prefix + UUID.randomUUID().toString().replace("-", "") + ".search";
                    output = new OutputResult(WAStar_EES_GeneralExperiment.TEMP_DIR + tempFileName);
                    break;
                } catch (FileAlreadyExistsException e) {
                    System.out.println("[WARNING] Output path found - trying again");
                }
            }
        } else {
            try {
                output = new OutputResult(outputPath, prefix, true);
                //Create the result file + overwrite if exists!
                //output = new OutputResult("results/vacuumrobot/generated/generated+wastar+extended", true);
                //output = new OutputResult("results/dockyardrobot/generated/generated+wastar+optimal", true);
                //output = new OutputResult("results/vacuumrobot/generated+ees+extended-test", true);
                //output = new OutputResult("results/fifteenpuzzle/korf100-new", true);
                //output = new OutputResult("results/pancakes/generated-40/generated+ees+extended", true);
            } catch (FileAlreadyExistsException e) {
                System.err.println("Output file " + outputPath + " already exists");
                System.exit(-1);
            }
        }
        // Add the header immediately if needed
        if (writeHeader) {
            this._writeHeaderLineToOutput(output);
        }
        return output;
    }

    /*******************************************************************************************************************
     * Private class for MultiThreaded run
     ******************************************************************************************************************/

    private class WorkerThread implements Runnable {
        private int threadID;
        // The names of all the result files are stored in this (synchronized) list
        private List<String> resultFiles;
        private SearchAlgorithm algorithm;
        private SearchDomain domain;
        private String problemDescription;
        private String outputFilePrefix;
        private OutputResult output;

        /**
         * The constructor of the worker thread
         *
         * @param threadID ID of the thread (for debugging reasons)
         * @param domain The domain to run on
         * @param algorithm The algorithm to run
         * @param description The description of the problem (can be null)
         * @param outputFilePrefix The prefix that should be added to the created output file
         * @param resultFiles The result files list - to add the result later
         *
         * @throws IOException If something wrong occurred
         */
        public WorkerThread(int threadID,
                            SearchDomain domain,
                            SearchAlgorithm algorithm,
                            String description,
                            String outputFilePrefix,
                            List<String> resultFiles) throws IOException {

            this.threadID = threadID;
            this.resultFiles = resultFiles;
            this.algorithm = algorithm;
            this.domain = domain;
            this.problemDescription = (description != null)? " (" +description+ ") " : "";
            this.outputFilePrefix = (outputFilePrefix != null)? outputFilePrefix : "";
            //System.out.println("Created Thread with ID " + threadID);
        }

        @Override
        public void run() {
            System.out.println("[INFO] Thread " + this.threadID + " is now running " + this.problemDescription);
            // Setup the output
            try {
                this.output = WAStar_EES_GeneralExperiment.this.getOutputResult(null, this.outputFilePrefix, false);
                System.out.println("[INFO] Thread " + this.threadID + " will be written to " + this.output.getFname());
            } catch (IOException e) {
                this.resultFiles.add("Failed (Alg: " + this.algorithm.toString() +
                        ", Domain: " + domain.toString() + ")");
                return;
            }
            // Otherwise, run
            try {
                SearchResult result = this.algorithm.search(this.domain);
                System.out.println("[INFO] Thread " + this.threadID + " is Done");
                // No solution
                if (!result.hasSolution()) {
                    this.output.appendNewResult(WAStar_EES_GeneralExperiment.this._getNoSolutionResult(result));
                    System.out.println("[INFO] Thread " + this.threadID + this.problemDescription + ": NoSolution");
                } else {
                    double[] resultData = WAStar_EES_GeneralExperiment.this._getSolutionResult(result);
                    this.output.appendNewResult(resultData);
                    System.out.println("[INFO] Thread " + this.threadID + this.problemDescription + ": " +
                            Arrays.toString(resultData));
                }
            } catch (OutOfMemoryError e) {
                this.output.appendNewResult(WAStar_EES_GeneralExperiment.this._getOutOfMemoryResult());
                System.out.println("[INFO] Thread " + this.threadID + this.problemDescription + ": OutOfMemory");
            }
            this.output.close();
            this.resultFiles.add(this.output.getFname());
            System.out.println("[INFO] Thread " + this.threadID + " is destructed");
        }
    }

    /*******************************************************************************************************************
     * Public member definitions
     ******************************************************************************************************************/

    /**
     * Runs an experiment using the WAStar and EES algorithms in a SINGLE THREAD!
     *
     * @param firstInstance The id of the first instance to solve
     * @param instancesCount The number of instances to solve
     * @param outputPath The name of the output file (can be null : in this case a random path will be chosen)
     *
     * @return Name of all the created output files
     *
     * @throws java.io.IOException
     */
    public String[] runGridPathFindingWithPivotsExperimentSingleThreaded(
            int firstInstance, int instancesCount, String outputPath, boolean needHeader) throws IOException {

        SingleWeight[] weights = this.weights.EXTENDED_WEIGHTS;
        int[] pivotsCounts = new int[]{1, 2, 3, 4, 5, 6, 8, 10};
        List<String> allOutputFiles = new ArrayList<>(pivotsCounts.length);

        // Create the domain by reading the first instance (the pivots DB is read once!)
        SearchDomain domain =
                DomainsCreation.createGridPathFindingInstanceFromAutomaticallyGeneratedWithTDH(
                        firstInstance + ".in",
                        10);

        for (int pivotsCount : pivotsCounts) {
            System.out.println("[INFO] Runs experiment with " + pivotsCount + " pivots");
            OutputResult output = this.getOutputResult(outputPath + "-" + pivotsCount, null, needHeader);
            // Go over all the possible combinations and solve!
            for (int i = firstInstance; i <= instancesCount; ++i) {
                // Create the domain by reading the relevant instance file
                //SearchDomain domain = DomainsCreation.createDockyardRobotInstanceFromAutomaticallyGenerated(i + ".in");
                domain =
                        DomainsCreation.createGridPathFindingInstanceFromAutomaticallyGeneratedWithTDH(
                                domain,
                                i + ".in",
                                pivotsCount);
                // Bypass not found files
                if (domain == null) {
                    continue;
                }
                for (Weights.SingleWeight w : weights) {
                    double weight = w.getWeight();
                    output.write(i + "," + w.wg + "," + w.wh + "," + weight + ",");
                    for (boolean reopen : this._avoidUnnecessaryReopens(weights, this.reopenPossibilities)) {
                        SearchAlgorithm alg = new WAStar(weight, reopen);
                        //SearchAlgorithm alg = new EES(weight, reopen);
                        System.out.println("[INFO] Instance: " + i + ", Weight: " + weight + ", Reopen: " + reopen);
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
     * Runs an experiment using the WAStar and EES algorithms in a SINGLE THREAD!
     *
     * @param firstInstance The id of the first instance to solve
     * @param instancesCount The number of instances to solve
     * @param outputPath The name of the output file (can be null : in this case a random path will be chosen)
     *
     * @return The name of the output file where all the data recedes
     *
     * @throws java.io.IOException
     */
    public String runExperimentSingleThreaded(int firstInstance, int instancesCount,
                                              String outputPath, boolean needHeader) throws IOException {

        OutputResult output = this.getOutputResult(outputPath, null, needHeader);
        SingleWeight[] weights = this.weights.EXTENDED_WEIGHTS;

        // Go over all the possible combinations and solve!
        for (int i = firstInstance; i <= instancesCount; ++i) {
            // Create the domain by reading the relevant instance file
            SearchDomain domain = DomainsCreation.createGridPathFindingInstanceFromAutomaticallyGenerated(i + ".in");
            // Bypass not found files
            if (domain == null) {
                continue;
            }
            for (Weights.SingleWeight w : weights) {
                double weight = w.getWeight();
                output.write(i + "," + w.wg + "," + w.wh + "," + weight + ",");
                for (boolean reopen : this._avoidUnnecessaryReopens(weights, this.reopenPossibilities)) {
                    SearchAlgorithm alg = new WAStar(weight, reopen);
                    //SearchAlgorithm alg = new EES(weight, reopen);
                    System.out.println("[INFO] Instance: " + i + ", Weight: " + weight + ", Reopen: " + reopen);
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
                }
                output.newline();
            }
        }
        output.close();
        return output.getFname();
    }

    /**
     * Runs an experiment using the WAStar and EES algorithms using MULTIPLE THREADS!
     *
     * @param firstInstance The id of the first instance to solve
     * @param instancesCount The number of instances to solve
     * @param outputPath The name of the output file (can be null : in this case a random path will be chosen)
     *
     * @throws java.io.IOException
     */
    public void runExperimentMultiThreaded(int firstInstance, int instancesCount, String outputPath)
            throws IOException {
        // -1 is because the main thread should also receive CPU
        // Another -1 : for the system ...
        int actualThreadCount = WAStar_EES_GeneralExperiment.THREAD_COUNT - 2;
        ExecutorService executor = Executors.newFixedThreadPool(actualThreadCount);
        List<String> resultFiles = new ArrayList<>();
        System.out.println("[INFO] Created thread pool with " + actualThreadCount + " threads");
        List<String> syncResultFiles = Collections.synchronizedList(resultFiles);

        // Weights
        SingleWeight[] weights = this.weights.OPTIMAL_WEIGHTS;
        //boolean[] reopenPossibilities = this.reopenPossibilities;
        boolean[] reopenPossibilities = this.reopenPossibilities;

        try {
            int threadID = 0;
            System.out.println("[INFO] Creating threads ... ");
            // Go over all the possible combinations and solve!
            for (int i = firstInstance; i <= instancesCount; ++i) {
                // Create the domain by reading the relevant instance file
                for (SingleWeight w : weights) {
                    double weight = w.getWeight();
                    for (boolean reopen : reopenPossibilities) {
                        SearchDomain domain =
                                DomainsCreation.createDockyardRobotInstanceFromAutomaticallyGenerated(i + ".in");
                        // Bypass not found files
                        if (domain == null) {
                            continue;
                        }
                        SearchAlgorithm alg = new WAStar(weight, reopen);
                        Runnable worker = new WorkerThread(
                                threadID++,
                                domain, alg,
                                "Instance: " + i + ", Weight: " + weight + ", Reopen: " + reopen,
                                i + "-" + weight + "-" + reopen + "-",
                                syncResultFiles);
                        executor.execute(worker);
                    }
                }
            }
            System.out.println("[INFO] Done creating " + threadID + " threads. Now waits them for finishing");
            executor.shutdown();
            while (!executor.isTerminated()) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    // Do nothing
                }
            }
        } finally {
            System.out.println("[INFO] All threads finished, unifying the data ...");
            outputPath = this.writeAllToFile(weights, resultFiles, outputPath);
            System.out.println("[INFO] Done - see " + outputPath);
            int deleted = 0;
            for (String filename : syncResultFiles) {
                if (new File(filename).delete()) {
                    ++deleted;
                }
            }
            System.out.println("[WARNING] Deleted " + deleted + "/" + syncResultFiles.size() + " temp files");
        }
    }

    /**
     * Takes all the single files written by multi-threaded run, and unifies them to a single file
     * @param weights The weights used in the run
     * @param resultFiles A collection of the result files
     * @param outputPath The name of the output file to write the output
     *
     * @return The name of the final output file
     *
     * @throws IOException If something wrong occurred
     */
    private String writeAllToFile(SingleWeight[] weights,
                                  List<String> resultFiles,
                                  String outputPath) throws IOException {
        OutputResult output = null;
        try {
            // Get new output result (and write the header)
            output = this.getOutputResult(outputPath, null, true);
        } catch (IOException e) {
            System.err.println("[ERROR] Got exception creating output: " + e.getMessage());
            System.out.println("[WARNING] Writing result to screen");
            System.exit(-1);
        }

        // Mapping from real weight to weight object

        Map<Double, SingleWeight> rawWeightToWeight = new HashMap<>();
        for (SingleWeight current : weights) {
            rawWeightToWeight.put(current.getWeight(), current);
        }

        Map<String, String> outputMap = new HashMap<>();

        // Go over all the result files and take their output
        for (String filename : resultFiles) {
            // System.out.println("[INFO] Working with file " + filename);
            // Get the basename from the file
            String baseName = new File(filename).getName();
            String[] split = baseName.split("-");
            assert split.length == 4;
            int instanceID = Integer.parseInt(split[0]);
            double weight = Double.parseDouble(split[1]);
            boolean reopen = Boolean.parseBoolean(split[2]);
            SingleWeight currentWeight = rawWeightToWeight.get(weight);
            assert currentWeight != null;
            String currentKey = instanceID + "," + currentWeight.wh + "," + currentWeight.wg + "," + weight;
            String currentResult = Utils.fileToString(filename).trim();
            // If already has some data - just append, otherwise, put the new data
            if (outputMap.containsKey(currentKey)) {
                String previousResult = outputMap.get(currentKey);
                // First AR, then NR
                if (reopen) {
                    outputMap.put(currentKey, currentResult + previousResult);
                } else {
                    outputMap.put(currentKey, previousResult + currentResult);
                }
            } else {
                outputMap.put(currentKey, currentResult);
            }
        }

        // Write the string to output or to screen
        StringBuilder sb = new StringBuilder();
        for (String key : outputMap.keySet()) {
            sb.append(key);
            sb.append(",");
            sb.append(outputMap.get(key));
            sb.append("\n");
        }
        if (output != null) {
            output.write(sb.toString());
            output.close();
            System.out.println("[INFO] Done writing to file " + output.getFname());
            return output.getFname();
        } else {
            System.out.println(sb.toString());
            return null;
        }
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
            WAStar_EES_GeneralExperiment experiment = new WAStar_EES_GeneralExperiment();
            experiment.runGridPathFindingWithPivotsExperimentSingleThreaded(
                    // First instance ID
                    1,
                    // Instances Count
                    100,
                    // Output Path
                    //"results/gridpathfinding/generated/brc202d.map/generated+wastar+extended-tdh-random",
                    "results/gridpathfinding/generated/maze512-1-6.map/generated+wastar+extended-tdh",
                    // Add header
                    true);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }


    /**
     * For single thread
     */
    public static void mainGeneralExperimentSingleThreaded() {
        // Solve with 100 instances
        try {
            WAStar_EES_GeneralExperiment experiment = new WAStar_EES_GeneralExperiment();
            experiment.runExperimentSingleThreaded(
                    // First instance ID
                    1,
                    // Instances Count
                    100,
                    // Output Path
                    //"results/dockyardrobot/generated-max-edge-2-out-of-place-30/generated-ees-extended",
                    "results/gridpathfinding/generated/maze512-1-6.map/generated+wastar+extended",
                    // Add header
                    true);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    /**
     * For multiple threads
     */
    public static void mainGeneralExperimentMultiThreaded() {
        // Solve with 100 instances
        try {
            WAStar_EES_GeneralExperiment experiment = new WAStar_EES_GeneralExperiment();
            experiment.runExperimentMultiThreaded(
                    // First instance ID
                    28,
                    // Instances Count
                    100,
                    // Output Path
                    "results/dockyardrobot/generated/generated+wastar+optimal-28.csv");
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    public static void cleanAllSearchFiles() {
        int cleaned = 0;
        File outDir = new File(WAStar_EES_GeneralExperiment.TEMP_DIR);
        for (File f: outDir.listFiles(
                new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return pathname.toString().endsWith(".search.csv");
                    }
                }
        )) {
            if (f.delete()) {
                ++cleaned;
            }
        }
        System.out.println("[INFO] Deleted " + cleaned + " files in total.");
    }

    /*******************************************************************************************************************
     * Main :)
     ******************************************************************************************************************/

    public static void main(String[] args) {
        //WAStar_EES_GeneralExperiment.cleanAllSearchFiles();
        WAStar_EES_GeneralExperiment.mainGeneralExperimentSingleThreaded();
        //WAStar_EES_GeneralExperiment.mainGridPathFindingExperimentWithPivotsSingleThreaded();
        //WAStar_EES_GeneralExperiment.mainGeneralExperimentMultiThreaded();
    }
}
