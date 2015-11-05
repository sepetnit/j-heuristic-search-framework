package org.cs4j.core;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;

public class OutputResult {
    private String basicResultsPath;
    private String fname;

    private FileWriter writer;
    private StringBuilder currentResult;

    /**
     * @param reopen Whether to perform reopening
     *
     * @return A string representation of the reopening policy
     */
    private String _getReopenPolicyToString(boolean reopen) {
        return (reopen? "always": "never") + "_reopen";
    }

    /**
     * The constructor of the class
     *
     * @param pathPrefix A prefix of the results path
     * @param wg The multiplier on g
     * @param wh The multiplier on h
     * @param reopenPolicyStr A string representation of the reopening policy
     * @param reopenPolicyBln A boolean representation of the reopening policy
     *
     * @throws IOException
     */
    public OutputResult(String pathPrefix, double wg, double wh,
                        String reopenPolicyStr, boolean reopenPolicyBln,
                        boolean overwriteFile)
            throws IOException {
        /**
         * In case we don't have a string representation of the reopen policy - let's create it explicitly
         */
        if (reopenPolicyStr == null) {
            reopenPolicyStr = this._getReopenPolicyToString(reopenPolicyBln);
        }
        if (pathPrefix == null) {
            while (pathPrefix == null || new File(pathPrefix).exists()) {
                int rand = (int) (Math.random() * 100);
                pathPrefix = "./results/{" + rand + "}_" + this.fname;
            }
        }
        this.basicResultsPath = pathPrefix;
        String pathPostfix = ((wg == -1) || (wh == -1))? "" : (int) wg + "_" + (int) wh + "_" + reopenPolicyStr;
        this.fname = this.basicResultsPath +  pathPostfix + ".csv";
        File f = new File(this.fname);
        boolean fileExists = f.exists();
        if (fileExists && !overwriteFile) {
            throw new FileAlreadyExistsException(this.fname);
        } else if (fileExists && !f.delete()) {
            throw new IOException("File " + this.fname + " was marked for deletion, but failed!");
        }

        this.writer = new FileWriter(f);
        this.currentResult = new StringBuilder();
    }

    public OutputResult(String pathPrefix, double wg, double wh, boolean reopenPolicy) throws IOException {
        this(pathPrefix, wg, wh, null, reopenPolicy, false);
    }

    /**
     * Create a general output result (without references of weight or reopening policy in the name)
     *
     * @param pathPrefix The prefix of the output path
     *
     * @throws IOException
     */
    public OutputResult(String pathPrefix) throws IOException {
        this(pathPrefix, -1, -1, null, false, false);
    }

    /**
     * Create a general output result (without references of weight or reopening policy in the name), and overwrites
     * the file if it already exists
     *
     * @param pathPrefix The prefix of the output path
     *
     * @throws IOException
     */
    public OutputResult(String pathPrefix, boolean overwrite) throws IOException {
        this(pathPrefix, -1, -1, null, false, overwrite);
    }

    /**
     * This constructor is used for test issues
     *
     * @param wg The multiplier on g
     * @param wh The multiplier on h
     * @param reopen Reopening policy
     *
     * @throws IOException
     */
    public OutputResult(double wg, double wh, boolean reopen) throws IOException {
        this(null, wg, wh, reopen);
    }

    /**
     * @return The prefix of the results file
     */
    public String getPathPrefix() {
        return this.basicResultsPath;
    }


    /**
     * @return The full name of the output file
     */
    public String getFname() {
        return this.fname;
    }

    /**
     * Appends a new result (of a single run) to the output
     *
     * @param result The generated result
     */
    public void appendNewResult(double[] result) {
        for (double d : result) {
            this.currentResult.append(d);
            this.currentResult.append(",");
        }
    }

    /**
     * Writes a String line into the result without a newline
     *
     * @param str The string to write
     *
     * @throws IOException In case an error occurred during writing
     */
    public void write(String str) throws IOException {
        this.writer.write(str);
    }

    /**
     * Writes a String line into the result and appends a newline at the end
     *
     * @param line The line to writeln
     *
     * @throws IOException In case an error occurred during writing
     */
    public void writeln(String line) throws IOException {
        this.write(line);
        this.write("\n");
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
