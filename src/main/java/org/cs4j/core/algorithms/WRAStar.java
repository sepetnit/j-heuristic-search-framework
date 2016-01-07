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

import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchDomain.Operator;
import org.cs4j.core.SearchDomain.State;
import org.cs4j.core.SearchResult;
import org.cs4j.core.algorithms.SearchResultImpl.SolutionImpl;
import org.cs4j.core.collections.BinHeap;
import org.cs4j.core.collections.BucketHeap.BucketHeapElement;
import org.cs4j.core.collections.PackedElement;
import org.cs4j.core.collections.Pair;
import org.cs4j.core.collections.SearchQueue;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A* Search and Weighted A* Search which makes a kind of repairing procedure in order to return solutions that are
 * within the required suboptimality bound
 *
 * @author Matthew Hatem
 *
 * (Edited by Vitali Sepetnitsky)
 */
public class WRAStar implements SearchAlgorithm {

    private static final int QID = 0;

    private static final Map<String, Class> WRAStarPossibleParameters;

    // Declare the parameters that can be tunes before running the search
    static
    {
        WRAStarPossibleParameters = new HashMap<>();
        WRAStar.WRAStarPossibleParameters.put("weight", Double.class);
        WRAStar.WRAStarPossibleParameters.put("w-admissibility-deviation-percentage", String.class);
        WRAStar.WRAStarPossibleParameters.put("iteration-to-start-reopening", Integer.class);
        WRAStar.WRAStarPossibleParameters.put("bpmx", Boolean.class);
        WRAStar.WRAStarPossibleParameters.put("restart-closed-list", Boolean.class);
    }

    // The domain for the search
    protected SearchDomain domain;

    // Open list (frontier)
    protected SearchQueue<Node> open;

    // Open list (frontier)
    protected SearchQueue<Node> cleanup;

    // Inconsistent list
    protected Map<PackedElement, Node> incons;

    // Closed list (seen states)
    protected Map<PackedElement, Node> closed;

    // TODO ...
    protected HeapType heapType;

    // For weighted A*
    protected double weight;
    private double wAdmissibilityDeviation;

    // The iteration of run, from which we start reopening
    private int iterationToStartReopening;

    public enum HeapType {BIN, BUCKET}

    protected boolean useBPMX;

    // Whether to empty the closed list after each iteration
    private boolean restartClosedList;

    public WRAStar() {
        // Default values
        this.weight = 1.0;
        this.useBPMX = false;
        this.heapType = HeapType.BIN;
        // By default - not deviation from the actual weight is permitted
        this.wAdmissibilityDeviation = 1.0d;
        // By default, we never reopen for any iteration
        this.iterationToStartReopening = Integer.MAX_VALUE;
        // By default, empty the closed list after each iteration
        this.restartClosedList = true;
    }

    @Override
    public String getName() {
        return "wrastar";
    }

    protected void _initDataStructures() {
        this.open =
                new BinHeap<>(
                        new OpenNodeComparator(),
                        0);
        this.cleanup =
                new BinHeap<>(
                        new CleanupNodeComparator(),
                        1);
        this.incons = new HashMap<>();
        this.closed = new HashMap<>();
    }

    @Override
    public Map<String, Class> getPossibleParameters() {
        return WRAStar.WRAStarPossibleParameters;
    }

