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
    }

    // The domain for the search
    private SearchDomain domain;
    // Open list (frontier)
    private SearchQueue<Node> open;
    // Closed list (seen states)
    private Map<PackedElement, Node> closed;

    // TODO ...
    private HeapType heapType;

    // TODO ...
    protected double maxCost;

    public enum HeapType {BIN, BUCKET}

    // For weighted A*
    protected double weight;
    // Whether to perform reopening of states
    private boolean reopen;

    /**
     * Sets the default values for the relevant fields of the algorithm
     */
    private void _initDefaultValues() {
        // Default values
        this.weight = 1.0;
        this.reopen = true;
    }

    protected WAStar(double maxCost, HeapType heapType) {
        this.maxCost = maxCost;
        this.heapType = heapType;
        this._initDefaultValues();
    }

    /**
     * A constructor
     *
     * @param heapType the type of heap to use (BIN | BUCKET)
     *
     */
    public WAStar(HeapType heapType) {
        this(Double.MAX_VALUE, heapType);
    }

    /**
     * A default constructor of the class (weight of 1.0, binary heap and AR)
     *
     */
    public WAStar() {
        this(Double.MAX_VALUE, HeapType.BIN);
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

                // Treat duplicates
                if (this.closed.containsKey(childNode.packed)) {
                    // Count the duplicates
                    ++result.duplicates;
                    // Get the previous copy of this node (and extract it)
                    Node dupChildNode = this.closed.get(childNode.packed);
                    // Take the h value from the previous version of the node (for case of randomization of h values)
                    childNode.computeFValue(dupChildNode.h);
                    // All this is relevant only if we reached the node via a cheaper path
                    if (dupChildNode.f > childNode.f) {
                        // If false - let's check it!
                        //assert dupChildNode.g > childNode.g;
                        if (dupChildNode.g > childNode.g) {

                            // Node in closed but we get duplicate
                            if (this.weight == 1.0 && dupChildNode.getIndex(this.open.getKey()) == -1 && this.domain.isCurrentHeuristicConsistent()) {
                                System.out.println(dupChildNode.f + " " + childNode.f);
                                System.out.println(dupChildNode.g + " " + childNode.g);
                                System.out.println(dupChildNode.h + " " + childNode.h);
                                //System.out.println(dupChildNode.parent.packed.getFirst());
                                //System.out.println(dupChildNode.packed.getFirst());
                                //System.out.println(domain.unpack(dupChildNode.parent.packed).dumpState());
                                //System.out.println(domain.unpack(childNode.parent.packed).dumpState());
                                assert false;
                            }

                            // In any case update the duplicate with the new values - we reached it via a shorter path
                            dupChildNode.f = childNode.f;
                            dupChildNode.g = childNode.g;
                            dupChildNode.op = childNode.op;
                            dupChildNode.pop = childNode.pop;
                            dupChildNode.parent = childNode.parent;

                            // In case the duplicate is also in the open list - let's just update it there
                            // (since we updated g and f)
                            if (dupChildNode.getIndex(this.open.getKey()) != -1) {
                                ++result.opupdated;
                                this.open.update(dupChildNode);
                                this.closed.put(dupChildNode.packed, dupChildNode);
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
                                this.closed.put(dupChildNode.packed, dupChildNode);
                            }
                        }
                    }
                    // Otherwise, the node is new (hasn't been reached yet)
                } else {
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
            default: {
                throw new NotImplementedException();
            }
        }
    }


    /**
     * The node class
     */
    protected final class Node extends SearchQueueElementImpl implements BucketHeapElement {
        private double f;
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
            // If each operation costs something, we should add the cost to the g value of the parent
            this.g = (parent != null) ? parent.g + cost : cost;

            // Update h and f values
            this.computeFValue(state.getH());

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
            this.packed = WAStar.this.domain.pack(state);
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
        public void computeFValue(double updatedHValue) {
            this.h = updatedHValue;
            this.f = this.g + (WAStar.this.weight * this.h);
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
            return (level == 0) ? this.f : this.g;
        }
    }

    /**
     * The nodes comparator class
     */
    protected final class NodeComparator implements Comparator<Node> {

        @Override
        public int compare(final Node a, final Node b) {
            // First compare by f (smaller is preferred), then by g (bigger is preferred)
            if (a.f < b.f) return -1;
            if (a.f > b.f) return 1;
            if (a.g > b.g) return -1;
            if (a.g < b.g) return 1;
            return 0;
        }
    }

}
