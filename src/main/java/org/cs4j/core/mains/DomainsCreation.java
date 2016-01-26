package org.cs4j.core.mains;

import org.cs4j.core.SearchDomain;
import org.cs4j.core.domains.*;

import java.io.*;

/**
 * Created by sepetnit on 11/10/2015.
 *
 */
public class DomainsCreation {

    /*******************************************************************************************************************
     * Private  static methods : Domains creation
     ******************************************************************************************************************/

    public static SearchDomain createGridPathFindingInstanceFromAutomaticallyGenerated(String instance) throws FileNotFoundException {
        //InputStream is = new FileInputStream(new File("input/gridpathfinding/generated/maze512-1-6.map/" + instance));
        InputStream is = new FileInputStream(new File("input/gridpathfinding/generated/den400d.map/" + instance));
        return new GridPathFinding(is);
    }

    public static SearchDomain createGridPathFindingInstanceFromAutomaticallyGeneratedWithTDH(
            String gridName, String instance, int pivotsCount) throws IOException {
        String mapFileName = "input/gridpathfinding/generated/"+gridName;
        //String mapFileName = "input/gridpathfinding/generated/ost003d.map";
        //String mapFileName = "input/gridpathfinding/generated/den400d.map";
        //String mapFileName = "input/gridpathfinding/generated/maze512-1-6.map";
        //String pivotsFileName = "input/gridpathfinding/raw/maps/" + new File(mapFileName).getName() + ".pivots.pdb";
        //String pivotsFileName = "input/gridpathfinding/raw/mazes/maze1/_maze512-1-6-80.map.pivots.pdb";
        String pivotsFileName = "input/gridpathfinding/raw/maps/"+gridName+".pivots.pdb";
        //String pivotsFileName = "input/gridpathfinding/raw/maps/ost003d.map.pivots.pdb";
        //String pivotsFileName = "input/gridpathfinding/raw/maps/den400d.map.pivots.pdb";
        InputStream is = new FileInputStream(new File(mapFileName + "/" + instance));
        GridPathFinding problem = new GridPathFinding(is);
        //problem.setAdditionalParameter("heuristic", "dh-furthest");
        //problem.setAdditionalParameter("heuristic", "dh-md-average-md-if-dh-is-0");
        problem.setAdditionalParameter("heuristic", "dh-random-pivot");
        //problem.setAdditionalParameter("heuristic", "dh-random-pivots");
        //problem.setAdditionalParameter("random-pivots-count", 5 + "");
        //problem.setAdditionalParameter("heuristic", "random-dh-md");
        problem.setAdditionalParameter("pivots-distances-db-file", pivotsFileName);
        problem.setAdditionalParameter("pivots-count", pivotsCount + "");
        return problem;
    }

    public static SearchDomain createGridPathFindingInstanceFromAutomaticallyGeneratedWithTDH(String gridName,
                                                                                              SearchDomain previous,
                                                                                              String instance,
                                                                                              int pivotsCount)
            throws IOException {
        String mapFileName = "input/gridpathfinding/generated/"+gridName;
        //String mapFileName = "input/gridpathfinding/generated/ost003d.map";
        //String mapFileName = "input/gridpathfinding/generated/den400d.map";
        //String mapFileName = "input/gridpathfinding/generated/maze512-1-6.map";
        InputStream is = new FileInputStream(new File(mapFileName, instance));
        GridPathFinding problem = new GridPathFinding((GridPathFinding)previous, is);
        // Change the number of pivots
        problem.setAdditionalParameter("pivots-count", pivotsCount + "");
        return problem;
    }

    public static SearchDomain createTopSpin10InstanceWithPDBs(SearchDomain previous, String instance)
            throws IOException {
        String topSpinDirectory = "input/topspin/topspin10";
        InputStream is = new FileInputStream(new File(topSpinDirectory, instance));
        return new TopSpin((TopSpin)previous, is);
    }

    public static SearchDomain createTopSpin10InstanceWithPDBs(String instance) throws FileNotFoundException {
        String topSpinDirectory = "input/topspin/topspin10";
        InputStream is = new FileInputStream(new File(topSpinDirectory, instance));
        TopSpin problem = new TopSpin(is);

        problem.setAdditionalParameter("pdb-data",
                "0-" + (9*8*7*6) + "-{0,1,2,3,4}-input\\topspin\\topspin10\\Size10Spin4Pattern_0_1_2_3_4");
        problem.setAdditionalParameter("pdb-data",
                "1-" + (9*8*7*6) + "-{3,4,5,6,7}-input\\topspin\\topspin10\\Size10Spin4Pattern_3_4_5_6_7");
        /*
        problem.setAdditionalParameter("pdb-data",
                "2-" + (9*8*7*6) + "-{5,6,7,8,9}-input\\topspin\\topspin10\\Size10Spin4Pattern_5_6_7_8_9");
        */

        problem.setAdditionalParameter("heuristic", "random-pdb");
        return problem;
    }

