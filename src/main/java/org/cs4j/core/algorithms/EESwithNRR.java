/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cs4j.core.algorithms;

import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchResult;

import java.util.HashMap;
import java.util.Map;

/**
 * EES which makes a kind of repairing procedure in order to return solutions that are
 * within the required suboptimality bound
 *
 * @author Matthew Hatem
 *
 * (Edited by Vitali Sepetnitsky)
 */
public class EESwithNRR extends EES {

    private static final int QID = 0;

    private static final Map<String, Class> EESWithNRRPossibleParameters;

    // Declare the parameters that can be tunes before running the search
    static
    {
        EESWithNRRPossibleParameters = new HashMap<>();
        EESwithNRR.EESWithNRRPossibleParameters.put("weight", Double.class);
    }

    // TODO: Check if it works ...
    private double wAdmissibilityDeviation;

    // TODO: Use it!
    // The iteration of run, from which we start reopening
    private int iterationToStartReopening;

    protected boolean useBPMX;

    protected enum ALGORITHM_TYPE {
        Type1,
        Type2
    }

    protected ALGORITHM_TYPE algorithmType;

    // Defines the available types of reruning the search if searching with NR failed
    private enum RERUN_TYPES {
        // Stop the search (no rerun is available)
        NO_RERUN,
        // Rerun the search but now, run with AR
        NRR1,
        // Continue the search - expand all the nodes that were not expanded previously
        NRR1dot5,
        // Perform the reopening in iterations (NR+ICL, move ICL to OPEN, NR+ICL again, etc.)
        NRR2
    }

    // The type of re-runing to apply if the search failed to run with NR (no solution of the required cost was found)
    private RERUN_TYPES rerun;

    public EESwithNRR() {
        super();
        this.useBPMX = false;
        // By default - not deviation from the actual weight is permitted
        this.wAdmissibilityDeviation = 1.0d;
        // By default, we never reopen for any iteration
        this.iterationToStartReopening = Integer.MAX_VALUE;
        this.rerun = RERUN_TYPES.NO_RERUN;
        this.algorithmType = ALGORITHM_TYPE.Type1;
    }

    @Override
    public String getName() {
        return "ees+nrr";
    }

    @Override
    public Map<String, Class> getPossibleParameters() {
        return EESwithNRR.EESWithNRRPossibleParameters;
    }

    @Override
    public void setAdditionalParameter(String parameterName, String value) {
        switch (parameterName) {
            case "nrr-type": {
                switch (value) {
                    case "nrr1": {
                        this.rerun = RERUN_TYPES.NRR1;
                        break;
                    }
                    case "nrr1.5": {
                        this.rerun = RERUN_TYPES.NRR1dot5;
                        break;
                    }
                    case "nrr2": {
                        this.rerun = RERUN_TYPES.NRR2;
                        break;
                    }
                    default: {
                        System.out.println("[ERROR] The available rerun types are 'nrr1' and 'nrr1.5' and 'nrr2'");
                        throw new IllegalArgumentException();
                    }
                }
                break;
            }
            // There are two types of NRR1.5/2 :
            //  1. We can run EES with fmin which is based only on the OPEN list
            //     In this case, we check if the solution is within the bound by taking the bestF value from OPEN+ICL
            //     This is called Type1
            //  2. We can run EES with fmin which is based on the OPEN list + on ICL list
            //     In this case, we return if focal list is empty
            //     This is called Type2
            case "nrr1dot5or2-type": {
                switch (value) {
                    case "Type1": {
                        this.algorithmType = ALGORITHM_TYPE.Type1;
                        break;
                    }
                    case "Type2": {
                        this.algorithmType = ALGORITHM_TYPE.Type2;
                        break;
                    }
                    default: {
                        System.out.println("[ERROR] The available types are 'Type1' and 'Type2'");
                        throw new IllegalArgumentException();
                    }
                }
                break;
            }
            default: {
                // Call the function from EES
                super.setAdditionalParameter(parameterName, value);
            }
        }
    }

    /**
     * TODO: Best F should be chosen from CLEANUP (sorted by F) + ICL (Since, these F values are also important)
     *
     * @param previousBestF The previous value of bestF
     *
     * @return The found value of best F
     */
    private double getBestF(double previousBestF) {
        double toReturn = -1.0d;
        if (this.cleanupForICL.size() > 0) {
            double currentBestF = this.cleanupForICL.peek().getF();
            // Return the current best f-value of cleanup only if it is greater the current or if the current f-value
            // is infinity
            if (previousBestF < currentBestF || previousBestF == -1) {
                toReturn = currentBestF;
            }
        }
        if (toReturn == -1.0d) {
            if (previousBestF == -1.0d) {
                toReturn = Integer.MAX_VALUE;
            } else {
                toReturn = previousBestF;
            }
        }
        return toReturn;
    }

