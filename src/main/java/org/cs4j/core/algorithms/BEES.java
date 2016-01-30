package org.cs4j.core.algorithms;

import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchDomain.Operator;
import org.cs4j.core.SearchDomain.State;
import org.cs4j.core.SearchResult;
import org.cs4j.core.collections.BinHeap;
import org.cs4j.core.collections.PackedElement;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An implementation of Bounded Cost Explicit Estimation Search
 *
 * According to the following paper: Faster Bounded-Cost Search Using Inadmissible Estimates
 */
public class BEES implements SearchAlgorithm {
    private static final int OPEN_ID = 0;
    private static final int CLEANUP_ID = 1;

    private static final Map<String, Class> BEESPossibleParameters;

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

    // Declare the parameters that can be tunes before running the search
    static
    {
        BEESPossibleParameters = new HashMap<>();
        BEES.BEESPossibleParameters.put("max-cost", Double.class);
        BEES.BEESPossibleParameters.put("reopen", Boolean.class);
        BEES.BEESPossibleParameters.put("rerun-if-not-found-and-nr", Boolean.class);
    }

    private SearchDomain domain;

    // The type of re-runing to apply if the search failed to run with NR (no solution of the required cost was found)
    private RERUN_TYPES rerun;
    // The maximum cost (C) for the search
    private double maxCost;
    // Whether to perform reopening of nodes
    private boolean reopen;

    // open is implemented as a binary heap and actually contains nodes ordered by their dHat(n) values
    // Note that this list contains only nodes which support the following rule: fHat(n) <= C
    private BinHeap<Node> open;
    // cleanup is implemented as a binary heap and actually contains nodes ordered by their f values
    private BinHeap<Node> cleanup;
    // Inconsistent list
    protected Map<PackedElement, Node> incons;
    // Closed list
    private Map<PackedElement, Node> closed;

    /**
     * Initializes all the data structures required for the search, especially OPEN, FOCAL, CLEANUP and CLOSED lists
     */
    private void _initDataStructures(boolean clearOpen, boolean clearIncons, boolean clearClosed) {
        if (clearOpen || this.open == null) {
            this.open =
                    new BinHeap<>(
                            new OpenNodeComparator(),
                            BEES.OPEN_ID);
            this.cleanup =
                    new BinHeap<>(
                            new CleanupNodeComparator(),
                            BEES.CLEANUP_ID);
        }
        // Note that here if incons is null it means we don't actually need it!
        if (clearIncons && this.incons != null) {
            this.incons = new HashMap<>();
        }

        if (clearClosed || this.closed == null) {
            this.closed = new HashMap<>();
        }
    }

    /**
     * The constructor of the class
     */
    public BEES() {
        // Initially, the maxCost is MAX_DOUBLE (Converge to greedy search of dHat(n)) and reopening is allowed
        this.maxCost = Double.MAX_VALUE;
        this.reopen = true;
        this.rerun = RERUN_TYPES.NO_RERUN;
    }

    @Override
    public String getName() {
        return "bees";
    }

    private Node _selectNode() {
        Node toReturn = null;
        if (!this.open.isEmpty()) {
            toReturn = this.open.poll();
            if (toReturn.getIndex(BEES.CLEANUP_ID) != -1) {
                this.cleanup.remove(toReturn);
            }
        } else if (!this.cleanup.isEmpty()) {
            toReturn = this.cleanup.poll();
        }
        return toReturn;
    }


    /**
     * The function inserts a new generated node into the lists
     *
     * @param node The node to insert
     */
    private void _insertNode(Node node) {
        if (node.fHat <= this.maxCost) {
            this.open.add(node);
        }
        this.cleanup.add(node);
        this.closed.put(node.packed, node);
    }

