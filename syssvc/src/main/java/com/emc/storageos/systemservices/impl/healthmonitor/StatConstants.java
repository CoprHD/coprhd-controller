/*
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
package com.emc.storageos.systemservices.impl.healthmonitor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Class that holds all constants that are used by health monitor service.
 */
public interface StatConstants {

    // various directory and files names for the indicators we wish to collect stats for.
    public static final String PROC_DIR = "/proc";
    public static final String COMM_FILE = "/proc/%s/comm";
    public static final String CMDLINE_FILE = "/proc/%s/cmdline";
    public static final String STAT_FILE = "/proc/%s/stat";
    public static final String STATM_FILE = "/proc/%s/statm";
    public static final String FD_DIR = "/proc/%s/fd";
    public static final String SELF_DIR = "self";
    public static final String LOAD_AVG = "/proc/loadavg";
    public static final String MEM_INFO = "/proc/meminfo";
    public static final String DISK_STATS = "/proc/diskstats";
    public static final String PROC_STAT = "/proc/stat";
    public static final String CPU_INFO = "/proc/cpuinfo";
    public static final String DF_COMMAND = "/bin/df";
    public static final long DF_COMMAND_TIMEOUT = 120000; //2 min
    public static final long HZ = 100;
    // the directories of services for which we wish to capture stats.
    public static final Set<String> ACCEPTABLE_PID_COMMAND_PREFIXES =
            Collections.unmodifiableSet(new HashSet<String>() {{
                add("/opt/storageos/bin/");
            }});
    // These are the disks that we wish to gather stats for from /proc/diskstats.
    public static final Set<String> ACCEPTABLE_DISK_IDS =
            Collections.unmodifiableSet(new HashSet<String>() {{
                add("sda");
                add("sdb");
                add("sdc");
            }});
    // String split values for regular express splitting.
    public static final String NULL_VALUES = "\\x00";
    public static final String SPACE_VALUE = "\\s+";
    public static final String MONITOR_SVCNAME = "monitor";
    public static final String COVERAGE_SVCNAME_SUFFIX = "coverage";
    public static final String UNKNOWN = "unknown";
    
    // for converting capacity unit
    public static int CAPACITY_CONVERSION_VALUE = 1024;
    
    public static int  STAT_PID = 0;
    public static int  STAT_NUM_THREADS = 19;
    public static int  STAT_STARTTIME = 21;
    public static int  STAT_VSIZE = 22;
    public static int  STAT_RSS = 23;
    
    public static int DEFAULT_PAGE_SIZE = 4096;
    
}
