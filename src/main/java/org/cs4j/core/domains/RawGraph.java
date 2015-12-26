package org.cs4j.core.domains;

import org.cs4j.core.SearchDomain;
import org.cs4j.core.collections.PackedElement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by user on 05/12/2015.
 *
 * TODO: Should implement all the methods and check!!!
 *
 */
public class RawGraph implements SearchDomain {

    /*
    RawGraphNode a = new RawGraphNode('A', 105);
    RawGraphNode c = new RawGraphNode('C', 99);
    RawGraphNode b = new RawGraphNode('B', 110);
    RawGraphNode g = new RawGraphNode('G', 0);

    RawGraphNode c1 = new RawGraphNode('X', 120);
    RawGraphNode c2 = new RawGraphNode('Y', 120);
    RawGraphNode c3 = new RawGraphNode('Z', 120);

    RawGraphEdge ac = new RawGraphEdge(a, c, 15);
    RawGraphEdge ab = new RawGraphEdge(a, b, 5);
    RawGraphEdge bc = new RawGraphEdge(b, c, 5);
    RawGraphEdge cg = new RawGraphEdge(c, g, 121);

    RawGraphEdge cc1 = new RawGraphEdge(c, c1, 1);
    RawGraphEdge cc2 = new RawGraphEdge(c, c2, 1);
    RawGraphEdge cc3 = new RawGraphEdge(c, c3, 1);
    */

    RawGraphNode a0 = new RawGraphNode('A', 0);
    RawGraphNode a1 = new RawGraphNode('B', 0);
    RawGraphNode a2 = new RawGraphNode('C', 3);
    RawGraphNode a3 = new RawGraphNode('D', 7);
    RawGraphNode a4 = new RawGraphNode('E', 13);
    RawGraphNode a5 = new RawGraphNode('F', 23);

    RawGraphEdge a5a4 = new RawGraphEdge(a5, a4, 1);
    RawGraphEdge a5a3 = new RawGraphEdge(a5, a3, 6);
    RawGraphEdge a5a2 = new RawGraphEdge(a5, a2, 9);
    RawGraphEdge a5a1 = new RawGraphEdge(a5, a1, 11);

    RawGraphEdge a4a3 = new RawGraphEdge(a4, a3, 1);
    RawGraphEdge a4a2 = new RawGraphEdge(a4, a2, 4);
    RawGraphEdge a4a1 = new RawGraphEdge(a4, a1, 6);

    RawGraphEdge a3a2 = new RawGraphEdge(a3, a2, 1);
    RawGraphEdge a3a1 = new RawGraphEdge(a3, a1, 3);

    RawGraphEdge a2a1 = new RawGraphEdge(a2, a1, 1);

    RawGraphEdge a1a0 = new RawGraphEdge(a1, a0, 19);



    Map<Integer, RawGraphNode> nodes;
    Map<RawGraphNode, RawGraphEdge[]> transitions;


    /**
     * The constructor of the class
     */
    /*
    public RawGraph1() {
        nodes = new HashMap<>();
        nodes.put(a.hashCode(), a);
        nodes.put(b.hashCode(), b);
        nodes.put(c.hashCode(), c);
        nodes.put(g.hashCode(), g);

        nodes.put(c1.hashCode(), c1);
        nodes.put(c2.hashCode(), c2);
        nodes.put(c3.hashCode(), c3);


        transitions = new HashMap<>();
        transitions.put(a, new RawGraphEdge[]{ab, ac});
        transitions.put(b, new RawGraphEdge[]{bc});

        transitions.put(c, new RawGraphEdge[]{cg, cc1, cc2, cc3});
        transitions.put(c1, new RawGraphEdge[]{});
        transitions.put(c2, new RawGraphEdge[]{});
        transitions.put(c3, new RawGraphEdge[]{});

        transitions.put(g, new RawGraphEdge[]{});
    }
    */