    @Override
    public Map<String, Class> getPossibleParameters() {
        return BEES.BEESPossibleParameters;
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
            } case "nrr-type": {
                if (this.reopen) {
                    System.out.println("[ERROR] Can define type of rerun only if reopen is not permitted");
                    throw new IllegalArgumentException();
                }
                switch (value) {
                    case "nrr1": {
                        this.rerun = RERUN_TYPES.NRR1;
                        break;
                    }
                    case "nrr1.5": {
                        this.rerun = RERUN_TYPES.NRR1dot5;
                        this.incons = new HashMap<>();
                        break;
                    }
                    case "nrr2": {
                        this.rerun = RERUN_TYPES.NRR2;
                        this.incons = new HashMap<>();
                        break;
                    }
                    default: {
                        System.out.println("[ERROR] The available rerun types are 'nrr1' and 'nrr1.5' and 'nrr2'");
                        throw new IllegalArgumentException();
                    }
                }
                break;
            } default: {
                System.err.println("[ERROR] No such parameter: " + parameterName + " (value: " + value + ")");
                throw new NotImplementedException();
            }
        }
    }

    private boolean canContinueRunning() {
        return !this.open.isEmpty() || !this.cleanup.isEmpty();
    }

    private SearchResult _search(boolean clearOpenList) {
        // The result will be stored here
        Node goal = null;

        SearchResultImpl result = new SearchResultImpl();
        result.startTimer();

        // Initialize all the data structures required for the search
        this._initDataStructures(clearOpenList, true, clearOpenList);

        // Extract the initial state from the domain
        State currentState = domain.initialState();
        // Initialize a search node using the state (contains data according to the current
        // algorithm)
        Node initialNode = new Node(currentState, null, null, null, null);

        // A trivial case
        if (domain.isGoal(currentState)) {
            System.err.println("[WARNING] Trivial case occurred - something wrong?!");
            assert false;
            goal = initialNode;
        }

        // Start the search: Add the node to the relevant lists
        this._insertNode(initialNode);

        // Loop while there is no solution and there are states in the OPEN list
        while ((goal == null) && this.canContinueRunning()) {
            // Take a node from the OPEN list (nodes are sorted according to the 'u' function)
            Node currentNode = this._selectNode();
            // Debug ...
            if (currentNode == null) {
                System.err.println("[ERROR] Selected node is null - something bad occurred!");
                assert false;
            }
            // Extract a state from the node
            currentState = domain.unpack(currentNode.packed);
            // expand the node (since, if its g satisfies the goal test - it would be already returned)
            ++result.expanded;
            // Go over all the successors of the state
            for (int i = 0; i < domain.getNumOperators(currentState); ++i) {
                // Get the current operator
                Operator op = domain.getOperator(currentState, i);
                // Don't apply the previous operator on the state - in order not to enter a loop
                if (op.equals(currentNode.pop)) {
                    continue;
                }
                // Otherwise, let's generate the child state
                ++result.generated;
                // Get it by applying the operator on the parent state
                State childState = domain.applyOperator(currentState, op);
                // Create a search node for this state
                Node childNode = new Node(childState, currentNode, currentState, op, op.reverse(currentState));

                // Prune nodes over the bound
                if (childNode.f > this.maxCost) {
                    continue;
                }

                // If the generated node satisfies the goal condition - let' mark the goal and break
                if (domain.isGoal(childState)) {
                    goal = childNode;
                    break;
                }

                // If we got here - the state isn't a goal!

                // Now, merge duplicates - let's check if the state already exists in CLOSE/OPEN:
                // In the node is not in the CLOSED list, then it is also not in the OPEN list
                // In any case it can't be that node is a goal - otherwise, we should return it
                // when we see it at first
                if (this.closed.containsKey(childNode.packed)) {
                    // Count the duplicates
                    ++result.duplicates;
                    // Take the duplicate node
                    Node dupChildNode = this.closed.get(childNode.packed);
                    // Consider only duplicates with higher g-value
                    if (dupChildNode.g > childNode.g) {
                        // In case the node is in the OPEN list - update its key using the new G
                        if (dupChildNode.getIndex(this.open.getKey()) != -1) {
                            ++result.opupdated;
                            // Update all the values from the duplicate state (including hHat and dHat)
                            dupChildNode.updateFromDuplicate(childNode, childState, currentState);
                            // Now, update the location of the node in OPEN and CLEANUP (already in CLOSED)
                            this.open.update(dupChildNode);
                            // It must be in cleanup too!
                            assert dupChildNode.getIndex(this.cleanup.getKey()) != -1;
                            this.cleanup.update(dupChildNode);
                        } else {
                            // In any case, update the duplicate node (remains the same in closed)
                            // Update all the values from the duplicate state (including hHat and dHat)
                            dupChildNode.updateFromDuplicate(childNode, childState, currentState);
                            // Return to OPEN list only if reopening is allowed
                            if (this.reopen) {
                                ++result.reopened;
                                if (dupChildNode.fHat <= this.maxCost) {
                                    this.open.add(dupChildNode);
                                }
                                this.cleanup.add(dupChildNode);
                            } else {
                                // For never reopen
                                if (this.incons != null) {
                                    this.incons.put(dupChildNode.packed, dupChildNode);
                                }
                            }
                        }
                    }
                    // Consider the new node only if its cost is lower than the maximum cost
                } else {
                    this._insertNode(childNode);
                }
            }
        }
        // Stop the timer and check that a goal was found
        result.stopTimer();

        // If a goal was found: update the solution
        if (goal != null) {
            SearchResultImpl.SolutionImpl solution = new SearchResultImpl.SolutionImpl(this.domain);
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

            Collections.reverse(path);
            solution.addOperators(path);

            Collections.reverse(statesPath);
            solution.addStates(statesPath);

            solution.setCost(cost);
            result.addSolution(solution);
        }

        return result;

    }

    /**
     * The function performs NRR for BEES - some iterations with NR (and putting the states that should be reopened
     * into an inconsistency list) are performed. Then, the algorithm continues solving using AR.
     *
     * @param nrIterationsCount The number of iterations of NR
     *
     * @return The found solution
     */
    private SearchResult iterativeSearch(int nrIterationsCount) {
        SearchResult accumulatorResult = new SearchResultImpl();
        // TODO: This is ugly!
        boolean previousReopenValue = this.reopen;

        SearchResult currentResult;
        this.reopen = false;

        while (true) {
            // Decrease the NR iterations
            --nrIterationsCount;
            // In case reopening should be stopped - assure this here
            if (nrIterationsCount < 0) {
                this.reopen = true;
            }
            // Search without cleaning open/closed
            currentResult = this._search(false);
            // Add current iteration
            ((SearchResultImpl) accumulatorResult).addIteration(1, this.maxCost,
                    currentResult.getExpanded(), currentResult.getGenerated());
            // We will break here if we found a valid solution, or if AR was performed and there is no solution ...
            if (currentResult.hasSolution() || this.reopen) {
                ((SearchResultImpl)currentResult).addIterations((SearchResultImpl)accumulatorResult);
                // Note that the finalResult is still based on only the previous counters, thus, adding to local will
                // reveal to the total value of counters
                currentResult.increase(accumulatorResult);
                break;
            }
            // Update the total result
            accumulatorResult.increase(currentResult);
            assert nrIterationsCount >= 0;
            System.out.println("[INFO] BEES Failed with NR, moves " + incons.size() +
                    " states to open and tries again");
            // Update number of reopened states
            ((SearchResultImpl) accumulatorResult).reopened += this.incons.size();
            for (PackedElement current : this.incons.keySet()) {
                this._insertNode(this.incons.get(current));
            }
            this.incons.clear();
            System.out.println("[INFO] Now there are " + this.open.size() + " states in open and " +
                this.cleanup.size() + " states in cleanup; runs again");
            // No option to continue looking for solution ...
            if (!this.canContinueRunning()) {
                // Note that the finalResult is still based on only the previous counters, thus, adding to local
                // will reveal to the total value of counters
                currentResult.increase(accumulatorResult);
                break;
            }
            // Back to start of the loop
        }
        this.reopen = previousReopenValue;
        return currentResult;
    }


    public SearchResult search(SearchDomain domain) {
        this.domain = domain;
        // Perform initialization of all the data structures used during the search
        this._initDataStructures(true, true, true);
        switch (this.rerun) {
            case NO_RERUN: {
                // Run a single iteration and stop
                return this._search(true);
            }
            case NRR1: {
                // First search with NR (clear Open)
                SearchResult nrResult = this._search(true);
                // Run from scratch if required
                if (!nrResult.hasSolution()) {
                    System.out.println("[INFO] PTS Failed with NR, tries again with AR from scratch");
                    this.reopen = true;
                    SearchResult arResult = this._search(true);
                    // Add current iteration
                    ((SearchResultImpl) arResult).addIteration(1, this.maxCost,
                            nrResult.getExpanded(), nrResult.getGenerated());
                    arResult.increase(nrResult);
                    // Revert to base state
                    this.reopen = false;
                    if (arResult.hasSolution()) {
                        System.out.println("[INFO] PTS with NR failed but PTS with AR from scratch succeeded.");
                    }
                    // In any case return the arResult
                    return arResult;
                }
                // Return the first result
                return nrResult;
            }
            case NRR1dot5: {
                return this.iterativeSearch(1);
            }
            case NRR2: {
                return this.iterativeSearch(Integer.MAX_VALUE);
            }
        }
        // We should not go here!
        return null;
    }

    /**
     * This comparator is used in order to sort the cleanup list on F
     */
    private final class CleanupNodeComparator implements Comparator<Node> {
        @Override
        public int compare(final Node a, final Node b) {
            // Smaller F is better
            if (a.f < b.f) {
                return -1;
            } else if (a.f > b.f) {
                return 1;
                // Otherwise, compare the nodes by G values: higher G is better
            } else if (b.g < a.g) {
                return -1;
            } else if (b.g > a.g) {
                return 1;
            }
            // Otherwise, the nodes are equal
            return 0;
        }
    }

    /**
     * This comparator is used to sort the open list on fHat (f^)
     */
    private final class OpenNodeComparator implements Comparator<Node> {
        @Override
        public int compare(final Node a, final Node b) {
            // Lower dHat is better (closer to goal)
            if (a.dHat < b.dHat) {
                return -1;
            } else if (a.dHat > b.dHat) {
                return 1;
                // Break ties on low fHat: Smaller FHat is better
            } if (a.fHat < b.fHat) {
                return -1;
            } else if (a.fHat > b.fHat) {
                return 1;
                // break ties on G : Higher is better
            } else if (a.g > b.g) {
                return -1;
            } else if (a.g < b.g) {
                return 1;
            }
            // If we are here - consider as equal
            return 0;
        }
    }

    /**
     * The EES node is more complicated than other nodes;
     * It is currently responsible for computing single step error corrections and dHat and hHat values.
     * Right now we only have path based single step error (SSE) correction implemented.
     *
     * TODO: implement other methods for SSE correction and design the necessary abstractions to move out of the
     * TODO: node class.
     */
    private class Node extends SearchQueueElementImpl {
        private double f;
        private double g;
        private double d;
        private double h;
        private double sseH;
        private double sseD;
        private double fHat;
        private double hHat;
        private double dHat;

        private int depth;
        private Operator op;
        private Operator pop;

        private Node parent;

        private PackedElement packed;

        /**
         * Use the Path Based Error Model by calculating the mean one-step error only along the current search
         * path: The cumulative single-step error experienced by a parent node is passed down to all of its children

         * @return The calculated sseHMean
         */
        private double __calculateSSEMean(double totalSSE) {
            return (this.g == 0) ? totalSSE : totalSSE / this.depth;
        }

        /**
         * @return The mean value of sseH
         */
        private double _calculateSSEHMean() {
            return this.__calculateSSEMean(this.sseH);
        }

        /**
         * @return The mean value of sseD
         */
        private double _calculateSSEDMean() {
            return this.__calculateSSEMean(this.sseD);
        }

        /**
         * @return The calculated hHat value
         *
         * NOTE: if our estimate of sseDMean is ever as large as one, we assume we have infinite cost-to-go.
         */
        private double _computeHHat() {
            double hHat = Double.MAX_VALUE;
            double sseDMean = this._calculateSSEDMean();
            if (sseDMean < 1) {
                double sseHMean = this._calculateSSEHMean();
                hHat = this.h + ( (this.d / (1 - sseDMean)) * sseHMean );
            }
            return hHat;
        }

        /**
         * @return The calculated dHat value
         *
         * NOTE: if our estimate of sseDMean is ever as large as one, we assume we have infinite distance-to-go
         */
        private double _computeDHat() {
            double dHat = Double.MAX_VALUE;
            double sseDMean = this._calculateSSEDMean();
            if (sseDMean < 1) {
                dHat = this.d / (1 - sseDMean);
            }
            return dHat;
        }

        /**
         * The function computes the values of dHat and hHat of this node, based on that values of the parent node
         * and the cost of the operator that generated this node
         *
         * @param parent The parent node
         * @param edgeCost The cost of the operation which generated this node
         */
        private void _computePathHats(Node parent, double edgeCost) {
            if (parent != null) {
                // Calculate the single step error caused when calculating h and d
                this.sseH = parent.sseH + ((edgeCost + this.h) - parent.h);
                this.sseD = parent.sseD + ((1 + this.d) - parent.d);
            }

            this.hHat = this._computeHHat();
            this.dHat = this._computeDHat();
            this.fHat = this.g + this.hHat;

            // This must be true assuming the heuristic is admissible (fHat may only overestimate the cost to the goal)
            //assert this.fHat >= this.f;
            assert this.dHat >= 0;
        }

        /**
         * The constructor of the class
         *
         * @param state The state which is represented by this node
         * @param parent The parent node
         * @param op The operator which generated this node
         * @param pop The reverse operator (which will cause to generation of the parent node)
         */
        private Node(State state, Node parent, State parentState, Operator op, final Operator pop) {
            // The size of the key is 2
            super(2);
            this.packed = domain.pack(state);
            this.op = op;
            this.pop = pop;
            this.parent = parent;

            // Calculate the cost of the node:
            double cost = (op != null) ? op.getCost(state, parentState) : 0;
            this.g = cost;
            this.depth = 1;
            // Our g equals to the cost + g value of the parent
            if (parent != null) {
                this.g += parent.g;
                this.depth += parent.depth;
            }

            this.h = state.getH();

            // Start of PathMax
            //if (parent != null) {
            //    double costsDiff = this.g - parent.g;
            //    this.h = Math.max(this.h, (parent.h - costsDiff));
            //}
            // End of PathMax

            this.d = state.getD();
            this.f = this.g + this.h;

            // Default values
            this.sseH = 0;
            this.sseD = 0;

            // Compute the actual values of sseH and sseD
            this._computePathHats(parent, cost);
        }

        private void updateFromDuplicate(Node duplicateNode,
                                         State thisUnpackedState,
                                         State parentUnpackedState) {
            this.g = duplicateNode.g;
            this.f = duplicateNode.f;
            this.op = duplicateNode.op;
            this.pop = duplicateNode.pop;
            this.parent = duplicateNode.parent;
            // Calculate the cost of the node:
            double cost = (op != null) ? op.getCost(thisUnpackedState, parentUnpackedState) : 0;
            this._computePathHats(this.parent, cost);
        }

        @Override
        public double getF() {
            return this.f;
        }
    }
}
