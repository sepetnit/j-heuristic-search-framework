package org.cs4j.core.mains;

import org.cs4j.core.OutputResult;
import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchResult;
import org.cs4j.core.SearchResult.Solution;
import org.cs4j.core.algorithms.BEES;
import org.cs4j.core.algorithms.PHS;
import org.cs4j.core.algorithms.PTS;
import org.cs4j.core.domains.Utils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by sepetnit on 11/5/2015.
 *
 */
public class PTS_BEES_PHS_GeneralExperiment {

    /*******************************************************************************************************************
     * Private static fields
     ******************************************************************************************************************/

    private static final String TEMP_DIR = "C:\\Windows\\Temp\\";
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();

    /*******************************************************************************************************************
     * Private  fields
     ******************************************************************************************************************/

    private boolean reopenPossibilities[] = new boolean[]{true, false};

    /*******************************************************************************************************************
     * Private methods
     ******************************************************************************************************************/

    /***
     * Write a header line into the output
     * @param outputResult The output result which points to a file
     * @throws IOException
     */
    private void _writeHeaderLineToOutput(OutputResult outputResult) throws IOException {
        // Write the header line
        outputResult.writeln(
                "InstanceID,MaxCost," +
                        "AR-Slv,AR-Dep,AR-Ggl,AR-Gen,AR-Exp,AR-Dup,AR-Oup,AR-Rep," +
                        "NR-Slv,NR-Dep,NR-Ggl,NR-Gen,NR-Exp,NR-Dup,NR-Oup,NR-Rep,"
        );
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
        // Append-  Sol- -1:out of memory
        //          Dep- -1,
        //          Ggl- -1,
        //          Gen-  0,
        //          Exp-  0,
        //          Dup-  0,
        //          Oup-  0 (updated in open),
        //          Rep-  0
        return new double[]{-1, -1, -1, 0, 0, 0, 0, 0};
    }

