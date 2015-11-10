package org.cs4j.core.old;

import com.carrotsearch.hppc.LongObjectOpenHashMap;
import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchDomain.Operator;
import org.cs4j.core.SearchDomain.State;
import org.cs4j.core.SearchResult;
import org.cs4j.core.algorithms.SearchQueueElementImpl;
import org.cs4j.core.collections.*;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.Comparator;
import java.util.Map;

public class EESKBFSV2 implements SearchAlgorithm {

    private static final int CLEANUP_ID = 0;
    private static final int FOCAL_ID = 1;

    private LongObjectOpenHashMap<Node> closed =
            new LongObjectOpenHashMap<Node>();

    private SearchDomain domain;
    private double weight;
    private int k = 1;

    // cleanup is implemented as a binary heap
    private BinHeap<Node> cleanup =
            new BinHeap<Node>(new CleanupNodeComparator(), CLEANUP_ID);

    // open is implemented as a RedBlack tree
    private OpenNodeComparator openComparator = new OpenNodeComparator();
    private GEQueue<Node> gequeue = new GEQueue<Node>(openComparator,
            new GENodeComparator(), new FocalNodeComparator(), FOCAL_ID);

    /**
     * The constructor.
     *
     *
     */
    public EESKBFSV2(double weight, int k) {
        this.weight = weight;
        this.k = k;
    }

    @Override
    public Map<String, Class> getPossibleParameters() {
        return null;
    }

    @Override
    public void setAdditionalParameter(String parameterName, String value) {
        throw new NotImplementedException();
    }

    /* (non-Javadoc)
     * @see edu.unh.ai.search.SearchAlgorithm#search(java.lang.Object)
     */
    @Override
    public SearchResult search(SearchDomain domain) {
        /*
        this.domain = domain;

        Node goal = null;
        SearchResultImpl result = new SearchResultImpl();
        result.startTimer();

        State initState = domain.initialState();
        Node initNode = new Node(initState, null, null, null);
        insertNode(initNode, initNode);
        gequeue.updateFocal(null, initNode, 0);

        while (!gequeue.isEmpty()) {
            if (goal != null){
                break;
            }
            List<Node> kQueue = new ArrayList<Node>();
            Node oldBest = gequeue.peekOpen();
            for (int i = 0; i < this.k; i++) {
                if (gequeue.isEmpty()){
                    break;
                }
                Node n = selectNode();
                if (n != null) {
                    State state = domain.unpack(n.packed);
                    int numOps = domain.getNumOperators(state);
                    if (numOps>0) {
                        kQueue.add(n);
                    }else{
                        i--;
                    }
                }
            }

            if (kQueue.isEmpty()) {
                break;
            }
            for (Node n : kQueue) {
                State state = domain.unpack(n.packed);
                if (domain.isGoal(state)) {
                    goal = n;
                    break;
                }

                result.expanded++;
                int numOps = domain.getNumOperators(state);
                for (int i = 0; i < numOps; i++) {
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
                        if (dup.f > node.f) {
                            if (dup.getIndex(CLEANUP_ID) != -1) {
                                gequeue.remove(dup);
                                cleanup.remove(dup);
                                closed.remove(dup.packed);
                            }
                            insertNode(node, oldBest);
                        }
                    } else {
                        insertNode(node, oldBest);
                    }
                }

            }
            Node newBest = gequeue.peekOpen();
            int fHatChange = openComparator.compareIgnoreTies(newBest, oldBest);
            gequeue.updateFocal(oldBest, newBest, fHatChange);

        }
        result.stopTimer();

        if (goal != null) {
            SolutionImpl solution = new SolutionImpl();
            List<Operator> path = new ArrayList<Operator>();
            for (Node p = goal; p != null; p = p.parent) {
                path.add(p.op);
            }
            Collections.reverse(path);
            solution.addOperators(path);
            solution.setCost(goal.g);
            result.addSolution(solution);
        }

        return result;
        */
        return null;
    }

    private void insertNode(Node node, Node oldBest) {
        /*
        gequeue.add(node, oldBest);
        cleanup.add(node);
        closed.put(node.packed, node);
        */
    }

    private Node selectNode() {
        Node value = null;
        Node bestDHat = gequeue.peekFocal();
        Node bestFHat = gequeue.peekOpen();
        Node bestF = cleanup.peek();
        if (bestDHat != null && bestF != null && bestFHat != null) {
            // best dhat
            if (bestDHat.fHat <= weight * bestF.f) {
                value = gequeue.pollFocal();
                cleanup.remove(value);
            }
            // best fhat
            else if (bestFHat.fHat <= weight * bestF.f) {
                value = gequeue.pollOpen();
                cleanup.remove(value);
            }
            // best f
            else {
                value = cleanup.poll();
                gequeue.remove(value);
            }
        }
        return value;
    }

