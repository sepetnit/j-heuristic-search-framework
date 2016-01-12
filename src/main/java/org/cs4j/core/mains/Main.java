package org.cs4j.core.mains;

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
        SearchAlgorithm algo = new WAStar();
        testSearchAlgorithm(domain, algo);
    }

    public SearchDomain createFifteenPuzzleMine(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/fifteenpuzzle/mine1000/"+instance));
        FifteenPuzzle puzzle = new FifteenPuzzle(is, FifteenPuzzle.COST_FUNCTION.HEAVY);
        return puzzle;
    }

    public SearchDomain createFifteenPuzzleKorf(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/fifteenpuzzle/korf100/"+instance));
        FifteenPuzzle puzzle = new FifteenPuzzle(is, FifteenPuzzle.COST_FUNCTION.UNIT);
        return puzzle;
    }

    public SearchDomain createGridPathFinding(String instance) throws FileNotFoundException {
        //InputStream is = new FileInputStream(new File("input/gridpathfinding/generated/brc202d.map/"+instance));
        InputStream is = new FileInputStream(new File("input/gridpathfinding/generated/maze512-1-6.map/"+instance));
        GridPathFinding gridPathFindingInstance = new GridPathFinding(is);
        return gridPathFindingInstance;
    }

    public SearchDomain createGridPathFindingWithPivots(String instance) throws FileNotFoundException {
        String pivotsFileName = "input/gridpathfinding/raw/mazes/maze1/_maze512-1-6.map.pivots.pdb";
        InputStream is = new FileInputStream(new File("input/gridpathfinding/generated/maze512-1-6.map/"+instance));
        GridPathFinding gridPathFindingInstance = new GridPathFinding(is);
        gridPathFindingInstance.setAdditionalParameter("heuristic", "dh-furthest");
        //problem.setAdditionalParameter("heuristic", "dh-md-average-md-if-dh-is-0");
        gridPathFindingInstance.setAdditionalParameter("pivots-distances-db-file", pivotsFileName);
        gridPathFindingInstance.setAdditionalParameter("pivots-count", 1 + "");
        return gridPathFindingInstance;
    }

    public SearchDomain createFifteenPuzzleUnitWithPDB78(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/fifteenpuzzle/korf100/"+instance));
        FifteenPuzzle puzzle = new FifteenPuzzle(is);
        String PDBsDirs[] = new String[]{"C:\\users\\user\\", "H:\\"};
        puzzle.setAdditionalParameter("heuristic", "pdb-78");
        for (String PDBsDir : PDBsDirs) {
            try {
                puzzle.setAdditionalParameter(
                        "pdb-78-files",
                        PDBsDir + "PDBs\\15-puzzle\\dis_1_2_3_4_5_6_7," +
                                PDBsDir + "PDBs\\15-puzzle\\dis_8_9_10_11_12_13_14_15");
                break;
            } catch (IllegalArgumentException e) { }
        }
        puzzle.setAdditionalParameter("use-reflection", true + "");
        return puzzle;
    }

    public SearchDomain createFifteenPuzzleUnitWithPDB555(String instance) throws FileNotFoundException {
        //InputStream is = new FileInputStream(new File("input/fifteenpuzzle/mine100-last-year/"+instance));
        InputStream is = new FileInputStream(new File("input/fifteenpuzzle/korf100/"+instance));
        FifteenPuzzle puzzle = new FifteenPuzzle(is);
        String PDBsDirs[] = new String[]{"C:\\users\\user\\", "H:\\"};
        puzzle.setAdditionalParameter("heuristic", "pdb-555");
        for (String PDBsDir : PDBsDirs) {
            try {
                puzzle.setAdditionalParameter(
                        "pdb-555-files",
                        PDBsDir + "PDBs\\15-puzzle\\dis_1_2_3_4_5," +
                                PDBsDir + "PDBs\\15-puzzle\\dis_6_7_8_9_10," +
                                PDBsDir + "PDBs\\15-puzzle\\dis_11_12_13_14_15");
                break;
            } catch (IllegalArgumentException e) { }
        }
        puzzle.setAdditionalParameter("use-reflection", true + "");
        return puzzle;
    }

    public SearchDomain createFifteenPuzzleUnit(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/fifteenpuzzle/korf100/"+instance));
        FifteenPuzzle puzzle = new FifteenPuzzle(is);
        return puzzle;
    }

    public SearchDomain createPancakesUnit(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/pancakes/generated/"+instance));
        Pancakes puzzle = new Pancakes(is);
        Pancakes.MIN_PANCAKE_FOR_PDB = 10;
        return puzzle;
    }

    public SearchDomain createDockyardRobot(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/dockyardrobot/generated-max-edge-2-out-of-place-30/" + instance));
        return new DockyardRobot(is);
    }

    public SearchDomain createVacuumRobot(String instance) throws FileNotFoundException {
        //InputStream is = new FileInputStream(new File("input/vacuumrobot/mine/" + instance));
        InputStream is = new FileInputStream(new File("input/vacuumrobot/generated/" + instance));
        return new VacuumRobot(is, VacuumRobot.COST_FUNCTION.HEAVY);
    }

    public SearchDomain createTopSpin10(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/topspin/topspin10/" + instance));
        return new TopSpin(is);
    }

    public SearchDomain createTopSpin12(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/topspin/topspin12/" + instance));
        return new TopSpin(is);
    }

    public SearchDomain createRawGraph() throws FileNotFoundException {
        // TODO: Read the graph from file
        return new RawGraph();
    }

    public static void mainFifteenPuzzleDomain(String[] args) throws IOException {
        Main mainTest = new Main();

        SearchDomain domain = mainTest.createFifteenPuzzleUnitWithPDB555("1.in");
        //SearchDomain domain = mainTest.createFifteenPuzzleKorf("2.in");
        SearchAlgorithm alg = new WAStar();
        SearchResult result = alg.search(domain);
        if (result.hasSolution()) {
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
        SearchDomain domain = mainTest.createGridPathFinding("1.in");
        //SearchAlgorithm alg = new EES(1, true);
        SearchAlgorithm alg = new WAStar();
        alg.setAdditionalParameter("weight", "1.0");
        alg.setAdditionalParameter("reopen", "true");
        SearchResult result = alg.search(domain);
        assert result.getSolutions().size() == 1;
        if (result.hasSolution()) {
            double d[] = new double[]{
                    1,
                    1,
                    result.getSolutions().get(0).getLength(),
                    result.getSolutions().get(0).getCost(),
                    result.getGenerated(),
                    result.getExpanded(),
                    ((SearchResultImpl) result).reopened};
            System.out.println(Arrays.toString(d));
            //System.out.println(result.getSolutions().get(0).dumpSolution());
        } else {
            System.out.println("No solution :-(");
        }
    }

    public static void mainGridPathFindingWithPivotsDomain(String[] args) throws IOException {
        Main mainTest = new Main();
        SearchDomain domain = mainTest.createGridPathFindingWithPivots("1.in");
        //SearchAlgorithm alg = new EES(1, true);
        SearchAlgorithm alg = new WAStar();
        alg.setAdditionalParameter("weight", "1.05");
        alg.setAdditionalParameter("reopen", "true");
        SearchResult result = alg.search(domain);
        assert result.getSolutions().size() == 1;
        if (result.hasSolution()) {
            double d[] = new double[]{
                    1,
                    1,
                    result.getSolutions().get(0).getLength(),
                    result.getSolutions().get(0).getCost(),
                    result.getGenerated(),
                    result.getExpanded(),
                    ((SearchResultImpl) result).reopened};
            System.out.println(Arrays.toString(d));
            //System.out.println(result.getSolutions().get(0).dumpSolution());
        } else {
            System.out.println("No solution :-(");
        }
    }

    public static void mainPancakesDomain(String[] args) throws IOException {
        Main mainTest = new Main();
        SearchDomain domain = mainTest.createPancakesUnit("1.in");
        SearchAlgorithm alg = new WAStar();
        SearchResult result = alg.search(domain);
        if (result.hasSolution()) {
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

    public static void mainDockyardRobotDomain() throws IOException {
        Main mainTest = new Main();
        SearchDomain domain = mainTest.createDockyardRobot("19.in");
        SearchAlgorithm alg = new WAStar();
        alg.setAdditionalParameter("weight", "1.0");
        alg.setAdditionalParameter("reopen", "true");
        SearchResult result = alg.search(domain);
        if (result.hasSolution()) {
            double d[] = new double[]{
                    1,
                    1,
                    result.getSolutions().get(0).getLength(),
                    result.getSolutions().get(0).getCost(),
                    result.getGenerated(),
                    result.getExpanded(),
                    ((SearchResultImpl) result).reopened};
            System.out.println(Arrays.toString(d));
            //System.out.printf(result.getSolutions().get(0).dumpSolution());
        } else {
            System.out.println("No solution :-(");
        }
    }

    public static void mainVacuumRobotDomain(String[] args) throws IOException {
        Main mainTest = new Main();
        SearchDomain domain = mainTest.createVacuumRobot("8.in");
        SearchAlgorithm alg = new WAStar();
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
        } else {
            System.out.println("No solution :-(");
        }
    }

    public static void mainTopSpin10Domain(String[] args) throws IOException {
        Main mainTest = new Main();
        SearchDomain domain = mainTest.createTopSpin10("1.in");

        //domain.setAdditionalParameter("pdb-data",
        //        "0-" + (9*8*7*6) + "-{0,1,2,3,4}-input\\topspin\\topspin10\\Size10Spin4Pattern_0_1_2_3_4");
        //domain.setAdditionalParameter("pdb-data",
        //        "1-" + (9*8*7*6) + "-{3,4,5,6,7}-input\\topspin\\topspin10\\Size10Spin4Pattern_3_4_5_6_7");
        //domain.setAdditionalParameter("pdb-data",
        //        "2-" + (9*8*7*6) + "-{5,6,7,8,9}-input\\topspin\\topspin10\\Size10Spin4Pattern_5_6_7_8_9");

        domain.setAdditionalParameter("pdb-data",
                "0-" + (9*8) + "-{0,1,2}-input\\topspin\\topspin10\\Size10Spin4Pattern_0_1_2");
        SearchAlgorithm alg = new WAStar();
        alg.setAdditionalParameter("weight", 5 + "");
        alg.setAdditionalParameter("reopen", true + "");
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
            SearchResult.Solution s = result.getSolutions().get(0);
            System.out.println(s.dumpSolution());
            System.out.println(Arrays.toString(d));
        } else {
            System.out.println("No solution :-(");
        }
    }

    public static void mainTopSpin12Domain(String[] args) throws IOException {
        Main mainTest = new Main();
        SearchDomain domain = mainTest.createTopSpin12("1.in");

        domain.setAdditionalParameter("pdb-data",
                "0-" + (11*10) + "-{0,1,2}-input\\topspin\\topspin12\\Size12Spin4Pattern_0_1_2");
        domain.setAdditionalParameter("pdb-data",
                "1-" + (11*10*9) + "-{0,1,2,3}-input\\topspin\\topspin12\\Size12Spin4Pattern_0_1_2_3");

        /*
        domain.setAdditionalParameter("pdb-data",
                "2-" + (11*10*9*8*7) + "-{0,1,2,3,4,5}-input\\topspin\\topspin12\\Size12Spin4Pattern_0_1_2_3_4_5");
        */
        domain.setAdditionalParameter("heuristic", "random-pdb");
        SearchAlgorithm alg = new WAStar();
        alg.setAdditionalParameter("weight", 6 + "");
        alg.setAdditionalParameter("reopen", true + "");
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
            SearchResult.Solution s = result.getSolutions().get(0);
            System.out.println(s.dumpSolution());
            System.out.println(Arrays.toString(d));
        } else {
            System.out.println("No solution :-(");
        }
    }

    public static void mainRawGraphDomain(String[] args) throws IOException {
        Main mainTest = new Main();
        SearchDomain domain = mainTest.createRawGraph();
        SearchAlgorithm alg = new WAStar();
        //alg.setAdditionalParameter("weight", "1.5");
        //alg.setAdditionalParameter("bpmx", true + "");

        SearchResult result1 = alg.search(domain);
        if (result1.getSolutions().size() > 0) {
            double d[] = new double[]{
                    result1.getSolutions().get(0).getLength(),
                    result1.getSolutions().get(0).getCost(),
                    result1.getExpanded(),
                    ((SearchResultImpl) result1).reopened};
            System.out.println(Arrays.toString(d));
            System.out.println(
                    result1.getSolutions().get(0).getStates().size()
            );
        } else {
            System.out.println("No solution :-(");
        }

        alg.setAdditionalParameter("reopen", false + "");
        SearchResult result2 = alg.search(domain);
        if (result2.getSolutions().size() > 0) {
            double d[] = new double[]{
                    result2.getSolutions().get(0).getLength(),
                    result2.getSolutions().get(0).getCost(),
                    result2.getExpanded(),
                    ((SearchResultImpl) result2).reopened};
            System.out.println(Arrays.toString(d));
            System.out.println(result2.getSolutions().get(0).getStates().size());

        } else {
            System.out.println("No solution :-(");
        }

        /*
        SearchAlgorithm alg2 = new WRAStar();

        SearchResult result3 = alg2.search(domain);
        if (result3.getSolutions().size() > 0) {
            double d[] = new double[]{
                    result3.getSolutions().get(0).getLength(),
                    result3.getSolutions().get(0).getCost(),
                    result3.getExpanded(),
                    ((SearchResultImpl) result3).reopened};
            System.out.println(Arrays.toString(d));
            System.out.println(
                    result3.getSolutions().get(0).getStates().size()
            );
        } else {
            System.out.println("No solution :-(");
        }
        */
    }

    public static void mainPTS(String[] args) throws IOException {
        Main mainTest = new Main();
        SearchDomain domain = mainTest.createFifteenPuzzleKorf("2.in");
        SearchAlgorithm alg = new PTS();
        alg.setAdditionalParameter("max-cost", "80");
        SearchResult result = alg.search(domain);
        if (result.hasSolution()) {
            double d[] = new double[]{
                    1,
                    1,
                    result.getSolutions().get(0).getLength(),
                    result.getSolutions().get(0).getCost(),
                    result.getGenerated(),
                    result.getExpanded(),
                    result.getDuplicates(),
                    result.getUpdatedInOpen(),
                    result.getReopened()
            };
            System.out.println(Arrays.toString(d));
        } else {
            System.out.println("No solution :-(");
        }
    }

    public static void mainEES(String[] args) throws IOException {
        Main mainTest = new Main();
        Weights weights = new Weights();
        //System.out.println("wh,wg,wh/wg,AR-Depth,AR-Generated,AR-Expanded,AR-Reopened,NR-Depth,NR-Generated,NR-Expanded,NR-Reopened");
        boolean reopenPossibilities[] = new boolean[]{true, false};

        for (Weights.SingleWeight w : weights.KORF_WEIGHTS) {
            double totalWeight = w.wh / w.wg;
            System.out.println("Solving for weight: wg : " + w.wg + " wh: " + w.wh);
            for (boolean reopen : reopenPossibilities) {
                try {
                    OutputResult output = new OutputResult(w.wg, w.wh, reopen);
                    output.writeln("InstanceID,Found,Depth,Generated, Expanded,Reopened");
                    for (int i = 1; i <= 100; ++i) {
                        SearchDomain domain = mainTest.createFifteenPuzzleKorf(i + "");
                        SearchAlgorithm alg = new WAStar();
                        alg.setAdditionalParameter("weight", totalWeight + "");
                        alg.setAdditionalParameter("reopen", reopen + "");
                        System.out.println("Solving instance " + i + " For weight " + totalWeight + " reopen? " + reopen);
                        SearchResult result = alg.search(domain);
                        double d[];
                        if (result.hasSolution()) {
                            d = new double[]{
                                    i,
                                    1,
                                    result.getSolutions().get(0).getLength(),
                                    //result.getSolutions().get(0).getLength(),
                                    result.getGenerated(),
                                    result.getExpanded(),
                                    ((SearchResultImpl) result).reopened};
                        } else {
                            d = new double[]{
                                    i, 0, 0, 0, 0, 0
                            };
                        }
                        output.appendNewResult(d);
                        output.newline();
                    }
                    output.close();
                } catch (FileAlreadyExistsException e) {
                    System.out.println("File already found for wg " + w.wg + " wh " + w.wh);
                }
            }
        }
    }

    public static void mainDP(String[] args, int num, SearchAlgorithm alg) throws IOException {
        Main mainTest = new Main();
        Weights weights = new Weights();
        boolean reopen = true;
        boolean overwriteFile = true;
        String relPath = "C:\\Users\\Daniel\\Documents\\thesis\\Research Data\\";
        OutputResult summary = new OutputResult(relPath+"results\\pancakes\\"+num+"\\Summary_"+alg.getName(),num+"_", -1, -1, "", reopen, overwriteFile);
        summary.writeln("WG,WH,"+alg.getName()+" Solved,"+alg.getName()+" Generated,"+alg.getName()+" Depth");
        for (Weights.SingleWeight w : weights.NATURAL_WEIGHTS) {
            double totalWeight = w.wh / w.wg;
            System.out.println("Solving for weight: wg : " + w.wg + " wh: " + w.wh);
            double resultSummary[] = new double[5];
            resultSummary[0] = w.wg;   resultSummary[1] = w.wh;
            try {
                OutputResult output = new OutputResult(relPath+"results\\pancakes\\"+num+"\\"+alg.getName()+"_",num+"_", w.wg, w.wh, num+"", reopen, overwriteFile);
                output.writeln("InstanceID,Found,Depth,Generated, Expanded,Reopened");
                for (int i = 1; i <= 100; ++i) {
                    InputStream is = new FileInputStream(new File(relPath+"input/pancakes/generated-"+num+"/" + i+".in"));
                    SearchDomain domain = new Pancakes(is);
                    alg.setAdditionalParameter("weight", totalWeight + "");
                    System.out.println("Solving instance " + i + " For weight " + totalWeight + " reopen? " + reopen);
                    SearchResult result = alg.search(domain);
                    double d[];
                    if (result.hasSolution()) {
                        d = new double[]{
                                i,
                                1,
                                result.getSolutions().get(0).getLength(),
                                //result.getSolutions().get(0).getLength(),
                                result.getGenerated(),
                                result.getExpanded(),
                                ((SearchResultImpl) result).reopened};
                        resultSummary[2] += 1;
                        resultSummary[3] += result.getGenerated();
                        resultSummary[4] += result.getSolutions().get(0).getLength();
                    } else {
                        d = new double[]{
                                i, 0, 0, 0, 0, 0
                        };
                    }
                    output.appendNewResult(d);
                    output.newline();
                }
                output.close();
                summary.appendNewResult(resultSummary);
                summary.newline();
            } catch (FileAlreadyExistsException e) {
                System.out.println("File already found for wg " + w.wg + " wh " + w.wh);
            }
        }
        summary.close();
    }


    public static void main(String[] args) throws IOException {
//        SearchAlgorithm alg = new DP();
        SearchAlgorithm alg = new WAStar();
//        SearchAlgorithm alg = new EES(1);
        Main.mainDP(args,10,alg);
        //Main.mainTopSpin12Domain(args);
        Main.mainRawGraphDomain(args);
        //Main.mainFifteenPuzzleDomain(args);
        //Main.mainGridPathFindingWithPivotsDomain(args);
        //Main.mainGridPathFindingDomain(args);
        //Main.mainPancakesDomain(args);
        //Main.mainVacuumRobotDomain(args);
        //Main.mainDockyardRobotDomain();

        //Main.mainPTS(args);
    }
}