    public static SearchDomain createTopSpin12InstanceWithPDBs(SearchDomain previous, String instance)
            throws IOException {
        String topSpinDirectory = "input/topspin/topspin12";
        InputStream is = new FileInputStream(new File(topSpinDirectory, instance));
        return new TopSpin((TopSpin)previous, is);
    }

    public static SearchDomain createTopSpin12InstanceWithPDBs(String instance) throws FileNotFoundException {
        String topSpinDirectory = "input/topspin/topspin12";
        InputStream is = new FileInputStream(new File(topSpinDirectory, instance));
        TopSpin problem = new TopSpin(is);


        problem.setAdditionalParameter("pdb-data",
                "0-" + (11*10) + "-{0,1,2}-input\\topspin\\topspin12\\Size12Spin4Pattern_0_1_2");
        problem.setAdditionalParameter("pdb-data",
                "1-" + (11*10*9) + "-{0,1,2,3}-input\\topspin\\topspin12\\Size12Spin4Pattern_0_1_2_3");
        problem.setAdditionalParameter("pdb-data",
                "2-" + (11*10*9*8*7) + "-{0,1,2,3,4,5}-input\\topspin\\topspin12\\Size12Spin4Pattern_0_1_2_3_4_5");

        problem.setAdditionalParameter("heuristic", "random-pdb");
        return problem;
    }

    public static SearchDomain createTopSpin16InstanceWithPDBs(SearchDomain previous, String instance)
            throws IOException {
        String topSpinDirectory = "input/topspin/topspin16";
        InputStream is = new FileInputStream(new File(topSpinDirectory, instance));
        return new TopSpin((TopSpin)previous, is);
    }

    public static SearchDomain createTopSpin16InstanceWithPDBs(String instance) throws FileNotFoundException {
        String topSpinDirectory = "input/topspin/topspin16";
        InputStream is = new FileInputStream(new File(topSpinDirectory, instance));
        TopSpin problem = new TopSpin(is);

        problem.setAdditionalParameter("pdb-data",
                "0-" + (15*14*13*12*11*10*9) + "-{0,1,2,3,4,5,6,7}-input\\topspin\\topspin16\\Size16Spin4Pattern_0_1_2_3_4_5_6_7");
        problem.setAdditionalParameter("pdb-data",
                "1-" + (15*14*13*12*11*10*9) + "-{0,2,3,4,5,6,7,8}-input\\topspin\\topspin16\\Size16Spin4Pattern_0_2_3_4_5_6_7_8");
        problem.setAdditionalParameter("pdb-data",
                "2-" + (15*14*13*12*11*10*9) + "-{0,3,4,5,6,7,8,9}-input\\topspin\\topspin16\\Size16Spin4Pattern_0_3_4_5_6_7_8_9");
        problem.setAdditionalParameter("pdb-data",
                "3-" + (15*14*13*12*11*10*9) + "-{0,4,5,6,7,8,9,10}-input\\topspin\\topspin16\\Size16Spin4Pattern_0_4_5_6_7_8_9_10");
        problem.setAdditionalParameter("pdb-data",
                "4-" + (15*14*13*12*11*10*9) + "-{0,5,6,7,8,9,10,11}-input\\topspin\\topspin16\\Size16Spin4Pattern_0_5_6_7_8_9_10_11");
        problem.setAdditionalParameter("pdb-data",
                "5-" + (15*14*13*12*11*10*9) + "-{0,6,7,8,9,10,11,12}-input\\topspin\\topspin16\\Size16Spin4Pattern_0_6_7_8_9_10_11_12");
        problem.setAdditionalParameter("pdb-data",
                "6-" + (15*14*13*12*11*10*9) + "-{0,7,8,9,10,11,12,13}-input\\topspin\\topspin16\\Size16Spin4Pattern_0_7_8_9_10_11_12_13");
        problem.setAdditionalParameter("pdb-data",
                "7-" + (15*14*13*12*11*10*9) + "-{0,8,9,10,11,12,13,14}-input\\topspin\\topspin16\\Size16Spin4Pattern_0_8_9_10_11_12_13_14");
        problem.setAdditionalParameter("pdb-data",
                "8-" + (15*14*13*12*11*10*9) + "-{0,9,10,11,12,13,14,15}-input\\topspin\\topspin16\\Size16Spin4Pattern_0_9_10_11_12_13_14_15");

        problem.setAdditionalParameter("heuristic", "random-pdb");

        return problem;
    }

