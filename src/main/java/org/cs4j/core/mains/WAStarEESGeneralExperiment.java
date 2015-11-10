package org.cs4j.core.mains;

import org.cs4j.core.OutputResult;
import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchResult;
import org.cs4j.core.SearchResult.Solution;
import org.cs4j.core.algorithms.WAStar;
import org.cs4j.core.data.Weights;
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
public class WAStarEESGeneralExperiment {

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
     * Private  static methods : Domains creation
     ******************************************************************************************************************/

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

    public static SearchDomain createDockyardRobotInstanceFromAutomaticallyGenerated(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/dockyardrobot/generated/" + instance));
        return new DockyardRobot(is);
    }

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
                "InstanceID,Wh,Wg,Weight," +
                        "AR-Slv,AR-Dep,AR-Ggl,AR-Gen,AR-Exp,AR-Dup,AR-Oup,AR-Rep," +
                        "NR-Slv,NR-Dep,NR-Ggl,NR-Gen,NR-Exp,NR-Dup,NR-Oup,NR-Rep,"
        );
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
                    output = new OutputResult(WAStarEESGeneralExperiment.TEMP_DIR + tempFileName);
                    break;
                } catch (FileAlreadyExistsException e) {
                    System.out.println("[WARNING] Output path found - trying again");
                }
            }
        } else {
            try {
                output = new OutputResult(outputPath, true);
                // Create the result file + overwrite if exists!
                // output = new OutputResult("results/vacuumrobot/generated/generated+wastar+extended", true);
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
        private OutputResult output;

        public WorkerThread(int threadID,
                            SearchDomain domain,
                            SearchAlgorithm algorithm,
                            List<String> resultFiles) throws IOException {

            this.threadID = threadID;
            this.resultFiles = resultFiles;
            this.algorithm = algorithm;
            this.domain = domain;
            //System.out.println("Created Thread with ID " + threadID);
        }

        @Override
        public void run() {
            System.out.println("Thread " + this.threadID + " is now running :)");
            // Setup the output
            try {
                this.output = WAStarEESGeneralExperiment.this.getOutputResult(null, false);
            } catch (IOException e) {
                this.resultFiles.add("Failed (Alg: " + this.algorithm.toString() +
                        ", Domain: " + domain.toString() + ")");
                return;
            }
            // Otherwise, run
            try {
                SearchResult result = this.algorithm.search(this.domain);
                List<Solution> solutions = result.getSolutions();
                // No solution
                if (!result.hasSolution()) {
                    this.output.appendNewResult(WAStarEESGeneralExperiment.this._getNoSolutionResult());
                } else {
                    double[] resultData = WAStarEESGeneralExperiment.this._getSolutionResult(result);
                    System.out.println("Done: " + Arrays.toString(resultData));
                    //System.out.println(solutions.get(0).dumpSolution());
                    this.output.appendNewResult(resultData);
                }
            } catch (OutOfMemoryError e) {
                System.out.println("Got out of memory :(");
                // The first -1 is for marking out-of-memory
                this.output.appendNewResult(WAStarEESGeneralExperiment.this._getOutOfMemoryResult());
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

        OutputResult output = this.getOutputResult(outputPath, needHeader);

        // Go over all the possible combinations and solve!
        for (int i = firstInstance; i <= instancesCount; ++i) {
            // Create the domain by reading the relevant instance file
            SearchDomain domain =
                    WAStarEESGeneralExperiment.createVacuumRobotInstanceFromAutomaticallyGenerated(i + ".in");
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
                    System.out.println("[INFO] Instance: " + i + ", Weight: " + weight + ", Reopen: " + reopen);
                    try {
                        SearchResult result = alg.search(domain);
                        // No solution
                        if (!result.hasSolution()) {
                            output.appendNewResult(this._getNoSolutionResult());
                        } else {
                            double[] resultData = this._getSolutionResult(result);
                            System.out.println(Arrays.toString(resultData));
                            //System.out.println(solutions.get(0).dumpSolution());
                            output.appendNewResult(resultData);
                        }
                    } catch (OutOfMemoryError e) {
                        System.out.println("Got out of memory :(");
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
        int actualThreadCount = WAStarEESGeneralExperiment.THREAD_COUNT - 1;
        ExecutorService executor = Executors.newFixedThreadPool(actualThreadCount);
        List<String> resultFiles = new ArrayList<>();
        System.out.println("[INFO] Created thread pool with " + actualThreadCount + " threads");
        List<String> syncResultFiles = Collections.synchronizedList(resultFiles);

        try {
            int threadID = 0;
            System.out.println("[INFO] Creating threads ... ");
            // Go over all the possible combinations and solve!
            for (int i = firstInstance; i <= instancesCount; ++i) {
                // Create the domain by reading the relevant instance file
                SearchDomain domain =
                        WAStarEESGeneralExperiment.createDockyardRobotInstanceFromAutomaticallyGenerated(i + ".in");
                // Bypass not found files
                if (domain == null) {
                    continue;
                }
                for (Weights.SingleWeight w : this.weights.EXTENDED_WEIGHTS) {
                    double weight = w.getWeight();
                    for (boolean reopen : this.reopenPossibilities) {
                        SearchAlgorithm alg = new WAStar(weight, reopen);
                        Runnable worker = new WorkerThread(threadID++, domain, alg, syncResultFiles);
                        executor.execute(worker);
                    }
                }
            }
            System.out.println("[INFO] Done creating " + threadID + " threads. Now waits them for finishing");
            executor.shutdown();
            while (!executor.isTerminated()) {
                try {
                    Thread.sleep(1000);
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
     * For single thread
     */
    public static void mainGeneralExperimentSingleThreaded() {
        // Solve with 100 instances
        try {
            WAStarEESGeneralExperiment experiment = new WAStarEESGeneralExperiment();
            experiment.runExperimentSingleThreaded(
                    // First instance ID
                    1,
                    // Instances Count
                    100,
                    // Output Path
                    "results/vacuumrobot/generated/generated+wastar+extended",
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
            WAStarEESGeneralExperiment experiment = new WAStarEESGeneralExperiment();
            experiment.runExperimentMultiThreaded(
                    // First instance ID
                    1,
                    // Instances Count
                    100,
                    // Output Path
                    "results/dockyardrobot/generated/test");
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    public static void cleanAllSearchFiles() {
        int cleaned = 0;
        File outDir = new File(WAStarEESGeneralExperiment.TEMP_DIR);
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
        WAStarEESGeneralExperiment.cleanAllSearchFiles();
        //EESGeneralExperiment.mainGeneralExperimentSingleThreaded();
        WAStarEESGeneralExperiment.mainGeneralExperimentMultiThreaded();
    }
}
