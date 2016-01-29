package org.cs4j.core.collections;

import java.util.Comparator;

/**
 * A data structure for open and focal lists
 *
 * @param <E> Type of data to save in the list
 */
//public class GEQueue<E extends RBTreeElement<E, E> & MinHeapable> {
public class GEQueue<E extends SearchQueueElement & RBTreeElement<E, E>> {
    private RBTree<E, E> open;
    private BinHeap<E> focal;
    private int id;
    private Comparator<E> geComparator;

    private static final int ADD = 0;
    private static final int REMOVE = 1;

    private RBTreeVisitor<E> focalVisitor = new RBTreeVisitor<E>() {
        @Override
        public void visit(E e, int op) {
            switch(op) {
                case ADD: {
                    // Add to focal if the element isn't already contained there
                    if (e.getIndex(GEQueue.this.id) == -1) {
                        GEQueue.this.focal.add(e);
                    }
                    break;
                }
                case REMOVE: {
                    if (e.getIndex(GEQueue.this.id) != -1) {
                        // Remove from focal if the element is contained there
                        assert e.getIndex(GEQueue.this.id) != -1 : e.toString() + " " + GEQueue.this.id;
                        GEQueue.this.focal.remove(e);
                    }
                    break;
                }
            }
        }
    };

    public GEQueue(Comparator<E> openComparator,
                   Comparator<E> geComparator,
                   Comparator<E> focalComparator,
                   int id) {
        this.id = id;
        this.geComparator = geComparator;
        this.focal = new BinHeap<>(focalComparator, id);
        this.open = new RBTree<>(openComparator, geComparator);
    }

    public boolean isEmpty() {
        return this.open.peek() == null;
    }

    public int size() {
        return this.open.size();
    }

    public int focalSize() {
        return this.focal.size();
    }

    /**
     * Add to queue
     *
     * @param e The element to add
     * @param oldBest The old element which was the 'best' one in the queue
     */
    public void add(E e, E oldBest) {
        assert e.getNode() == null;
        this.open.insert(e, e);
        // assumes oldBest is still valid
        if (this.geComparator.compare(e, oldBest) <= 0) {
            this.focal.add(e);
        }
        assert e.getNode() != null;
    }

    public void updateFocal(E oldBest, E newBest, int fHatChange) {
        assert newBest != null;
        assert newBest.getNode() != null;
        
        // did best f^ change?
        if (oldBest == null || fHatChange != 0) {
            // did best f^ go down?
            if (oldBest != null && fHatChange < 0) {
                // try {
                    this.open.visit(newBest, oldBest, GEQueue.REMOVE, this.focalVisitor);
                // }
                // catch (ArrayIndexOutOfBoundsException e){
                //    System.out.print(oldBest);
                //    System.out.print("Error");
                //   }
            // then best f^ when up
            } else if (oldBest == null || oldBest.getNode() == null) {
                this.open.visit(oldBest, newBest, GEQueue.ADD, this.focalVisitor);
            }
        }
        // verifyFocal();
    }
  
    //public void verifyFocal() {
    //    // Take the best f^
    //    E best = this.open.peek();
    //    // make sure all nodes in focal are close to best
    //    List<E> heap = ((BinHeap)this.focal).heap;
    //    for (E o : heap) {
    //      assert this.geComparator.compare(o, best) <= 0;
    //    }
    //    // make sure all nodes in open that are close to best are in focal
    //    List<E> rbNodes = this.open.getValues();
    //    // Go over all the nodes in open and for every node check that:
    //    // The node is not suitable to be in focal or The node is in focal
    //    for (E o : rbNodes) {
    //        assert this.geComparator.compare(o, best) > 0 || o.getIndex(this.id) != -1;
    //    }
    //}

    /**
     * Removes a node from OPEN and also from FOCAL
     *
     * @param e The node to remove
     */
    public void remove(E e) {
        assert e.getNode() != null;
        this.open.delete(e);
        if (e.getIndex(id) != -1) {
            this.focal.remove(e);
        }
    }

    /**
     * Polling a node from OPEN
     *
     * NOTE: Removes the node from FOCAL!
     *
     * @return The extracted node
     */
    public E pollOpen() {
        E e = this.open.poll();
        if (e != null && e.getIndex(id) != -1) {
            this.focal.remove(e);
        }
        return e;
    }

    /**
     * Polling a node from FOCAL
     *
     * NOTE: Removes the node from OPEN!
     *
     * @return The extracted node
     */
    public E pollFocal() {
        E e = this.focal.poll();
        if (e != null) {
            assert e.getNode() != null;
            this.open.delete(e);
        }
        return e;
    }

    /**
     * Peeks a node from OPEN (without removing it)
     *
     * @return The extracted node
     */
    public E peekOpen() {
        return this.open.peek();
    }

    /**
     * Peeks a node from FOCAL (without removing it)
     *
     * @return The extracted node
     */
    public E peekFocal() {
        return this.focal.peek();
    }
}