    @Override
    public void setAdditionalParameter(String parameterName, String value) {
        switch (parameterName) {
            case "weight": {
                this.weight = Double.parseDouble(value);
                if (this.weight < 1.0d) {
                    System.out.println("[ERROR] The weight must be >= 1.0");
                    throw new IllegalArgumentException();
                } else if (this.weight == 1.0d) {
                    System.out.println("[WARNING] Weight of 1.0 is equivalent to A*");
                }
                break;
            }
            case "w-admissibility-deviation-percentage": {
                this.wAdmissibilityDeviation = Double.parseDouble(value);
                if (this.wAdmissibilityDeviation < 0.0d || this.wAdmissibilityDeviation >= 100.0d) {
                    System.out.println("[ERROR] The deviation percentage must be in [0, 100)");
                    throw new IllegalArgumentException();
                }
                this.wAdmissibilityDeviation = 1.0d + (this.wAdmissibilityDeviation / 100.0d);
                break;
            }
            case "iteration-to-start-reopening": {
                this.iterationToStartReopening = Integer.parseInt(value);
                if (this.iterationToStartReopening < 1) {
                    System.out.println("[ERROR] We can start reopening at least after the first iteration");
                    throw new IllegalArgumentException();
                }
                break;
            }
            case "bpmx": {
                this.useBPMX = Boolean.parseBoolean(value);
                if (this.useBPMX) {
                    System.out.println("[INFO] WRAStar will be ran with BPMX");
                }
                break;
            }
            case "restart-closed-list": {
                this.restartClosedList = Boolean.parseBoolean(value);
                break;
            }
            default: {
                throw new NotImplementedException();
            }
        }
    }

    private SearchResult.Solution createSolution(Node goal) {
        SolutionImpl solution = new SolutionImpl(this.domain);
        List<Operator> path = new ArrayList<>();
        List<State> statesPath = new ArrayList<>();
        double cost = 0;
        State currentPacked = domain.unpack(goal.packed);
        State currentParentPacked = null;
        for (Node currentNode = goal;
             currentNode != null;
             currentNode = currentNode.parent, currentPacked = currentParentPacked) {
            // If op of current node is not null that means that p has a parent
            if (currentNode.op != null) {
                path.add(currentNode.op);
                currentParentPacked = domain.unpack(currentNode.parent.packed);
                cost += currentNode.op.getCost(currentPacked, currentParentPacked);
            }
            statesPath.add(domain.unpack(currentNode.packed));
        }
        // The actual size of the found path can be only lower the G value of the found goal
        assert cost <= goal.g;
        if (cost - goal.g < 0) {
            System.out.println("[INFO] Goal G is higher that the actual cost " +
                    "(G: " + goal.g +  ", Actual: " + cost + ")");
        }

        Collections.reverse(path);
        solution.addOperators(path);

        Collections.reverse(statesPath);
        solution.addStates(statesPath);

        solution.setCost(cost);
        return solution;
    }

