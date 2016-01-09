/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.vplex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class to hold data collected from a VPlex perpetual performance data file
 */
public class VPlexPerpetualCSVFileData {
    // Constants
    public static final String ZERO = "0";
    public static final String NO_DATA = "no data";
    // Name of the file
    private String name;
    // Headers
    private List<String> headers;
    // Structure to hold the data. Each entry in the list is a data line that has a map of key-values representing the data points
    private List<Map<String, String>> data;

    public VPlexPerpetualCSVFileData(String name) {
        this.name = name;
    }

    /**
     * The values returned represent values that describe what the metrics are (Kb/sec, IOs/sec, etc.)
     *
     * @return List of String values presenting the headers
     */
    public List<String> getHeaders() {
        return Collections.unmodifiableList(headers);
    }

    /**
     * Save the headers for this data set
     * 
     * @param headers [IN] - metric names
     */
    public void addHeaders(String[] headers) {
        if (this.headers == null) {
            this.headers = new ArrayList<>();
        }
        this.headers.clear();
        Collections.addAll(this.headers, headers);
    }

    /**
     * Reset object's data collections
     */
    public void resetData() {
        if (this.data != null) {
            this.data.clear();
        }
    }

    /**
     * Add the data values. This would represent a single line in the perpetual data file.
     * 
     * @param values [IN] - Data values
     */
    public void addDataLine(String[] values) {
        assert(headers != null && headers.isEmpty());

        // Create data if it doesn't exist
        if (data == null) {
            data = new ArrayList<>();
        }

        // Iterate through the passed in values. The number of values should match the number
        // of header entries, which specify what the value represents (time, Kb/sec, etc.)
        Map<String, String> dataMap = new HashMap<>();
        for (String value : values) {
            for (String header : headers) {
                String toAdd = value;
                // For possibly very common values, use a static String reference
                if (value.equals(ZERO)) {
                    toAdd = ZERO;
                } else if (value.equals(NO_DATA)) {
                    toAdd = NO_DATA;
                }
                dataMap.put(header, toAdd);
            }
        }
        // Add the mapping to the data line structure
        data.add(dataMap);
    }

    /**
     * Return the data line values
     *
     * @return List of Map of String (metric name) to String (metric value)
     */
    public List<Map<String, String>> getDataLines() {
        return Collections.unmodifiableList(data);
    }
}