    /**
     * Checks if the given solution is within the bound
     *
     * @param solutionCost The value of the solution cost to check if it is within the bound
     *
     * @return Whether the calculated solution is within the bound
     */
    private boolean checkSolutionWithinTheBound(double solutionCost, double deviatedWeight,
                                                double previousBestF, double[] bestF) {
        double optimalCost = this.domain.getOptimalSolutionCost();
        // TODO: This is ugly ...
        bestF[0] = this.getBestF(previousBestF);
        double suboptimalBoundSup = (optimalCost != -1) ?
                (solutionCost / optimalCost) :
                (solutionCost / bestF[0]);
        System.out.println("[INFO] Approximated suboptimal bound is " + suboptimalBoundSup);
        if (suboptimalBoundSup <= deviatedWeight) {
            System.out.println("[INFO] Bound is sufficient (required: " + deviatedWeight + ", got: " +
                    suboptimalBoundSup + ")");
            return true;
        } else {
            System.out.println("[INFO] Insufficient bound (" + suboptimalBoundSup + " > " +
                    deviatedWeight + ")");
        }
        return false;
    }

    @Override
    protected Node getNodeWithBestF() {
        switch (this.algorithmType) {
            // Based on OPEN only
            case Type1: {
                return this.cleanup.peek();
            }
            case Type2: {
                return this.cleanupForICL.peek();
            }
            default: {
                // Shouldn't be here!
                return null;
            }
        }
    }

    @Override
    protected boolean shouldRun() {
        switch (this.algorithmType) {
            // Based on OPEN only
            case Type1: {
                return !this.gequeue.isEmpty();
            }
            case Type2: {
                // There is something in Focal
                return this.gequeue.peekFocal() != null;
            }
            default: {
                // Should be here!
                return false;
            }
        }
    }

    @Override
    protected void _initDataStructures(boolean clearOpen, boolean clearIncons, boolean clearClosed) {
        if (clearIncons || this.incons == null) {
            this.incons = new HashMap<>();
        }
        // Call previous ...
        super._initDataStructures(clearOpen, clearIncons, clearClosed);

    }

    public SearchResult searchNRR1(double deviatedWeight) {
        double solutionCost = Double.MAX_VALUE;

        // This is the result for the first NR
        this.reopen = false;

        SearchResult nrResult = super.search(domain);

        if (nrResult.hasSolution()) {
            // Get current solution and check if it is sufficient
            SearchResult.Solution currentSolution = nrResult.getSolutions().get(0);
            solutionCost = currentSolution.getCost();
            // No previous bestF - just pass -1.0d
            if (this.checkSolutionWithinTheBound(solutionCost, deviatedWeight, -1.0d, new double[1])) {
                return nrResult;
            }
        }
        // Otherwise, solve with AR
        System.out.println("[INFO] Failed with NR, tries again with AR from scratch");

        // Now, search with reopening
        this.reopen = true;

        SearchResult arResult = super.search(domain);

        if (arResult.hasSolution()) {
            // Add previous iteration of NR in any case (TODO: Required?)
            ((SearchResultImpl)arResult).addIteration(1, solutionCost,
                    nrResult.getExpanded(), nrResult.getGenerated());
            arResult.increase(nrResult);
            System.out.println("[INFO] NR failed AR from scratch succeeded.");
            return arResult;
        }
        // Return the previous result - a special case ...
        ((SearchResultImpl)nrResult).addIteration(1, solutionCost, nrResult.getExpanded(), nrResult.getGenerated());
        nrResult.increase(arResult);
        return nrResult;
    }

    protected void _insertNodeNoCleanupForICL(Node node, Node oldBest) {
        this.gequeue.add(node, oldBest);
        this.cleanup.add(node);
        this.closed.put(node.packed, node);
    }