    /**
     * Returns an initialized outputResult object
     *
     * @param outputPath The output path (can be null and in this case a random file is created)
     * @param writeHeader Whether to add a header line immediately
     *
     * @return The created output result
     */
    private OutputResult getOutputResult(String outputPath, boolean writeHeader) throws IOException {
        OutputResult output = null;
        // Temporary
        if (outputPath == null) {
            while (true) {
                try {
                    String tempFileName = UUID.randomUUID().toString().replace("-", "") + ".search";
                    output = new OutputResult(PTS_BEES_PHS_GeneralExperiment.TEMP_DIR + tempFileName);
                    break;
                } catch (FileAlreadyExistsException e) {
                    System.out.println("[WARNING] Output path found - trying again");
                }
            }
        } else {
            try {
                output = new OutputResult(outputPath, true);
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
     * Max costs handling
     ******************************************************************************************************************/

    private class MaxCostsCreationElement {
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
        List<Integer> maxCostsAsList = new ArrayList<>(maxCosts);
        Collections.sort(maxCostsAsList);
        return Utils.integerListToArray(maxCostsAsList);
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
        private OutputResult output;

        /**
         * The constructor of the worker thread
         *
         * @param threadID ID of the thread (for debugging reasons)
         * @param domain The domain to run on
         * @param algorithm The algorithm to run
         * @param description The description of the problem (can be null)
         * @param resultFiles The result files list - to add the result later
         *
         * @throws IOException If something wrong occurred
         */
        public WorkerThread(int threadID,
                            SearchDomain domain,
                            SearchAlgorithm algorithm,
                            String description,
                            List<String> resultFiles) throws IOException {

            this.threadID = threadID;
            this.resultFiles = resultFiles;
            this.algorithm = algorithm;
            this.domain = domain;
            this.problemDescription = (description != null)? " (" +description+ ") " : "";
            //System.out.println("Created Thread with ID " + threadID);
        }

        @Override
        public void run() {
            System.out.println("[INFO] Thread " + this.threadID + " is now running " + this.problemDescription);
            // Setup the output
            try {
                this.output = PTS_BEES_PHS_GeneralExperiment.this.getOutputResult(null, false);
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
                    this.output.appendNewResult(PTS_BEES_PHS_GeneralExperiment.this._getNoSolutionResult(result));
                    System.out.println("[INFO] Thread " + this.threadID + this.problemDescription + ": NoSolution");
                } else {
                    double[] resultData = PTS_BEES_PHS_GeneralExperiment.this._getSolutionResult(result);
                    this.output.appendNewResult(resultData);
                    System.out.println("[INFO] Thread" + this.threadID + this.problemDescription + ": " +
                            Arrays.toString(resultData));
                }
            } catch (OutOfMemoryError e) {
                this.output.appendNewResult(PTS_BEES_PHS_GeneralExperiment.this._getOutOfMemoryResult());
                System.out.println("[INFO] Thread " + this.threadID + this.problemDescription + ": OutOfMemory");
            }
            this.output.close();
            this.resultFiles.add(this.output.getFname());
            System.out.println("Thread " + this.threadID + " is done :)");
        }
    }

    /*******************************************************************************************************************
     * Public member definitions
     ******************************************************************************************************************/

    /**
     * Runs an experiment using the different algorithms, in a SINGLE THREAD!
     *
     * @param firstInstance The id of the first instance to solve
     * @param instancesCount The number of instances to solve
     * @param maxCosts The max costs array
     * @param outputPath The name of the output file (can be null : in this case a random path will be chosen)
     *
     * @return Names of the output files where all the data recedes
     *
     * @throws java.io.IOException
     */
    public String[] runExperimentSingleThreaded(int firstInstance, int instancesCount, int[] maxCosts,
                                                String outputPath, boolean needHeader) throws IOException {

        // Run using PHS, BEES, PTS
        //SearchAlgorithm[] algorithms = new SearchAlgorithm[]{new BEES(), new PTS(), new PHS()};
        SearchAlgorithm[] algorithms = new SearchAlgorithm[]{new PTS()};
        List<String> resultFiles = new ArrayList<>();

        for (SearchAlgorithm alg : algorithms) {
            System.out.println("[INFO] Experimenting with " + alg.getName() + " algorithm");
            String currentOutputPath = outputPath.replace("<alg-name>", alg.getName());
            OutputResult output = this.getOutputResult(currentOutputPath, needHeader);

            int[] realMaxCosts = maxCosts;

            // in case the maxCosts were not given - let's create some default costs array
            if (maxCosts == null) {
                realMaxCosts = PTS_BEES_PHS_GeneralExperiment.createMaxCosts(
                        new MaxCostsCreationElement[]{
                                //new MaxCostsCreationElement(0, 5, 2000)
                                //new MaxCostsCreationElement(80, 5, 800)
                                //new MaxCostsCreationElement(100, 5, 800)
                                new MaxCostsCreationElement(300, 5, 2000)
                        }
                );
                System.out.println("[WARNING] Created default costs array");
            }

            // Go over all the possible combinations and solve!
            for (int i = firstInstance; i <= instancesCount; ++i) {
                // Create the domain by reading the relevant instance file
                SearchDomain domain =
                        DomainsCreation.createGridPathFindingInstanceFromAutomaticallyGenerated(i + ".in");
                // Bypass not found files
                if (domain == null) {
                    continue;
                }
                for (int maxCost : realMaxCosts) {
                    output.write(i + "," + maxCost + ",");
                    for (boolean reopen : this.reopenPossibilities) {
                        alg.setAdditionalParameter("max-cost", maxCost + "");
                        alg.setAdditionalParameter("reopen", reopen + "");
                        System.out.println("[INFO] Instance: " + i + ", MaxCost: " + maxCost + ", Reopen: " + reopen);
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
                            System.out.println("[INFO] Done: OutOfMemory :-(");
                            output.appendNewResult(this._getOutOfMemoryResult());
                        }
                    }
                    output.newline();
                }
            }
            output.close();
            resultFiles.add(output.getFname());
        }
        return resultFiles.toArray(new String[algorithms.length]);
    }

    /**
     * Runs the experiment, but now uses NR and only if failed runs again with AR
     *
     * @param firstInstance The id of the first instance to solve
     * @param instancesCount The number of instances to solve
     * @param maxCosts The max costs array
     * @param outputPath The name of the output file (can be null : in this case a random path will be chosen)
     *
     * @return Names of the output files where all the data recedes
     *
     * @throws java.io.IOException
     */
    public String[] runExperimentSingleThreadedWithReRun(int firstInstance, int instancesCount, int[] maxCosts,
                                                         String outputPath) throws IOException {


        // Run using PHS, BEES, PTS
        //SearchAlgorithm[] algorithms = new SearchAlgorithm[]{new PTS(), new BEES(), new PHS()};
        SearchAlgorithm[] algorithms = new SearchAlgorithm[]{new PTS()};
        List<String> resultFiles = new ArrayList<>();

        for (SearchAlgorithm alg : algorithms) {
            System.out.println("[INFO] Experimenting with " + alg.getName() + " algorithm");
            String currentOutputPath = outputPath.replace("<alg-name>", alg.getName());
            OutputResult output = this.getOutputResult(currentOutputPath, false);
            // Write the header line
            output.writeln(
                    "InstanceID,MaxCost," +
                            "NAR-Slv,NAR-Dep,NAR-Cst,NAR-Gen,NAR-Exp,NAR-Dup,NAR-Oup,NAR-Rep,"
            );

            int[] realMaxCosts = maxCosts;

            // in case the maxCosts were not given - let's create some default costs array
            if (maxCosts == null) {
                realMaxCosts = PTS_BEES_PHS_GeneralExperiment.createMaxCosts(
                        new MaxCostsCreationElement[]{
                                // DockyardRobot
                                //new MaxCostsCreationElement(10, 5, 400)
                                // VacuumRobot
                                //new MaxCostsCreationElement(0, 5, 2000)
                                // ost003d.map
                                //new MaxCostsCreationElement(80, 5, 800)
                                // den400d.map
                                //new MaxCostsCreationElement(100, 5, 800)
                                // brc002d.map
                                new MaxCostsCreationElement(300, 5, 2000)
                        }
                );
                System.out.println("[WARNING] Created default costs array");
            }

            // Go over all the possible combinations and solve!
            for (int i = firstInstance; i <= instancesCount; ++i) {
                // Create the domain by reading the relevant instance file
                SearchDomain domain =
                        DomainsCreation.createGridPathFindingInstanceFromAutomaticallyGenerated(i + ".in");
                // Bypass not found files
                if (domain == null) {
                    continue;
                }
                for (int maxCost : realMaxCosts) {
                    output.write(i + "," + maxCost + ",");
                    alg.setAdditionalParameter("max-cost", maxCost + "");
                    alg.setAdditionalParameter("reopen", false + "");
                    alg.setAdditionalParameter("rerun-type-if-not-found", "continue-ar");
                    System.out.println("[INFO] Instance: " + i + ", MaxCost: " + maxCost);
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
                        System.out.println("[INFO] Done: OutOfMemory :-(");
                        output.appendNewResult(this._getOutOfMemoryResult());
                    }
                    output.newline();
                }
            }
            output.close();
            resultFiles.add(output.getFname());
        }
        return resultFiles.toArray(new String[algorithms.length]);
    }

