package org.cs4j.core.domains;

import java.io.IOException;

public class InvalidInputFileException extends IOException {
  public InvalidInputFileException(String message) {
    super(message);
  }
}
