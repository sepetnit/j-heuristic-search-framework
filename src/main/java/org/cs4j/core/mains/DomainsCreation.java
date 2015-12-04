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
        InputStream is = new FileInputStream(new File("input/gridpathfinding/generated/maze512-1-6.map/" + instance));
        //InputStream is = new FileInputStream(new File("input/gridpathfinding/generated/den400d.map/" + instance));
        return new GridPathFinding(is);
    }

    public static SearchDomain createGridPathFindingInstanceFromAutomaticallyGeneratedWithTDH(
            String instance, int pivotsCount) throws IOException {
        String mapFileName = "input/gridpathfinding/generated/brc202d.map";
        //String mapFileName = "input/gridpathfinding/generated/maze512-1-6.map";
        //String pivotsFileName = "input/gridpathfinding/raw/maps/" + new File(mapFileName).getName() + ".pivots.pdb";
        //String pivotsFileName = "input/gridpathfinding/raw/mazes/maze1/_maze512-1-6-80.map.pivots.pdb";
        String pivotsFileName = "input/gridpathfinding/raw/maps/brc202d.map.pivots.pdb";
        InputStream is = new FileInputStream(new File(mapFileName + "/" + instance));
        GridPathFinding problem = new GridPathFinding(is);
        //problem.setAdditionalParameter("heuristic", "dh-furthest");
        //problem.setAdditionalParameter("heuristic", "dh-md-average-md-if-dh-is-0");
        //problem.setAdditionalParameter("heuristic", "dh-random-pivot");
        problem.setAdditionalParameter("heuristic", "dh-random-pivots");
        problem.setAdditionalParameter("random-pivots-count", 9 + "");
        //problem.setAdditionalParameter("heuristic", "random-dh-md");
        problem.setAdditionalParameter("pivots-distances-db-file", pivotsFileName);
        problem.setAdditionalParameter("pivots-count", pivotsCount + "");
        return problem;
    }

    public static SearchDomain createGridPathFindingInstanceFromAutomaticallyGeneratedWithTDH(SearchDomain previous,
                                                                                              String instance,
                                                                                              int pivotsCount)
            throws IOException {
        String mapFileName = "input/gridpathfinding/generated/brc202d.map";
        //String mapFileName = "input/gridpathfinding/generated/maze512-1-6.map";
        InputStream is = new FileInputStream(new File(mapFileName, instance));
        GridPathFinding problem = new GridPathFinding((GridPathFinding)previous, is);
        // Change the number of pivots
        problem.setAdditionalParameter("pivots-count", pivotsCount + "");
        return problem;
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
        String filename = "input/pancakes/generated-10/" + instance;
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
        InputStream is = new FileInputStream(new File("input/fifteenpuzzle/korf100-real/" + instance));
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
        InputStream is = new FileInputStream(new File("input/fifteenpuzzle/korf100-real/" + instance));
        return new FifteenPuzzle((FifteenPuzzle)previous, is);
    }

    public static SearchDomain createDockyardRobotInstanceFromAutomaticallyGenerated(String instance) throws FileNotFoundException {
        InputStream is = new FileInputStream(new File("input/dockyardrobot/generated-max-edge-2-out-of-place-30/" + instance));
        return new DockyardRobot(is);
    }
}
