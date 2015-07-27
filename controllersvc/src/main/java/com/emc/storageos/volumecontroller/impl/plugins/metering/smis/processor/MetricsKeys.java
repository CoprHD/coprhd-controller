/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor;

import com.emc.storageos.db.client.model.StringMap;

public enum MetricsKeys {
    /** The double value that is the port metric. 0-100% */
    portMetric,
    /** The double percent busy average for the current sample period. */
    avgPercentBusy, 
    /** The double percent busy ema which decays over many sample periods. */
    emaPercentBusy,
    /** The idleTicksValue at the previous sample. */
    idleTicksValue, 
    /** The cumulativeTicksValue at the previous sample. */
    cumTicksValue, 
    /** The kbytesTransferred value at the previous sample. */
    kbytesValue,
    /** The IOPs value at the previous sample. */
    iopsValue, 
    /** The integer initiator count using a port. */
    initiatorCount,
    /** The integer volume count using a port. */
    volumeCount,
    /** The time recorded on the array for the previous sample. */
    lastSampleTime, 
    /** The ViPR time the current average started. */
    avgStartTime, 
    /** The number of samples in the current sample period average. */
    avgCount,
    /** The computed average port bandwidth percent usage at an instance of time. Double, percent. */
    avgPortPercentBusy,    
    /** The computed average port cpu percent usage at an instance of time. Double, percent. */
    avgCpuPercentBusy,
    /** boolean to indicate one of port metric: volume count, initiator count, avgCpuPercentBusy, or avgPortPercentBusy exceeded ceiling value **/
    allocationDisqualified,
    /** Unmanaged volume count (computed from UnManagedExportMasks). */
    unmanagedVolumeCount,
    /** Unmanaged initiator count (computed from UnManagedExportMasks). */
    unmanagedInitiatorCount,
    /** The ViPR time this port/cpu was last processed. */
    lastProcessingTime;
    
    static public Long getLong(MetricsKeys key, StringMap map) {
        Long value = 0L;
        if (map.containsKey(key.name()) && !map.get(key.name()).equals("")) {
            value = Long.decode(map.get(key.name()));
        }
        return value;
    }
    
    static void putLong(MetricsKeys key, Long value, StringMap map) {
        map.put(key.name(), value.toString());
    }
    
    static public Integer getInteger(MetricsKeys key, StringMap map) {
        Integer value = 0;
        if (map.containsKey(key.name()) && !map.get(key.name()).equals("")) {
            value = Integer.decode(map.get(key.name()));
        }
        return value;
    }
    
    static void putInteger(MetricsKeys key, Integer value, StringMap map) {
        map.put(key.name(), value.toString());
    }
    
    static public Double getDouble(MetricsKeys key, StringMap map) {
        Double value = 0.0;
        if (map.containsKey(key.name()) && !map.get(key.name()).equals("")) {
            value = new Double(map.get(key.name()));
        }
        return value;
    }
    static public Double getDoubleOrNull(MetricsKeys key, StringMap map) {
        Double value = null;
        if (map.containsKey(key.name()) && !map.get(key.name()).equals("")) {
            value = new Double(map.get(key.name()));
        }
        return value;
    }
    static public void putDouble(MetricsKeys key, Double value, StringMap map) {
        map.put(key.name(), value.toString());
    }
    
    
    static public Boolean getBoolean(MetricsKeys key, StringMap map) {
        Boolean value = false;
        if (map.containsKey(key.name()) && !map.get(key.name()).equals("")) {
            value = Boolean.parseBoolean(map.get(key.name()));
        }
        return value;
    }
    
    static public void putBoolean(MetricsKeys key, Boolean value, StringMap map) {
        map.put(key.name(), value.toString());
    }
}
