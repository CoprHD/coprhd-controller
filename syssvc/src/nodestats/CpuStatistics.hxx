/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
#ifndef _CpuStatistics_HXX
#define _CpuStatistics_HXX

#include <math.h>
#include "ProcStat.hxx"
#include "ProcLoadavg.hxx"

//
// Gather system-wide information from /proc FS and calculate and diplay all values, percentages
// and interval totals in the exact fashion as collectl when invoked with the "-sc" argument.
//
// [CPU]User%: from /proc/stat "cpu" cpu summary line "user" value.
// [CPU]Nice%: from /proc/stat "cpu" cpu summary line "nice" value.
// [CPU]Sys%: from /proc/stat "cpu" cpu summary line "system" value.
// [CPU]Wait: from /proc/stat "cpu" cpu summary line "iowait" value.
// [CPU]Idle%: from /proc/stat "cpu" cpu summary line "idle" value.
// [CPU]Totl%: Total of [CPU]User%, [CPU]Nice%, [CPU]Sys% along with 
//             and "irq", "softirq" and "steal" values from /proc/stat "cpu" cpu summary line.
// [CPU]ProcQue: from /proc/stat "cpu" cpu summary line "procs_blocked" value.
// [CPU]ProcRun: from /proc/stat "cpu" cpu summary line "procs_running" value.
// [CPU]L-Avg1: from /proc/loadavg first field value.
// [CPU]L-Avg5: from /proc/loadavg second field value.
// [CPU]L-Avg15: from /proc/loadavg third field value.
//
class cpuStatistics
{
    private:
        std::ostringstream *headerPrintStream;
        std::ostringstream *dataPrintStream;
        procStat stat;
        procLoadavg loadavg;
        long user;
        long nice;
        long system;
        long idle;
        long irq;
        long soft;
        long steal;
        long wait;
        long total;
        long userP;
        long niceP;
        long systemP;
        long irqP;
        long softP;
        long stealP;
    public:
        cpuStatistics(std::ostringstream& dpStream, std::ostringstream& hpStream)
        {
            user = nice = system = idle = irq = soft = steal = wait = total = userP = niceP = systemP = irqP = softP =  stealP = 0;
            if (dpStream.good())
            {
                dataPrintStream = &dpStream;
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to perform write operations to data print stream. Exiting.", getpid());
                exit(10);
            }

            if (hpStream.good())
            {
                headerPrintStream = &hpStream;
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to perform write operations to header print stream. Exiting.", getpid());
                exit(10);
            }
        }

        void fetchCpuStatistics(std::string baseProcDir)
        {
            // Gather necessary system wide statistics statistics.
            stat.gatherProcStatData(baseProcDir);
            loadavg.gatherProcLoadavgData(baseProcDir);

            // Append the CPU statistics to the output stream.
            if (headerPrintStream->good())
            {
                *headerPrintStream << "[CPU]User%,";
                *headerPrintStream << "[CPU]Nice%,";
                *headerPrintStream << "[CPU]Sys%,";
                *headerPrintStream << "[CPU]Wait%,";
                *headerPrintStream << "[CPU]Idle%,";
                *headerPrintStream << "[CPU]Totl%,";
                *headerPrintStream << "[CPU]ProcQue,";
                *headerPrintStream << "[CPU]ProcRun,";
                *headerPrintStream << "[CPU]L-Avg1,";
                *headerPrintStream << "[CPU]L-Avg5,";
                *headerPrintStream << "[CPU]L-Avg15,";
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to perform write operations to CPU header print stream. Exiting.", getpid());
                exit(10);
            }

            user = stat.getTimeInUserMode()-stat.getLastTimeInUserMode();
            nice = stat.getTimeInNiceMode()-stat.getLastTimeInNiceMode();
            system = stat.getTimeInSystemTime()-stat.getLastTimeInSystemTime();
            wait = stat.getIoWaitTime()-stat.getLastIoWaitTime();
            idle = stat.getIdleTime()-stat.getLastIdleTime();
            irq = stat.getIrqTime()-stat.getLastIrqTime();
            soft = stat.getSoftIrqTime()-stat.getLastSoftIrqTime();
            steal = stat.getStealTime()-stat.getLastStealTime();
            total = user+nice+system+idle+irq+soft+steal+wait;

            // Total adjustment identical to collectl
            if (!total) total = 1;

            userP = 100*user/total;
            niceP = 100*nice/total;
            systemP = 100*system/total;
            irqP = 100*irq/total;
            softP = 100*soft/total;
            stealP = 100*steal/total;

            if (dataPrintStream->good())
            {
                //[CPU]User%: Time spent in User mode, not including time spend in "nice" mode.
                *dataPrintStream << floor(100*user/total) << ",";
                //[CPU]Nice%: Time spent in Nice mode, that is lower priority as adjusted by the nice command
                //and have the "N" status flag set when examined with "ps".
                *dataPrintStream << floor(100*nice/total) << ",";
                //[CPU]Sys%: This is time spent in "pure" system time.
                *dataPrintStream << floor(100*system/total) << ",";
                //[CPU]Wait: Also known as "iowait", this is the time the CPU was idle during an outstanding
                //disk I/O request. This is not considered to be part of the total or system times reported in brief mode.
                *dataPrintStream << floor(100*wait/total) << ",";
                //[CPU]Idle%: Total processes "twiddling thumbs" waiting for I/O to complete
                *dataPrintStream << floor(100*idle/total) << ",";
                //[CPU]Totl%: Total percentage of total CPU being used.
                *dataPrintStream << floor(userP+niceP+systemP+irqP+softP+stealP) << ",";
                //[CPU]ProcQue: Number of processes in the run queue.
                *dataPrintStream << loadavg.getLoadQue() << ",";
                //[CPU]ProcRun: Number of processes in the run state.
                *dataPrintStream << loadavg.getLoadRun() << ",";
                //[CPU]L-Avg1: Load average over the last minute.
                *dataPrintStream << loadavg.getLoadAvg1() << ",";
                //[CPU]L-Avg5: Load average over the last 5 minutes.
                *dataPrintStream << loadavg.getLoadAvg5() << ",";
                //[CPU]L-Avg15: Load average over the last 15 minutes.
                *dataPrintStream << loadavg.getLoadAvg15() << ",";
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to perform write operations to CPU data print stream. Exiting.", getpid());
                exit(10);
            }
            return;
        }
};
#endif
