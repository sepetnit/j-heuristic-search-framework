package org.cs4j.core.algorithms;

import com.sun.istack.internal.NotNull;
import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchDomain.Operator;
import org.cs4j.core.SearchDomain.State;
import org.cs4j.core.SearchResult;
import org.cs4j.core.algorithms.SearchResultImpl.SolutionImpl;
import org.cs4j.core.collections.*;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EES implements SearchAlgorithm {
    private static final int CLEANUP_ID = 0;
    private static final int FOCAL_ID = 1;
    // TODO: Later, it should be removed ...
    private static final int CLEANUP_ICL_ID = 2;


    private static final Map<String, Class> EESPossibleParameters;

    // Declare the parameters that can be tuned before running the search
    static
    {
        EESPossibleParameters = new HashMap<>();
        EES.EESPossibleParameters.put("weight", Double.class);
        EES.EESPossibleParameters.put("reopen", Boolean.class);
    }

    protected SearchDomain domain;

    protected double weight;
    protected boolean reopen;

    protected OpenNodeComparator openComparator = new OpenNodeComparator();

    protected GEQueue<Node> gequeue;
    // cleanup is implemented as a binary heap and actually contains nodes ordered by their f values
    protected BinHeap<Node> cleanup;

    // cleanupForICL is implemented as a binary heap and actually contains nodes ordered by their f values
    // its purpose it to contain all the nodes from OPEN+ICL, sorted by their f (not Wf) values
    // this is in order to estimate the w-suboptimality of the found goal
    // TODO: This implementation is very inefficient, we will update it more efficiently later ...
    protected BinHeap<Node> cleanupForICL;

    // Inconsistent list
    protected Map<PackedElement, Node> incons;
    // Closed list
    // private LongObjectOpenHashMap<Node> closed;
    protected Map<PackedElement, Node> closed;

    /**
     * Initializes all the data structures required for the search, especially OPEN, FOCAL, CLEANUP and CLOSED lists
     */
    /*private void _initDataStructures() {
        this.gequeue =
                new GEQueue<>(
                        this.openComparator,
                        new GENodeComparator(),
                        new FocalNodeComparator(),
                        EES.FOCAL_ID);
        this.cleanup =
                new BinHeap<>(
                        new CleanupNodeComparator(),
                        EES.CLEANUP_ID);

        //this.closed = new LongObjectOpenHashMap<>();
        this.closed = new HashMap<>();
    }*/


    /**
     * The constructor of the class
     *
     */
    public EES() {
        // Default values
        this.weight = 1.0;
        this.reopen = true;
    }

    @Override
    public String getName() {
        return "ees";
    }

    /**
     * This functions allows us to take the node with the best F value from the OPEN list
     *
     * @return Also, note that this function can be overridden by the subclasses
     */
    protected Node getNodeWithBestF() {
        return this.cleanup.peek();
    }

    private Node _selectNode() {
        Node toReturn;

        Node bestDHat = this.gequeue.peekFocal();

        Node bestFHat = this.gequeue.peekOpen();
        Node bestF = this.getNodeWithBestF();

        if (bestDHat == null) {
            //System.out.println("Empty Focal!");
        }

        // best dHat (d^);
        if (bestDHat != null && bestDHat.fHat <= this.weight * bestF.f) {
            toReturn = this.gequeue.pollFocal();
            // Also, remove from cleanup
            this.cleanup.remove(toReturn);
            this.cleanupForICL.remove(toReturn);
            // best fHat (f^)
        } else if (bestFHat.fHat <= this.weight * bestF.f) {
            toReturn = this.gequeue.pollOpen();
            // Also, remove from cleanup
            this.cleanup.remove(toReturn);
            this.cleanupForICL.remove(toReturn);
            // Otherwise, take the best f from cleanup
        } else {
            toReturn = this.cleanup.poll();
            this.gequeue.remove(toReturn);
            this.cleanupForICL.remove(toReturn);
        }
        return toReturn;
    }

    /**
     * The function inserts a new generated node into the lists
     *
     * @param node The node to insert
     * @param oldBest The previous node that was considered as the best
     *                (required in order to update OPEN and FOCAL lists)
     */
    protected void _insertNode(Node node, Node oldBest) {
        this.gequeue.add(node, oldBest);
        this.cleanup.add(node);
        this.cleanupForICL.add(node);
        this.closed.put(node.packed, node);
    }

    /**
     * Given that a child node was generated, which is already contained in the CLOSED or OPEN list, we may want to
     * reinsert it to the lists. In that case we want to update the parent pointers/
     *
     * @param dupChildNode The duplicate node, found in CLOSED
     * @param newParentNode The parent node from which {@see dupChildNode} was generated
     * @param newChildNode The newly generated node
     */
    private void _updateParentAndChildPointers(Node dupChildNode, Node newParentNode, Node newChildNode) {

        //           (parent)
        // parent <-          duplicate

        // Go to the parent of the duplicate node and remove the duplicated node from the parent's children
        if (dupChildNode.parent != null) {
            dupChildNode.parent.children.remove(dupChildNode.packed);
            dupChildNode.parent = null;
        }

        //              (children)
        // duplicate ->            children

        // Go to the children of the duplicate and update their parent to the new child
        // Take all the children of the found duplicate and update their parent node
        for (Node grandSon : dupChildNode.children.values()) {
            grandSon.parent = newChildNode;
            newChildNode.children.put(grandSon.packed, grandSon);
        }

        // Finally, we can destroy the duplicate node
        dupChildNode.children.clear();

        // Add the child node to be a child of its parent
        newParentNode.children.put(newChildNode.packed, newChildNode);

    }

    @Override
    public Map<String, Class> getPossibleParameters() {
        return EES.EESPossibleParameters;
    }

    @Override
    public void setAdditionalParameter(String parameterName, String value) {
        switch (parameterName) {
            case "weight": {
                this.weight = Double.parseDouble(value);
                if (this.weight < 1.0d) {
                    System.out.println("[ERROR] The weight must be >= 1.0");
                    throw new IllegalArgumentException();
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

    protected void _initDataStructures(boolean clearOpen, boolean clearIncons, boolean clearClosed) {
        if (clearOpen || this.gequeue == null) {
            this.gequeue =
                    new GEQueue<>(
                            this.openComparator,
                            new GENodeComparator(),
                            new FocalNodeComparator(),
                            EES.FOCAL_ID);
            this.cleanup =
                    new BinHeap<>(
                            new CleanupNodeComparator(),
                            EES.CLEANUP_ID);
            this.cleanupForICL =
                    new BinHeap<>(
                            new CleanupNodeComparator(),
                            EES.CLEANUP_ICL_ID);
        }

        if (clearClosed || this.closed == null) {
            //this.closed = new LongObjectOpenHashMap<>();
            this.closed = new HashMap<>();
        }
    }

    /**
     * @return true if the algorithm should run and false otherwise
     */
    protected boolean shouldRun() {
        return !this.gequeue.isEmpty();
    }

    protected SearchResult _search(boolean clearOpenList, boolean clearClosedList,
                                   double maxPreviousCost) {
        Node goal = null;
        // Initialize the result
        SearchResultImpl result = new SearchResultImpl();

        // Should be empty at the start of iteration!
        assert this.incons == null || this.incons.isEmpty();

        // Init all the queues relevant to search (destroy previous results)
        this._initDataStructures(clearOpenList, true, clearClosedList);

        result.startTimer();

        // Loop while there is some node in open
        while (this.shouldRun()) {
            // First, take the best node from the open list (best f^)
            Node oldBest = this.gequeue.peekOpen();

            // Debug
            if (result.getPassedTimeInSeconds() > 180) {
                System.out.println("[WARNING] Time overflow (" + result.getPassedTimeInSeconds() + ")");
                break;
            }

            // Now this node is in closed only, and not in open
            Node bestNode = this._selectNode();

            // Debug! TODO: Remove it
            if (this.gequeue.isEmpty()) {
                System.out.println("[WARNING] GEQueue became empty");
            }

            // TODO: Can it happen?
            if (bestNode == null) {
                System.out.println("[WARNING] No Best Node from Open");
                break;
            }

            // Prune
            if (bestNode.getF() >= maxPreviousCost) {
                continue;
            }

            // Extract the state from the chosen node
            State state = this.domain.unpack(bestNode.packed);
            // Check if it is a goal
            if (this.domain.isGoal(state)) {
                goal = bestNode;
                break;
            }

            // Here, we decided to expand the node
            ++result.expanded;
            int numOps = domain.getNumOperators(state);

            // Go over all the possible operators
            for (int i = 0; i < numOps; ++i) {
                Operator op = domain.getOperator(state, i);
                // Bypass reverse operations
                if (op.equals(bestNode.pop)) {
                    continue;
                }
                ++result.generated;
                // Apply the operator and extract the child state
                State childState = domain.applyOperator(state, op);
                // Create the child node
                Node childNode = new Node(childState, bestNode, state, op, op.reverse(state));

                // Prune
                if (childNode.getF() >= maxPreviousCost) {
                    continue;
                }

                // merge duplicates

                // ==> This means it is in CLOSED (and maybe in OPEN too!) - a duplicate was found!
                if (this.closed.containsKey(childNode.packed)) {
                    ++result.duplicates;
                    // Extract the duplicate
                    Node dupChildNode = this.closed.get(childNode.packed);
                    // In case the node should be re-considered
                    if (dupChildNode.g > childNode.g) {

                        // ==> Means it is in OPEN ==> remove it and reinsert with updated values
                        if (dupChildNode.getIndex(EES.CLEANUP_ID) != -1) {
                            ++result.opupdated;
                            this.gequeue.remove(dupChildNode);
                            this.cleanup.remove(dupChildNode);
                            this.cleanupForICL.remove(dupChildNode);
                            this.closed.remove(dupChildNode.packed);

                            // Update all the pointers
                            this._updateParentAndChildPointers(dupChildNode, bestNode, childNode);

                            this._insertNode(childNode, oldBest);

                            // The node is in the CLOSED list only
                        } else {
                            // If re-opening is allowed: insert the node back to lists (OPEN, FOCAL and CLEANUP)
                            if (this.reopen) {
                                ++result.reopened;

                                // Update all the pointers
                                this._updateParentAndChildPointers(dupChildNode, bestNode, childNode);

                                this._insertNode(childNode, oldBest);

                                // Otherwise, just update the value of the node in CLOSED (without re-inserting it)
                            } else {

                                if (this.incons == null) {
                                    dupChildNode.f = childNode.f;
                                    dupChildNode.g = childNode.g;
                                    dupChildNode.op = childNode.op;
                                    dupChildNode.pop = childNode.pop;
                                    dupChildNode._computePathHats(
                                            childNode.parent,
                                            op.getCost(childState, state)
                                    );

                                    // Go to the parent of the duplicate node and remove this node from its children
                                    if (dupChildNode.parent != null) {
                                        dupChildNode.parent.children.remove(dupChildNode.packed);
                                    }

                                    // Update the parent of the duplicate node
                                    dupChildNode.parent = childNode.parent;
                                    dupChildNode.parent.children.put(dupChildNode.packed, dupChildNode);
                                } else {

                                    // Update all the pointers
                                    this._updateParentAndChildPointers(dupChildNode, bestNode, childNode);
                                    if (!this.incons.containsKey(childNode.packed)) {
                                        // Also, add the node into cleanupForICL (contains all the nodes for OPEN+ICL)
                                        this.cleanupForICL.add(childNode);
                                    } else {
                                        Node previousChildNode = this.incons.get(childNode.packed);
                                        this.cleanupForICL.remove(previousChildNode);
                                        this.cleanupForICL.add(childNode);
                                    }
                                    // Insert into incons (this doesn't mean it will be used!)
                                    this.incons.put(childNode.packed, childNode);
                                }

                            }
                        }
                    }
                // New node - not in CLOSED
                } else {
                    this._insertNode(childNode, oldBest);
                    bestNode.children.put(childNode.packed, childNode);
                }
            }

            // Note that gequeue may become empty at this stage!
            if (!this.gequeue.isEmpty()) {
                // After the old-best node was expanded, let's update the best node in OPEN and FOCAL
                Node newBest = this.gequeue.peekOpen();
                int fHatChange = this.openComparator.compareIgnoreTies(newBest, oldBest);
                this.gequeue.updateFocal(oldBest, newBest, fHatChange);
            }

            // Debug:
            //if (this.cleanupForICL.size() != this.cleanup.size() + this.incons.size()) {
            //    System.out.println(this.cleanupForICL.size() + " != " + this.cleanup.size() + "+" + this.incons.size() + " (= " +
            //            (this.cleanup.size() + this.incons.size()) +")");
            //}
        }

        result.stopTimer();

        // If a goal was found: update the solution
        if (goal != null) {
            SolutionImpl solution = new SolutionImpl(this.domain);
            List<Operator> path = new ArrayList<>();
            List<State> statesPath = new ArrayList<>();
            System.out.println("[INFO] Solved - Generating output path.");
            long cost = 0;

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
            //assert statesPath.size() <= goal.g + 1;
            if (statesPath.size() - goal.g < 1) {
                System.out.println("[INFO] Goal G is higher that the actual path " +
                        "(G: " + goal.g +  ", Actual: " + statesPath.size() + ")");
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
     * Creates the initial state and node and adds them into the relevant lists
     */
    public void prepareForSearch() {
        // Create the initial state and node
        State initState = this.domain.initialState();
        Node initNode = new Node(initState, null, null, null, null);
        // Insert the initial node into all the lists
        this._insertNode(initNode, initNode);
        // Update FOCAL with the inserted node (no change in f^) - required since oldBest is null in this case
        this.gequeue.updateFocal(null, initNode, 0);
    }

    @Override
    public SearchResult search(SearchDomain domain) {
        // Init all the queues relevant to search (destroy previous results)
        this._initDataStructures(true, true, true);
        this.domain = domain;

        // Perform the relevant preparation ...
        this.prepareForSearch();

        // Run the algorithm ...
        return this._search(false, false, Integer.MAX_VALUE);
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
     * This comparator is used in order to sort the FOCAL list on dHat (d^)
     */
    private final class FocalNodeComparator implements Comparator<Node> {
        @Override
        public int compare(final Node a, final Node b) {
            // Lower dHat is better (less steps estimated for reaching the goal)
            if (a.dHat < b.dHat) {
                return -1;
            } else if (a.dHat > b.dHat) {
                return 1;
                // Break ties on low fHat
            }  else if (a.fHat < b.fHat) {
                return -1;
            } else if (a.fHat > b.fHat) {
                return 1;
                // Finally, break ties by looking on G (higher G value is better)
            } else if (a.g > b.g) {
                return -1;
            } else if (a.g < b.g) {
                return 1;
            }
            return 0;
        }
    }

    /**
     * This comparator is used to sort the open list on fHat (f^)
     */
    protected final class OpenNodeComparator implements Comparator<Node> {
        @Override
        public int compare(final Node a, final Node b) {
            // Lower fHat is better (less cost estimated to reach the goal)
            if (a.fHat < b.fHat) {
                return -1;
            } else if (a.fHat > b.fHat) {
                return 1;
                // break ties on low D
            } else if (a.d < b.d) {
                return -1;
            } else if (a.d > b.d) {
                return 1;
                // break ties on high G
            } else if (a.g > b.g) {
                return -1;
            } else if (a.g < b.g) {
                return 1;
            }
            // If we are here - consider as equal
            return 0;
        }

        public int compareIgnoreTies(final Node a, final Node b) {
            if (a.fHat < b.fHat) {
                return -1;
            } else if (a.fHat > b.fHat) {
                return 1;
            }
            return 0;
        }
    }

    // sort on f^ value
    private final class GENodeComparator implements Comparator<Node> {
        @Override
        public int compare(final Node a, final Node b) {
            if (a.fHat < EES.this.weight * b.fHat) {
                return -1;
            } else if (a.fHat > EES.this.weight * b.fHat) {
                return 1;
            }
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
    protected class Node extends SearchQueueElementImpl implements RBTreeElement<Node, Node>, Comparable<Node> {
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

        // The immediate children of the node
        private Map<PackedElement, Node> children;

        // Public in order to be used by subclassed (specifically EESwithNRR
        public PackedElement packed;
        private RBTreeNode<Node, Node> rbnode = null;

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
            double hHat = this.h;
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
            double dHat = this.d;
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
        public void _computePathHats(Node parent, double edgeCost) {
            if (parent != null) {
                // Calculate the single step error caused when calculating h and d
                this.sseH = parent.sseH + ((edgeCost + this.h) - parent.h);
                this.sseD = parent.sseD + ((1 + this.d) - parent.d);
            }

            this.hHat = this._computeHHat();
            this.dHat = this._computeDHat();
            this.fHat = this.g + this.hHat;

            // This must be true assuming the heuristic is consistent (fHat may only overestimate the cost to the goal)
            if (EES.this.domain.isCurrentHeuristicConsistent()) {
                assert this.fHat >= this.f;
            }
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
            // The size of the key is 3 (for the CLEANUP+ICL list)
            super(3);
            this.packed = domain.pack(state);
            this.op = op;
            this.pop = pop;
            this.parent = parent;
            this.children = new HashMap<>();

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
        public int compareTo(@NotNull Node other) {
            // Nodes are compared by default by their f value (and if f values are equal - by g value)

            // F value: lower f is better
            int diff = (int) (this.f - other.f);
            if (diff == 0) {
                // G value: higher g is better
                return (int) (other.g - this.g);
            }
            return diff;
        }

        @Override
        public RBTreeNode<Node, Node> getNode() {
            return this.rbnode;

        }

        @Override
        public int hashCode() {
            return (int)this.packed.getLongsSum();
        }

        @Override
        public void setNode(RBTreeNode<Node, Node> node) {
            this.rbnode = node;
        }

        @Override
        public double getF() {
            return this.f;
        }
    }
}
