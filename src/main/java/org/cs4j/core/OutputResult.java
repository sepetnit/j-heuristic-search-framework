package org.cs4j.core;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;

public class OutputResult {
  private String fname;
  private FileWriter writer;
  private StringBuilder currentResult;

  public OutputResult(double wg, double wh, boolean reopen) throws IOException {
    this.fname = "" + (int)wg + "_" + (int)wh + "_" + (reopen? "always" : "never") + "_reopen.csv";
    File f = new File("results/fifteenpuzzle/korf100/ees_heavy", this.fname);
    if (f.exists()) {
      throw new FileAlreadyExistsException(this.fname);
    }
    this.writer = new FileWriter(f);
    this.currentResult = new StringBuilder();
  }

  public void appendNewResult(double[] result) {
    for (double d : result) {
      this.currentResult.append(d);
      this.currentResult.append(",");
    }
  }

  public String getFname() {
    return this.fname;
  }

  public void write(String line) throws IOException {
    this.writer.write(line + "\n");
  }

  public void newline() throws IOException {
    if (this.currentResult.length() > 0) {
      this.writer.write(this.currentResult.toString());
      this.writer.write("\n");
      this.writer.flush();
      this.currentResult = new StringBuilder();
    }
  }

  public void close() {
    if (this.writer != null) {
      try {
        // Clear this result
        this.newline();
        this.writer.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
