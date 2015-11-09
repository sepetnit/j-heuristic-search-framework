package org.cs4j.core.generators;

import java.io.IOException;
import java.util.Random;

/**
 * Created by sepetnit on 11/8/2015.
 *
 */
public class GeneralInstancesGenerator {
    protected Random rand;

    public GeneralInstancesGenerator() {
        this.rand = new Random();
    }

    /**
     * Appends a newline to the given StringBuild
     *
     * @param sb A StringBuilder object to update
     */
    protected void _appendNewLine(StringBuilder sb) {
        sb.append("\n");
    }

    protected void _appendIntFieldToStringBuilder(String fieldName, int value, StringBuilder sb) {
        sb.append(fieldName);
        sb.append(": ");
        sb.append(value);
        this._appendNewLine(sb);
    }

    /**
     * Reads an integer from the input argument and assures its validity
     *
     * @param input The input to parse and read an integer from
     * @param min The minimum enforced value of the integer (-1 if irrelevant)
     * @param max The maximum enforced value of the integer (-1 if irrelevant)
     * @param type What the integer represents (a string representation)
     *
     * @return The parsed integer
     *
     * @throws java.io.IOException If something wrong occurred
     */
    protected static int readIntNumber(String input, int min, int max, String type) throws IOException {
        try {
            // Parse the input
            int toReturn = Integer.parseInt(input);
            if (min != -1 && toReturn < min) {
                throw new IOException("Too low " + type + " (must be >= " + min + "): " + input);
            } else if (max != -1 && toReturn > max) {
                throw new IOException("Too high " + type + " (must be <= " + max + "): " + input);
            }
            return toReturn;
        } catch (NumberFormatException e) {
            throw new IOException("Invalid " + type + ": " + input);
        }
    }

}
