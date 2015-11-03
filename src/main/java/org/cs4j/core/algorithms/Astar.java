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
 */
public class Astar implements SearchAlgorithm {

    private static final int QID = 0;

    private SearchDomain domain;
    private SearchQueue<Node> open;
    private Map<Long, Node> closed = new HashMap<Long, Node>();

    private HeapType heapType;

    protected double weight;
    protected double maxCost;

    private List<Operator> path = new ArrayList<>(3);
    private List<State> statesPath = new ArrayList<>(3);

    public enum HeapType {BIN, BUCKET}

    private boolean reopen;

    /**
     * The Constructor
     */
    public Astar() {
        this(1.0, Double.MAX_VALUE, HeapType.BIN, true);
    }

    /**
     * The Constructor
     *
     * @param heapType the type of heap to use (BIN | BUCKET)
     */
    public Astar(HeapType heapType) {
        this(1.0, Double.MAX_VALUE, heapType, true);
    }

    protected Astar(double weight, double maxCost, HeapType heapType, boolean reopen) {
        this.weight = weight;
        this.maxCost = maxCost;
        this.heapType = heapType;
        this.open = buildHeap(heapType, 100);
        this.reopen = reopen;
    }

    private SearchQueue<Node> buildHeap(HeapType heapType, int size) {
        SearchQueue<Node> heap = null;
        switch (heapType) {
            case BUCKET:
                heap = new BucketHeap<Node>(size, QID);
                break;
            case BIN:
                heap = new BinHeap<Node>(new NodeComparator(), 0);
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

        State state = domain.initialState();

        Node initNode = new Node(state);

        open.add(initNode);
        closed.put(initNode.packed, initNode);
        while (!open.isEmpty()) {
            Node n = open.poll();
            state = domain.unpack(n.packed);

            // check for goal
            if (domain.isGoal(state)) {
                goalCost = n.g;
                for (Node p = n; p != null; p = p.parent) {
                    path.add(p.op);
                    statesPath.add(domain.unpack(p.packed));
                }
                Collections.reverse(path);
                Collections.reverse(statesPath);
                break;
            }

            // expand the node
            result.expanded++;
            for (int i = 0; i < domain.getNumOperators(state); i++) {
                Operator op = domain.getOperator(state, i);
                if (op.equals(n.pop)) {
                    continue;
                }
                result.generated++;
                State childState = domain.applyOperator(state, op);
                Node node = new Node(childState, n, op, op.reverse(state));

                // merge duplicates
                if (closed.containsKey(node.packed)) {
                    result.duplicates++;
                    Node dup = closed.get(node.packed);
                    if (dup.g > node.g) {
                        dup.f = node.f;
                        dup.g = node.g;
                        dup.op = node.op;
                        dup.pop = node.pop;
                        dup.parent = node.parent;
                        if (dup.getIndex(open.getKey()) != -1) {
                            open.update(dup);
                        } else {
                            // Return to OPEN list only if reopening is allowed
                            if (this.reopen) {
                                result.reopened++;
                                open.add(dup);
                            }
                        }
                    }
                } else {
                    open.add(node);
                    closed.put(node.packed, node);
                }
            }
        }

        result.stopTimer();

        if (path != null && path.size() > 0) {
            SolutionImpl solution = new SolutionImpl(this.domain);
            solution.addOperators(path);
            solution.addStates(statesPath);
            solution.setCost(goalCost);
            result.addSolution(solution);
        }

        return result;
    }

    /*
     * The node class
     */
    protected final class Node extends SearchQueueElementImpl implements BucketHeapElement {
        double f, g, h;
        Operator op, pop;
        Node parent;
        long packed;
        int[] secondaryIndex;

        private Node(State state) {
            this(state, null, null, null);
        }

        private Node(State state, Node parent, Operator op, Operator pop) {
            super(1);
            secondaryIndex = new int[(heapType == HeapType.BUCKET) ? 2 : 1];
            double cost = (op != null) ? op.getCost(state) : 0;
            this.h = state.getH();
            this.g = (parent != null) ? parent.g + cost : cost;
            this.f = this.g + (weight * this.h);
            this.parent = parent;
            packed = domain.pack(state);
            this.pop = pop;
            this.op = op;
        }

        @Override
        public void setSecondaryIndex(int key, int index) {
            secondaryIndex[key] = index;
        }

        @Override
        public int getSecondaryIndex(int key) {
            return secondaryIndex[key];
        }

        @Override
        public double getRank(int level) {
            return (level == 0) ? f : g;
        }
    }

    /*
     * The node comparator class
     */
    protected final class NodeComparator implements Comparator<Node> {
        @Override
        public int compare(final Node a, final Node b) {
            if (a.f < b.f) return -1;
            if (a.f > b.f) return 1;
            if (a.g > b.g) return -1;
            if (a.g < b.g) return 1;
            return 0;
        }
    }

}
