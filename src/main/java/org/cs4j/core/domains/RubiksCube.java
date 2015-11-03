/*package org.cs4j.core.domains;

import org.cs4j.core.SearchDomain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by sepetnit on 04/04/15.
 *

public class RubiksCube  implements SearchDomain {

  public RubiksCube(InputStream stream) {
    try {
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(stream));
      String line = reader.readLine();

      String dim[] = line.split(" ");
      /*width =/ Integer.parseInt(dim[0]);
      /*height =/ Integer.parseInt(dim[0]);

      line = reader.readLine();
      for (int t = 0; t < Ntiles; t++) {
        int p = Integer.parseInt(reader.readLine());
        init[p] = t;
      }

      line = reader.readLine();
      for (int t = 0; t < Ntiles; t++) {
        int p = Integer.parseInt(reader.readLine());
        if (p != t)
          throw new IllegalArgumentException("Non-canonical goal positions");
      }
    }
    catch (IOException exception) {
      exception.printStackTrace();
    }

    init(cost);
    for (int i=0; i<oplookup.length; i++)
      oplookup[i] = new FifteenPuzzleOperator(i);

  }


}*/