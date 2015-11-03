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
import org.cs4j.core.collections.SearchQueue;

/**
 * A* Search and Weighted A* Search
 *
 * @author Matthew Hatem
 *
 * (Edited by Vitali Sepetnitsky)
 */
public class AStar implements SearchAlgorithm {

    private static final int QID = 0;

    // The domain for the search
    private SearchDomain domain;
    // Open list (frontier)
    private SearchQueue<Node> open;
    // Closed list (seen states)
    private Map<Long, Node> closed = new HashMap<Long, Node>();

    // TODO ...
    private HeapType heapType;

    // For weighted A*
    protected double weight;

    // TODO ...
    protected double maxCost;

    private List<Operator> path = new ArrayList<>(3);
    private List<State> statesPath = new ArrayList<>(3);

    public enum HeapType {BIN, BUCKET}

    // Whether to perform reopening of states
    private boolean reopen;

    protected AStar(double weight, double maxCost, HeapType heapType, boolean reopen) {
        this.weight = weight;
        this.maxCost = maxCost;
        this.heapType = heapType;
        this.open = buildHeap(heapType, 100);
        this.reopen = reopen;
    }

    /**
     * The Constructor
     *
     * @param heapType the type of heap to use (BIN | BUCKET)
     *
     * NOTE: Use weight of 1.0 by default and perform reopening of states (AR)
     */
    public AStar(HeapType heapType) {
        this(1.0, Double.MAX_VALUE, heapType, true);
    }

    /**
     * The default Constructor of the class (weight of 1.0, binary heap and AR)
     */
    public AStar() {
        this(1.0, Double.MAX_VALUE, HeapType.BIN, true);
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

    @Override
    public SearchResult search(SearchDomain domain) {
        this.domain = domain;
        double goalCost = Double.MAX_VALUE;

        SearchResultImpl result = new SearchResultImpl();
        result.startTimer();

        // Let's instantiate the initial state
        State state = domain.initialState();
        // Create a graph node from this state
        Node initNode = new Node(state);

        // And add it to the frontier
        this.open.add(initNode);
        // The nodes are ordered in the closed list by their packed values
        this.closed.put(initNode.packed, initNode);

        // Loop over the frontier
        while (!this.open.isEmpty()) {
            // Take the first state (still don't remove it)
            Node n = this.open.poll();
            // Extract the state from the packed value of the node
            state = domain.unpack(n.packed);

            // Check for goal condition
            if (domain.isGoal(state)) {
                // Goal cost in this case is the value of g - how many steps we performed
                goalCost = n.g;
                // Let' extract the solution path
                for (Node p = n; p != null; p = p.parent) {
                    // Operators path
                    this.path.add(p.op);
                    // States path
                    this.statesPath.add(domain.unpack(p.packed));
                }
                // Finally, reverse the paths (make them to be presented from start to goal)
                Collections.reverse(path);
                Collections.reverse(statesPath);
                break;
            }

            // Expand the current node
            result.expanded++;
            // Go over all the possible operators and apply them
            for (int i = 0; i < domain.getNumOperators(state); ++i) {
                Operator op = domain.getOperator(state, i);
                // Try to avoid loops
                if (op.equals(n.pop)) {
                    continue;
                }
                // Here we actually generate a new state
                result.generated++;
                State childState = domain.applyOperator(state, op);
                Node node = new Node(childState, n, op, op.reverse(state));

                // Treat duplicates
                if (this.closed.containsKey(node.packed)) {
                    // Count them
                    result.duplicates++;
                    // Get the previous copy of this node (and extract it)
                    Node dup = this.closed.get(node.packed);
                    // All this is relevant only if we reached the node via a cheaper path
                    if (dup.g > node.g) {

                        // In any case update the duplicate with the new values - we reached it via a shorter path
                        dup.f = node.f;
                        dup.g = node.g;
                        dup.op = node.op;
                        dup.pop = node.pop;
                        dup.parent = node.parent;

                        // In case the duplicate is also in the open list - let's just update it there
                        // (since we updated g and f)
                        if (dup.getIndex(this.open.getKey()) != -1) {
                            this.open.update(dup);
                        // Otherwise, consider to reopen the node
                        } else {
                            // Return to OPEN list only if reopening is allowed
                            if (this.reopen) {
                                ++result.reopened;
                                this.open.add(dup);
                            }
                        }
                    }
                // Otherwise, the node is new (hasn't been reached yet)
                } else {
                    this.open.add(node);
                    this.closed.put(node.packed, node);
                }
            }
        }

        result.stopTimer();

        // If a path was found, let's instantiate a solution
        if (this.path != null && this.path.size() > 0) {
            SolutionImpl solution = new SolutionImpl(this.domain);
            solution.addOperators(this.path);
            solution.addStates(this.statesPath);
            solution.setCost(goalCost);
            result.addSolution(solution);
        }

        return result;
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
        private long packed;
        private int[] secondaryIndex;

        private Node(State state, Node parent, Operator op, Operator pop) {
            // Size of key
            super(1);
            // TODO: Why?
            this.secondaryIndex = new int[(heapType == HeapType.BUCKET) ? 2 : 1];
            double cost = (op != null) ? op.getCost(state) : 0;
            this.h = state.getH();
            // If each operation costs something, we should add the cost to the g value of the parent
            this.g = (parent != null) ? parent.g + cost : cost;
            this.f = this.g + (AStar.this.weight * this.h);
            // Parent node
            this.parent = parent;
            this.packed = AStar.this.domain.pack(state);
            this.pop = pop;
            this.op = op;
        }

        /**
         * A constructor of the class that instantiates only the state
         *
         * @param state The state which this node represents
         */
        private Node(State state) {
            this(state, null, null, null);
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
