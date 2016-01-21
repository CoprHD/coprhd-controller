/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.healthmonitor;

import com.emc.storageos.systemservices.exceptions.SyssvcException;
import com.emc.storageos.systemservices.exceptions.SyssvcInternalException;
import com.emc.storageos.systemservices.impl.healthmonitor.models.CPUStats;
import com.emc.storageos.services.util.Exec;
import com.emc.vipr.model.sys.healthmonitor.DataDiskStats;
import com.emc.vipr.model.sys.healthmonitor.DiskStats;
import com.emc.vipr.model.sys.healthmonitor.ProcModels;
import com.google.common.primitives.UnsignedLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides static methods to retrieve info from /proc..
 */
public class ProcStats extends ProcModels implements StatConstants {
    private static final Logger _log = LoggerFactory.getLogger(ProcStats.class);

    /**
     * Retrieves CPU stats from /proc/stat file.
     */
    public static CPUStats getCPUStats() throws IOException, SyssvcInternalException {
        String[] fileData = FileReadUtil.readLines(PROC_STAT);
        for (String line : fileData) {
            if (line != null && line.startsWith("cpu")) {
                String[] stats = line.trim().split(SPACE_VALUE);
                UnsignedLong systemMode = UnsignedLong.valueOf(stats[3]);
                if (stats.length > 7) {
                    systemMode = systemMode.plus(UnsignedLong.valueOf(stats[6])
                            .plus(UnsignedLong.valueOf(stats[7])));
                }
                return new CPUStats(UnsignedLong.valueOf(stats[1]).plus
                        (UnsignedLong.valueOf(stats[2])), systemMode,
                        UnsignedLong.valueOf(stats[4]),
                        UnsignedLong.valueOf(stats[5]));
            }
        }

        throw SyssvcException.syssvcExceptions.syssvcInternalError("CPU stats not found.");
    }

    /**
     * Returns number of CPUs
     */
    public static int getCPUCount() {
        try {
            String[] fileData = FileReadUtil.readLines(CPU_INFO);
            int ncpu = 0;
            for (String line : fileData) {
                if (line != null && line.startsWith("processor")) {
                    ncpu++;
                }
            }
            _log.info("Number of processors: {}", ncpu);
            return ncpu;
        } catch (Exception e) {
            _log.error("Error occurred while getting CPU count: {}", e);
        }
        return 0;
    }

    /**
     * Returns CPU frequence
     */
    public static float getCPUFrequence() {
        float cpuFreq = 0;
        try {
            String[] fileData = FileReadUtil.readLines(CPU_INFO);
            for (String line : fileData) {
                if (line != null && line.contains("MHz")) {
                    String[] cpuFreqData = line.split(":");
                    cpuFreq += Float.valueOf(cpuFreqData[1]);
                }
            }
        } catch (Exception e) {
            _log.error("Error occurred while getting CPU frequence: {}", e);
        }
        return cpuFreq;
    }

    /**
     * Returns the number of files in the File Descriptors from /proc/[pid]/fd
     * directory. Default to 0 if null.
     */
    public static int getFileDescriptorCntrs(String pid) {
        File file = new File(String.format(FD_DIR, pid));
        if (file.exists()) {
            File[] files = file.listFiles();
            return files != null ? files.length : 0;
        } else {
            return 0;
        }
    }

    /**
     * Returns service (i.e. syssvc, dbsvc, coordinatorsvc,
     * etc) name from /proc/{pid}/cmdline file and adds it to the passed
     * in serviceStats object. Service name should be prefixed with
     * ACCEPTABLE_PID_COMMAND_PREFIXES.
     * 
     * @param pid process id from which service stats is extracted
     */
    public static String getServiceName(String pid) throws IOException,
            SyssvcInternalException {
        // validate that CMDLINE file exists.
        File cmdLineFile = new File(String.format(CMDLINE_FILE, pid));
        if (cmdLineFile.exists()) {
            // The /proc/[pid]/cmdline file exists and is not the /proc/self/
            // directory,
            // continue trying to get the service type.
            String cmdLineString = FileReadUtil.readFirstLine(String.format
                    (CMDLINE_FILE, pid));
            // if the /proc/[pid]/cmdline contains an acceptable command from the
            // list, we
            // should process this pid.
            // split the string delimited by null.
            String[] splitCmdLineString = cmdLineString.split(NULL_VALUES);
            for (String acceptablePrefix : ACCEPTABLE_PID_COMMAND_PREFIXES) {
                if (splitCmdLineString[0].startsWith(acceptablePrefix)) {
                    return splitCmdLineString[0].substring
                            (acceptablePrefix.length());
                }
            }
        }

        throw SyssvcException.syssvcExceptions.serviceNameNotFoundException(pid);
    }

    /**
     * Reads the /proc/diskstats data and builds a map using the desired counters.
     * 
     * @return Map with disk id as key.
     */
    public static Map<String, DiskStats> getDiskStats() throws IOException,
            SyssvcInternalException {
        Map<String, DiskStats> diskStatsMap = new HashMap<String, DiskStats>();
        String[] fileData = FileReadUtil.readLines(DISK_STATS);
        for (String line : fileData) {
            String[] diskStatArray = line.trim().split(SPACE_VALUE);

            if (!ACCEPTABLE_DISK_IDS.contains(diskStatArray[2])) {
                continue;
            }

            // verify if disk stats array has 13 elements
            if (diskStatArray.length < 13) {
                throw SyssvcException.syssvcExceptions.syssvcInternalError(
                        "Disk stats file is invalid.");
            }

            DiskStats diskStats = new DiskStats(diskStatArray[2],
                    Long.parseLong(diskStatArray[3]),
                    Long.parseLong(diskStatArray[5]),
                    Long.parseLong(diskStatArray[6]),
                    Long.parseLong(diskStatArray[7]),
                    Long.parseLong(diskStatArray[9]),
                    Long.parseLong(diskStatArray[10]),
                    Long.parseLong(diskStatArray[12]));
            diskStatsMap.put(diskStats.getDiskId(), diskStats);
        }
        return diskStatsMap;
    }

