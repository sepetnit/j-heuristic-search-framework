package org.cs4j.core.algorithms;

import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchDomain.Operator;
import org.cs4j.core.SearchDomain.State;
import org.cs4j.core.SearchResult;
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
 * Potential Search
 *
 * @author Vitali Sepetnitsky
 */
public class PTS implements SearchAlgorithm {

    // The domain to which the search problem belongs
    private SearchDomain domain;

    // OPEN and CLOSED lists
    private SearchQueue<Node> open;
    private Map<PackedElement, Node> closed;

    // The result path
    private List<Operator> path;
    private List<State> statesPath;

    // The maximum cost of the search C
    protected double maxCost;
    // Whether reopening is allowed
    private boolean reopen;

    private static final Map<String, Class> PTSPossibleParameters;

    // Declare the parameters that can be tunes before running the search
    static
    {
        PTSPossibleParameters = new HashMap<String, Class>();
        PTSPossibleParameters.put("MaxCost", Double.class);
        PTSPossibleParameters.put("Reopen", Boolean.class);
    }

    public PTS() {
        this.path = null;
        this.statesPath = null;
        // Initial values (afterwards they can be set independently)
        this.maxCost = Double.MAX_VALUE;
        this.reopen = true;
    }

    private void _initDataStructures() {
        this.open = new BinHeap<>(new PTS.NodeComparator(), 100);
        this.closed = new HashMap<>();
        this.path = new ArrayList<>();
        this.statesPath = new ArrayList<>();
    }

    @Override
    public Map<String, Class> getPossibleParameters() {
        return PTS.PTSPossibleParameters;
    }

    @Override
    public void setAdditionalParameter(String parameterName, String value) {
        if (parameterName.equals("reopen")) {
            this.reopen = Boolean.parseBoolean(value);
        } else if (parameterName.equals("maxCost")) {
            this.maxCost = Double.parseDouble(value);
        } else {
            System.err.println("No such parameter: " + parameterName + " (value: " + value + ")");
            throw new NotImplementedException();
        }
    }

    public SearchResult search(SearchDomain domain) {
        this.domain = domain;
        // The result will be stored here
        Node goal = null;
        double goalCost = Double.MAX_VALUE;
        // Initialize all the data structures required for the search
        this._initDataStructures();

        SearchResultImpl result = new SearchResultImpl();
        result.startTimer();

        // Extract the initial state from the domain
        State currentState = domain.initialState();
        // Initialize a search node using the state (contains data according to the current
        // algorithm)
        Node initialNode = new Node(currentState);

        // Start the search: Add the node to the OPEN and CLOSED lists
        this.open.add(initialNode);
        // n in OPEN ==> n in CLOSED -Thus- ~(n in CLOSED) ==> ~(n in OPEN)
        this.closed.put(initialNode.packed, initialNode);

        // Loop while there is no solution and there are states in the OPEN list
        while ((goal == null) && !this.open.isEmpty()) {
            // Take a node from the OPEN list (nodes are sorted according to the 'u' function)
            Node currentNode = open.poll();
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

                // If the generated node satisfies the goal condition - let' mark the goal and break
                if (childNode.f < this.maxCost && domain.isGoal(childState)) {
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
                    if (dupChildNode.f > dupChildNode.f) {
                        // Consider only duplicates with higher G value
                        if (dupChildNode.g > childNode.g) {

                            // Make the duplicate to be successor of the current parent node
                            dupChildNode.f = childNode.f;
                            dupChildNode.g = childNode.g;
                            dupChildNode.op = childNode.op;
                            dupChildNode.pop = childNode.pop;
                            dupChildNode.parent = childNode.parent;

                            // In case the node is in the OPEN list - update its key using the new G
                            if (dupChildNode.getIndex(open.getKey()) != -1) {
                                this.open.update(dupChildNode);
                                this.closed.put(dupChildNode.packed, dupChildNode);
                            } else {
                                // Return to OPEN list only if reopening is allowed
                                if (this.reopen) {
                                    ++result.reopened;
                                    open.add(dupChildNode);
                                }
                                this.closed.put(dupChildNode.packed, dupChildNode);
                            }
                        }
                    }
                // Consider the new node only if its cost is lower than the maximum cost
                } else {
                    // Otherwise, add the node to the search lists
                    this.open.add(childNode);
                    this.closed.put(childNode.packed, childNode);
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
     * The Node is the basic data structure which is used by the algorithm during the search -
     * OPEN and CLOSED lists contain nodes which are created from the domain states
     */
    private final class Node extends SearchQueueElementImpl implements BucketHeap.BucketHeapElement {
        private double f;
        private double g;
        private double h;

        private Operator op;
        private Operator pop;

        private Node parent;

        private PackedElement packed;

        private int[] secondaryIndex;

        /**
         * An extended constructor which receives the initial state, but also the parent of the node
         * and operators (last and previous)
         *
         * @param state The state from which the node should be created
         * @param parent The parent node
         * @param parentState The state of the parent
         * @param op The operator which was applied to the parent state in order to get the current
         *           one
         * @param pop The operator which will reverse the last applied operation which revealed the
         *            current state
         */
        private Node(State state, Node parent, State parentState, Operator op, Operator pop) {
            // The size of the key (for SearchQueueElementImpl) is 1
            super(1);
            this.secondaryIndex = new int[1];
            // WHY THE COST IS OF APPLYING THE OPERATOR ON THAT NODE????
            // SHOULDN'T IT BE ON THE PARENT???
            // OR EVEN MAYBE WE WANT EITHER PARENT **AND** THE CHILD STATES TO PASS TO THE getCost
            // FUNCTION IN ORDER TO GET THE OPERATOR VALUE ...
            double cost = (op != null) ? op.getCost(state, parentState) : 0;
            this.h = state.getH();
            this.g = (parent != null)? parent.g + cost : cost;
            this.f = this.g + this.h;
            this.parent = parent;
            this.packed = domain.pack(state);
            this.pop = pop;
            this.op = op;
        }

        /**
         * Default constructor which creates the node from some given state
         *
         * {see Node(State, Node, State, Operator, Operator)}
         *
         * @param state The state from which the node should be created
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
    }

    /**
     * The node comparator class
     */
    private final class NodeComparator implements Comparator<Node> {
        @Override
        public int compare(final Node a, final Node b) {
            double aCost = (PTS.this.maxCost - a.g) / a.h;
            double bCost = (PTS.this.maxCost - b.g) / b.h;

            if (aCost > bCost) {
                return -1;
            }

            if (aCost < bCost) {
                return 1;
            }

            // Here we have a tie
            return 0;
        }
    }
}
