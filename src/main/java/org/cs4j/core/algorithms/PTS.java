package org.cs4j.core.algorithms;

import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchResult;
import org.cs4j.core.collections.BinHeap;
import org.cs4j.core.collections.BucketHeap;
import org.cs4j.core.collections.SearchQueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Potential Search
 *
 * @author Vitali Sepetnitsky
 */
public class PTS implements SearchAlgorithm {
    // The maximum cost of the search C
    protected double maxCost;

    // The domain to which the search problem belongs
    private SearchDomain domain;

    // OPEN and CLOSED lists
    private SearchQueue<Node> open;
    private Map<long[], Node> closed = new HashMap<>();
    // Whether reopening is allowed
    private boolean reopen;

    // The result path
    private List<SearchDomain.Operator> path;
    
    public PTS(double maxCost, boolean reopen) {
        this.maxCost = maxCost;
        this.open = new BinHeap<>(new NodeComparator(), 0);
        this.reopen = reopen;
        this.path = null;
    }

    public SearchResult search(SearchDomain domain) {
        this.domain = domain;
        double goalCost = Double.MAX_VALUE;
        // The result will be stored here
        SearchResultImpl result = new SearchResultImpl();
        result.startTimer();
        // Extract the initial state from the domain
        SearchDomain.State state = domain.initialState();
        // Initialize a search node using the state (contains data according to the current
        // algorithm)
        Node initNode = new Node(state);

        // Start the search: Add the node to the OPEN and CLOSED lists
        open.add(initNode);
        // n in OPEN ==> n in CLOSED -Thus- ~(n in CLOSED) ==> ~(n in OPEN)
        closed.put(initNode.packed, initNode);
        // Loop while there is no solution and there are states in the OPEN list
        while ((this.path == null) && !open.isEmpty()) {
            // Take a node from the OPEN list (nodes are sorted according to the 'u' function)
            Node n = open.poll();
            // Extract a state from the node
            state = domain.unpack(n.packed);
            // expand the node
            result.expanded++;
            // Go over all the successors of the state
            for (int i = 0; i < domain.getNumOperators(state); i++) {
                SearchDomain.Operator op = domain.getOperator(state, i);
                // Don't apply the previous operator on the state - in order not to enter a loop
                if (op.equals(n.pop)) {
                    continue;
                }
                // Otherwise, we found a new state
                result.generated++;
                // Get it by applying the operator on the parent state
                SearchDomain.State childState = domain.applyOperator(state, op);
                // Create a search node for this state
                Node node = new Node(childState, n, op, op.reverse(state));

                // Now, merge duplicates - let's check if the state already exists in CLOSE/OPEN:
                // In the node is not in the CLOSED list, then it is also not in the OPEN list
                // In any case it can't be that node is a goal - otherwise, we should return it
                // when we see it at first
                if (closed.containsKey(node.packed)) {
                    result.duplicates++;
                    // Take the duplicate node
                    Node dup = closed.get(node.packed);
                    // Consider only duplicates with higher G value
                    if (dup.g > node.g) {
                        // Make the duplicate to be successor of the current parent node
                        dup.f = node.f;
                        dup.g = node.g;
                        dup.op = node.op;
                        dup.pop = node.pop;
                        dup.parent = node.parent;
                        // In case the node is in the OPEN list - update its key using the new G
                        if (dup.getIndex(open.getKey()) != -1) {
                            open.update(dup);
                        } else {
                            //System.out.println("reopen");
                            // Return to OPEN list only if reopening is allowed
                            if (this.reopen) {
                                result.reopened++;
                                open.add(dup);
                            }
                        }
                    }
                // Consider the new node only if its cost is lower than the maximum cost
                } else if (node.g + node.h < maxCost) {
                    // A new node - let's check if it is goal and return it
                    if (domain.isGoal(childState)) {
                        goalCost = node.g;
                        this.path = new ArrayList<>(3);
                        // Create the path from the goal
                        for (Node p = node; p != null; p = p.parent) {
                            path.add(p.op);
                        }
                        Collections.reverse(path);
                        System.out.println("Found Solution :-)");
                        break;
                    }
                    // Otherwise, add the node to the search lists
                    open.add(node);
                    closed.put(node.packed, node);
                }
            }
        }
        // Stop the timer and check that a goal was found
        result.stopTimer();

        // Whether we found a goal?
        if (this.path != null && this.path.size() > 0) {
            SearchResultImpl.SolutionImpl solution = new SearchResultImpl.SolutionImpl();
            solution.addOperators(this.path);
            solution.setCost(goalCost);
            // A sanity check
            assert (solution.getCost() < maxCost);
            result.addSolution(solution);
            this.path.clear();
        }

        return result;
    }

    /**
     * The Node is the basic data structure which is used by the algorithm during the search -
     * OPEN and CLOSED lists contain nodes which are created from the domain states
     */
    private final class Node extends
                               SearchQueueElementImpl implements
                                                      BucketHeap.BucketHeapElement {
        private double f, g, h;
        private SearchDomain.Operator op, pop;
        private Node parent;
        private long[] packed;
        private int[] secondaryIndex;

        /**
         * Default constructor which creates the node from some given state
         *
         * @param state The state from which the node should be created
         */
        private Node(SearchDomain.State state) {
            this(state, null, null, null);
        }

        /**
         * An extended constructor which receives the initial state, but also the parent of the node
         * and operators (last and previous)
         *
         * @param state The state from which the node should be created
         * @param parent The parent state
         * @param op The operator which was applied to the parent state in order to get the current
         *           one
         * @param pop The operator which will reverse the last applied operation which revealed the
         *            current state
         */
        private Node(SearchDomain.State state, Node parent, SearchDomain.Operator op, SearchDomain.Operator pop) {
            // The size of the key (for SearchQueueElementImpl) is 1
            super(1);
            this.secondaryIndex = new int[1];
            // WHY THE COST IS OF APPLYING THE OPERATOR ON THAT NODE????
            // SHOULDN'T IT BE ON THE PARENT???
            // OR EVEN MAYBE WE WANT EITHER PARENT **AND** THE CHILD STATES TO PASS TO THE getCost
            // FUNCTION IN ORDER TO GET THE OPERATOR VALUE ...
            double cost = (op != null) ? op.getCost(state) : 0;
            this.h = state.getH();
            this.g = (parent != null)? parent.g + cost : cost;
            this.f = this.g + this.h;
            this.parent = parent;
            this.packed = domain.pack(state);
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

    /**
     * The node comparator class
     */
    private final class NodeComparator implements Comparator<Node> {
        @Override
        public int compare(final Node a, final Node b) {
            double aCost = (maxCost - a.g) / a.h;
            double bCost = (maxCost - b.g) / b.h;

            if (aCost > bCost) return -1;
            if (aCost < bCost) return 1;

            // Here we have a tie
            //System.out.println("A tie :-(");
            return 0;
        }
    }
}