    /**
     * Loads the memory information from /proc/meminfo. We are only concerned with the
     * first three rows.
     */
    public static MemoryStats getMemoryStats() {
        try {
            String[] fileData = FileReadUtil.readLines(MEM_INFO);
            Matcher matcher;
            Pattern pattern = Pattern.compile("[a-zA-Z]*:[ ]*([\\d]*)[ ]{1}kB");
            matcher = pattern.matcher(fileData[0]);
            String memTotal = matcher.find() ? matcher.group(1) : null;
            matcher = pattern.matcher(fileData[1]);
            String memFree = matcher.find() ? matcher.group(1) : null;
            matcher = pattern.matcher(fileData[2]);
            String memBuffers = matcher.find() ? matcher.group(1) : null;
            matcher = pattern.matcher(fileData[3]);
            String memCached = matcher.find() ? matcher.group(1) : null;
            return new MemoryStats(Long.parseLong(memTotal),
                    Long.parseLong(memFree),
                    Long.parseLong(memBuffers),
                    Long.parseLong(memCached));
        } catch (Exception e) {
            _log.error("Error occurred while getting node memory stats: {}", e);
        }
        return null;
    }

    /**
     * Populates node's load average data information from /proc/loadavg.
     */
    public static LoadAvgStats getLoadAvgStats() {
        try {
            String loadAvgDataLine = FileReadUtil.readFirstLine(LOAD_AVG);
            // split string into string[] delimited by a space.
            String[] loadAvgData = loadAvgDataLine.split(SPACE_VALUE);
            return new LoadAvgStats(Double.parseDouble(loadAvgData[0]),
                    Double.parseDouble
                            (loadAvgData[1]), Double.parseDouble
                            (loadAvgData[2]));
        } catch (Exception e) {
            _log.error("Error occurred while getting load avg stats: {}", e);
        }
        return null;
    }

    /**
     * Loads stats from /proc/[pid]/stat and /proc/[pid]/statm files
     */
    public static ProcessStatus getProcStats(String pid) {

        // using a space as the delimeter.
        String data;

        // get the /proc/[pid]/stat as a string and them split into string array
        // using a space as the delimeter.
        try {
            data = FileReadUtil.readFirstLine(String.format(STAT_FILE, pid));
            String[] procStatData = data.split(SPACE_VALUE);
            long startTimeSecs = UnsignedLong.valueOf(procStatData[STAT_STARTTIME]).dividedBy
                    (UnsignedLong.fromLongBits(HZ)).longValue();
            long currTimeSecs = new Date().getTime() / 1000;
            long upTimeSecs = currTimeSecs - (getBootTime() + startTimeSecs);
            return new ProcessStatus(upTimeSecs,
                    Long.parseLong(procStatData[STAT_NUM_THREADS]),
                    startTimeSecs,
                    Integer.parseInt(procStatData[STAT_PID]),
                    Long.parseLong(procStatData[STAT_RSS]) * getPageSize(),
                    Long.parseLong(procStatData[STAT_VSIZE]));
        } catch (Exception e) {
            _log.error("Error occurred while getting service stats from /stats file: {}", e);
        }

        return null;
    }

    /**
     * Returns boot time in seconds since the Epoch
     */
    private static long getBootTime() throws IOException {
        String[] fileData = FileReadUtil.readLines(PROC_STAT);
        for (String line : fileData) {
            if (line != null && line.startsWith("btime")) {
                String[] bootLine = line.trim().split(SPACE_VALUE);
                if (bootLine.length > 1) {
                    _log.info("Boot time in seconds: {}", bootLine[1]);
                    return Long.parseLong(bootLine[1]);
                }
            }
        }
        return 0;
    }

    /**
     * Gets used, available size for data and root with the help of df command.
     */
    public static DataDiskStats getDataDiskStats() {
        final String[] cmd = { DF_COMMAND };
        Exec.Result result = Exec.sudo(DF_COMMAND_TIMEOUT, cmd);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            _log.error("getDataDiskStats() is unsuccessful. Command exit value is: {}",
                    result.getExitValue());
            return null;
        }
        _log.info("df result: {}", result.getStdOutput());
        return parseDFResults(result.getStdOutput());
    }

    /**
     * Parses the input string and returns data disk stats object.
     */
    private static DataDiskStats parseDFResults(String dfResult) {
        String[] lines = dfResult.split("\n");
        DataDiskStats dataDiskStats = new DataDiskStats();
        for (String line : lines) {
            String[] v = line.split(SPACE_VALUE);
            if (v != null && v.length > 5) {
                if ("/".equals(v[5].trim())) {
                    dataDiskStats.setRootUsedKB(Long.parseLong(v[2]));
                    dataDiskStats.setRootAvailKB(Long.parseLong(v[3]));
                } else if ("/data".equals(v[5].trim())) {
                    dataDiskStats.setDataUsedKB(Long.parseLong(v[2]));
                    dataDiskStats.setDataAvailKB(Long.parseLong(v[3]));
                }
            }
        }
        return dataDiskStats;
    }

    /**
     * Returns default page size of 4k for now, need to be enhanced to return actual page size.
     * 
     * @return
     */
    private static int getPageSize() {
        return DEFAULT_PAGE_SIZE;
    }
}
