package org.cs4j.core.data;

/**
 * Created by sepetnit on 19/04/15.
 *
 */
public class Weights {
  public class SingleWeight {
    public double wh;
    public double wg;

    public SingleWeight(double wg, double wh) {
      this.wg = wg;
      this.wh = wh;
    }

    public SingleWeight(double wh) {
      this(1.0, wh);
    }

  }

  public SingleWeight[] weights = {
      new SingleWeight(2, 3),
      new SingleWeight(10),
      new SingleWeight(20),
      new SingleWeight(30),
      new SingleWeight(50),
      new SingleWeight(100),
      new SingleWeight(200),
      new SingleWeight(500),
      new SingleWeight(1000),
  };

  public SingleWeight[] fullWeights = {
/*      new SingleWeight(1, 49),
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
      new SingleWeight(9, 16),*/
      new SingleWeight(37, 63),
      new SingleWeight(19, 31)
  };
}
