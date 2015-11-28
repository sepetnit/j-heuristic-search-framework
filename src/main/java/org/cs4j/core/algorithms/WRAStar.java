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
        WRAStar.WRAStarPossibleParameters.put("w-admissibility-deviation-percentage", String.class);
    }

    // The domain for the search
    private SearchDomain domain;

    // Open list (frontier)
    private SearchQueue<Node> open;

    // Open list (frontier)
    private SearchQueue<Node> cleanup;

    // Inconsistent list
    private Map<PackedElement, Node> incons;

    // Closed list (seen states)
    private Map<PackedElement, Node> closed;

    // TODO ...
    private HeapType heapType;

    // For weighted A*
    protected double weight;
    private double wAdmissibilityDeviation;

    public enum HeapType {BIN, BUCKET}

    public WRAStar(double weight) {
        this.weight = weight;
        this.heapType = HeapType.BIN;
        // By default - not deviation from the actual weight is permitted
        this.wAdmissibilityDeviation = 1.0d;
    }

    @Override
    public String getName() {
        return "wrastar";
    }

    private void _initDataStructures() {
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
            case "w-admissibility-deviation-percentage": {
                this.wAdmissibilityDeviation = Double.parseDouble(value);
                if (this.wAdmissibilityDeviation < 0.0d || this.wAdmissibilityDeviation >= 100.0d) {
                    System.out.println("[ERROR] The deviation percentage must be in [0, 100)");
                    throw new IllegalArgumentException();
                }
                this.wAdmissibilityDeviation = 1.0d + (this.wAdmissibilityDeviation / 100.0d);
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

    public Node _search(SearchDomain domain, int iterationIndex, double maxPreviousCost, SearchResultImpl result) {
        Node goal = null;
        State currentState;

        result.startTimer();

        // Loop over the frontier
        while (!this.open.isEmpty()) {
            // Take the first state (still don't remove it)
            Node currentNode = this.open.poll();
            assert currentNode.getIndex(1) != -1;
            assert this.cleanup.remove(currentNode) != null;

            // Prune
            if (currentNode.rF >= maxPreviousCost) {
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

            // Expand the current node
            ++result.expanded;
            // Go over all the possible operators and apply them
            for (int i = 0; i < domain.getNumOperators(currentState); ++i) {
                Operator op = domain.getOperator(currentState, i);
                // Try to avoid loops
                if (op.equals(currentNode.pop)) {
                    continue;
                }
                // Here we actually generate a new state
                ++result.generated;
                State childState = domain.applyOperator(currentState, op);
                Node childNode = new Node(childState, currentNode, currentState, op, op.reverse(currentState));

                // Prune
                if (childNode.rF >= maxPreviousCost) {
                    continue;
                }

                // Treat duplicates
                if (this.closed.containsKey(childNode.packed)) {
                    // Count the duplicates
                    ++result.duplicates;
                    // Get the previous copy of this node (and extract it)
                    Node dupChildNode = this.closed.get(childNode.packed);
                    // Take the h value from the previous version of the node (for case of randomization of h values)
                    childNode.computeHValues(dupChildNode.h);
                    // All this is relevant only if we reached the node via a cheaper path
                    if (dupChildNode.wF > childNode.wF) {
                        // If false - let's check it!
                        //assert dupChildNode.g > childNode.g;
                        if (dupChildNode.g > childNode.g) {

                            // Node in closed but we get duplicate
                            if (this.weight == 1.0 && dupChildNode.getIndex(this.open.getKey()) == -1 && this.domain.isCurrentHeuristicConsistent()) {
                                System.out.println(dupChildNode.wF + " " + childNode.wF);
                                System.out.println(dupChildNode.g + " " + childNode.g);
                                System.out.println(dupChildNode.h + " " + childNode.h);
                                //System.out.println(dupChildNode.parent.packed.getFirst());
                                //System.out.println(dupChildNode.packed.getFirst());
                                //System.out.println(domain.unpack(dupChildNode.parent.packed).dumpState());
                                //System.out.println(domain.unpack(childNode.parent.packed).dumpState());
                                assert false;
                            }

                            // In any case update the duplicate with the new values - we reached it via a shorter path
                            dupChildNode.wF = childNode.wF;
                            dupChildNode.rF = childNode.rF;
                            dupChildNode.g = childNode.g;
                            dupChildNode.op = childNode.op;
                            dupChildNode.pop = childNode.pop;
                            dupChildNode.parent = childNode.parent;

                            // In case the duplicate is also in the open list - let's just update it there
                            // (since we updated g and wF)
                            if (dupChildNode.getIndex(this.open.getKey()) != -1) {
                                ++result.opupdated;
                                this.open.update(dupChildNode);
                                this.cleanup.update(dupChildNode);
                                this.closed.put(dupChildNode.packed, dupChildNode);
                                // Otherwise, consider to reopen the node
                            } else {
                                this.incons.put(dupChildNode.packed, dupChildNode);
                                if (dupChildNode.getIndex(this.cleanup.getKey()) != -1) {
                                    this.cleanup.update(dupChildNode);
                                } else {
                                    this.cleanup.add(dupChildNode);
                                }
                                this.closed.put(dupChildNode.packed, dupChildNode);
                            }
                        }
                    }
                    // Otherwise, the node is new (hasn't been reached yet)
                } else {
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
        // TODO ...
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
        if (this.weight != this.wAdmissibilityDeviation) {
            System.out.println("[WARNING] Required weight can be deviated (weight: " + this.weight +
                    ", deviation: " + this.wAdmissibilityDeviation + ", deviated: " + deviatedWeight + ")");
        }
        while (true) {
            SearchResultImpl result = new SearchResultImpl();
            previousGoal = lastGoal;
            Node foundGoal = this._search(domain, iterationIndex++, maxPreviousCost, result);
            if (this.cleanup.size() > 0) {
                double currentBestF = this.cleanup.peek().rF;
                if (bestF < currentBestF) {
                    bestF = currentBestF;

                    System.out.println("[INFO] Iteration: " + (iterationIndex+1) + ", bestF: " + bestF + ", sub: " + suboptimalBoundSup);
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
                suboptimalBoundSup = maxPreviousCost / bestF;
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
                this.closed = new HashMap<>();
                // Continue searching (don't empty CLOSED)
                System.out.println("[INFO] Calling another search iteration (maxCost = " + maxPreviousCost + ", bestF: " + bestF + ")");
                //this.closed = new HashMap<>();
            } else {
                suboptimalBoundSup = maxPreviousCost / bestF;
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
                    }
                    this.incons.clear();
                    this.closed = new HashMap<>();
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
        private double wF;
        private double rF;
        private double g;
        private double h;

        private Operator op;
        private Operator pop;

        private Node parent;
        private PackedElement packed;
        private int[] secondaryIndex;

        private Node(State state, Node parent, State parentState, Operator op, Operator pop) {
            // Size of key
            super(2);
            // TODO: Why?
            this.secondaryIndex = new int[(heapType == HeapType.BUCKET) ? 2 : 1];
            double cost = (op != null) ? op.getCost(state, parentState) : 0;
            // If each operation costs something, we should add the cost to the g value of the parent
            this.g = (parent != null) ? parent.g + cost : cost;
            // Update h and f values
            this.computeHValues(state.getH());

            // Start of PathMax
            /*
            if (parent != null) {
                double costsDiff = this.g - parent.g;
                this.h = Math.max(this.h, (parent.h - costsDiff));
            }
            */
            // End of PathMax

            // Parent node
            this.parent = parent;
            this.packed = WRAStar.this.domain.pack(state);
            this.pop = pop;
            this.op = op;
        }

        /**
         * The function computes the F values according to the given heuristic value (which is computed externally)
         *
         * Also, all other values that depend on h are updated
         *
         * @param updatedHValue The updated heuristic value
         */
        public void computeHValues(double updatedHValue) {
            this.h = updatedHValue;
            this.wF = this.g + (WRAStar.this.weight * this.h);
            this.rF = this.g + this.h;
        }

        /**
         * A constructor of the class that instantiates only the state
         *
         * @param state The state which this node represents
         */
        private Node(State state) {
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
            return (level == 0) ? this.wF : this.g;
        }
    }

    /**
     * The nodes comparator class
     */
    protected final class OpenNodeComparator implements Comparator<Node> {

        @Override
        public int compare(final Node a, final Node b) {
            // First compare by wF (smaller is preferred), then by g (bigger is preferred)
            if (a.wF < b.wF) {
                return -1;
            }
            if (a.wF > b.wF) {
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
            if (a.rF < b.rF) {
                return -1;
            }
            if (a.rF > b.rF) {
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
