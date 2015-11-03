package org.cs4j.core.domains;

import org.cs4j.core.collections.GeneralPair;

import java.io.BufferedReader;
import java.io.IOException;


/**
 * Converts from .map files to data_structures.grids
 */
/*public class GridsFromMapFilesCreator extends InputReader<GridPathFinding> {

    public static final String MAP_FILE_EXTENSION = "map";
    public static final String DEFAULT_OUTPUT_DIRECTORY = "converted_map_files";
    public static final char[] ALL_SIGNS = {'T', '@', 'S', 'G', '.'};

    private int startX;
    private int startY;

    private String getFileExtension(String fileName) {
        String extension = "";
        int i = fileName.lastIndexOf('.');
        if (i >= 0) {
            extension = fileName.substring(i + 1);
        }
        return extension;
    }

    private GeneralPair<Integer, Integer> readGridDimensions(BufferedReader reader) throws IOException {
        // Read the dimensions of the grid
        String heightLine = reader.readLine();
        assert (heightLine.startsWith("height"));
        String[] heightLineAsString = heightLine.split("\\s+");
        assert (heightLineAsString.length == 2);
        int height = Integer.parseInt(heightLineAsString[1]);
        String widthLine = reader.readLine();
        assert (widthLine.startsWith("width"));
        String[] widthLineAsString = widthLine.split("\\s+");
        assert (widthLineAsString.length == 2);
        int width = Integer.parseInt(widthLineAsString[1]);
        return new GeneralPair<>(width, height);
    }

    private boolean isValidRow(String row) {
        for (Character currentValid : GridsFromMapFilesCreator.ALL_SIGNS) {
            if (row.contains(currentValid.toString())) {
                return true;
            }
        }
        return false;
    }
}
*/