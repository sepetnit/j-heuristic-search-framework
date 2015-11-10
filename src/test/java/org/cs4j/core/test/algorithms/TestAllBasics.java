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
package org.cs4j.core.test.algorithms;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import org.junit.Assert;

import org.cs4j.core.SearchAlgorithm;
import org.cs4j.core.SearchDomain;
import org.cs4j.core.SearchResult;
import org.cs4j.core.SearchResult.Solution;
import org.cs4j.core.algorithms.WAStar;
import org.cs4j.core.algorithms.WAStar.HeapType;
import org.cs4j.core.algorithms.EES;
import org.cs4j.core.algorithms.IDAstar;
import org.cs4j.core.algorithms.RBFS;
import org.cs4j.core.algorithms.WRBFS;
import org.cs4j.core.domains.FifteenPuzzle;
import org.junit.Test;

public class TestAllBasics {
		
	@Test
	public void testAstarBinHeap() throws FileNotFoundException {
		SearchDomain domain = createFifteenPuzzle("12");
		SearchAlgorithm algo = new WAStar(HeapType.BIN);
		testSearchAlgorithm(domain, algo, 65271, 32470, 45);
	}	
	
	@Test
	public void testAstarBucketHeap() throws FileNotFoundException {
		SearchDomain domain = createFifteenPuzzle("12");
		SearchAlgorithm algo = new WAStar(HeapType.BUCKET);
		testSearchAlgorithm(domain, algo, 64963, 32334, 45);
	}		
	
	@Test
	public void testRBFS() throws FileNotFoundException {
		SearchDomain domain = createFifteenPuzzle("12");
		SearchAlgorithm algo = new RBFS();
		testSearchAlgorithm(domain, algo, 301098, 148421, 45);
	}	
	
	@Test
	public void testIDAstar() throws FileNotFoundException {
		SearchDomain domain = createFifteenPuzzle("12");
		SearchAlgorithm algo = new IDAstar();
		testSearchAlgorithm(domain, algo, 546343, 269708, 45);
	}		

	@Test
	public void testEES() throws FileNotFoundException {
		SearchDomain domain = createFifteenPuzzle("12");
		SearchAlgorithm algo = new EES(2);
		testSearchAlgorithm(domain, algo, 5131, 2506, 55);
	}	
	
	@Test
	public void testWRBFS() throws FileNotFoundException {
		SearchDomain domain = createFifteenPuzzle("12");
		SearchAlgorithm algo = new WRBFS();
		testSearchAlgorithm(domain, algo, 301098, 148421, 45);
	}	
	
	public SearchDomain createFifteenPuzzle(String instance) throws FileNotFoundException {
		InputStream is = new FileInputStream(new File("cs4j-master/input/fifteenpuzzle/korf100/"+instance));
		FifteenPuzzle puzzle = new FifteenPuzzle(is);
		return puzzle;
	}
	
	public void testSearchAlgorithm(SearchDomain domain, SearchAlgorithm algo, 
			long generated, long expanded, double cost) {
		SearchResult result = algo.search(domain);
		Solution sol = result.getSolutions().get(0);
		Assert.assertTrue(result.getWallTimeMillis() > 1);
		Assert.assertTrue(result.getWallTimeMillis() < 200);
		Assert.assertTrue(result.getCpuTimeMillis() > 1);
		Assert.assertTrue(result.getCpuTimeMillis() < 200);
		Assert.assertTrue(result.getGenerated() == generated);
		Assert.assertTrue(result.getExpanded() == expanded);
		Assert.assertTrue(sol.getCost() == cost);
		Assert.assertTrue(sol.getLength() == cost+1);
	}
	
	public static void main(String[] args) throws FileNotFoundException {
		TestAllBasics test = new TestAllBasics();
		test.testEES();
	}

}
