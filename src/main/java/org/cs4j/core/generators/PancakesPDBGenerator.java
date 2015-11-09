package org.cs4j.core.generators;

import org.cs4j.core.SearchResult;
import org.cs4j.core.algorithms.AStar;
import org.cs4j.core.algorithms.DFS;
import org.cs4j.core.algorithms.EES;
import org.cs4j.core.collections.Pair;
import org.cs4j.core.domains.Pancakes;
import org.cs4j.core.domains.Utils;
//import org.cs4j.core.domains.PancakesWithDontCares;

import java.util.*;

/**
 * Created by sepetnit on 11/7/2015.
 *
 * Generates a PDB for the generic Pancakes problem
 *
 */
public class PancakesPDBGenerator extends GeneralInstancesGenerator {
    private int size;
    private int specificStartIndex;

    private Map<Long, Map<Long, Long>> pdb;
    private int[] specific;
    private List<Integer> specificAsList;
    Map<Integer, Integer> converter;

    public PancakesPDBGenerator(int size, int specificStartIndex) {
        this.size = size;
        this.specificStartIndex = specificStartIndex;

        this.specific = this._getSpecificIndexes(size, specificStartIndex);
        this.specificAsList = Utils.intArrayToIntegerList(this.specific);
        this.converter = new HashMap<>();
        for (int i = 0; i < this.specific.length; ++i) {
            converter.put(this.specific[i], i);
        }

        this.pdb = new HashMap<>();
    }

    /**
     * Implement (n k) in o(k)
     * @param n Size of the data
     * @param k Number of objects to choose from data
     *
     * @return The result of (n k)
     */
    private double _choose(int n, int k) {
        if (k < 0 || k > n) return 0;
        if (k > n / 2) {
            // choose(n,k) == choose(n,n-k),
            // so this could save a little effort
            k = n - k;
        }
        double denominator = 1.0;
        double numerator = 1.0;
        for (int i = 1; i <= k; ++i) {
            denominator *= i;
            numerator *= (n + 1 - i);
        }
        return numerator / denominator;
    }

    /**
     *
     * @param arr The input array
     * @param start Start index in arr (from which combinations should be taken)
     * @param end End index in arr (from which combinations should be taken)
     * @param result Temporary array to store current combination
     * @param index Current index in data[]
     * @param combinationsSize Size of a combinations to create
     * @param dst The result list of combinations
     *
     */
    private void _getPossibleCombinationsRec(int arr[], int start, int end,
                                             int result[], int index,
                                             int combinationsSize,
                                             List<List<Integer>> dst) {
        // Current combination is ready - add it to the list
        if (combinationsSize == index) {
            List<Integer> toAdd = new ArrayList<>();
            for (int current : result) {
                toAdd.add(current);
            }
            dst.add(toAdd);
            return;
        }

        // Replace index with all possible elements.
        // TODO:
        // The condition "end - i + 1 >= r - index" makes sure that including one element at index will make a
        // combination with remaining elements at remaining positions
        for (int i = start; i <= end && ((end - i + 1) >= (combinationsSize - index)); ++i) {
            result[index] = arr[i];
            this._getPossibleCombinationsRec(arr, i + 1, end, result, index + 1, combinationsSize, dst);
        }
    }

    private List<List<Integer>> _getPossibleCombinations(int arr[], int combinationsSize) {
        List<List<Integer>> dst = new ArrayList<>();
        int[] result = new int[combinationsSize];
        // Fill the destination list (call the recursive function)
        this._getPossibleCombinationsRec(arr, 0, arr.length - 1, result, 0, combinationsSize, dst);
        // Let's assert the length of the results
        assert dst.size() == (int)this._choose(arr.length, combinationsSize);
        return dst;
    }

    /**
     * Builds an array of a default size, such that arr[i] = i
     *
     * @param size The size of the required array
     *
     * @return The built array
     */
    private int[] _getDefaultArray(int size) {
        int[] indexes = new int[size];
        for (int i = 0; i < indexes.length; ++i) {
            indexes[i] = i;
        }
        return indexes;
    }

    private int[][] getAllPossibleSubProblems(int size, int[] specificPancakes) {
        assert specificPancakes.length < size;
        List<Integer> specificPancakesAsList = Utils.intArrayToIntegerList(specificPancakes);
        int[] otherPancakes = new int[size - specificPancakes.length];
        // Fill other pancakes
        int index = 0;
        for (int p = 0; p < size; ++p) {
            if (!specificPancakesAsList.contains(p)) {
                otherPancakes[index++] = p;
            }
        }
        // Get all the possible combination of indexes for the specific pancakes
        List<List<Integer>> possibleIndexesCombinations =
                this._getPossibleCombinations(
                        this._getDefaultArray(size),
                        specificPancakes.length);
        int[][] inputPancakesProblems = new int[possibleIndexesCombinations.size()][];
        for (int i = 0; i < possibleIndexesCombinations.size(); ++i) {
            // System.out.println("Creating problem # " + i);
            inputPancakesProblems[i] = new int[size];
            // Fill with default value
            Arrays.fill(inputPancakesProblems[i], -1);
            List<Integer> currentIndexes = possibleIndexesCombinations.get(i);
            // Fill specific pancakes
            for (int j = 0; j < specificPancakes.length; ++j) {
                inputPancakesProblems[i][currentIndexes.get(j)] = specificPancakes[j];
            }
            index = 0;
            for (int j = 0; j < inputPancakesProblems[i].length; ++j) {
                // Fill some value if -1
                if (inputPancakesProblems[i][j] == -1) {
                    inputPancakesProblems[i][j] = otherPancakes[index++];
                }
            }
        }
        return inputPancakesProblems;
    }

