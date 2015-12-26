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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchDomain.Operator;
import org.cs4j.core.SearchDomain.State;
import org.cs4j.core.SearchResult;
import org.cs4j.core.algorithms.SearchResultImpl.SolutionImpl;
import org.cs4j.core.collections.BinHeap;
import org.cs4j.core.collections.BucketHeap;
import org.cs4j.core.collections.BucketHeap.BucketHeapElement;
import org.cs4j.core.collections.PackedElement;
import org.cs4j.core.collections.Pair;
import org.cs4j.core.collections.SearchQueue;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * A* Search and Weighted A* Search
 *
 * @author Matthew Hatem
 *
 * (Edited by Vitali Sepetnitsky)
 */
public class WAStar implements SearchAlgorithm {

    private static final int QID = 0;

    private static final Map<String, Class> WAStarPossibleParameters;

    // Declare the parameters that can be tuned before running the search
    static
    {
        WAStarPossibleParameters = new HashMap<>();
        WAStar.WAStarPossibleParameters.put("weight", Double.class);
        WAStar.WAStarPossibleParameters.put("reopen", Boolean.class);
        WAStar.WAStarPossibleParameters.put("max-cost", Double.class);
        WAStar.WAStarPossibleParameters.put("bpmx", Boolean.class);
    }

    // The domain for the search
    private SearchDomain domain;
    // Open list (frontier)
    private SearchQueue<Node> open;
    // Closed list (seen states)
    private Map<PackedElement, Node> closed;

    // TODO ...
    private HeapType heapType;

    public enum HeapType {BIN, BUCKET}

    // For weighted A*
    protected double weight;
    // Whether to perform reopening of states
    private boolean reopen;

    protected double maxCost;

    protected boolean useBPMX;

    /**
     * Sets the default values for the relevant fields of the algorithm
     */
    private void _initDefaultValues() {
        // Default values
        this.weight = 1.0;
        this.reopen = true;
        this.maxCost = Double.MAX_VALUE;
        this.useBPMX = false;
    }


    /**
     * A constructor
     *
     * @param heapType the type of heap to use (BIN | BUCKET)
     *
     */
    protected WAStar(HeapType heapType) {
        this.heapType = heapType;
        this._initDefaultValues();
    }

    /**
     * A default constructor of the class (weight of 1.0, binary heap and AR)
     *
     */
    public WAStar() {
        this(HeapType.BIN);
    }

    @Override
    public String getName() {
        return "wastar";
    }

    /**
     * Creates a heap according to the required type (Builder design pattern)
     *
     * @param heapType Type of the required heap (choose from the available types)
     * @param size Initial size of the heap
     *
     * NOTE: In case of unknown type, null is returned (no exception is thrown)
     * @return The created heap
     */
    private SearchQueue<Node> buildHeap(HeapType heapType, int size) {
        SearchQueue<Node> heap = null;
        switch (heapType) {
            case BUCKET:
                heap = new BucketHeap<>(size, QID);
                break;
            case BIN:
                heap = new BinHeap<>(new NodeComparator(), 0);
                break;
        }
        return heap;
    }

    private void _initDataStructures() {
        this.open = buildHeap(heapType, 100);
        this.closed = new HashMap<>();
    }

