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
#ifndef _MemoryStatistics_HXX
#define _MemoryStatistics_HXX

#include "ProcMeminfo.hxx"
#include "ProcVmstat.hxx"

//
// Gather system-wide information from /proc FS and calculate and diplay all values, percentages
// and interval totals in the exact fashion as collectl when invoked with the "-sm" argument.
//
// [MEM]Tot: from /proc/meminfo "MemTotal" value.
// [MEM]Free: from /proc/meminfo "MemFree" value.
// [MEM]Used: [MEM]Tot-[MEM]Free
// [MEM]Shared: from /proc/meminfo "Shmem" value.
// [MEM]Buf: from /proc/meminfo "Buffers" value.
// [MEM]Cached: from /proc/meminfo "Cached" value.
// [MEM]SwapTot: from /proc/meminfo "SwapTotal" value.
// [MEM]SwapFree: from /proc/meminfo "SwapFree" value.
// [MEM]SwapUsed: [MEM]SwapTot-[MEM]SwapFree
// [MEM]SwapIn: from /proc/vmstat "pswpin" value.
// [MEM]PageIn: from /proc/vmstat "pswpout" value.
// [MEM]PageOut: from /proc/vmstat "pgpgout" value.
// [MEM]PageFaults: from /proc/vmstat "pgfault" value.
//
class memoryStatistics
{
    private:
        std::ostringstream *headerPrintStream;
        std::ostringstream *dataPrintStream;
        procMeminfo meminfo;
        procVmstat vmstat;
        unsigned long swapin;
        unsigned long swapout;
        unsigned long pagein;
        unsigned long pageout;
        unsigned long pagefault;
    public:
        memoryStatistics(std::ostringstream& dpStream, std::ostringstream& hpStream)
        {
            swapin = swapout = pagein = pageout = pagefault = 0;
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

        void fetchMemoryStatistics(std::string baseProcDir, int secInterval)
        {
            // Gather necessary system wide statistics statistics.
            meminfo.gatherProcMeminfoData(baseProcDir);
            vmstat.gatherProcVmstatData(baseProcDir);

            // Append the memory statistics to the output stream.
            if (headerPrintStream->good())
            {
                *headerPrintStream << "[MEM]Tot,";
                *headerPrintStream << "[MEM]Used,";
                *headerPrintStream << "[MEM]Free,";
                *headerPrintStream << "[MEM]Shared,";
                *headerPrintStream << "[MEM]Buf,";
                *headerPrintStream << "[MEM]Cached,";
                *headerPrintStream << "[MEM]SwapTot,";
                *headerPrintStream << "[MEM]SwapUsed,";
                *headerPrintStream << "[MEM]SwapFree,";
                *headerPrintStream << "[MEM]SwapIn,";
                *headerPrintStream << "[MEM]SwapOut,";
                *headerPrintStream << "[MEM]PageIn,";
                *headerPrintStream << "[MEM]PageOut,";
                *headerPrintStream << "[MEM]PageFaults,";
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to perform write operations to Memory header print stream. Exiting.", getpid());
                exit(10);
            }

            swapin = vmstat.getSwapin()-vmstat.getLastSwapin();
            swapout = vmstat.getSwapout()-vmstat.getLastSwapout();
            pagein = vmstat.getPagein()-vmstat.getLastPagein();
            pageout = vmstat.getPageout()-vmstat.getLastPageout();
            pagefault = vmstat.getPagefault()-vmstat.getLastPagefault();

            if (dataPrintStream->good())
            {
                //[MEM]Tot: Total physical memory
                *dataPrintStream << meminfo.getMemTot() << ",";
                //[MEM]Used: Used physical memory. This does not include memory used by the kernel itself.
                *dataPrintStream << meminfo.getMemTot()-meminfo.getMemFree() << ",";
                //[MEM]Free: Unallocated physical memory
                *dataPrintStream << meminfo.getMemFree() << ",";
                //[MEM]Shared: Shared memory.
                *dataPrintStream << meminfo.getMemShared() << ",";
                //[MEM]Buf: The amount of memory used as buffers.
                *dataPrintStream << meminfo.getMemBuf() << ",";
                //[MEM]Cached: The amount of memory used as cache.
                *dataPrintStream << meminfo.getMemCached() << ",";
                //[MEM]SwapTot: Total swap.
                *dataPrintStream << meminfo.getSwapTotal() << ",";
                //[MEM]SwapUsed: Used swap.
                *dataPrintStream << meminfo.getSwapTotal()-meminfo.getSwapFree() << ",";
                //[MEM]SwapFree: Free swap.
                *dataPrintStream << meminfo.getSwapFree() << ",";
                //[MEM]SwapIn: Memory swapped in/sec
                *dataPrintStream << swapin/secInterval << ",";
                //[MEM]SwapIn: Memory swapped out/sec
                *dataPrintStream << swapout/secInterval << ",";
                //[MEM]PageIn: Pages swapped out/sec
                *dataPrintStream << pagein/secInterval << ",";
                //[MEM]PageOut: Total number of pages written by block devices
                *dataPrintStream << pageout/secInterval << ",";
                //[MEM]PageFaults: Page faults.
                *dataPrintStream << pagefault/secInterval << ",";
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to perform write operations to Memory data print stream. Exiting.", getpid());
                exit(10);
            }
            return;
        }
};
#endif
