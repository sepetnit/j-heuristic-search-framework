package org.cs4j.core.old;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/**
 * A data_structures.general reader for instances
 */
public abstract class InputReader<T> {

  private String baseDirName;

  public InputReader(String baseDirName) {
    this.baseDirName = baseDirName;
  }

  public InputReader() {
    this("");
  }

  protected abstract T internalReadFromFile(String fileName, BufferedReader reader)
      throws InvalidInputFileException, IOException;

  public T readFromFile(File file) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(file));
    try {
      return this.internalReadFromFile(file.toString(), reader);
    } finally {
      try {
        reader.close();
      } catch (IOException e) {
        // We have nothing to do here!
      }
    }
  }

  public T readFromFile(String fileName) throws IOException {
    File file = new File(fileName);
    if (file.isFile()) {
      return this.readFromFile(file);
    } else if (!this.baseDirName.equals("")) {
      file = new File(this.baseDirName, fileName);
      if (file.isFile()) {
        return this.readFromFile(file);
      }
    }
    // In any other case, throw the exception
    throw new FileNotFoundException();
  }
}

