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
#ifndef _DiskStatistics_HXX
#define _DiskStatistics_HXX

#include "ProcDiskstats.hxx"

//
// Gather system-wide information from /proc FS and calculate and diplay all values, percentages
// and interval totals in the exact fashion as collectl when invoked with the "-sd" argument..
//
// [DSK]ReadTot: from /proc/diskstats "sda" disk summary line "number of reads completed" value.
// [DSK]WriteTot: from /proc/diskstats "sda" disk summary line "number of writes completed" value.
// [DSK]OpsTot: [DSK]ReadTot+[DSK]WriteTot
// [DSK]ReadKBTot: from /proc/diskstats "sda" disk summary line "number of sectors read" value.
// [DSK]WriteKBTot: from /proc/diskstats "sda" disk summary line "number of sectors written" value.
// [DSK]KbTot: [DSK]ReadKBTot+[DSK]WriteKBTot
//
class diskStatistics
{
    private:
        std::ostringstream *headerPrintStream;
        std::ostringstream *dataPrintStream;
        procDiskstats diskStats;
        unsigned long dskRead;
        unsigned long dskReadKB;
        unsigned long dskWrite;
        unsigned long dskWriteKB;
    public:
        diskStatistics(std::ostringstream &dpStream, std::ostringstream &hpStream)
        {
            dskRead = dskReadKB = dskWrite = dskWriteKB = 0;
            if (dpStream.good())
            {
                dataPrintStream = &dpStream;
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to perform write operations to disk data print stream. Exiting.", getpid());
                exit(10);
            }
            if (hpStream.good())
            {
                headerPrintStream = &hpStream;
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to perform write operations to disk header print stream. Exiting.", getpid());
                exit(10);
            }
        }

        void fetchDiskStatistics(std::string baseProcDir, int secInterval)
        {
            // Gather necessary disk statistics.
            diskStats.gatherProcDiskstatsData(baseProcDir);

            // Append the disk statistics to the output stream.
            if (headerPrintStream->good())
            {
                *headerPrintStream << "[DSK]ReadTot,";
                *headerPrintStream << "[DSK]WriteTot,";
                *headerPrintStream << "[DSK]OpsTot,";
                *headerPrintStream << "[DSK]ReadKBTot,";
                *headerPrintStream << "[DSK]WriteKBTot,";
                *headerPrintStream << "[DSK]KbTot";
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to perform write operations to disk header print stream. Exiting.", getpid());
                exit(10);
            }

            dskRead = diskStats.getDskRead()-diskStats.getLastDskRead();
            dskReadKB = (diskStats.getDskReadKB()-diskStats.getLastDskReadKB())/2;
            dskWrite = diskStats.getDskWrite()-diskStats.getLastDskWrite();
            dskWriteKB = (diskStats.getDskWriteKB()-diskStats.getLastDskWriteKB())/2;

            // Note that the KB read and write values are divided by 2 in the collectl source code.
            if (dataPrintStream->good())
            {
                //[DSK]ReadTot: Total number of reads/sec.
                *dataPrintStream << dskRead/secInterval << ",";
                //[DSK]WriteTot: Total number of writes/sec.
                *dataPrintStream << dskWrite/secInterval << ",";
                //[DSK]OpsTot: Total Reads/Writes
                *dataPrintStream << (dskRead+dskWrite)/secInterval << ",";
                //[DSK]ReadKBTot: Total KB reads/sec
                *dataPrintStream << dskReadKB/secInterval << ",";
                //[DSK]WriteKBTot: Total KB writes/sec
                *dataPrintStream << dskWriteKB/secInterval << ",";
                //[DSK]KbTot: Total KBreads/KBwrites
                *dataPrintStream << (dskReadKB+dskWriteKB)/secInterval << endl;
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to perform write operations to disk data print stream. Exiting.", getpid());
                exit(10);
            }
            return;
        }
};
#endif
