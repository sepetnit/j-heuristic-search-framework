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
        InputStream is = new FileInputStream(new File("input/gridpathfinding/generated/brc202d.map/" + instance));
        return new GridPathFinding(is);
    }

    public static SearchDomain createGridPathFindingInstanceFromAutomaticallyGeneratedWithTDH(String instance)
            throws IOException {
        String mapFileName = "input/gridpathfinding/generated/brc202d";
        String pivotsFileName = "input/gridpathfinding/raw/maps/" + new File(mapFileName).getName() + ".pivots";
        int pivotsCount = 10;
        InputStream is = new FileInputStream(new File(mapFileName + ".map/" + instance));
        GridPathFinding problem = new GridPathFinding(is);
        problem.setAdditionalParameter("heuristic", "tdh-furthest");
        problem.setAdditionalParameter("pivots-input-file", pivotsFileName);
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
}
