/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.healthmonitor;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.systemservices.exceptions.SyssvcException;
import com.emc.storageos.systemservices.impl.healthmonitor.models.CPUStats;
import com.emc.vipr.model.sys.healthmonitor.DiskStats;
import com.emc.vipr.model.sys.healthmonitor.ServiceStats;
import com.google.common.primitives.UnsignedLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that calculates node and its services statistics.
 * Node stats are retrieved from
 * /proc/meminfo: contains information about memory usage of the system
 * /proc/diskstats: contains disk I/O statistics for each disk device.
 * /proc/loadavg: contains information about load average numbers
 * <p/>
 * Service stats are retrieved from /proc/{pid}/comm: the command that invoked this process /proc/{pid}/cmdline: contains command line for
 * the process. Gives service name. /proc/{pid}/stat: contains status information about the process /proc/{pid}/statm: contains information
 * about memory usage /proc/{pid}/fd: contains entries for files that this process has opened
 */
public class NodeStatsExtractor implements StatConstants {

    private static final Logger _log = LoggerFactory.getLogger(NodeStatsExtractor.class);

    /**
     * Method that returns all service statistics in the order of availableservices list.
     */
    public static List<ServiceStats> getServiceStats(
            List<String> availableServices) {
        List<ServiceStats> serviceStatsList = new ArrayList<ServiceStats>();
        Map<String, ServiceStats> tempServiceStatsMap = new HashMap<String,
                ServiceStats>();
        File procDir = new File(PROC_DIR);
        File[] procFiles = procDir.listFiles();
        // Get required data from the relevant
        // /proc/[pid]/(comm,cmdline,stat,statm,fd) files.
        for (File procFile : procFiles) {
            String pid = procFile.getName().trim();
            if (pid.equalsIgnoreCase(SELF_DIR)) {
                continue;
            }
            try {
                String serviceName = ProcStats.getServiceName(pid);

                if (!serviceName.isEmpty() && !MONITOR_SVCNAME
                        .equals(serviceName)) {
                    String commandFile = null;
                    try {
                        commandFile = FileReadUtil.readFirstLine(String.format
                                (COMM_FILE, pid));
                    } catch (Exception e) {
                        _log.error("Error occurred while reading command file: {}", e);
                    }
                    _log.info("Get serviceStats for service {}", serviceName);
                    if (serviceName.contains(COVERAGE_SVCNAME_SUFFIX)) {
                        serviceName = serviceName.split("-")[0];
                    }

                    ServiceStats serviceStats = new ServiceStats(serviceName, commandFile,
                            ProcStats.getFileDescriptorCntrs(pid),
                            ProcStats.getProcStats(pid));
                    tempServiceStatsMap.put(serviceName, serviceStats);
                }
            } catch (SyssvcException ex) {
                if (ex.getServiceCode() == ServiceCode.SYS_INTERNAL_SERVICE_NAME_NOT_FOUND) {
                    continue;
                }
                _log.debug("Syssvc Exception: {}",ex);
            } catch (Exception e) {
                _log.debug("Internal error: {}", e);
            }
        }

        // Ordering service stats
        if (availableServices == null || availableServices.isEmpty()) {
            _log.warn("List of available services is null or empty: {}",
                    availableServices);
            return new ArrayList<ServiceStats>(tempServiceStatsMap.values());
        } else {
            for (String svcName : availableServices) {
                if (tempServiceStatsMap.containsKey(svcName)) {
                    serviceStatsList.add(tempServiceStatsMap.remove(svcName));
                } else {
                    serviceStatsList.add(new ServiceStats(svcName));
                }
            }
            return serviceStatsList;
        }
    }

    /**
     * Get /proc/diskstats data. If "interval" value is > 0 this will get stats again
     * after sleep for interval seconds.
     * 
     * @param intervalInSecs interval value in seconds
     * @return List of disk stats
     */
    public static List<DiskStats> getDiskStats(int intervalInSecs) {
        // Getting disk/cpu stats
        try {
            Map<String, DiskStats> oldDiskDataMap = ProcStats.getDiskStats();
            CPUStats oldCPUStats = ProcStats.getCPUStats();
            // sleep if needed
            Map<String, DiskStats> newDiskDataMap = null;
            CPUStats newCPUStats = null;
            if (intervalInSecs > 0) {
                try {
                    Thread.sleep(intervalInSecs * 1000);
                } catch (InterruptedException e) {
                    _log.error("Thread Sleep InterrupdtedExcepion: {}", e);
                    return null;
                }
                // Getting disk/cpu stats after sleep
                newDiskDataMap = ProcStats.getDiskStats();
                newCPUStats = ProcStats.getCPUStats();
            }
            // perform method that will actually perform the calucations.
            return getDifferentialDiskStats(oldDiskDataMap, newDiskDataMap, getCPUTimeDeltaMS(oldCPUStats,
                    newCPUStats));
        } catch (Exception e) {
            _log.error("Error occurred while getting disk stats: {}", e);
        }
        return null;
    }

