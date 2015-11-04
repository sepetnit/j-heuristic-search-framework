import org.cs4j.core.OutputResult;
import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchResult;
import org.cs4j.core.algorithms.*;
import org.cs4j.core.data.Weights;
import org.cs4j.core.domains.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.util.Arrays;

public class Main {

    public double[] testSearchAlgorithm(SearchDomain domain, SearchAlgorithm algo) {
        SearchResult result = algo.search(domain);
        SearchResult.Solution sol = result.getSolutions().get(0);
        //System.out.println(result.getWallTimeMillis());
        //System.out.println(result.getCpuTimeMillis());
//        System.out.println("Generated: "+result.getGenerated());
//        System.out.println("Expanded: "+result.getExpanded());
//        System.out.println("Cost: "+sol.getCost());
//        System.out.println("Length: "+sol.getLength());
        return new double[]{result.getGenerated(),result.getExpanded(),sol.getCost(),sol.getLength()};
    }
    public double[] testKBFSEES(int k, String puzzle) throws FileNotFoundException {
        SearchDomain domain = createFifteenPuzzleKorf(puzzle);
        SearchAlgorithm algo = new EESKBFS(2,k);
        return testSearchAlgorithm(domain, algo);
    }

    public double[] testKBFSEESV2(int k, String puzzle) throws FileNotFoundException {
        SearchDomain domain = createFifteenPuzzleKorf(puzzle);
        SearchAlgorithm algo = new EESKBFSV2(2,k);
        return testSearchAlgorithm(domain, algo);
    }

    public void testEES() throws FileNotFoundException {
        SearchDomain domain = createFifteenPuzzleKorf("82");
        SearchAlgorithm algo = new EES(2);
        testSearchAlgorithm(domain, algo);
    }

    public void testRBFS() throws FileNotFoundException {
        SearchDomain domain = createFifteenPuzzleKorf("80");
        SearchAlgorithm algo = new RBFS();
        testSearchAlgorithm(domain, algo);
    }

    public void testAstar() throws FileNotFoundException {
        SearchDomain domain = createFifteenPuzzleKorf("20");
        SearchAlgorithm algo = new AStar();
        testSearchAlgorithm(domain, algo);
    }

    public SearchDomain createFifteenPuzzleMine(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/fifteenpuzzle/mine1000/"+instance));
        FifteenPuzzle puzzle = new FifteenPuzzle(is, FifteenPuzzle.COST_FUNCTION.HEAVY);
        return puzzle;
    }

    public SearchDomain createFifteenPuzzleKorf(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/fifteenpuzzle/korf100/"+instance));
        FifteenPuzzle puzzle = new FifteenPuzzle(is, FifteenPuzzle.COST_FUNCTION.HEAVY);
        return puzzle;
    }

    public SearchDomain createGridPathFinding(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/gridpathfinding/test/"+instance));
        GridPathFinding gridPathFindingInstance = new GridPathFinding(is);
        return gridPathFindingInstance;
    }

    public SearchDomain createFifteenPuzzleUnit(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/fifteenpuzzle/korf100/"+instance));
        FifteenPuzzle puzzle = new FifteenPuzzle(is);
        return puzzle;
    }

    public SearchDomain createPancakesUnit(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/pancakes/"+instance));
        Pancakes puzzle = new Pancakes(is);
        return puzzle;
    }

    public SearchDomain createDockyardRobot(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/dockyardrobot/mine/" + instance));
        return new DockyardRobot(is);
    }

    public SearchDomain createVacuumRobot(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/vacuumrobot/mine/" + instance));
        return new VacuumRobot(is);
    }

    public static void mainFifteenPuzzleDomain(String[] args) throws IOException {
        Main mainTest = new Main();
        SearchDomain domain = mainTest.createFifteenPuzzleKorf("2");
        SearchAlgorithm alg = new AStar();
        SearchResult result = alg.search(domain);
        if (result.getSolutions().size() > 0) {
            double d[] = new double[]{
                    1,
                    1,
                    result.getSolutions().get(0).getLength(),
                    result.getSolutions().get(0).getCost(),
                    result.getGenerated(),
                    result.getExpanded(),
                    ((SearchResultImpl) result).reopened};
            System.out.println(Arrays.toString(d));
            System.out.println(result.getSolutions().get(0).dumpSolution());
        } else {
            System.out.println("No solution :-(");
        }
    }

    public static void mainGridPathFindingDomain(String[] args) throws IOException {
        Main mainTest = new Main();
        SearchDomain domain = mainTest.createGridPathFinding("brc202d.map");
        SearchAlgorithm alg = new AStar();
        SearchResult result = alg.search(domain);
        if (result.getSolutions().size() > 0) {
            double d[] = new double[]{
                    1,
                    1,
                    result.getSolutions().get(0).getLength(),
                    result.getSolutions().get(0).getCost(),
                    result.getGenerated(),
                    result.getExpanded(),
                    ((SearchResultImpl) result).reopened};
            System.out.println(Arrays.toString(d));
            System.out.println(result.getSolutions().get(0).dumpSolution());
        } else {
            System.out.println("No solution :-(");
        }
    }

    public static void mainPancakesDomain(String[] args) throws IOException {
        Main mainTest = new Main();
        SearchDomain domain = mainTest.createPancakesUnit("1.in");
        SearchAlgorithm alg = new AStar();
        SearchResult result = alg.search(domain);
        if (result.getSolutions().size() > 0) {
            double d[] = new double[]{
                    1,
                    1,
                    result.getSolutions().get(0).getLength(),
                    result.getSolutions().get(0).getCost(),
                    result.getGenerated(),
                    result.getExpanded(),
                    ((SearchResultImpl) result).reopened};
            System.out.println(Arrays.toString(d));
            System.out.println(result.getSolutions().get(0).dumpSolution());
        } else {
            System.out.println("No solution :-(");
        }
    }

