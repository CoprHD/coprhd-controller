/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.vipr.model.sys.healthmonitor;

import javax.xml.bind.annotation.XmlElement;

/**
 * Classes used by ProcStats class.
 */
public class ProcModels {

    public static class MemoryStats {
        public MemoryStats() {
        }

        public MemoryStats(long memTotal, long memFree, long memBuffers, long memCached) {
            this.memTotal = memTotal;
            this.memFree = memFree;
            this.memBuffers = memBuffers;
            this.memCached = memCached;
        }

        private long memTotal;
        private long memFree;
        private long memBuffers;
        private long memCached;

        @XmlElement(name = "total_memory_in_kb")
        public long getMemTotal() {
            return memTotal;
        }

        @XmlElement(name = "free_memory_in_kb")
        public long getMemFree() {
            return memFree;
        }

        @XmlElement(name = "memory_buffers_in_kb")
        public long getMemBuffers() {
            return memBuffers;
        }
        
        @XmlElement(name = "memory_cached_in_kb")
        public long getMemCached() {
            return memCached;
        }
       

        public void setMemTotal(long memTotal) {
            this.memTotal = memTotal;
        }

        public void setMemFree(long memFree) {
            this.memFree = memFree;
        }

        public void setMemBuffers(long memBuffers) {
            this.memBuffers = memBuffers;
        }
        public void setMemCached(long memCached){
        	this.memCached = memCached;
        }
    }

    public static class LoadAvgStats {

        public LoadAvgStats() {
        }

        public LoadAvgStats(double loadAvgTasksPastMinute,
                            double loadAvgTasksPastFiveMinutes,
                            double loadAvgTasksPastFifteenMinutes) {
            this.loadAvgTasksPastMinute = loadAvgTasksPastMinute;
            this.loadAvgTasksPastFiveMinutes = loadAvgTasksPastFiveMinutes;
            this.loadAvgTasksPastFifteenMinutes = loadAvgTasksPastFifteenMinutes;
        }

        private double loadAvgTasksPastMinute;
        private double loadAvgTasksPastFiveMinutes;
        private double loadAvgTasksPastFifteenMinutes;

        @XmlElement(name = "load_avg_last_one_minute")
        public double getLoadAvgTasksPastMinute() {
            return loadAvgTasksPastMinute;
        }

        @XmlElement(name = "load_avg_last_five_minutes")
        public double getLoadAvgTasksPastFiveMinutes() {
            return loadAvgTasksPastFiveMinutes;
        }

        @XmlElement(name = "load_avg_last_fifteen_minutes")
        public double getLoadAvgTasksPastFifteenMinutes() {
            return loadAvgTasksPastFifteenMinutes;
        }

        public void setLoadAvgTasksPastMinute(double loadAvgTasksPastMinute) {
            this.loadAvgTasksPastMinute = loadAvgTasksPastMinute;
        }

        public void setLoadAvgTasksPastFiveMinutes(double loadAvgTasksPastFiveMinutes) {
            this.loadAvgTasksPastFiveMinutes = loadAvgTasksPastFiveMinutes;
        }

        public void setLoadAvgTasksPastFifteenMinutes(
                double loadAvgTasksPastFifteenMinutes) {
            this.loadAvgTasksPastFifteenMinutes = loadAvgTasksPastFifteenMinutes;
        }
    }

    public static class ProcessStatus {

        public ProcessStatus() {
        }

        public ProcessStatus(long upTime, long numberOfThreads,
                             long startTime, int pid,
                             long residentMem, long virtualMemSizeInBytes) {
            this.upTime = upTime;
            this.startTime = startTime;
            this.pid = pid;
            this.residentMem = residentMem;
            this.numberOfThreads = numberOfThreads;
            this.virtualMemSizeInBytes = virtualMemSizeInBytes;
        }

        private long upTime;
        private long startTime;

        private int pid;
        private long residentMem;
        private long numberOfThreads;
        private long virtualMemSizeInBytes;

        @XmlElement(name = "total_uptime_seconds")
        public long getUpTime() {
            return upTime;
        }

        @XmlElement(name = "start_time_seconds")
        public long getStartTime() {
            return startTime;
        }

        @XmlElement(name = "resident_mem_size_in_bytes")
        public long getResidentMem() {
            return residentMem;
        }

        @XmlElement(name = "pid")
        public int getPid() {
            return pid;
        }

        @XmlElement(name = "active_threads_ctr")
        public long getNumberOfThreads() {
            return numberOfThreads;
        }

        @XmlElement(name = "virtual_mem_size_in_bytes")
        public long getVirtualMemSizeInBytes() {
            return virtualMemSizeInBytes;
        }

        public void setUpTime(long upTime) {
            this.upTime = upTime;
        }
        
        public void setPid(int pid) {
            this.pid = pid;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }

        public void setResidentMem(long residentMem) {
            this.residentMem = residentMem;
        }

        public void setNumberOfThreads(long numberOfThreads) {
            this.numberOfThreads = numberOfThreads;
        }

        public void setVirtualMemSizeInBytes(long virtualMemSizeInBytes) {
            this.virtualMemSizeInBytes = virtualMemSizeInBytes;
        }

    }
}