    protected Node _search(SearchDomain domain, int iterationIndex,
                           double maxPreviousCost, SearchResultImpl result) {
        Node goal = null;
        State currentState;

        result.startTimer();

        // Default value
        boolean reopen = false;
        // Check if we need to change the default value
        if (iterationIndex >= this.iterationToStartReopening) {
            System.out.println("[WARNING] Iteration " + iterationIndex + " implies reopening");
            reopen = true;
        }

        // Loop over the frontier
        while (!this.open.isEmpty()) {
            // Take the first state (still don't remove it)
            Node currentNode = this.open.poll();
            assert currentNode.getIndex(1) != -1;
            assert this.cleanup.remove(currentNode) != null;

            // Prune
            if (currentNode.getRf() >= maxPreviousCost) {
                continue;
            }

            // Extract the state from the packed value of the node
            currentState = domain.unpack(currentNode.packed);

            //System.out.println(currentState.dumpStateShort());
            // Check for goal condition
            if (domain.isGoal(currentState)) {
                goal = currentNode;
                break;
            }

            List<Pair<State, Node>> children = new ArrayList<>();

            // Expand the current node
            ++result.expanded;
            // Stores parent h-cost (from path-max)
            double bestHValue = 0.0d;
            // First, let's generate all the children
            // Go over all the possible operators and apply them
            for (int i = 0; i < domain.getNumOperators(currentState); ++i) {
                Operator op = domain.getOperator(currentState, i);
                // Try to avoid loops
                if (op.equals(currentNode.pop)) {
                    continue;
                }
                State childState = domain.applyOperator(currentState, op);
                Node childNode = new Node(childState, currentNode, currentState, op, op.reverse(currentState));
                // Prune
                if (childNode.getRf() >= maxPreviousCost) {
                    continue;
                }
                // Here we actually generated a new state
                ++result.generated;
                // Perform only if BPMX is required
                if (this.useBPMX) {
                    bestHValue = Math.max(bestHValue, childNode.h - op.getCost(childState, currentState));
                }
                children.add(new Pair<>(childState, childNode));
            }

            // Update the H Value of the parent in case of BPMX
            if (this.useBPMX) {
                currentNode.h = Math.max(currentNode.h, bestHValue);
                // Prune
                if (currentNode.getRf() >= maxPreviousCost) {
                    continue;
                }
            }

            // Go over all the possible operators and apply them
            for (Pair<State, Node> currentChild : children) {
                State childState = currentChild.getKey();
                Node childNode = currentChild.getValue();
                double edgeCost = childNode.op.getCost(childState, currentState);

                // Prune
                if (childNode.getRf() >= maxPreviousCost) {
                    continue;
                }

                // Treat duplicates
                if (this.closed.containsKey(childNode.packed)) {
                    // Count the duplicates
                    ++result.duplicates;
                    // Get the previous copy of this node (and extract it)
                    Node dupChildNode = this.closed.get(childNode.packed);

                    // Propagate the H value to child (in case of BPMX)
                    if (this.useBPMX) {
                        dupChildNode.h = Math.max(dupChildNode.h, currentNode.h - edgeCost);
                    }

                    if (dupChildNode.g > childNode.g) {
                        // Check that the f actually decreases
                        if (dupChildNode.getWf() > childNode.getWf()) {
                            // Do nothing
                        } else {
                            assert false;
                            continue;
                        }

                        // Node in closed but we get duplicate
                        if (this.weight == 1.0 && dupChildNode.getIndex(this.open.getKey()) == -1 && this.domain.isCurrentHeuristicConsistent()) {
                            System.out.println(dupChildNode.getWf() + " " + childNode.getWf());
                            System.out.println(dupChildNode.g + " " + childNode.g);
                            System.out.println(dupChildNode.h + " " + childNode.h);
                            //System.out.println(dupChildNode.parent.packed.getFirst());
                            //System.out.println(dupChildNode.packed.getFirst());
                            //System.out.println(domain.unpack(dupChildNode.parent.packed).dumpState());
                            //System.out.println(domain.unpack(childNode.parent.packed).dumpState());
                            assert false;
                        }

                        // In any case update the duplicate with the new values - we reached it via a shorter path
                        dupChildNode.g = childNode.g;
                        dupChildNode.op = childNode.op;
                        dupChildNode.pop = childNode.pop;
                        dupChildNode.parent = childNode.parent;

                        // In case the duplicate is also in the open list - let's just update it there
                        // (since we updated g)
                        if (dupChildNode.getIndex(this.open.getKey()) != -1) {
                            ++result.opupdated;
                            this.open.update(dupChildNode);
                            this.cleanup.update(dupChildNode);
                            // Otherwise, consider to reopen the node
                        } else {
                            // Never Reopen: Use Incons
                            if (!reopen) {
                                this.incons.put(dupChildNode.packed, dupChildNode);
                                // Always Reopen: Perform standard reopening
                            } else {
                                ++result.reopened;
                                this.open.add(dupChildNode);
                            }
                            // Update cleanup
                            if (dupChildNode.getIndex(this.cleanup.getKey()) != -1) {
                                this.cleanup.update(dupChildNode);
                            } else {
                                this.cleanup.add(dupChildNode);
                            }
                        }
                    } else {
                        // A shorter path has not been found, but let's update the node in open if its h increased
                        if (this.useBPMX) {
                            if (dupChildNode.getIndex(this.open.getKey()) != -1) {
                                this.open.update(dupChildNode);
                            }
                            if (dupChildNode.getIndex(this.cleanup.getKey()) != -1) {
                                this.cleanup.update(dupChildNode);
                            }
                        }
                    }
                    // Otherwise, the node is new (hasn't been reached yet)
                } else {
                    // Propagate the H value to child (in case of BPMX)
                    if (this.useBPMX) {
                        childNode.h = Math.max(childNode.h, currentNode.h - edgeCost);
                    }
                    this.open.add(childNode);
                    this.cleanup.add(childNode);
                    this.closed.put(childNode.packed, childNode);
                }
            }
        }

        /*
        System.out.println(open.size());
        System.out.println(incons.size());
        System.out.println(cleanup.size());
        assert open.size() + incons.size() == cleanup.size();
        */

        result.stopTimer();

        // If a goal was found: update the solution
        if (goal != null) {
            result.addSolution(this.createSolution(goal));
            // Record current iteration
            result.addIteration(iterationIndex, maxPreviousCost, result.expanded, result.generated);
        }

        return goal;
    }

