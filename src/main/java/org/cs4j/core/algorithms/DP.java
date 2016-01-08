package org.cs4j.core.algorithms;

import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchResult;
import org.cs4j.core.collections.*;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.*;

/**
 * Created by Daniel on 21/12/2015.
 */
public class DP  implements SearchAlgorithm {

    private static final int QID = 0;

    private static final Map<String, Class> DPPossibleParameters;

    // Declare the parameters that can be tuned before running the search
    static
    {
        DPPossibleParameters = new HashMap<>();
        DP.DPPossibleParameters.put("weight", Double.class);
        DP.DPPossibleParameters.put("reopen", Boolean.class);
        DP.DPPossibleParameters.put("emptyFocalRatio", Integer.class);
    }

    // The domain for the search
    private SearchDomain domain;
    // Open list (frontier)
    private BinHeapF<Node> open = new BinHeapF<>(new NodeComparator());
    // Closed list (seen states)
    private Map<PackedElement, Node> closed;

    // TODO ...
    private HeapType heapType;

    // TODO ...
    protected double maxCost;

    public enum HeapType {BIN, BUCKET}

    // For Dynamic Potential Bound
    protected double weight;
    // Whether to perform reopening of states
    private boolean reopen;
    //when to empty Focal
    private int emptyFocalRatio;

    /**
     * Sets the default values for the relevant fields of the algorithm
     */
    private void _initDefaultValues() {
        // Default values
        this.weight = 1.0;
        this.reopen = true;
        this.emptyFocalRatio = Integer.MAX_VALUE;
    }

    protected DP(double maxCost, HeapType heapType) {
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
    public DP(HeapType heapType) {
        this(Double.MAX_VALUE, heapType);
    }

    /**
     * A default constructor of the class (weight of 1.0, binary heap and AR)
     *
     */
    public DP() {
        this(Double.MAX_VALUE, HeapType.BIN);
    }

    @Override
    public String getName() {
        if(this.emptyFocalRatio==Integer.MAX_VALUE) return "DP";
        else return "DP"+this.emptyFocalRatio;
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
                heap = new BinHeapF<>(new NodeComparator());
                break;
        }
        return heap;
    }