    public static void mainDRobotDomain(String[] args) throws IOException {
        Main mainTest = new Main();
        SearchDomain domain = mainTest.createDockyardRobot("2.in");
        SearchAlgorithm alg = new AStar();
        SearchResult result = alg.search(domain);
        if (result.getSolutions().size() > 0) {
            double d[] = new double[]{
                    1,
                    1,
                    result.getSolutions().get(0).getLength(),
                    result.getSolutions().get(0).getCost(),
                    result.getGenerated(),
                    result.getExpanded(),
                    ((SearchResultImpl) result).reopened};
            System.out.println(Arrays.toString(d));
            System.out.printf(result.getSolutions().get(0).dumpSolution());
        } else {
            System.out.println("No solution :-(");
        }
    }

    public static void mainVacuumRobotDomain(String[] args) throws IOException {
        Main mainTest = new Main();
        SearchDomain domain = mainTest.createVacuumRobot("1.in");
        SearchAlgorithm alg = new AStar();
        SearchResult result = alg.search(domain);
        if (result.getSolutions().size() > 0) {
            double d[] = new double[]{
                    1,
                    1,
                    result.getSolutions().get(0).getLength(),
                    result.getSolutions().get(0).getCost(),
                    result.getGenerated(),
                    result.getExpanded(),
                    ((SearchResultImpl) result).reopened};
            System.out.println(Arrays.toString(d));
            System.out.printf(result.getSolutions().get(0).dumpSolution());
        } else {
            System.out.println("No solution :-(");
        }
    }

    public static void mainPTS(String[] args) throws IOException {
        Main mainTest = new Main();
        SearchDomain domain = mainTest.createFifteenPuzzleUnit(1 + "");
        SearchAlgorithm alg = new PTS(50, true);
        SearchResult result = alg.search(domain);
        if (result.getSolutions().size() > 0) {
            double d[] = new double[]{
                1,
                1,
                result.getSolutions().get(0).getLength(),
                //result.getSolutions().get(0).getLength(),
                result.getGenerated(),
                result.getExpanded(),
                ((SearchResultImpl) result).reopened};
            System.out.println(Arrays.toString(d));
        } else {
            System.out.println("No solution :-(");
        }
    }

    public static void main1(String[] args) throws IOException {
        Main mainTest = new Main();
        Weights weights = new Weights();
        //System.out.println("wh,wg,wh/wg,AR-Depth,AR-Generated,AR-Expanded,AR-Reopened,NR-Depth,NR-Generated,NR-Expanded,NR-Reopened");
        boolean reopenPossibilities[] = new boolean[] {true, false};

        for (Weights.SingleWeight w : weights.fullWeights) {
            double totalWeight = w.wh / w.wg;
            System.out.println("Solving for weight: wg : " + w.wg + " wh: " + w.wh);
            for (boolean reopen : reopenPossibilities) {
                try {
                    OutputResult output = new OutputResult(w.wg, w.wh, reopen);
                    output.write("InstanceID,Found,Depth,Generated, Expanded,Reopened");
                    for (int i = 1 ; i <= 100; ++i) {
                        SearchDomain domain = mainTest.createFifteenPuzzleKorf(i + "");
                        SearchAlgorithm alg = new WAstar(totalWeight, reopen);
                        System.out.println("Solving instance " + i + " For weight " + totalWeight + " reopen? " + reopen);
                        SearchResult result = alg.search(domain);
                        double d[];
                        if (result.getSolutions().size() > 0) {
                             d = new double[]{
                                     i,
                                    1,
                                    result.getSolutions().get(0).getLength()    ,
                                    //result.getSolutions().get(0).getLength(),
                                    result.getGenerated(),
                                    result.getExpanded(),
                                    ((SearchResultImpl) result).reopened};
                        } else {
                            d = new double[] {
                                    i, 0, 0, 0, 0, 0
                            };
                        }
                    output.appendNewResult(d);
                        output.newline();
                    }
                    output.close();
                } catch (FileAlreadyExistsException e) {
                    System.out .println("File already found for wg " + w.wg + " wh " + w.wh);
                }
            }
        }
        //mainTest.testEES();
        //System.out.println("--------");
        //mainTest.testKBFSEES(2);
        //mainTest.testAstar();
        /*
        int[] kArray = new int[]{1,50,100,1000,1500, 2000};
        System.out.println("K,Generated,Expanded,Cost,Length");
        for (int k : kArray) {
            double[] meanArray = new double[]{0,0,0,0};
//            System.out.println("K:"+k);
            for (int i = 1; i <101 ; i++) {
                double[] res = mainTest.testKBFSEESV2(k, Integer.toString(i));
                meanArray[0]+=res[0];
                meanArray[1]+=res[1];
                meanArray[2]+=res[2];
                meanArray[3]+=res[3];
            }
            meanArray[0]=meanArray[0]/100;
            meanArray[1]=meanArray[1]/100;
            meanArray[2]=meanArray[2]/100;
            meanArray[3]=meanArray[3]/100;
            System.out.println(k+","+meanArray[0]+","+meanArray[1]+","+meanArray[2]+","+meanArray[3]);

        }
        */

//        mainTest.testKBFSEES(1);
//        System.out.println("--------");
//        mainTest.testEES();
//        System.out.println("--------");
//        mainTest.testEES();
//        System.out.println("--------");
//        mainTest.testEES();
    }


    public static void main(String[] args) throws IOException {
        //Main.mainFifteenPuzzleDomain(args);
        //Main.mainGridPathFindingDomain(args);
        //Main.mainPancakesDomain(args);
        Main.mainVacuumRobotDomain(args);
        //Main.mainDRobotDomain(args);
    }
}