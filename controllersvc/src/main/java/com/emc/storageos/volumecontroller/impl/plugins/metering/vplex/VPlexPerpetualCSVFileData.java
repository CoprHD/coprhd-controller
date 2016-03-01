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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to hold data collected from a VPlex perpetual performance data file
 */
public class VPlexPerpetualCSVFileData {
    // Constants
    public static final String ZERO = "0";
    public static final String NO_DATA = "no data";
    public static final String TIME_UTC = "Time (UTC)";
    public static final String TIME = "Time";
    public static final String DIRECTOR_BUSY = "director.busy";
    public static final String DIRECTOR_FE_OPS = "director.fe-ops";
    public static final String FE_PORT_READ = "fe-prt.read";
    public static final String FE_PORT_WRITE = "fe-prt.write";
    public static final String FE_PORT_OPS = "fe-prt.ops";
    public static final String HEADER_KEY_DIRECTOR_BUSY = "director.busy (%)";
    public static final String HEADER_KEY_DIRECTOR_FE_OPS = "director.fe-ops (counts/s)";
    public static final String HEADER_KEY_TIME_UTC = "Time (UTC)";

    private static final Pattern FILENAME_PATTERN = Pattern.compile(".*?/([\\w\\-_]+)_PERPETUAL_vplex_sys_perf_mon.log");
    // Total number of lines in the file
    private final int totalLines;
    // Name of the director to which this file applies
    private String directorName;
    // Name of the file
    private String name;
    // Headers
    private List<String> headers;
    // Structure to hold the data. Each entry in the list is a data line that has a map of key-values representing the data points
    private List<Map<String, String>> data;
    // Mapping of Time Values to their numeric index in the 'data' list
    private Map<String, Integer> timeToDataIndex;

    public VPlexPerpetualCSVFileData(String name, int totalLines) {
        this.name = name;
        this.totalLines = totalLines;
        Matcher matcher = FILENAME_PATTERN.matcher(this.name);
        if (matcher.matches()) {
            this.directorName = matcher.group(1);
        }
    }

    /**
     * Total lines that are in the file
     * 
     * @return int
     */
    public int getTotalLines() {
        return totalLines;
    }

    /**
     * Returns the name of the director to which this file applies
     *
     * @return String name
     */
    public String getDirectorName() {
        return directorName;
    }

    /**
     * Return the name and path of the file
     * 
     * @return String name
     */
    public String getName() {
        return name;
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
    public void close() {
        if (data != null) {
            data.clear();
            data = null;
        }
        if (headers != null) {
            headers.clear();
            headers = null;
        }
        if (timeToDataIndex != null) {
            timeToDataIndex.clear();
            timeToDataIndex = null;
        }
    }

    /**
     * Add the data values. This would represent a single line in the perpetual data file.
     * 
     * @param values [IN] - Data values
     */
    public void addDataLine(String[] values) {
        assert (headers != null && !headers.isEmpty() && headers.size() == values.length);

        // Create data if it doesn't exist
        if (data == null) {
            data = new ArrayList<>(totalLines);
            timeToDataIndex = new HashMap<>();
        }

        // Iterate through the passed in values. The number of values should match the number
        // of header entries, which specify what the value represents (time, Kb/sec, etc.)
        Map<String, String> dataMap = new HashMap<>();
        int numHeaders = headers.size();
        int currentDataIndex = data.size();
        for (int index = 0; index < numHeaders; index++) {
            String header = headers.get(index);
            String value = values[index];
            // For possibly very common values, use a static String reference
            if (value.equals(ZERO)) {
                value = ZERO;
            } else if (value.equals(NO_DATA)) {
                value = NO_DATA;
            }
            dataMap.put(header, value);
            if (header.equals(TIME_UTC)) {
                timeToDataIndex.put(value, currentDataIndex);
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

    /**
     * For a given 'timeUTC' value, find its index in the data lines.
     * 
     * @param timeUTC [IN] - Time UTC value to look up
     * @return An index into the 'data' List if 'timeUTC' exists, otherwise 0.
     */
    public Integer getDataIndexForTime(Long timeUTC) {
        Integer result = timeToDataIndex.get(timeUTC.toString());
        return (result != null) ? result : 0;
    }

    @Override
    public String toString() {
        return String.format("VPlexPerpetualCSVFileData{name='%s', totalLines=%d}", name, totalLines);
    }
}