    /*
     * The EES node is more complicated than other nodes.  It
     * is currently responsible for computing single step error
     * corrections and dhat and hhat values.  Right now we only
     * have path based single step error(SSE) correction implemented.
     *
     * TODO implement other methods for SSE correction and design
     * the necessary abstractions to move this out of the node class.
     */
    private class Node extends SearchQueueElementImpl
            implements RBTreeElement<Node, Node>, Comparable<Node> {

        double f, g, d, h, sseH, sseD, fHat, hHat, dHat;
        int depth;
        Operator op, pop;
        Node parent;
        PackedElement packed;
        RBTreeNode<Node, Node> rbnode = null;

        private Node(State state, Node parent, State parentState, Operator op, final Operator pop) {
            super(2);
            this.packed = domain.pack(state);
            this.parent = parent;
            this.op = op;
            this.pop = pop;

            double cost = (op != null) ? op.getCost(state, parentState) : 0;
            this.g = cost;
            if (parent != null) {
                this.g += parent.g;
                this.depth = parent.depth + 1;
            }
            this.h = state.getH();
            this.d = state.getD();
            this.f = g + h;

            computePathHats(parent, cost);
        }

        private void computePathHats(Node parent, double edgeCost) {
            if (parent != null) {
                this.sseH = parent.sseH + ((edgeCost + h) - parent.h);
                this.sseD = parent.sseD + ((1 + d) - parent.d);
            }
            this.hHat = computeHHat();
            this.dHat = computeDHat();
            this.fHat = g + hHat;

            assert fHat >= f;
            assert dHat >= 0;
        }

        private double computeHHat() {
            double hHat = Double.MAX_VALUE;
            double sseMean = (g == 0) ? sseH : sseH / depth;
            double dMean = (g == 0) ? sseD : sseD / depth;
            if (dMean < 1) {
                hHat = h + ((d / (1 - dMean)) * sseMean);
            }
            return hHat;
        }

        private double computeDHat() {
            double dHat = Double.MAX_VALUE;
            double dMean = (g == 0) ? sseD : sseD / depth;
            if (dMean < 1) {
                dHat = d / (1 - dMean);
            }
            return dHat;
        }

        @Override
        public int compareTo(Node o) {
            int diff = (int) (this.f - o.f);
            if (diff == 0) return (int) (o.g - this.g);
            return diff;
        }

        @Override
        public RBTreeNode<Node, Node> getNode() {
            return rbnode;
        }

        @Override
        public void setNode(RBTreeNode<Node, Node> node) {
            this.rbnode = node;
        }
    }

    /*
     * Used to sort the cleanup list on f.
     */
    private final class CleanupNodeComparator implements Comparator<Node> {
        @Override
        public int compare(final Node a, final Node b) {
            if (a.f < b.f) return -1;
            else if (a.f > b.f) return 1;
            else if (b.g < a.g) return -1;
            else if (b.g > a.g) return 1;
            return 0;
        }
    }

    /*
     * Used to sort the focal list on dhat.
     */
    private final class FocalNodeComparator implements Comparator<Node> {
        @Override
        public int compare(final Node a, final Node b) {
            if (a.dHat < b.dHat) return -1;
            else if (a.dHat > b.dHat) return 1;
                // break ties on low fHat
            else if (a.fHat < b.fHat) return -1;
            else if (a.fHat > b.fHat) return 1;
                // break ties on high g
            else if (a.g > b.g) return -1;
            else if (a.g < b.g) return 1;
            return 0;
        }
    }

    /*
     * Used to sort the open list on fhat.
     */
    private final class OpenNodeComparator implements Comparator<Node> {
        @Override
        public int compare(final Node a, final Node b) {
            if (a.fHat < b.fHat) return -1;
            else if (a.fHat > b.fHat) return 1;
                // break ties on low d
            else if (a.d < b.d) return -1;
            else if (a.d > b.d) return 1;
                // break ties on high g
            else if (a.g > b.g) return -1;
            else if (a.g < b.g) return 1;
            return 0;
        }

        public int compareIgnoreTies(final Node a, final Node b) {
            if (a.fHat < b.fHat) return -1;
            else if (a.fHat > b.fHat) return 1;
            return 0;
        }
    }

    // sort on a.f and b.f'
    private final class GENodeComparator implements Comparator<Node> {
        @Override
        public int compare(final Node a, final Node b) {
            if (a.fHat < weight * b.fHat) return -1;
            else if (a.fHat > weight * b.fHat) return 1;
            return 0;
        }
    }

}
