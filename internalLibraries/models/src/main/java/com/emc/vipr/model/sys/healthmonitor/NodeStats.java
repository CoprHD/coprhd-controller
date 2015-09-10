/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.healthmonitor;

import com.emc.vipr.model.sys.healthmonitor.HealthMonitorConstants;
import com.emc.vipr.model.sys.healthmonitor.ProcModels.LoadAvgStats;
import com.emc.vipr.model.sys.healthmonitor.ProcModels.MemoryStats;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents node statistics
 */
@XmlRootElement(name = "node_stats")
public class NodeStats {

    private String nodeId;
    private String nodeName;
    private String ip;
    // proc/loadavg
    private LoadAvgStats loadAvgStats;
    // proc/meminfo
    private MemoryStats memoryStats;
    // df results
    private DataDiskStats dataDiskStats;
    private List<ServiceStats> serviceStatsList;
    private List<DiskStats> diskStatsList;

    // Default constructor for JAXB
    public NodeStats() {
        this.nodeId = HealthMonitorConstants.UNKNOWN;
        this.nodeName = HealthMonitorConstants.UNKNOWN;
    }

    public NodeStats(String nodeId,String nodeName, String ip, LoadAvgStats loadAvgStats,
                     MemoryStats memoryStats, DataDiskStats dataDiskStats,
                     List<ServiceStats> serviceStatsList,
                     List<DiskStats> diskStatsList) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.ip = ip;
        this.loadAvgStats = loadAvgStats;
        this.memoryStats = memoryStats;
        this.dataDiskStats = dataDiskStats;
        this.serviceStatsList = serviceStatsList;
        this.diskStatsList = diskStatsList;
    }

    @XmlElement(name = "node_id")
    public String getNodeId() {
        return nodeId;
    }

    @XmlElement(name = "node_name")
    public String getNodeName() {
        return nodeName;
    }

    @XmlElement(name = "load_avg")
    public LoadAvgStats getLoadAvgStats() {
        return loadAvgStats;
    }

    @XmlElement(name = "memory")
    public MemoryStats getMemoryStats() {
        return memoryStats;
    }

    @XmlElementWrapper(name = "service_stats_list")
    @XmlElement(name = "service_stats")
    public List<ServiceStats> getServiceStatsList() {
        if (serviceStatsList == null) {
            serviceStatsList = new ArrayList<ServiceStats>();
        }
        return serviceStatsList;
    }

    @XmlElementWrapper(name = "disk_io_stats_list")
    @XmlElement(name = "disk_io_stats")
    public List<DiskStats> getDiskStatsList() {
        if (diskStatsList == null) {
            diskStatsList = new ArrayList<DiskStats>();
        }
        return diskStatsList;
    }

    @XmlElement(name = "ip")
    public String getIp() {
        return ip;
    }

    @XmlElement(name = "data_disk_stats")
    public DataDiskStats getDataDiskStats() {
        return dataDiskStats;
    }

    public void setDataDiskStats(DataDiskStats dataDiskStats) {
        this.dataDiskStats = dataDiskStats;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setLoadAvgStats(LoadAvgStats loadAvgStats) {
        this.loadAvgStats = loadAvgStats;
    }

    public void setMemoryStats(MemoryStats memoryStats) {
        this.memoryStats = memoryStats;
    }

    public void setServiceStatsList(List<ServiceStats> serviceStatsList) {
        this.serviceStatsList = serviceStatsList;
    }

    public void setDiskStatsList(List<DiskStats> diskStatsList) {
        this.diskStatsList = diskStatsList;
    }
}
