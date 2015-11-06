package org.cs4j.core.data;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by sepetnit on 19/04/15.
 *
 */
public class Weights {
    // Will contain BASIC_WEIGHTS + LOW_WEIGHTS
    public SingleWeight[] EXTENDED_WEIGHTS;

    public class SingleWeight implements Comparable<SingleWeight> {
        public double wh;
        public double wg;

        /**
         * The constructor of the class
         *
         * @param wg Multiplier of g
         * @param wh Multiplier of h
         */
        public SingleWeight(double wg, double wh) {
            this.wg = wg;
            this.wh = wh;
        }

        /**
         * A constructor of the class which receives only wh (assumes wg = 1.0)
         *
         * @param wh Multiplier of h
         */
        public SingleWeight(double wh) {
            this(1.0, wh);
        }

        /**
         * @return A unified weight
         */
        public double getWeight() {
            return this.wh / this.wg;
        }


        @Override
        public int compareTo(SingleWeight other) {
            return this.getWeight() < other.getWeight()? -1 : 1;
        }
    }

    /**
     * The constructor of the class
     */
    public Weights() {
        this.EXTENDED_WEIGHTS = new SingleWeight[this.BASIC_WEIGHTS.length + this.LOW_WEIGHTS.length];
        List<SingleWeight> basicWeights = Arrays.asList(this.BASIC_WEIGHTS);
        List<SingleWeight> lowWeights = Arrays.asList(this.LOW_WEIGHTS);
        List<SingleWeight> extended = new ArrayList<>(this.EXTENDED_WEIGHTS.length);
        extended.addAll(basicWeights);
        extended.addAll(lowWeights);
        this.EXTENDED_WEIGHTS = extended.toArray(new SingleWeight[extended.size()]);
        Arrays.sort(this.EXTENDED_WEIGHTS);
    }

    public final SingleWeight[] BASIC_WEIGHTS = {
            new SingleWeight(2, 3),
            new SingleWeight(3),
            new SingleWeight(5),
            new SingleWeight(10),
            new SingleWeight(20),
            new SingleWeight(30),
            new SingleWeight(50),
            new SingleWeight(100),
            new SingleWeight(200),
            new SingleWeight(500),
            new SingleWeight(1000),
    };

    public final SingleWeight[] LOW_WEIGHTS = {
            new SingleWeight(1.05),
            new SingleWeight(1.1),
            new SingleWeight(1.2),
            new SingleWeight(1.3),
            new SingleWeight(1.4),
            new SingleWeight(1.5),
            new SingleWeight(1.6),
            new SingleWeight(1.7),
            new SingleWeight(1.8),
            new SingleWeight(1.9),
            new SingleWeight(2.0),
            new SingleWeight(2.1),
            new SingleWeight(2.2),
            new SingleWeight(2.3),
            new SingleWeight(2.4),
            new SingleWeight(2.5),
            new SingleWeight(2.6),
            new SingleWeight(2.7),
            new SingleWeight(2.8),
            new SingleWeight(2.9),
            new SingleWeight(3.0),
    };

    public SingleWeight[] KORF_WEIGHTS = {
    /*
        new SingleWeight(1, 49),
        new SingleWeight(3, 97),
        new SingleWeight(1, 24),
        new SingleWeight(1, 19),
        new SingleWeight(3, 47),
        new SingleWeight(7, 93),
        new SingleWeight(2, 23),
        new SingleWeight(9, 91),
        new SingleWeight(1, 9),
        new SingleWeight(11, 89),
        new SingleWeight(1, 8),
        new SingleWeight(3, 22),
        new SingleWeight(1, 7),
        new SingleWeight(13, 87),
        new SingleWeight(7, 43),
        new SingleWeight(1, 6),
        new SingleWeight(3, 17),
        new SingleWeight(4, 21),
        new SingleWeight(1, 5),
        new SingleWeight(17, 83),
        new SingleWeight(9, 41),
        new SingleWeight(19, 81),
        new SingleWeight(1, 4),
        new SingleWeight(21, 79),
        new SingleWeight(11, 39),
        new SingleWeight(23, 77),
        new SingleWeight(6, 19),
        new SingleWeight(1, 3),
        new SingleWeight(13, 37),
        new SingleWeight(27, 73),
        new SingleWeight(7, 18),
        new SingleWeight(29, 71),
        new SingleWeight(3, 7),
        new SingleWeight(31, 69),
        new SingleWeight(8, 17),
        new SingleWeight(33, 67),
        new SingleWeight(1, 2),
        new SingleWeight(17, 33),
        new SingleWeight(7, 13),
        new SingleWeight(9, 16)
        new SingleWeight(37, 63),
        new SingleWeight(19, 31),
    */
    };
}
