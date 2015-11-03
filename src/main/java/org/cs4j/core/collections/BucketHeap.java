/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cs4j.core.collections;

import java.util.ArrayList;
import java.util.List;

import org.cs4j.core.collections.BucketHeap.BucketHeapElement;

/**
 * A bucket heap.
 * 
 * @author Matthew Hatem
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class BucketHeap<E extends BucketHeapElement> implements SearchQueue<E> {
	
  private int fill, size;
  private int min = Integer.MAX_VALUE;
  private Bucket[] buckets;
  private int key;
  
  public BucketHeap(int size, int key) {
  	this.size = size;
    this.buckets = new Bucket[size];
    this.key = key;
  }
  
  @Override
  public int getKey() {
  	return key;
  }
  
  @Override
  public void add(E n) {
    int p0 = (int)n.getRank(0);
    if (p0 < min) {
      min = p0;
    }
    Bucket<E> bucket = buckets[p0];
    if (bucket == null) {
      bucket = new Bucket<E>(buckets.length, key);
      buckets[p0] = bucket;
    }
    n.setIndex(key, p0);
    
    int p1 = (int)n.getRank(1);
    bucket.push(n, p1);
    n.setSecondaryIndex(key, p1);
    
    fill++;
  }
  
  @Override
  public E poll() {
    for (; min < buckets.length; min++) {
      Bucket minBucket = buckets[min];
      if (minBucket != null && !minBucket.isEmpty()) break;
    }
    fill--;
    Bucket<E> minBucket = buckets[min];
    E e = minBucket.pop();
    e.setIndex(key, -1);
    e.setSecondaryIndex(key, -1);
    return e;
  }
  
  @Override
  public E peek() {
  	int min = this.min;
    for (; min < buckets.length; min++) {
      Bucket minBucket = buckets[min];
      if (minBucket != null && !minBucket.isEmpty()) break;
    }
    Bucket<E> minBucket = buckets[min];
    return minBucket.peek();
  }
  
	@Override
	public void update(E e) {
		int p0 = e.getIndex(key);
		if (p0 > buckets.length-1 || p0 < 0)
			throw new IllegalArgumentException();
		Bucket<E> b = buckets[p0];
		b.remove(e);
		add(e);
	}
	
	@Override
	public E remove(E e) {
		int p0 = e.getIndex(key);
		if (p0 > buckets.length-1 || p0 < 0)
			throw new IllegalArgumentException();
		Bucket<E> b = buckets[p0];
		b.remove(e);
		return e;
	}

	@Override
	public void clear() {
		fill = 0;
		min = Integer.MAX_VALUE;
		buckets = new Bucket[size];
	}
  
  @Override
  public boolean isEmpty() { 
    return fill == 0; 
  }
  
  @Override
  public int size() {
  	return fill;
  }

  private static final class Bucket<E extends BucketHeapElement> {
    private int fill, max;
    private ArrayList[] bins;
    private int key;
    
    Bucket(int size, int key) {
      bins = new ArrayList[size];
      this.key = key;
    }
        
    private void push(E n, int p) {
       if (p > max) {
        max = p; 
      }
      ArrayList<E> binP = bins[p];
      if (binP == null) {
        binP = new ArrayList<E>(10000);
        bins[p] = binP;
      }
      binP.add(n);
      fill++;
    }
    
    private E pop() {
      for ( ; max > 0; max--) {
        ArrayList<E> maxBin = bins[max];
        if (maxBin != null && !maxBin.isEmpty()) break;
      }
      ArrayList<E> maxBin = bins[max];
      int last = maxBin.size()-1;
      E n = maxBin.get(last);
      maxBin.remove(last);
      fill--;
      return n;
    }
    
    private E peek() {
    	int max = this.max;
      for ( ; max > 0; max--) {
        ArrayList<E> maxBin = bins[max];
        if (maxBin != null && !maxBin.isEmpty()) break;
      }
      ArrayList<E> maxBin = bins[max];
      int last = maxBin.size()-1;
      return maxBin.get(last);
    }
    
    private void remove(E e) {
  		int p1 = e.getSecondaryIndex(key);
  		if (p1 > bins.length-1 || p1 < 0)
  			throw new IllegalArgumentException();
  		List<E> list = bins[p1];
  		if (!list.remove(e))
  			throw new IllegalArgumentException();
  		fill--;
    }
    
    private boolean isEmpty() {
      return fill == 0;
    }
  }
  
  public interface BucketHeapElement extends SearchQueueElement {
  	
  	public void setSecondaryIndex(int key, int index);
  	
  	public int getSecondaryIndex(int key);
  	
  	public double getRank(int level);
  	
  }

}