    @Override
    public SearchResult search(SearchDomain domain) {

        this.domain = domain;

        double maxPreviousCost = Double.MAX_VALUE;

        // Initialize all the data structures required for the search
        this._initDataStructures();

        // Let's instantiate the initial state
        State currentState = domain.initialState();
        // Create a graph node from this state
        Node initNode = new Node(currentState);

        // And add it to the frontier
        this.open.add(initNode);
        this.cleanup.add(initNode);
        // The nodes are ordered in the closed list by their packed values
        this.closed.put(initNode.packed, initNode);


        System.out.println("[INFO] Performing first search");
        SearchResultImpl previousResult = null;
        Node lastGoal = null;
        Node previousGoal;
        int iterationIndex = 0;
        double bestF = 0;
        double suboptimalBoundSup = Double.MAX_VALUE;
        double deviatedWeight = this.weight * this.wAdmissibilityDeviation;
        if (this.weight != deviatedWeight) {
            System.out.println("[WARNING] Required weight can be deviated (weight: " + this.weight +
                    ", deviation: " + this.wAdmissibilityDeviation + ", deviated: " + deviatedWeight + ")");
        }
        double optimalCost = domain.getOptimalSolutionCost();
        while (true) {
            SearchResultImpl result = new SearchResultImpl();
            previousGoal = lastGoal;
            Node foundGoal = this._search(domain, iterationIndex++, maxPreviousCost, result);
            if (this.cleanup.size() > 0) {
                double currentBestF = this.cleanup.peek().getRf();
                if (bestF < currentBestF) {
                    bestF = currentBestF;

                    System.out.println("[INFO] Iteration: " + (iterationIndex + 1) + ", bestF: " + bestF + ", sub: " + suboptimalBoundSup);
                }
            }
            if (result.hasSolution()) {
                lastGoal = foundGoal;
                // Get current solution
                SearchResult.Solution currentSolution = result.getSolutions().get(0);
                // In any case, update the previous result solution values
                double prevExp = result.getExpanded();
                if (previousResult != null) {
                    result.addIterations(previousResult);
                    result.increase(previousResult);
                }
                assert currentSolution.getCost() <= maxPreviousCost;
                previousResult = result;
                // Update the maximum cost (if the search continues, all the nodes with g+h > maxCost will be pruned)
                maxPreviousCost = currentSolution.getCost();
                suboptimalBoundSup = (optimalCost != -1)? (maxPreviousCost / optimalCost) : (maxPreviousCost / bestF);
                System.out.println("[INFO] Approximated suboptimal bound is " + suboptimalBoundSup);
                if (suboptimalBoundSup <= deviatedWeight) {
                    System.out.println("[INFO] Bound is sufficient (required: " + deviatedWeight + ", got: " +
                            suboptimalBoundSup + ")");
                    return result;
                }
                System.out.println("[INFO] Insufficient bound (" + suboptimalBoundSup + " > " + deviatedWeight + "), expanded: " + prevExp);
                // In case we don't have any state to re-open: let's continue
                if (this.incons.isEmpty()) {
                    System.out.println("[INFO] No locally inconsistent states, returns current result");
                    return result;
                }
                /*
                while (!this.open.isEmpty()) {
                    this.open.poll();
                }*/
                // Now, let's continue the search
                for (Node current : this.incons.values()) {
                    this.open.add(current);
                }
                this.incons.clear();
                if (this.restartClosedList) {
                    this.closed = new HashMap<>();
                }
                System.out.println("[INFO] Calling another search iteration (maxCost = " + maxPreviousCost + ", bestF: " + bestF + ")");
            } else {
                suboptimalBoundSup = (optimalCost != -1)? (maxPreviousCost / optimalCost) : (maxPreviousCost / bestF);
                System.out.println("[INFO] (NoSolution) Iteration: " + (iterationIndex + 1) +
                        ", bestF: " + bestF + ", sub: " + suboptimalBoundSup);
                if (suboptimalBoundSup <= deviatedWeight) {
                    System.out.println("[INFO] (NoSolution) Bound is sufficient (required: " + deviatedWeight + ", got: " +
                            suboptimalBoundSup + ")");
                    return previousResult;
                }
                if (this.incons.size() > 0) {
                    System.out.println("[INFO] Last search emptied the open list, but incons still contains " + this.incons.size() + " states");
                    // Now, let's continue the search
                    for (Node current : this.incons.values()) {
                        this.open.add(current);
                        if (previousResult != null) {
                            ++previousResult.reopened;
                        }
                    }
                    this.incons.clear();
                    if (this.restartClosedList) {
                        this.closed = new HashMap<>();
                    }
                    continue;
                }
                assert this.cleanup.isEmpty();
                System.out.println("[INFO] Last search didn't reveal to solution - returns the previous search");
                SearchResult.Solution sol = this.createSolution(previousGoal);
                if (sol.getCost() < previousResult.getSolutions().get(0).getCost()) {
                    System.out.println("Win : prev cost " + previousResult.getSolutions().get(0).getCost() + " current cost " + sol.getCost() );
                    System.exit(-1);
                }

                return previousResult;
            }
        }
    }

