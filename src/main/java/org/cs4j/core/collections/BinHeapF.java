package org.cs4j.core.collections;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by Daniel on 08/01/2016.
 */
public class BinHeapF<E extends SearchQueueElement> implements SearchQueue<E> {

    private HashMap<Double, Integer> countF = new HashMap<>();
    private double fmin;
    private BinHeap<E> heapF;
    private BinHeap<E> heap;

    public BinHeapF(Comparator<E> cmp) {
        this.heapF = new BinHeap<>(new FComparator(), 0);
        this.heap =new BinHeap<>(cmp, 1);
    }

    public double getFmin(){
        return fmin;
    }

    public void add(E e) {
        heap.add(e);
        heapF.add(e);
        countF_add(e.getF());
    }

    private void countF_add(double Val){
        if(countF.containsKey(Val))
            countF.put(Val,countF.get(Val)+1);
        else {
            countF.put(Val, 1);
        }
    }

    private void reorder(){
        if(heap.peek() instanceof reComputeable) {
//            System.out.println("should reorder " + fmin);
            for (int i = 0; i < heap.size(); i++) {
                E e = heap.getElementAt(i);
                ((reComputeable) e).reCalcValue();
                if(e.getF()!=heap.getElementAt(i).getF()){
                    System.out.println("wrong update" + e);
                }
                heap.update(e);
                if (i != e.getIndex(heap.getKey())) {
                    //System.out.println("wrong reorder1" + e);
                    i--;
                }
            }
            for (int i = 0; i < heap.size(); i++) {
                E e = heap.getElementAt(i);
                heap.update(e);
                if (i != e.getIndex(heap.getKey())) {
                    System.out.println("wrong reorder2" + e);
                }
            }
        }
    }

    @Override
    public E poll() {
        E e = heap.peek();
        return remove(e);
    }

    public E peekF() {
        return heapF.peek();
    }

    @Override
    public E peek() {
        return heap.peek();
    }

    @Override
    public void update(E e) {
        throw new UnsupportedOperationException("Invalid operation for BinheapF, use updateF instead.");
    }

    public void updateF(E newNode, double oldf) {
        countF_add(newNode.getF());
        countF_remove(oldf);
        heap.update(newNode);
        heapF.update(newNode);
    }

    public boolean isEmpty() {
        return this.heap.peek() == null;
    }

    @Override
    public int size() {
        return heap.size();
    }

    @Override
    public void clear() {
        heap.clear();
        heapF.clear();
        countF.clear();
    }

    @Override
    public E remove(E e) {
        heap.remove(e);
        heapF.remove(e);
        countF_remove(e.getF());
        return e;
    }

    private void countF_remove(double Val){
        countF.put(Val,countF.get(Val)-1);
//        test();
        if(countF.get(Val)==0){
            countF.remove(Val);
            if(Val==fmin && heapF.size()>0){//find next lowest fmin
                fmin = heapF.peek().getF();
                reorder();
            }
        }
    }

    @Override
    public int getKey() {
        return 0;
    }

    public int getFminCount(){
        return countF.get(fmin);
    }

    public void setFmin(double fmin) {
        if(this.fmin==0) {
            this.fmin = fmin;
        }
    }

    protected final class FComparator implements Comparator<E> {
        @Override
        public int compare(E o1, E o2) {
            if(o1.getF() < o2.getF()) return -1;
            if(o1.getF() > o2.getF()) return 1;
            return 0;
        }
    }

    private void test(){
        BinHeap list = heapF;
//        System.out.println(heap.size());
        Iterator it = countF.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
//            System.out.println(pair.getKey() + " = " + pair.getValue());
        }

        for(int i=0; i < list.size(); i++){
            E e = (E) list.getElementAt(i);
            double Val = e.getF();
            countF.put(Val,countF.get(Val)-1);
            if(countF.get(Val)<0){
                System.out.println("test failed");
            }
        }

        it = countF.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
//            System.out.println(pair.getKey() + " = " + pair.getValue());
        }

        for(int i=0; i < list.size(); i++){
            E e = (E) list.getElementAt(i);
            double Val = e.getF();
            if(countF.containsKey(Val))
                countF.put(Val,countF.get(Val)+1);
            else countF.put(Val,1);
        }
    }

}
