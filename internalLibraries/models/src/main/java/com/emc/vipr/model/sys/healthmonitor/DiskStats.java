/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.healthmonitor;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents node disk statistics
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "disk_io_stats")
public class DiskStats {

    private String diskId;
    // number of sectors read per second
    private double sectorsReadPerSec;
    // number of sectors written per second
    private double sectorsWritePerSec;

    // number of reads issued per second
    private double readPerSec;
    // number of writes completed per second
    private double writePerSec;
    // Average wait
    private double avgWait;
    // average disk service time
    private double avgSvcTime;
    // Utilization at disk(percent)
    private double utilPerc;

    // Non-xml variables
    // number of sectors read
    // Note: Change to BigInteger if more precision is required
    private long sectorsRead;
    // number of sectors written
    // Note: Change to BigInteger if more precision is required
    private long sectorsWrite;
    // number of reads issued
    private long numberOfReads;
    // number of writes completed
    private long numberOfWrites;
    // Time in queue + service for read
    private long readTicks;
    // Time in queue + service for write
    private long writeTicks;
    // number of ms spent doing I/Os
    private long numberOfIOInMs;
    
    // Default constructor for JAXB
    public DiskStats() {
        this.diskId = HealthMonitorConstants.UNKNOWN;
    }

    // These values are calculated based on the values read from /proc/diskstats
    public DiskStats(String diskId, double readPerSec, double sectorsReadPerSec,
                     double writePerSec, double sectorsWritePerSec, double avgWait,
                     double avgSvcTime, double utilPerc) {
        this.diskId = diskId;
        this.readPerSec = readPerSec;
        this.sectorsReadPerSec = sectorsReadPerSec;
        this.writePerSec = writePerSec;
        this.sectorsWritePerSec = sectorsWritePerSec;
        this.avgWait = avgWait;
        this.avgSvcTime = avgSvcTime;
        this.utilPerc = utilPerc;
    }

    // These values are directly read from /proc/diskstats
    public DiskStats(String diskId, long numberOfReads, long sectorsRead,
                     long readTicks, long numberOfWrites, long sectorsWrite,
                     long writeTicks, long numberOfIOInMs) {
        this.diskId = diskId;
        this.numberOfReads = numberOfReads;
        this.sectorsRead = sectorsRead;
        this.readTicks = readTicks;
        this.numberOfWrites = numberOfWrites;
        this.sectorsWrite = sectorsWrite;
        this.writeTicks = writeTicks;
        this.numberOfIOInMs = numberOfIOInMs;
    }

    @XmlElement(name = "disk_id")
    public String getDiskId() {
        return diskId;
    }

    @XmlElement(name = "sectors_read_per_second")
    public double getSectorsReadPerSec() {
        return sectorsReadPerSec;
    }

    @XmlElement(name = "sectors_write_per_second")
    public double getSectorsWritePerSec() {
        return sectorsWritePerSec;
    }

    @XmlElement(name = "reads_per_second")
    public double getReadPerSec() {
        return readPerSec;
    }

    @XmlElement(name = "writes_per_second")
    public double getWritePerSec() {
        return writePerSec;
    }

    public long getNumberOfIOInMs() {
        return numberOfIOInMs;
    }

    public long getSectorsRead() {
        return sectorsRead;
    }

    public long getSectorsWrite() {
        return sectorsWrite;
    }

    public long getNumberOfReads() {
        return numberOfReads;
    }

    public long getNumberOfWrites() {
        return numberOfWrites;
    }

    public long getReadTicks() {
        return readTicks;
    }

    public long getWriteTicks() {
        return writeTicks;
    }

    @XmlElement(name = "avg_wait")
    public double getAvgWait() {
        return avgWait;
    }

    @XmlElement(name = "avg_svctime")
    public double getAvgSvcTime() {
        return avgSvcTime;
    }

    @XmlElement(name = "util_perc")
    public double getUtilPerc() {
        return utilPerc;
    }

    public void setDiskId(String diskId) {
        this.diskId = diskId;
    }

    public void setSectorsReadPerSec(double sectorsReadPerSec) {
        this.sectorsReadPerSec = sectorsReadPerSec;
    }

    public void setSectorsWritePerSec(double sectorsWritePerSec) {
        this.sectorsWritePerSec = sectorsWritePerSec;
    }

    public void setReadPerSec(double readPerSec) {
        this.readPerSec = readPerSec;
    }

    public void setWritePerSec(double writePerSec) {
        this.writePerSec = writePerSec;
    }

    public void setAvgWait(double avgWait) {
        this.avgWait = avgWait;
    }

    public void setAvgSvcTime(double avgSvcTime) {
        this.avgSvcTime = avgSvcTime;
    }

    public void setUtilPerc(double utilPerc) {
        this.utilPerc = utilPerc;
    }
}