    private int[] _getSpecificIndexes(int size, int specificStartIndex) {
        int[] toReturn = new int[size - specificStartIndex];
        for (int i = 0; i < toReturn.length; ++i) {
            toReturn[i] = specificStartIndex + i;
        }
        return toReturn;
    }

    private void swap(int[] arr, int first, int second) {
        int tmp = arr[first];
        arr[first] = arr[second];
        arr[second] = tmp;
    }

    public long _getMyrvoldAndRuskeyHashValue(int[] pancakes, int[] w, int n) {
        if (n == 1) {
            return 0;
        }
        int d = pancakes[n - 1];
        this.swap(pancakes, pancakes[n - 1], pancakes[w[n - 1]]);
        this.swap(pancakes, w[n - 1], w[d]);
        return d + n * _getMyrvoldAndRuskeyHashValue(pancakes, w, n - 1);
    }

    public long _getMyrvoldAndRuskeyHashValue(int[] pancakes) {
        int[] pancakesCopy = new int[pancakes.length];
        System.arraycopy(pancakes, 0, pancakesCopy, 0, pancakes.length);
        int[] w = new int[pancakesCopy.length];
        for (int i = 0; i < pancakesCopy.length - 1; ++i) {
            w[pancakesCopy[i]] = i;
        }
        return this._getMyrvoldAndRuskeyHashValue(pancakesCopy, w, pancakes.length);
    }

    public Pair<Long, Long> hash(int[] pancakes,  List<Integer> specificPancakes, Map<Integer, Integer> converter) {
        int[] specific = new int[specificPancakes.size()];
        long hash1 = this._getMyrvoldAndRuskeyHashValue(specific);
        int previousIndex = -1;
        int currentIndex = -1;
        long hash2 = 0;
        for (int p = 0; p < pancakes.length; ++p) {
            if (specificPancakes.contains(pancakes[p])) {
                if (previousIndex == -1) {
                    previousIndex = p;
                } else if (currentIndex == -1) {
                    currentIndex = p;
                    hash2 |= (currentIndex - previousIndex);
                } else {
                    previousIndex = currentIndex;
                    currentIndex = p;
                    hash2 |= (currentIndex - previousIndex);
                }
                hash2 <<= 4;
            }
        }
        return new Pair<>(hash1, hash2);
    }

    private void _store(int[] subProblem, long cost) {
        Pair<Long, Long> hashValues = this.hash(subProblem, specificAsList, converter);
        System.out.println("[INFO] Solved (PDB[" + hashValues.toString() + "] = " + cost + ")");
        Map<Long, Long> internal = this.pdb.get(hashValues.getKey());
        if (internal == null) {
            internal = new HashMap<>();
            pdb.put(hashValues.getKey(), internal);
        }
        assert !internal.containsKey(hashValues.getValue());
        internal.put(hashValues.getValue(), cost);
    }

    public void createPDB() {
        // Preserve previous
        int tmp = Pancakes.MIN_PANCAKE_FOR_PDB;
        Pancakes.MIN_PANCAKE_FOR_PDB = this.specificStartIndex;
        int[][] allSubProblems =
                this.getAllPossibleSubProblems(size, this.specific);
        EES ees = new EES(1.0);
        for (int count = 0; count < allSubProblems.length; ++count) {
            int[] subProblem = allSubProblems[count];
            System.out.println("[INFO] Solving: (" + (count + 1) + "/" + allSubProblems.length + ") " +
                    Arrays.toString(subProblem));
            Pancakes instance = new Pancakes(subProblem);
            SearchResult result = ees.search(instance);
            assert result != null;
            long cost = (long)result.getSolutions().get(0).getCost();
            this._store(subProblem, cost);
        }
        Pancakes.MIN_PANCAKE_FOR_PDB = tmp;

    }

    /**
     * This main function generates the PDB
     *
     * @param args The arguments to main - should contain only the size of the Pancakes problem
     */
    public static void main(String[] args) {
        PancakesPDBGenerator generator = new PancakesPDBGenerator(17, 10);
        generator.createPDB();
        System.out.println("Done.");
    }
}
