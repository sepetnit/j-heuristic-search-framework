package org.cs4j.core.mains;

import org.cs4j.core.OutputResult;
import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchResult;
import org.cs4j.core.SearchResult.Solution;
import org.cs4j.core.algorithms.AStar;
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
public class EESGeneralExperiment {


    private static final String TEMPDIR = "C:\\Windows\\Temp\\";
    private static final int THREAD_COUNT = 7;

    Weights weights = new Weights();
    boolean reopenPossibilities[] = new boolean[]{true, false};

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
     * Private member definitions
     ******************************************************************************************************************/

    /***
     * Write a header line into the output
     * @param outputResult
     * @throws IOException
     */
    private void writeHeaderLineToOutput(OutputResult outputResult) throws IOException {
        // Write the header line
        outputResult.writeln(
                "InstanceID,Wh,Wg,Weight," +
                        "AR-Slv,AR-Dep,AR-Ggl,AR-Gen,AR-Exp,AR-Dup,AR-Oup,AR-Rep," +
                        "NR-Slv,NR-Dep,NR-Ggl,NR-Gen,NR-Exp,NR-Dup,NR-Oup,NR-Rep,"
        );
    }

    /**
     * Returns an initializes outputResult
     *
     * @param outputPath The output path (can be null)
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
                    output = new OutputResult(EESGeneralExperiment.TEMPDIR + tempFileName);
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
        if (writeHeader) {
            this.writeHeaderLineToOutput(output);
        }
        return output;
    }

    /*******************************************************************************************************************
     * Public member definitions
     ******************************************************************************************************************/

    /**
     * Runs an experiment using the WAStar and EES algorithms
     *
     * @param firstInstance The id of the first instance to solve
     * @param instancesCount The number of instances to solve
     * @param outputPath The name of the output file (can be null : in this case a random path will be chosen)
     *
     * @return The name of the output file where all the data recedes
     *
     * @throws java.io.IOException
     */
    public String runExperiment(int firstInstance, int instancesCount,
                                String outputPath, boolean needHeader) throws IOException {

        OutputResult output = this.getOutputResult(outputPath, needHeader);

        // Go over all the possible combinations and solve!
        for (int i = firstInstance; i <= instancesCount; ++i) {
            // Create the domain by reading the relevant instance file
            SearchDomain domain = EESGeneralExperiment.createVacuumRobotInstanceFromAutomaticallyGenerated(i + ".in");
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
                        // No solution
                        if (result.hasSolution()) {
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
                            List<Solution> solutions = result.getSolutions();
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
        return output.getFname();
    }

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

    private double[] _getOutOfMemoryResult() {
        return new double[]{-1, -1, -1, 0, 0, 0, 0, 0};
    }

    private class WorkerThread implements Runnable {
        int threadID;
        List<String> resultFiles;
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
                this.output = EESGeneralExperiment.this.getOutputResult(null, false);
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
                    this.output.appendNewResult(EESGeneralExperiment.this._getNoSolutionResult());
                } else {
                    double[] resultData = EESGeneralExperiment.this._getSolutionResult(result);
                    System.out.println("Done: " + Arrays.toString(resultData));
                    //System.out.println(solutions.get(0).dumpSolution());
                    this.output.appendNewResult(resultData);
                }
            } catch (OutOfMemoryError e) {
                System.out.println("Got out of memory :(");
                // The first -1 is for marking out-of-memory
                this.output.appendNewResult(EESGeneralExperiment.this._getOutOfMemoryResult());
            }
            this.output.close();
            this.resultFiles.add(this.output.getFname());
            System.out.println("Thread " + this.threadID + " is done :)");
        }
    }

    public void runExperimentMultiThreaded(int firstInstance, int instancesCount, String outputPath)
            throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(EESGeneralExperiment.THREAD_COUNT);
        List<String> resultFiles = new ArrayList<>();
        List<String> syncResultFiles = Collections.synchronizedList(resultFiles);

        try {
            int threadID = 0;
            System.out.println("[INFO] Creating threads ... ");
            // Go over all the possible combinations and solve!
            for (int i = firstInstance; i <= instancesCount; ++i) {
                // Create the domain by reading the relevant instance file
                SearchDomain domain =
                        EESGeneralExperiment.createDockyardRobotInstanceFromAutomaticallyGenerated(i + ".in");
                // Bypass not found files
                if (domain == null) {
                    continue;
                }
                for (Weights.SingleWeight w : weights.EXTENDED_WEIGHTS) {
                    double weight = w.getWeight();
                    for (boolean reopen : reopenPossibilities) {
                        SearchAlgorithm alg = new AStar(weight, reopen);
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
            for (String fname : syncResultFiles) {
                System.out.println(Utils.fileToString(fname).trim());
                System.out.println("[WARNING] Deleting " + fname + "(result: " + new File(fname).delete() + ")");
            }
        }
        System.out.println("Finished all threads");
    }

    /*******************************************************************************************************************
     * Different Main definitions
     ******************************************************************************************************************/

    public static void mainGeneralExperiment() {
        // Solve with 100 instances
        try {
            EESGeneralExperiment experiment = new EESGeneralExperiment();
            experiment.runExperiment(
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

    public static void mainGeneralExperimentMultiThreaded() {
        // Solve with 100 instances
        try {
            EESGeneralExperiment experiment = new EESGeneralExperiment();
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


    /*******************************************************************************************************************
     * Main :)
     ******************************************************************************************************************/

    public static void main(String[] args) {
        //EESGeneralExperiment.mainGeneralExperiment();
        EESGeneralExperiment.mainGeneralExperimentMultiThreaded();
    }

}
