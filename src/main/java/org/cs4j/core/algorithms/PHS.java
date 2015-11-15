package org.cs4j.core.algorithms;

import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchDomain.Operator;
import org.cs4j.core.SearchDomain.State;
import org.cs4j.core.SearchResult;
import org.cs4j.core.algorithms.SearchResultImpl.SolutionImpl;
import org.cs4j.core.collections.BinHeap;
import org.cs4j.core.collections.BucketHeap;
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
 * Created by sepetnit on 11/12/2015.
 *
 * This class implements Pure Heuristic Search (PHS) which is sometimes called Greedy Search as a kind of
 * Bounded-Suboptimal-Search algorithm
 *
 * The algorithm supports pruning which means that a maximum cost can be defined and all states whose h value
 * is greater than the maximum value can be pruned
 */

public class PHS implements SearchAlgorithm {

    private static final Map<String, Class> PHSPossibleParameters;

    // Declare the parameters that can be tunes before running the search
    static
    {
        PHSPossibleParameters = new HashMap<>();
        PHSPossibleParameters.put("max-cost", Double.class);
        PHSPossibleParameters.put("reopen", Boolean.class);
        PHSPossibleParameters.put("rerun-if-not-found-and-nr", Boolean.class);
    }

    private static final int QID = 0;

    public enum HeapType {BIN, BUCKET}
    // TODO ...
    private HeapType heapType;

    // The domain for the search
    private SearchDomain domain;
    // Open list (frontier)
    private SearchQueue<Node> open;
    // Closed list (seen states)
    private Map<PackedElement, Node> closed;

    protected double maxCost;
    // Whether to perform reopening of states
    private boolean reopen;
    // Whether to re-run the algorithm with AR if solution not found and currently NR
    private boolean rerun;

    /**
     * The default Constructor for PHS (AR and maximum cost of DOUBLE.MAX_VALUE)
     */
    public PHS() {
        this.heapType = HeapType.BIN;
        // Default values for parameters
        this.maxCost = Double.MAX_VALUE;
        this.reopen = true;
        // Initially, no rerun is allowed if NR failed
        this.reopen = false;
    }

    @Override
    public String getName() {
        return "phs";
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
        // (Initial size is 100)
        this.open = buildHeap(heapType, 100);
        this.closed = new HashMap<>();
    }

    @Override
    public Map<String, Class> getPossibleParameters() {
        return PHS.PHSPossibleParameters;
    }

    @Override
    public void setAdditionalParameter(String parameterName, String value) {
        switch (parameterName) {
            case "reopen": {
                this.reopen = Boolean.parseBoolean(value);
                break;
            } case "max-cost": {
                this.maxCost = Double.parseDouble(value);
                break;
            } case "rerun-if-not-found-and-nr": {
                this.rerun = Boolean.parseBoolean(value);
                break;
            } default: {
                System.err.println("No such parameter: " + parameterName + " (value: " + value + ")");
                throw new NotImplementedException();
            }
        }
    }

    public SearchResult _search(SearchDomain domain) {
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

        // A trivial case
        if (domain.isGoal(currentState)) {
            goal = initNode;
            System.err.println("[WARNING] Trivial case occurred - something wrong?!");
            assert false;
        }

        // Loop over the frontier
        while ((goal == null) && !this.open.isEmpty()) {
            // Take the first state (still don't remove it)
            Node currentNode = this.open.poll();
            // Extract the state from the packed value of the node
            currentState = domain.unpack(currentNode.packed);

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

                // Ignore the node if its f value is too big
                if (childNode.f > this.maxCost) {
                    continue;
                }

                // If the generated node satisfies the goal condition - let' mark the goal and break
                if (domain.isGoal(childState)) {
                    goal = childNode;
                    break;
                }

                // Treat duplicates
                if (this.closed.containsKey(childNode.packed)) {
                    // Count the duplicates
                    ++result.duplicates;
                    // Get the previous copy of this node (and extract it)
                    Node dupChildNode = this.closed.get(childNode.packed);
                    // All this is relevant only if we reached the node via a cheaper path
                    if (dupChildNode.f > childNode.f) {
                        // If false - let's check it!
                        //assert dupChildNode.g > childNode.g;
                        if (dupChildNode.g > childNode.g) {

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
                                // Otherwise, consider to reopen the node
                            } else {
                                // Return to OPEN list only if reopening is allowed
                                if (this.reopen) {
                                    ++result.reopened;
                                    this.open.add(dupChildNode);
                                }
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
            System.out.println("[INFO] Solved - Generating output path.");
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

            // Assert path is at most of maxCost length
            assert path.size() <= this.maxCost;

            Collections.reverse(path);
            solution.addOperators(path);

            Collections.reverse(statesPath);
            solution.addStates(statesPath);

            solution.setCost(cost);
            result.addSolution(solution);
        }

        return result;
    }

    public SearchResult search(SearchDomain domain) {
        SearchResult toReturn = this._search(domain);
        if (!toReturn.hasSolution() && (!this.reopen && this.rerun)) {
            System.out.println("[INFO] PHS Failed with NR, tries again with AR");
            this.reopen = true;
            SearchResult toReturnAR = this._search(domain);
            toReturnAR.increase(toReturn);
            // Revert to base state
            this.reopen = false;
            if (toReturnAR.hasSolution()) {
                System.out.println("[INFO] PHS with NR failed but PHS with AR succeeded.");
            }
            return  toReturnAR;
        }
        return toReturn;
    }

    /**
     * The node class
     */
    protected final class Node extends SearchQueueElementImpl implements BucketHeap.BucketHeapElement {
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
            this.h = state.getH();

            // If each operation costs something, we should add the cost to the g value of the parent
            this.g = (parent != null) ? parent.g + cost : cost;

            // Start of PathMax
            if (parent != null) {
                double costsDiff = this.g - parent.g;
                this.h = Math.max(this.h, (parent.h - costsDiff));
            }
            // End of PathMax
            this.f = this.g + this.h;

            // Parent node
            this.parent = parent;
            this.packed = PHS.this.domain.pack(state);
            this.pop = pop;
            this.op = op;
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
            return (level == 0) ? this.h : this.g;
        }
    }

    /**
     * The nodes comparator class
     */
    protected final class NodeComparator implements Comparator<Node> {

        @Override
        public int compare(final Node a, final Node b) {
            // First compare by h (smaller is preferred), then by g (bigger is preferred)
            if (a.h < b.h) return -1;
            if (a.h > b.h) return 1;
            if (a.g > b.g) return -1;
            if (a.g < b.g) return 1;
            return 0;
        }
    }

}
