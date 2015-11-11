package org.cs4j.core;

import java.util.Map;

/**
 * Created by user on 11/11/2015.
 *
 * The object that implements that interface defines some parameters that can be tuned
 *
 */
public interface SearchConfigurable {

    /**
     * Retrieves and returns the possible paramters that can be set for this algorithm
     *
     * @return A mapping of parameters (name=>type) or null if there are no such parameters
     */
    Map<String, Class> getPossibleParameters();

    /**
     * The function allows setting of different parameters of the search, according to the algorithm
     *
     * e.g. Whether to allow reopening in WAstar or the maximum cost in PTS
     *  @param parameterName The name of the parameter
     * @param value The value of the parameter
     */
    void setAdditionalParameter(String parameterName, String value);

}