    public RawGraph() {
        nodes = new HashMap<>();
        nodes.put(a0.hashCode(), a0);
        nodes.put(a1.hashCode(), a1);
        nodes.put(a2.hashCode(), a2);
        nodes.put(a3.hashCode(), a3);
        nodes.put(a4.hashCode(), a4);
        nodes.put(a5.hashCode(), a5);

        transitions = new HashMap<>();
        transitions.put(a5, new RawGraphEdge[]{a5a4, a5a3, a5a2, a5a1});
        transitions.put(a4, new RawGraphEdge[]{a4a3, a4a2, a4a1});
        transitions.put(a3, new RawGraphEdge[]{a3a2, a3a1});
        transitions.put(a2, new RawGraphEdge[]{a2a1});
        transitions.put(a1, new RawGraphEdge[]{a1a0});
    }



    @Override
    public State initialState() {
        //return a;
        return a5;
    }

    @Override
    public boolean isGoal(State state) {
        RawGraphNode n = (RawGraphNode)state;
        return n.name == 'A';
    }

    @Override
    public int getNumOperators(State state) {
        RawGraphNode n = (RawGraphNode)state;
        return transitions.get(n).length;
    }

    @Override
    public Operator getOperator(State state, int index) {
        RawGraphNode n = (RawGraphNode)state;
        return transitions.get(n)[index];
    }

    @Override
    public State applyOperator(State state, Operator op) {
        RawGraphEdge edge = (RawGraphEdge)op;
        RawGraphNode node = (RawGraphNode)state;
        if (node.equals(edge.a)) {
            return edge.b;
        } else if (node.equals(edge.b)) {
            return edge.a;
        } else {
            // TODO
            assert false;
            return null;
        }
    }

    @Override
    public State copy(State state) {
        RawGraphNode node = (RawGraphNode)state;
        return new RawGraphNode(node.name, node.h, node.parent);
    }

    @Override
    public PackedElement pack(State state) {
        RawGraphNode node = (RawGraphNode)state;
        return new PackedElement(node.hashCode());
    }

    @Override
    public State unpack(PackedElement packed) {
        assert packed.getLongsCount() == 1;
        return this.nodes.get((int)packed.getFirst());
    }

    @Override
    public String dumpStatesCollection(State[] states) {
        return null;
    }

    @Override
    public boolean isCurrentHeuristicConsistent() {
        return false;
    }

    @Override
    public void setOptimalSolutionCost(double cost) {

    }

    @Override
    public double getOptimalSolutionCost() {
        return 0;
    }

    @Override
    public Map<String, Class> getPossibleParameters() {
        return null;
    }

    @Override
    public void setAdditionalParameter(String parameterName, String value) {

    }

    private class RawGraphNode implements SearchDomain.State {
        private char name;
        private double h;
        RawGraphNode parent;

        private RawGraphNode(char name, double h) {
            this.name = name;
            this.h = h;
        }

        private RawGraphNode(char name, double h, RawGraphNode parent) {
            this(name, h);
            this.parent = parent;
        }

        @Override
        public boolean equals(Object other) {
            try {
                RawGraphNode node = (RawGraphNode)other;
                return node.name == this.name && node.h == this.h;
            } catch (ClassCastException e) {
                return false;
            }
        }

        @Override
        public State getParent() {
            return this.parent;
        }

        public double getH() {
            return this.h;
        }

        @Override
        public double getD() {
            return this.h;
        }

        @Override
        public String dumpState() {
            return this.name + "";
        }

        @Override
        public String dumpStateShort() {
            return this.dumpState();
        }
    }

    private class RawGraphEdge implements Operator {
        private RawGraphNode a;
        private RawGraphNode b;
        private double cost;

        private RawGraphEdge(RawGraphNode a, RawGraphNode b, double cost) {
            this.a = a;
            this.b = b;
            this.cost = cost;
        }

        @Override
        public double getCost(State state, State parent) {
            RawGraphNode a = (RawGraphNode)state;
            RawGraphNode b = (RawGraphNode)parent;
            assert (a.equals(this.a) && b.equals(this.b)) || (a.equals(this.b) && b.equals(this.a));
            return this.cost;
        }

        @Override
        public Operator reverse(State state) {
            RawGraphNode s = (RawGraphNode)state;
            assert s.equals(this.a) || s.equals(this.b);
            return this;
        }
    }
}