    /**
     * This methods does the work of calculating the diskstats.
     * calculated values: read/sec, write/sec, read_sec/sec, write_sec/sec
     * avg_wait, svc_time, %util.
     * 
     * @param oldDiskDataMap disk data values collected during initial run
     * @param newDiskDataMap disk data values collected after 2s
     */
    private static List<DiskStats> getDifferentialDiskStats(
            Map<String, DiskStats> oldDiskDataMap,
            Map<String, DiskStats> newDiskDataMap, double deltaMS) {
        List<DiskStats> diskStatsList = new ArrayList<DiskStats>();
        // iterate though initial map as driver for getting data from the
        // compare map and determining the average per second;
        DecimalFormat decimalFormat = new DecimalFormat("#####0.00");
        for (Map.Entry<String, DiskStats> entry : oldDiskDataMap.entrySet()) {
            String diskId = entry.getKey();
            DiskStats oldStats = entry.getValue();
            DiskStats newStats = null;
            if (newDiskDataMap != null && newDiskDataMap.get(diskId) != null) {
                newStats = newDiskDataMap.get(diskId);
            }
            DiskStats diffStats = getDifference(oldStats, newStats);

            // number of requests
            double numOfIOs = diffStats.getNumberOfReads()
                    + diffStats.getNumberOfWrites();

            // await
            double wait = numOfIOs > 0 ?
                    (diffStats.getReadTicks() + diffStats.getWriteTicks()) / numOfIOs : 0;

            // svctm
            double svcTime = numOfIOs > 0 ? diffStats.getNumberOfIOInMs() / numOfIOs : 0;

            // %util
            double busy = 0;
            if (deltaMS > 0) {
                busy = 100 * diffStats.getNumberOfIOInMs() / deltaMS;
                busy = busy > 100 ? 100 : busy;
            }

            diskStatsList.add(new DiskStats(diskId,
                    Double.parseDouble(decimalFormat.format(getRate
                            (diffStats.getNumberOfReads(), deltaMS))),
                    Double.parseDouble(decimalFormat.format(getRate(diffStats
                            .getSectorsRead(), deltaMS))),
                    Double.parseDouble(decimalFormat.format(getRate
                            (diffStats.getNumberOfWrites(), deltaMS))),
                    Double.parseDouble(decimalFormat.format(getRate(diffStats
                            .getSectorsWrite(), deltaMS))),
                    Double.parseDouble(decimalFormat.format(wait)),
                    Double.parseDouble(decimalFormat.format(svcTime)),
                    Double.parseDouble(decimalFormat.format(busy))));
        }
        return diskStatsList;
    }

    /**
     * Calculates the difference between newStats and oldStats values and returns them
     * as new disk stats object.
     * If interval is 0, newStats will be null. in this case it just returns
     * oldStats
     */
    private static DiskStats getDifference(DiskStats oldStats, DiskStats newStats) {
        if (newStats == null) {
            return oldStats;
        } else {
            return new DiskStats(oldStats.getDiskId(),
                    diff(newStats.getNumberOfReads(), oldStats.getNumberOfReads()),
                    diff(newStats.getSectorsRead(), oldStats.getSectorsRead()),
                    diff(newStats.getReadTicks(), oldStats.getReadTicks()),
                    diff(newStats.getNumberOfWrites(), oldStats.getNumberOfWrites()),
                    diff(newStats.getSectorsWrite(), oldStats.getSectorsWrite()),
                    diff(newStats.getWriteTicks(), oldStats.getWriteTicks()),
                    diff(newStats.getNumberOfIOInMs(), oldStats.getNumberOfIOInMs()));
        }
    }

    private static long diff(long newVal, long oldVal) {
        if (newVal >= oldVal) {
            return newVal - oldVal;
        } else {
            // If values are wrapped, adding max val to it and subtracting
            _log.debug("New value is wrapped. newVal: {} oldVal: {}", newVal, oldVal);
            return newVal + (Long.MAX_VALUE - oldVal);
        }
    }

    private static UnsignedLong diff(UnsignedLong newVal, UnsignedLong oldVal) {
        if (newVal.compareTo(oldVal) >= 0) {
            return newVal.minus(oldVal);
        } else {
            // If values are wrapped, adding max val to it and subtracting
            _log.debug("New value is wrapped. newVal: {} oldVal: {}", newVal, oldVal);
            return UnsignedLong.MAX_VALUE.minus(oldVal).plus(newVal);
        }
    }

    /**
     * Calculates and returns the value/sec.
     */
    protected static double getRate(long val, double deltaMS) {
        _log.debug("Calculating per sec for val: {} with delta: {}", val, deltaMS);
        return deltaMS > 0 ? (1000 * val / deltaMS) : 0;
    }

    /**
     * Returns the cpu time spent in MS. If interval is > 0 delta value is returned.
     * This is used to calculate per sec values.
     */
    protected static double getCPUTimeDeltaMS(CPUStats oldCPUStats, CPUStats
            newCPUStats) {
        if (oldCPUStats != null) {
            double statsDiff;
            UnsignedLong oldTotal = oldCPUStats.getUserMode().plus(
                    oldCPUStats.getSystemMode()
                            .plus(oldCPUStats.getIdle().plus(oldCPUStats.getIowait())));
            if (newCPUStats != null) {
                UnsignedLong newTotal = newCPUStats.getUserMode().plus(
                        newCPUStats.getSystemMode().plus(
                                newCPUStats.getIdle().plus(newCPUStats.getIowait())));
                statsDiff = newTotal.minus(oldTotal).doubleValue();
            } else {
                statsDiff = oldTotal.doubleValue();
            }
            return ProcStats.getCPUCount() > 0 ? 1000.0 * statsDiff / ProcStats
                    .getCPUCount() / HZ : 0;
        } else {
            return 0;
        }
    }
}
