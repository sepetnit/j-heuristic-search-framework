package org.cs4j.core.generators;

import org.cs4j.core.SearchResult;
import org.cs4j.core.algorithms.DFS;
//import org.cs4j.core.domains.PancakesWithDontCares;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by sepetnit on 11/7/2015.
 *
 * Generates a PDB for the generic Pancakes problem
 *
 */
public class PancakesPDBGenerator {

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
        // Get all the possible combination of indexes for the specific pancakes
        List<List<Integer>> possibleIndexesCombinations =
                this._getPossibleCombinations(
                        this._getDefaultArray(size),
                        specificPancakes.length);
        int[][] inputPancakesProblems = new int[possibleIndexesCombinations.size()][];
        for (int i = 0; i < possibleIndexesCombinations.size(); ++i) {
            // System.out.println("Creating problem # " + i);
            inputPancakesProblems[i] = new int[size];
            // Fill with default values
            Arrays.fill(inputPancakesProblems[i], 100);
            List<Integer> currentIndexes = possibleIndexesCombinations.get(i);
            // Fill specific pancakes
            for (int j = 0; j < specificPancakes.length; ++j) {
                inputPancakesProblems[i][currentIndexes.get(j)] = specificPancakes[j];
            }
        }
        return inputPancakesProblems;
    }

    public void createPDB(int size, int[] specificPancakes) {
        /*
        int[][] allSubProblems = this.getAllPossibleSubProblems(size, specificPancakes);
        DFS dfs = new DFS();
        for (int[] subProblem : allSubProblems) {
            System.out.println(Arrays.toString(subProblem));
            PancakesWithDontCares instance = new PancakesWithDontCares(subProblem, specificPancakes);
            SearchResult result = dfs.search(instance);
            assert result != null;
            System.out.println(result + " " + result.getSolutions().size());
            System.out.println("Cost is " + result.getSolutions().get(0).getCost());
        }*/
    }

    /**
     * This main function generates the PDB
     *
     * @param args The arguments to main - should contain only the size of the Pancakes problem
     */
    public static void main(String[] args) {
        PancakesPDBGenerator generator = new PancakesPDBGenerator();
        int[] specific = {10, 11, 12, 13, 14, 15, 16};
        generator.createPDB(17, specific);
        System.out.println("Done.");
    }
}