    private void _initDataStructures() {
        this.open = new BinHeapF<>(new NodeComparator());
        //this.open = buildHeap(heapType, 100);
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
        SearchDomain.State currentState = domain.initialState();
        // set initial H as Fmin for potential computation
        this.open.setFmin(currentState.getH());
        // Create a graph node from this state
        Node initNode = new Node(currentState);

        // And add it to the frontier
        this.open.add(initNode);
        // The nodes are ordered in the closed list by their packed values
        this.closed.put(initNode.packed, initNode);

        forntierLoop:
        // Loop over the frontier
        while (!this.open.isEmpty()) {
            // Take the first state (still don't remove it)
            Node currentNode;
            currentNode = this.open.peek();
            if(open.getFminCount() * emptyFocalRatio < result.generated && emptyFocalRatio!=Integer.MAX_VALUE){
                currentNode = this.open.peekF();
            }

            if(currentNode.potential < DP.this.weight - 0.00001){
                System.out.println("F:"+currentNode.f);
                System.out.println("G:"+currentNode.g);
                System.out.println("H:"+currentNode.h);
            }

            // Extract the state from the packed value of the node
            currentState = domain.unpack(currentNode.packed);

            // Check for goal condition
            if (domain.isGoal(currentState)) {
                goal = currentNode;
                break;
            }

            // Expand the current node
            ++result.expanded;
            // Go over all the possible operators and apply them
            for (int i = 0; i < domain.getNumOperators(currentState); ++i) {
                SearchDomain.Operator op = domain.getOperator(currentState, i);
                // Try to avoid loops
                if (op.equals(currentNode.pop)) {
                    continue;
                }
                // Here we actually generate a new state
                ++result.generated;
                if(result.generated > 7000000){
                    System.out.println("[INFO] nearly out of memory - exiting state");
                    break forntierLoop;
                }
                SearchDomain.State childState = domain.applyOperator(currentState, op);
                Node childNode = new Node(childState, currentNode, currentState, op, op.reverse(currentState));

                // Treat duplicates
                if (this.closed.containsKey(childNode.packed)) {
                    // Count the duplicates
                    ++result.duplicates;
                    // Get the previous copy of this node (and extract it)
                    Node dupChildNode = this.closed.get(childNode.packed);
                    // Take the h value from the previous version of the node (for case of randomization of h values)
                    childNode.computeNodeValue(dupChildNode.h);
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
                            double oldf = dupChildNode.f;
                            dupChildNode.f = childNode.f;
                            dupChildNode.g = childNode.g;
                            dupChildNode.op = childNode.op;
                            dupChildNode.pop = childNode.pop;
                            dupChildNode.parent = childNode.parent;
                            dupChildNode.potential = childNode.potential;

                            // In case the duplicate is also in the open list - let's just update it there
                            // (since we updated g and f)
                            if (dupChildNode.getIndex(this.open.getKey()) != -1) {
                                ++result.opupdated;
                                this.open.updateF(dupChildNode,oldf);
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
            this.open.remove(currentNode);
        }

        result.stopTimer();

        // If a goal was found: update the solution
        if (goal != null) {
            SearchResultImpl.SolutionImpl solution = new SearchResultImpl.SolutionImpl(this.domain);
            List<SearchDomain.Operator> path = new ArrayList<>();
            List<SearchDomain.State> statesPath = new ArrayList<>();
            // System.out.println("[INFO] Solved - Generating output path.");
            double cost = 0;

            SearchDomain.State currentPacked = domain.unpack(goal.packed);
            SearchDomain.State currentParentPacked = null;
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
                System.out.println("[INFO] Goal G is higher than the actual cost " +
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
        return DP.DPPossibleParameters;
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
            case "emptyFocalRatio": {
                this.emptyFocalRatio = Integer.parseInt(value);
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
    protected final class Node extends SearchQueueElementImpl implements BucketHeap.BucketHeapElement,reComputeable {
        private double f;
        private double g;
        private double h;
        private double potential;

        private SearchDomain.Operator op;
        private SearchDomain.Operator pop;

        private Node parent;
        private PackedElement packed;
        private int[] secondaryIndex;
        private double fcounterFmin;

        private Node(SearchDomain.State state, Node parent, SearchDomain.State parentState, SearchDomain.Operator op, SearchDomain.Operator pop) {
            // Size of key
            super(2);
            // TODO: Why?
            this.secondaryIndex = new int[(heapType == HeapType.BUCKET) ? 2 : 1];
            double cost = (op != null) ? op.getCost(state, parentState) : 0;
            // If each operation costs something, we should add the cost to the g value of the parent
            this.g = (parent != null) ? parent.g + cost : cost;

            // Update potential, h and f values
            this.computeNodeValue(state.getH());

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
            this.packed = DP.this.domain.pack(state);
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
        public void computeNodeValue(double updatedHValue) {
            this.h = updatedHValue;
            this.f = this.g + this.h;
            this.fcounterFmin = open.getFmin();
            this.potential =  (this.fcounterFmin*DP.this.weight -this.g)/this.h;
        }

        public void reCalcValue() {
            if(open.getFmin() != this.fcounterFmin) {
                this.fcounterFmin = open.getFmin();
                this.potential =  (this.fcounterFmin*DP.this.weight -this.g)/this.h;
            }
        }


        /**
         * A constructor of the class that instantiates only the state
         *
         * @param state The state which this node represents
         */
        private Node(SearchDomain.State state) {
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

        @Override
        public double getF() {
            return this.f;
        }
    }

    /**
     * The nodes comparator class
     */
    protected final class NodeComparator implements Comparator<Node> {

        @Override
        public int compare(final Node a, final Node b) {
            // First compare by potential (bigger is preferred), then by f (smaller is preferred), then by g (bigger is preferred)
            if (a.potential > b.potential) return -1;
            if (a.potential < b.potential) return 1;

            if (a.f < b.f) return -1;
            if (a.f > b.f) return 1;

            if (a.g > b.g) return -1;
            if (a.g < b.g) return 1;
            return 0;
        }
    }
}
