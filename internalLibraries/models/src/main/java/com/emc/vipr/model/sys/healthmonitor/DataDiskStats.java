/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.healthmonitor;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Stats (available, used) for data, root.
 */
@XmlRootElement(name = "data_disk_stats")
public class DataDiskStats {

    private long rootUsedKB;
    private long rootAvailKB;
    private long dataUsedKB;
    private long dataAvailKB;

    // Default constructor for JAXB
    public DataDiskStats() {
    }

    public DataDiskStats(long rootUsedKB, long rootAvailKB, long dataUsedKB,
            long dataAvailKB) {
        this.rootUsedKB = rootUsedKB;
        this.rootAvailKB = rootAvailKB;
        this.dataUsedKB = dataUsedKB;
        this.dataAvailKB = dataAvailKB;
    }

    @XmlElement(name = "root_used_kb")
    public long getRootUsedKB() {
        return rootUsedKB;
    }

    @XmlElement(name = "root_available_kb")
    public long getRootAvailKB() {
        return rootAvailKB;
    }

    @XmlElement(name = "data_used_kb")
    public long getDataUsedKB() {
        return dataUsedKB;
    }

    @XmlElement(name = "data_available_kb")
    public long getDataAvailKB() {
        return dataAvailKB;
    }

    public void setRootUsedKB(long rootUsedKB) {
        this.rootUsedKB = rootUsedKB;
    }

    public void setRootAvailKB(long rootAvailKB) {
        this.rootAvailKB = rootAvailKB;
    }

    public void setDataUsedKB(long dataUsedKB) {
        this.dataUsedKB = dataUsedKB;
    }

    public void setDataAvailKB(long dataAvailKB) {
        this.dataAvailKB = dataAvailKB;
    }
}