    /**
     * Runs an experiment using the WAStar and EES algorithms using MULTIPLE THREADS!
     *
     * @param firstInstance The id of the first instance to solve
     * @param instancesCount The number of instances to solve
     * @param maxCosts The max costs array
     * @param outputPath The name of the output file (can be null : in this case a random path will be chosen)
     *
     * @throws java.io.IOException
     */
    public void runExperimentMultiThreaded(int firstInstance, int instancesCount, int[] maxCosts, String outputPath)
            throws IOException {
        // -1 is because the main thread should also receive CPU
        // Another -1 : for the system ...
        int actualThreadCount = PTS_BEES_PHS_GeneralExperiment.THREAD_COUNT - 2;
        ExecutorService executor = Executors.newFixedThreadPool(actualThreadCount);
        List<String> resultFiles = new ArrayList<>();
        System.out.println("[INFO] Created thread pool with " + actualThreadCount + " threads");
        List<String> syncResultFiles = Collections.synchronizedList(resultFiles);

        int[] realMaxCosts = maxCosts;

        // in case the maxCosts were not given - let's create some default costs array
        if (maxCosts == null) {
            realMaxCosts = PTS_BEES_PHS_GeneralExperiment.createMaxCosts(
                    new MaxCostsCreationElement[]{
                            new MaxCostsCreationElement(100, 10, 400)
                    }
            );
        }

        try {
            int threadID = 0;
            System.out.println("[INFO] Creating threads ... ");
            // Go over all the possible combinations and solve!
            for (int i = firstInstance; i <= instancesCount; ++i) {
                for (int maxCost : realMaxCosts) {
                    for (boolean reopen : this.reopenPossibilities) {
                        // Create the domain by reading the relevant instance file
                        SearchDomain domain =
                                DomainsCreation.create15PuzzleInstanceFromKorfInstancesPDB555(i + ".in");
                        // Bypass not found files
                        if (domain == null) {
                            continue;
                        }
                        SearchAlgorithm alg = new PTS();
                        alg.setAdditionalParameter("maxCost", maxCost + "");
                        alg.setAdditionalParameter("reopen", reopen + "");
                        Runnable worker = new WorkerThread(
                                threadID++,
                                domain, alg,
                                "Instance: " + i + ", MaxCost: " + maxCost + ", Reopen: " + reopen,
                                syncResultFiles);
                        executor.execute(worker);
                    }
                }
            }
            System.out.println("[INFO] Done creating " + threadID + " threads. Now waits them for finishing");
            executor.shutdown();
            while (!executor.isTerminated()) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    // Do nothing
                }
            }
        } finally {
            for (String filename : syncResultFiles) {
                System.out.println(Utils.fileToString(filename).trim());
                System.out.println("[WARNING] Deleting " + filename + "(result: " + new File(filename).delete() + ")");
            }
        }
        System.out.println("Finished all threads");
    }

    /*******************************************************************************************************************
     * Different Main definitions
     ******************************************************************************************************************/

    /**
     * Documentation for maxCosts+Output
     *
     * Fifteen Puzzle: new int[] {55, 60, 65, 70, 75, 80, 85, 90} =>
     *                 "results/fifteenpuzzle/korf100-new/pts-55-60-65-70-75-80-85-90",
     * Grids-brc202d:  new MaxCostsCreationElement(300, 20, 2000)
     *                 "results/gridpathfinding/generated/brc202d.map/pts-300-20-2000",
     *
     */

    /**
     * For single thread
     */
    public static void mainGeneralExperimentSingleThreaded() {
        // Solve with 100 instances
        try {
            PTS_BEES_PHS_GeneralExperiment experiment = new PTS_BEES_PHS_GeneralExperiment();
            experiment.runExperimentSingleThreaded(
                    // First instance ID
                    1,
                    // Instances Count
                    100,
                    // Max costs
                    null,
                    // Output Path
                    //"results/vacuumrobot/generated-10-dirt/last_<alg-name>-0-5-2000",
                    //"results/gridpathfinding/generated/ost003d.map/<alg-name>-80-5-800",
                    //"results/gridpathfinding/generated/den400d.map/<alg-name>-100-5-800",
                    "results/gridpathfinding/generated/brc202d.map/<alg-name>-300-5-2000",
                    // Add header
                    true);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    public static void mainGeneralExperimentSingleThreadedWithReRun() {
        // Solve with 100 instances
        try {
            PTS_BEES_PHS_GeneralExperiment experiment = new PTS_BEES_PHS_GeneralExperiment();
            experiment.runExperimentSingleThreadedWithReRun(
                    // First instance ID
                    1,
                    // Instances Count
                    100,
                    // Max costs
                    null,
                    // Output Path
                    //"results/dockyardrobot/generated-max-edge-2-out-of-place-30/<alg-name>-10-5-400-rerun");
                    //"results/vacuumrobot/generated-10-dirt/<alg-name>-0-5-2000-rerun");
                    //"results/gridpathfinding/generated/ost003d.map/<alg-name>-80-5-800-rerun");
                    "results/gridpathfinding/generated/brc202d.map/<alg-name>-300-5-2000-rerun");
                    //"results/gridpathfinding/generated/den400d.map/<alg-name>-100-5-800-rerun");
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    public static void cleanAllSearchFiles() {
        int cleaned = 0;
        File outDir = new File(PTS_BEES_PHS_GeneralExperiment.TEMP_DIR);
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
        //PTS_BEES_PHS_GeneralExperiment.cleanAllSearchFiles();
        //PTS_BEES_PHS_GeneralExperiment.mainGeneralExperimentSingleThreaded();
        PTS_BEES_PHS_GeneralExperiment.mainGeneralExperimentSingleThreadedWithReRun();
        //PTS_BEES_PHS_GeneralExperiment.mainGeneralExperimentMultiThreaded();
    }
}
