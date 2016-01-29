package org.cs4j.core.mains;

import org.cs4j.core.SearchDomain;
import org.cs4j.core.auxiliary.GridsIREMeasuring;
import org.cs4j.core.auxiliary.TopSpinIREMeasuring;
import org.cs4j.core.domains.GridPathFinding;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by user on 29/01/2016.
 *
 */
public class IREMeasuring {

    public static final int MAXIMUM_STATES_COUNT_TO_MEASURE_IRE = (int)Math.pow(10, 6);

    public static void mainGridIREMeasuring() {
        String gridName = "den400d.map";
        int pivotsCount = 10;
        try {
            String mapFileName = "input/gridpathfinding/generated/"+gridName;
            String pivotsFileName = "input/gridpathfinding/raw/maps/"+gridName+".pivots.pdb";
            InputStream is = new FileInputStream(new File(mapFileName + "/1.in"));
            GridsIREMeasuring gridsIREMeasuring = new GridsIREMeasuring(is);
            gridsIREMeasuring.setAdditionalParameter("heuristic", "dh-random-pivot");
            gridsIREMeasuring.setAdditionalParameter("pivots-distances-db-file", pivotsFileName);
            gridsIREMeasuring.setAdditionalParameter("pivots-count", pivotsCount + "");
            System.out.println("IRE is " +
                    gridsIREMeasuring.measureIRE(IREMeasuring.MAXIMUM_STATES_COUNT_TO_MEASURE_IRE));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void mainTopSpinIREMeasuring() {
        TopSpinIREMeasuring topSpinIREMeasuring = new TopSpinIREMeasuring();
        System.out.println("IRE is " +
                topSpinIREMeasuring.measureIRE(IREMeasuring.MAXIMUM_STATES_COUNT_TO_MEASURE_IRE));
    }

    public static void main(String[] args) {
        mainTopSpinIREMeasuring();
        //mainGridIREMeasuring();
    }

}