    public SearchResult searchNRR1dot5or2(double deviatedWeight, int nrIterationsCount) {

        // In general reopen is false, let's make it true if required
        this.reopen = false;

        double maxPreviousCost = Double.MAX_VALUE;
        double bestF[] = new double[]{-1.0d};

        SearchResultImpl accumulatorResult = new SearchResultImpl();
        // This is for storing the last result
        // (for the case last result is None but previous result returned a solution)
        SearchResultImpl lastResult = new SearchResultImpl();

        boolean shouldReturn = false;

        // Clear all the data structures - for a fresh search!
        this._initDataStructures(true, true, true);

        // Prepare (create the initial node and insert into the lists)
        this.prepareForSearch();

        while (true) {
            // Decrease the NR iterations
            --nrIterationsCount;

            SearchResult currentResult =
                    this._search(
                        // Don't clear anything - repair is performed!
                        false, false,
                        // Use the last solution cost for pruning (by F value)?
                        // TODO? Last cost?? The heuristic function is not monotonic!
                        maxPreviousCost);
            // Add current iteration
            accumulatorResult.addIteration(1, maxPreviousCost,
                    currentResult.getExpanded(), currentResult.getGenerated());
            // We will break here if we found a valid solution, or if AR was performed and there is no solution ...

            if (!this.reopen) {
                double solutionCost = maxPreviousCost;
                if (currentResult.hasSolution()) {
                    // Store the lastResult
                    lastResult = (SearchResultImpl)currentResult;
                    // Get current solution and check if it is sufficient
                    SearchResult.Solution currentSolution = currentResult.getSolutions().get(0);
                    solutionCost = currentSolution.getCost();
                    assert solutionCost <= maxPreviousCost;
                    maxPreviousCost = solutionCost;
                }
                // In case we are within the bound - let's mark it!
                if (this.checkSolutionWithinTheBound(solutionCost, deviatedWeight, bestF[0], bestF)) {
                    shouldReturn = true;
                }
            } else {
                // In case of reopening we must return immediately
                shouldReturn = true;
            }

            // Now check if we should return
            if (shouldReturn) {
                // A safety
                if (!currentResult.hasSolution()) {
                    currentResult = lastResult;
                }
                // Note that the finalResult is still based on only the previous counters, thus, adding to local will
                // reveal to the total value of counters
                currentResult.increase(accumulatorResult);
                ((SearchResultImpl)currentResult).addIterations(accumulatorResult);
                return currentResult;
            }

            // TODO: This check is a hack (even if there is no solution we should update the accumulator value)
            if (currentResult.hasSolution()) {
                // Otherwise, another iteration should be performed
                accumulatorResult.increase(currentResult);
            }

            assert nrIterationsCount >= 0 : "nrIterationsCount is " + nrIterationsCount;
            System.out.println("[INFO] Expanded " + currentResult.getExpanded() + " nodes during the last iteration");
            System.out.println("[INFO] Open now contains " + this.gequeue.size() + " states");
            System.out.println("[INFO] Failed with NR, moves " + this.incons.size() + " states to open and tries again");
            accumulatorResult.reopened += this.incons.size();

            // Now, we should update OPEN+FOCAL with the values from INCONS

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////

            // First, take the best node from the open list (best f^)
            Node oldBest = this.gequeue.peekOpen();

            // In case gequeue is empty it means we need to find the best node and make it to be in focal ...
            if (oldBest == null) {
                // Take the best node from cleanupForICL
                oldBest = this.cleanupForICL.peek();
            }

            // Now, Move all states from incons to open
            for (Node current : this.incons.values()) {
                // Insert the node to open+focal+cleanup!
                this._insertNodeNoCleanupForICL(current, oldBest);
            }

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////

            // No option to continue looking for solution ...
            if (this.gequeue.isEmpty()) {
                System.out.println("[INFO] Open is empty - returns previous solution");
                // A safety
                if (!currentResult.hasSolution()) {
                    currentResult = lastResult;
                }
                ((SearchResultImpl)currentResult).addIterations(accumulatorResult);
                // Since we already increased the accumulator result - just use its value
                ((SearchResultImpl)currentResult).copyCounters(accumulatorResult);
                return currentResult;
            }

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////

            // Let's update the best node in OPEN and FOCAL
            Node newBest = this.gequeue.peekOpen();

            int fHatChange = this.openComparator.compareIgnoreTies(newBest, oldBest);
            this.gequeue.updateFocal(oldBest, newBest, fHatChange);

            this.incons.clear();

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////

            System.out.println("[INFO] Open now contains " + this.gequeue.size() + " states (" +
                this.gequeue.focalSize() + " states in focal)");

            // Back to the start of the loop
            System.out.println("[INFO] Calling another search iteration (maxCost = " + maxPreviousCost +
                    ", bestF: " + bestF[0] + ")");

            // In case reopening should be stopped - assure this here
            if (nrIterationsCount <= 0) {
                this.reopen = true;
                System.out.println("[INFO] Next iteration will be with reopening");
            }
        }
        // TODO: Later!
        /*
        SearchResult.Solution sol = this.createSolution(previousGoal);
        if (sol.getCost() < previousResult.getSolutions().get(0).getCost()) {
            System.out.println("Win : prev cost " + previousResult.getSolutions().get(0).getCost() + " current cost " + sol.getCost() );
            System.exit(-1);
        }
        */
    }

    public SearchResult search(SearchDomain domain) {
        boolean previousReopenValue = this.reopen;
        SearchResult toReturn = null;

        this.domain = domain;
        // Set general values
        double deviatedWeight = this.weight * this.wAdmissibilityDeviation;
        if (this.weight != deviatedWeight) {
            System.out.println("[WARNING] Required weight can be deviated (weight: " + this.weight +
                    ", deviation: " + this.wAdmissibilityDeviation + ", deviated: " + deviatedWeight + ")");
        }
        // Solve according to the case
        switch (this.rerun) {
            case NO_RERUN: {
                this.reopen = false;
                // Run a single iteration and stop (NR) - nothing to repair!
                toReturn = this._search(
                            // Clear all the data structures
                            true, true,
                            // Bound is infinity
                            Double.MAX_VALUE);
                break;
            } case NRR1: {
                toReturn = this.searchNRR1(deviatedWeight);
                break;
            } case NRR1dot5: {
                toReturn = this.searchNRR1dot5or2(deviatedWeight, 1);
                break;
            } case NRR2: {
                toReturn = this.searchNRR1dot5or2(deviatedWeight, Integer.MAX_VALUE);
                break;
            }
        }
        // Restore the value of this.reopen
        this.reopen = previousReopenValue;
        // Return the calculated value
        return toReturn;
    }
}
