package org.cs4j.core.auxiliary;

import org.cs4j.core.collections.Pair;
import org.cs4j.core.collections.PairInt;
import org.cs4j.core.domains.GridPathFinding;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by user on 29/01/2016.
 *
 */
public class GridsIREMeasuring extends GridPathFinding {

    private final static int STATES_COUNT_FOR_PRINTING = 100;

    public GridsIREMeasuring(InputStream stream) {
        super(stream);
    }

    public double measureIRE(long maxDiffsCount) {
        // For avoiding duplicates
        Set<Pair<State, State>> statesSet = new HashSet<>();

        long hDiffsCounter = 0;
        double hDiffsSum = 0;

        boolean shouldStop = false;

        for (int x = 0; !shouldStop && x < this.getGridWidth(); ++x) {
            for (int y = 0; !shouldStop && y < this.getGridHeight(); ++y) {
                int locationIndex = this.map.getLocationIndex(new PairInt(x, y));
                if (this.map.isBlocked(locationIndex)) {
                    continue;
                }
                GridPathFindingState parentState = new GridPathFindingState();
                parentState.agentLocation = locationIndex;
                double parentH = parentState.getH();

                // Now, apply all the possible operators and find adjacent states
                for (int i = 0; i < this.getNumOperators(parentState); ++i) {
                    Operator op = this.getOperator(parentState, i);
                    State childState = this.applyOperator(parentState, op);

                    Pair<State, State> pair1 = new Pair<State, State>(parentState, childState);
                    Pair<State, State> pair2 = new Pair<State, State>(childState, parentState);

                    if (statesSet.contains(pair1) || statesSet.contains(pair2)) {
                        continue;
                    }
                    statesSet.add(pair1);

                    double childH = childState.getH();

                    hDiffsSum += Math.abs(parentH - childH);
                    ++hDiffsCounter;

                    if (hDiffsCounter >= maxDiffsCount) {
                        shouldStop = true;
                    }

                    if (hDiffsCounter % GridsIREMeasuring.STATES_COUNT_FOR_PRINTING == 0) {
                        System.out.print("\rAlready measured IRE of " + hDiffsCounter + " states");
                    }

                }
            }
        }

        System.out.println();
        return hDiffsSum / hDiffsCounter;
    }

}