    @Override
    public SearchResult search(SearchDomain domain) {
        this.domain = domain;
        Node goal = null;
        // Initialize all the data structures required for the search
        this._initDataStructures();
        SearchResultImpl result = new SearchResultImpl();
        result.startTimer();

        // Let's instantiate the initial state
        State currentState = domain.initialState();
        // Create a graph node from this state
        Node initNode = new Node(currentState);

        // And add it to the frontier
        this.open.add(initNode);
        // The nodes are ordered in the closed list by their packed values
        this.closed.put(initNode.packed, initNode);

        // Loop over the frontier
        while (!this.open.isEmpty()) {
            // Take the first state (still don't remove it)
            Node currentNode = this.open.poll();

            // Prune
            if (currentNode.getRf() >= this.maxCost) {
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
                if (currentNode.getRf() >= this.maxCost) {
                    continue;
                }
            }

            // Go over all the possible operators and apply them
            for (Pair<State, Node> currentChild : children) {
                State childState = currentChild.getKey();
                Node childNode = currentChild.getValue();
                double edgeCost = childNode.op.getCost(childState, currentState);

                // Prune
                if (childNode.getRf() >= this.maxCost) {
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

                    // Found a shorter path to the node
                    if (dupChildNode.g > childNode.g) {
                        // Check that the f actually decreases
                        if (dupChildNode.getWf() > childNode.getWf()) {
                            // Do nothing
                        } else {
                            if (this.domain.isCurrentHeuristicConsistent()) {
                                assert false;
                            }
                            continue;
                        }

                        // In any case update the duplicate with the new values - we reached it via a shorter path
                        dupChildNode.g = childNode.g;
                        dupChildNode.op = childNode.op;
                        dupChildNode.pop = childNode.pop;
                        dupChildNode.parent = childNode.parent;

                        // Node in closed but we get duplicate
                        if (this.weight == 1.0 &&
                                dupChildNode.getIndex(this.open.getKey()) == -1 &&
                                this.domain.isCurrentHeuristicConsistent()) {
                            System.out.println(dupChildNode.getWf() + " " + childNode.getWf());
                            System.out.println(dupChildNode.getRf() + " " + childNode.getRf());
                            System.out.println(dupChildNode.g + " " + childNode.g);
                            System.out.println(dupChildNode.h + " " + childNode.h);
                            //System.out.println(dupChildNode.parent.packed.getFirst());
                            //System.out.println(dupChildNode.packed.getFirst());
                            //System.out.println(domain.unpack(dupChildNode.parent.packed).dumpState());
                            //System.out.println(domain.unpack(childNode.parent.packed).dumpState());
                            assert false;
                        }


                        // In case the duplicate is also in the open list - let's just update it there
                        // (since we updated g)
                        if (dupChildNode.getIndex(this.open.getKey()) != -1) {
                            ++result.opupdated;
                            this.open.update(dupChildNode);
                            // Otherwise, consider to reopen the node
                        } else {
                            // For debugging issues!
                            if (this.weight == 1.0 && this.domain.isCurrentHeuristicConsistent()) {
                                assert false;
                            }

                            // Return to OPEN list only if reopening is allowed
                            if (this.reopen) {
                                ++result.reopened;
                                this.open.add(dupChildNode);
                            }
                        }
                    } else {
                        // A shorter path has not been found, but let's update the node in open if its h increased
                        if (this.useBPMX) {
                            if (dupChildNode.getIndex(this.open.getKey()) != -1) {
                                this.open.update(dupChildNode);
                            }
                        }
                    }
                    // Otherwise, the node is new (hasn't been reached yet)
                } else {
                    // Propagate the H value to child (in case of BPMX)
                    if (this.useBPMX) {
                        childNode.h = Math.max(childNode.h,  currentNode.h - edgeCost);
                    }
                    this.open.add(childNode);
                    this.closed.put(childNode.packed, childNode);
                }
            }
        }

        result.stopTimer();

        // If a goal was found: update the solution
        if (goal != null) {
            SolutionImpl solution = new SolutionImpl(this.domain);
            List<Operator> path = new ArrayList<>();
            List<State> statesPath = new ArrayList<>();
            // System.out.println("[INFO] Solved - Generating output path.");
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
            result.addSolution(solution);
        }

        return result;
    }

    @Override
    public Map<String, Class> getPossibleParameters() {
        return WAStar.WAStarPossibleParameters;
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
            case "reopen": {
                this.reopen = Boolean.parseBoolean(value);
                break;
            }
            case "bpmx": {
                this.useBPMX = Boolean.parseBoolean(value);
                if (this.useBPMX) {
                    System.out.println("[INFO] WAStar will be ran with BPMX");
                }
                break;
            }
            case "max-cost": {
                this.maxCost = Double.parseDouble(value);
                if (this.maxCost <= 0) {
                    System.out.println("[ERROR] The maximum possible cost must be >= 0");
                    throw new IllegalArgumentException();
                }
                break;
            }
            default: {
                throw new NotImplementedException();
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
        private PackedElement packed;
        private int[] secondaryIndex;

        private Node(State state, Node parent, State parentState, Operator op, Operator pop) {
            // Size of key
            super(1);
            // TODO: Why?
            this.secondaryIndex = new int[(heapType == HeapType.BUCKET) ? 2 : 1];
            double cost = (op != null) ? op.getCost(state, parentState) : 0;
            this.h = state.getH();
            // If each operation costs something, we should add the cost to the g value of the parent
            this.g = (parent != null) ? parent.g + cost : cost;

            // Parent node
            this.parent = parent;
            this.packed = WAStar.this.domain.pack(state);
            this.pop = pop;
            this.op = op;
        }

        /**
         * @return The value of the weighted evaluation function
         */
        public double getWf() {
            return this.g + (WAStar.this.weight * this.h);
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
            return (level == 0) ? this.getWf() : this.g;
        }
    }

    /**
     * The nodes comparator class
     */
    protected final class NodeComparator implements Comparator<Node> {

        @Override
        public int compare(final Node a, final Node b) {
            // First compare by wF (smaller is preferred), then by g (bigger is preferred)
            if (a.getWf() < b.getWf()) return -1;
            if (a.getWf() > b.getWf()) return 1;
            if (a.g > b.g) return -1;
            if (a.g < b.g) return 1;
            return 0;
        }
    }

}
