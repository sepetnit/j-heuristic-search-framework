package org.cs4j.core.tests;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by sepetnit on 11/8/2015.
 *
 */
public class Test {
    public static void main(String[] args) {
        Map<long[], Integer> test = new HashMap<>();
        long[] temp = new long[2];
        temp[0] = 100;
        temp[1] = 200;
        test.put(temp, 1);

        long[] temp1 = new long[2];
        temp1[0] = 100;
        temp1[1] = 200;

        System.out.println(test.containsKey(temp1));
    }
}
