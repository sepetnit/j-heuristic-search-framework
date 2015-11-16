package org.cs4j.core.generators;

import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchDomain.State;
import org.cs4j.core.algorithms.SearchQueueElementImpl;
import org.cs4j.core.collections.PackedElement;
import org.cs4j.core.domains.FifteenPuzzle;

import java.io.DataOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;

/**
 * Created by user on 16/11/2015.
 *
 */
public class FifteenPuzzlePDBGenerator {
    // Used for packing/unpacking
    private FifteenPuzzle fifteenPuzzleDomain;

    public FifteenPuzzlePDBGenerator() {
        this.fifteenPuzzleDomain = new FifteenPuzzle();
    }

    /**
     * Takes a vector PERM of N+1 elements, representing a permutation of N elements whose largest possible value is
     * the size of the puzzle (16), including the blank, and maps it to an integer that represents it uniquely.
     * The index for the heuristic table, which ignores the blank position, is computed by dividing this value by
     * SIZE-N (N is the PDB size, e.g. 5).
     */
    private int hash(int[] permutation) {
        return this.fifteenPuzzleDomain._getHashNIndex(permutation);
    }

    
    public void createPDB7(DataOutputStream output) {
        Queue<Node> queue = new LinkedList<>();
        Set<PackedElement> visited = new HashSet<>();

        // Let's instantiate the initial state
        SearchDomain.State state = this.fifteenPuzzleDomain.initialStateNoHeuristic();
        // Create a graph node from this state
        Node initNode = new Node(state);
        queue.add(initNode);
        while (!queue.isEmpty()) {
            Node currentNode = queue.poll();
            // Extract the state from the packed value of the node
            state = this.fifteenPuzzleDomain.unpackNoHeuristic(currentNode.packed);
            // Go over all the operators
            for (int i = 0; i < this.fifteenPuzzleDomain.getNumOperators(state); ++i) {
                SearchDomain.Operator op = this.fifteenPuzzleDomain.getOperator(state, i);
                // Avoid loops
                if (op.equals(currentNode.pop)) {
                    continue;
                }
                State childState = this.fifteenPuzzleDomain.applyOperator(state, op);
                Node childNode = new Node(childState, currentNode, op, op.reverse(state));
                // Ignore if duplicate
                if (!visited.contains(childNode.packed)) {
                    // Add to visited
                    visited.add(childNode.packed);
                }
            }
        }
    }

    public static void mainCreatePDB78() {
        String pdb7OutputFile = "dis_8_9_10_11_12_13_14_15.pdb";

    }

    /**
     * The node class
     */
    protected final class Node extends SearchQueueElementImpl {
        private Node parent;
        private PackedElement packed;
        private SearchDomain.Operator op;
        private SearchDomain.Operator pop;

        private Node(State state, Node parent, SearchDomain.Operator op, SearchDomain.Operator pop) {
            // Size of key
            super(0);
            // Parent node
            this.parent = parent;
            this.packed = FifteenPuzzlePDBGenerator.this.fifteenPuzzleDomain.pack(state);
            this.pop = pop;
            this.op = op;
        }

        /**
         * A constructor of the class that instantiates only the state
         *
         * @param state The state which this node represents
         */
        private Node(SearchDomain.State state) {
            this(state, null, null, null);
        }
    }
}