    public static SearchDomain createTopSpin18InstanceWithPDBs(SearchDomain previous, String instance)
            throws IOException {
        String topSpinDirectory = "input/topspin/topspin18";
        InputStream is = new FileInputStream(new File(topSpinDirectory, instance));
        return new TopSpin((TopSpin)previous, is);
    }


    // The k is for GAP-k heuristic setting
    public static SearchDomain createPancakesInstanceFromAutomaticallyGenerated(int size, String instance, int k) throws FileNotFoundException {
        Pancakes toReturn = null;
        String filename = "input/pancakes/generated-" + size + "/" + instance;
        try {
            InputStream is = new FileInputStream(new File(filename));
            toReturn = new Pancakes(is);
            toReturn.setAdditionalParameter("GAP-k", k + "");

        } catch (FileNotFoundException e) {
            System.out.println("[WARNING] File " + filename + " not found");
        }
        return toReturn;
    }

    public static SearchDomain createPancakesInstanceFromAutomaticallyGenerated(String instance) throws FileNotFoundException {
        String filename = "input/pancakes/generated-40/" + instance;
        try {
            InputStream is = new FileInputStream(new File(filename));
            return new Pancakes(is);
        } catch (FileNotFoundException e) {
            System.out.println("[WARNING] File " + filename + " not found");
            return null;
        }
    }

    public static SearchDomain createVacuumRobotInstanceFromAutomaticallyGenerated(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/vacuumrobot/generated-10-dirt/" + instance));
        return new VacuumRobot(is);
    }

    public static SearchDomain create15PuzzleInstanceFromKorfInstancesPDB555(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/fifteenpuzzle/korf100/" + instance));
        FifteenPuzzle puzzle = new FifteenPuzzle(is);
        String PDBsDirs[] = new String[]{"C:\\users\\user\\", "H:\\", "C:\\"};
        puzzle.setAdditionalParameter("heuristic", "pdb-555");
        boolean pdbFilesOK = false;
        for (String PDBsDir : PDBsDirs) {
            try {
                puzzle.setAdditionalParameter(
                        "pdb-555-files",
                        PDBsDir + "PDBs\\15-puzzle\\dis_1_2_3_4_5,"+
                                PDBsDir + "PDBs\\15-puzzle\\dis_6_7_8_9_10,"+
                                PDBsDir + "PDBs\\15-puzzle\\dis_11_12_13_14_15");
                pdbFilesOK = true;
                break;
            } catch (IllegalArgumentException e) { }
        }
        assert pdbFilesOK;
        puzzle.setAdditionalParameter("use-reflection", true + "");
        return puzzle;
    }


    public static SearchDomain create15PuzzleInstanceFromKorfInstancesPDB78(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/fifteenpuzzle/korf100-real/" + instance));
        FifteenPuzzle puzzle = new FifteenPuzzle(is);
        String PDBsDirs[] = new String[]{"C:\\users\\user\\", "H:\\", "C:\\"};
        puzzle.setAdditionalParameter("heuristic", "pdb-78");
        boolean pdbFilesOK = false;
        for (String PDBsDir : PDBsDirs) {
            try {
                puzzle.setAdditionalParameter(
                        "pdb-78-files",
                        PDBsDir + "PDBs\\15-puzzle\\dis_1_2_3_4_5_6_7," +
                                PDBsDir + "PDBs\\15-puzzle\\dis_8_9_10_11_12_13_14_15");
                pdbFilesOK = true;
                break;
            } catch (IllegalArgumentException e) { }
        }
        assert pdbFilesOK;
        puzzle.setAdditionalParameter("use-reflection", true + "");
        return puzzle;
    }

    public static SearchDomain create15PuzzleInstanceFromKorfInstances(SearchDomain previous, String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/fifteenpuzzle/korf100/" + instance));
        return new FifteenPuzzle((FifteenPuzzle)previous, is);
    }

    public static SearchDomain createDockyardRobotInstanceFromAutomaticallyGenerated(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/dockyardrobot/generated-max-edge-2-out-of-place-30/" + instance));
        return new DockyardRobot(is);
    }
}
