/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.io.Serializable;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Process Monitor Metrics like Memory usage, CPU usage,..
 */
@XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
@XmlRootElement(name = "memoryUsage")
public class ProcessMonitorMetrics extends TimeSeriesSerializer.DataPoint implements
        Serializable {

    private static final String NEWLINE = "\n";
    private static final String SEMICOLON = "; ";

    /**
     * indicates the service, which corresponds to this memory usage details
     * APIService, CoordinatorService, DBservice..
     */
    private String _serviceName;
    /**
     * Total memory allocated by a JVM.
     */
    private long _totalMemory;
    /**
     * JVM free Memory.
     */
    private long _freeMemory;
    /**
     * Max memory which can be allocated by JVM.
     */
    private long _maxMemory;
    /**
     * Used memory (totalMmemory - freeMemory) within JVM.
     */
    private long _usedMemory;
    /**
     * # processors available within JVM.
     */
    private String _processors;

    /**
     * time at which, usage is collected
     */
    private long _timeCollected;

    private long _cpuTime;

    /**
     * Maximum memory used since service started
     */
    private long _maxUsedMemory;

    /**
     * Time at which JVM used maximum memory
     */
    private String _timeCollectedMaxUsedMemory;

    public void setTotalMemory(long totalMemory) {
        _totalMemory = totalMemory;
    }

    @Name("totalMemory")
    public long getTotalMemory() {
        return _totalMemory;
    }

    public void setFreeMemory(long freeMemory) {
        _freeMemory = freeMemory;
    }

    @Name("freeMemory")
    public long getFreeMemory() {
        return _freeMemory;
    }

    public void setMaxMemory(long maxMemory) {
        _maxMemory = maxMemory;
    }

    @Name("maxMemory")
    public long getMaxMemory() {
        return _maxMemory;
    }

    public void setMaxUsedMemory(long MaxUsedMemory) {
        _maxUsedMemory = MaxUsedMemory;
    }

    @Name("maxMemoryHistorical")
    public long getMaxUsedMemory() {
        return _maxUsedMemory;
    }

    public void setUsedMemory(long usedMemory) {
        _usedMemory = usedMemory;
    }

    @Name("usedMemory")
    public long getUsedMemory() {
        return _usedMemory;
    }

    public void setServiceName(String serviceName) {
        _serviceName = serviceName;
    }

    @Name("serviceName")
    public String getServiceName() {
        return _serviceName;
    }

    public void setProcessors(String processors) {
        _processors = processors;
    }

    @Name("processors")
    public String getProcessors() {
        return _processors;
    }

    public void setTimeCollected(long timeCollected) {
        _timeCollected = timeCollected;
    }

    public long getTimeCollected() {
        return _timeCollected;
    }

    public void setTimeCollectedMaxUsedMemory(String timeCollectedMaxUsedMemory) {
        _timeCollectedMaxUsedMemory = timeCollectedMaxUsedMemory;
    }

    public String getTimeCollectedMaxUsedMemory() {
        return _timeCollectedMaxUsedMemory;
    }

    public void setCpuTime(long cpuTime) {
        _cpuTime = cpuTime;
    }

    public long getCpuTime() {
        return _cpuTime;
    }

    public String toString() {
        StringBuffer logMessage = new StringBuffer();
        logMessage.append(NEWLINE).append("Memory Usage Metrics ").append(NEWLINE);
        logMessage.append("Total  Memory: ");
        logMessage.append(getTotalMemory()).append("MB");
        logMessage.append(SEMICOLON).append(NEWLINE);
        logMessage.append("Available Free Memory: ");
        logMessage.append(getFreeMemory()).append("MB");
        logMessage.append(SEMICOLON).append(NEWLINE);
        logMessage.append("Available Maximum Memory : ");
        logMessage.append(getMaxMemory()).append("MB");
        logMessage.append(SEMICOLON).append(NEWLINE);
        logMessage.append("Used Memory: ");
        logMessage.append(getUsedMemory()).append("MB");
        logMessage.append(SEMICOLON).append(NEWLINE);

        if (getMaxUsedMemory() > 0) {
            logMessage.append("Max used Memory : ").append(getMaxUsedMemory()).append("MB at ").append(getTimeCollectedMaxUsedMemory());
            logMessage.append(SEMICOLON).append(NEWLINE);
        }

        return logMessage.toString();

    }

}