    /**
     * The node class
     */
    protected final class Node extends SearchQueueElementImpl implements BucketHeapElement {
        private double g;
        private double h;

        private Operator op;
        private Operator pop;

        private Node parent;
        protected PackedElement packed;
        private int[] secondaryIndex;

        private Node(State state, Node parent, State parentState, Operator op, Operator pop) {
            // Size of key
            super(2);
            // TODO: Why?
            this.secondaryIndex = new int[(heapType == HeapType.BUCKET) ? 2 : 1];
            double cost = (op != null) ? op.getCost(state, parentState) : 0;

            this.h = state.getH();
            // If each operation costs something, we should add the cost to the g value of the parent
            this.g = (parent != null) ? parent.g + cost : cost;

            // Parent node
            this.parent = parent;
            this.packed = WRAStar.this.domain.pack(state);
            this.pop = pop;
            this.op = op;
        }

        /**
         * @return The value of the weighted evaluation function
         */
        public double getWf() {
            return this.g + (WRAStar.this.weight * this.h);
        }

        /**
         * @return The value of the regular evaluation function
         */
        public double getRf() {
            return this.g + this.h;
        }

        /**
         * A constructor of the class that instantiates only the state
         *
         * @param state The state which this node represents
         */
        protected Node(State state) {
            this(state, null, null, null, null);
        }


        @Override
        public void setSecondaryIndex(int key, int index) {
            this.secondaryIndex[key] = index;
        }

        @Override
        public int getSecondaryIndex(int key) {
            return this.secondaryIndex[key];
        }

        @Override
        public double getRank(int level) {
            return (level == 0) ? this.getWf() : this.g;
        }
    }

    /**
     * The nodes comparator class
     */
    protected final class OpenNodeComparator implements Comparator<Node> {

        @Override
        public int compare(final Node a, final Node b) {
            // First compare by wF (smaller is preferred), then by g (bigger is preferred)
            if (a.getWf() < b.getWf()) {
                return -1;
            }
            if (a.getWf() > b.getWf()) {
                return 1;
            }
            if (a.g > b.g) {
                return -1;
            }
            if (a.g < b.g) {
                return 1;
            }
            return 0;
        }
    }

    /**
     * The nodes comparator class
     */
    protected final class CleanupNodeComparator implements Comparator<Node> {

        @Override
        public int compare(final Node a, final Node b) {
            if (a.getRf() < b.getRf()) {
                return -1;
            }
            if (a.getRf() > b.getRf()) {
                return 1;
            }
            if (a.g > b.g) {
                return -1;
            }
            if (a.g < b.g) {
                return 1;
            }
            return 0;
        }
    }

}
