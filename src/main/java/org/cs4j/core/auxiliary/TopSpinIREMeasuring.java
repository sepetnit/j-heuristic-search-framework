package org.cs4j.core.auxiliary;

import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchDomain.State;
import org.cs4j.core.collections.Pair;
import org.cs4j.core.mains.DomainsCreation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by user on 29/01/2016.
 *
 * This class allows measuring IRE for the TopSpin domain
 *
 * In order to perform that, it takes 100 random TopSpin instances and for each instances expands the search tree
 * to the depth 100 while measuring the IRE
 *
 * Finally, the average IRE is returned
 */
public class TopSpinIREMeasuring {

    private final static int STATES_COUNT_FOR_PRINTING = 100;
    private final static int INSTANCES_COUNT = 10;
    private final static int MAX_DEPTH = 10;

    private SearchDomain firstDomain;
    private Set<Pair<State, State>> checkPairsOfStates;

    // Contains a pair of:
    //  1. Total number of parent-child pairs whose heuristic diff was calculates
    //  2. The sum of diff of heuristic values
    private List<Pair<Integer, Double>> countAndSumValues;

    public TopSpinIREMeasuring() {
        System.out.println("[INFO] Initializing first domain");
        try {
            this.firstDomain = DomainsCreation.createTopSpin10InstanceWithPDBs("1.in");
        } catch (IOException e) {
            System.out.println("[ERROR] Failed to initialize the first domain");
            this.firstDomain = null;
        }
        System.out.println("[INFO] Done initializing first domain");
        // Initialize rest of local variables
        this.checkPairsOfStates = new HashSet<>();
        this.countAndSumValues = new ArrayList<>();
    }

    private void _calculateHDiffsForSingleInstance(SearchDomain domain, State parentState, int depth,
                                                  Pair<Integer, Double> countAndSum,
                                                  long maxDiffsCount) {
        if (depth == TopSpinIREMeasuring.MAX_DEPTH) {
            return;
        }

        double parentH = parentState.getH();

        for (int i = 0; i < domain.getNumOperators(parentState); ++i) {
            SearchDomain.Operator op = domain.getOperator(parentState, i);
            State childState = domain.applyOperator(parentState, op);

            Pair<State, State> pair1 = new Pair<State, State>(parentState, childState);
            Pair<State, State> pair2 = new Pair<State, State>(childState, parentState);

            // First, assure not in set of duplicates and add there
            if (this.checkPairsOfStates.contains(pair1) || this.checkPairsOfStates.contains(pair2)) {
                continue;
            }
            this.checkPairsOfStates.add(pair1);

            // Now, go over the child states
            double childH = childState.getH();

            int currentCount = countAndSum.getKey();
            countAndSum.setKey(currentCount + 1);

            double currentSum = countAndSum.getValue();
            countAndSum.setValue(currentSum + Math.abs(parentH - childH));

            if (currentCount % TopSpinIREMeasuring.STATES_COUNT_FOR_PRINTING == 0) {
                System.out.print("\r[INFO] Calculated for " + currentCount + " states");
            }

            // No need to perform extra work ...
            if (currentCount >= maxDiffsCount) {
                if (currentCount - 1 < maxDiffsCount) {
                    // The extra new line is because the \r
                    System.out.println();
                }
                return;
            }

            this._calculateHDiffsForSingleInstance(domain, childState, depth + 1, countAndSum, maxDiffsCount);
        }
    }

    private void calculateHDiffsForSingleInstance(int instanceID,
                                                 Pair<Integer, Double> countAndSum,
                                                 long maxDiffsCount) throws IOException {
        System.out.println("[INFO] Calculating hDiffs for instance " + instanceID);
        SearchDomain domain =
                DomainsCreation.createTopSpin10InstanceWithPDBs(
                        this.firstDomain,
                        instanceID + ".in");
        this._calculateHDiffsForSingleInstance(domain, domain.initialState(), 0, countAndSum, maxDiffsCount);
        System.out.println("[INFO] Done calculating hDiffs for instance " + instanceID);
    }

    private double calculateIRE() {
        int totalHDiffsCount = 0;
        double totalHDiffsSum = 0.0d;

        for (Pair<Integer, Double> current : this.countAndSumValues) {
            totalHDiffsCount += current.getKey();
            totalHDiffsSum += current.getValue();
        }

        return totalHDiffsSum / totalHDiffsCount;
    }

    public double measureIRE(long maxDiffsCount) {
        long maxDiffsCountForSingleInstance = maxDiffsCount / TopSpinIREMeasuring.INSTANCES_COUNT;

        for (int instanceID = 1; instanceID < TopSpinIREMeasuring.INSTANCES_COUNT; ++instanceID) {
            try {
                Pair<Integer, Double> currentCountAndSum = new Pair<>(0, 0.0d);
                this.calculateHDiffsForSingleInstance(instanceID, currentCountAndSum, maxDiffsCountForSingleInstance);
                this.countAndSumValues.add(currentCountAndSum);
            } catch (IOException e) {
                System.out.println("[ERROR] Can't read instance " + instanceID);
                return -1.0d;
            }
        }
        // Finally, calculate the IRE
        return this.calculateIRE();
    }

}
