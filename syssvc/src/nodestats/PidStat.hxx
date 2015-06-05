/**
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
#ifndef _pidStat_HXX
#define _pidStat_HXX

//
// File parser class for /proc/[pid]/stat
//
class pidStat
{
    private:
        std::string pid;
        std::string ppid;
        unsigned long vmSize;
        long vmRSS;
        unsigned long stime;
        unsigned long utime;
        long cutime;
        long cstime;
    public:
        pidStat()
        {
            pid = ppid = vmSize = vmRSS = stime = utime = cutime = cstime = 0;
        }

        int gatherPidStatData(char* pidDir, std::string baseProcDir)
        {
            pid = ppid = vmSize = vmRSS = stime = utime = cutime = cstime = 0;
            std::string pidStatFile;
            if (!baseProcDir.empty())
            {
                pidStatFile += baseProcDir;
            }
            else
            {
                pidStatFile += "/proc";
            }
            pidStatFile += "/";
            pidStatFile += pidDir;
            pidStatFile += "/stat";
            FILE *fp = fopen(pidStatFile.c_str(), "r");
            if (fp!=NULL)
            {
                int x = setvbuf(fp, (char *)NULL, _IOFBF, 512);
                char fileBuf[2048];
                // Read the entire stat file.
                fread(fileBuf, 2048, 1, fp );
                // Tokenize the contents.
                std::vector<std::string> mParts;
                size_t mPartsCount;
                std::string tmpPath = fileBuf;
                mPartsCount = Utils::Tokenize(tmpPath, " ", mParts);
                // pid
                pid = mParts[0];
                // Skip "comm"
                // Skip "state"
                // ppid
                ppid = mParts[3];
                // Skip "pgrp"
                // Skip "session"
                // Skip "tty_nr"
                // Skip "tpgid"
                // Skip "flags"
                // Skip "minflt"
                // Skip "cminflt"
                // Skip "majflt"
                // Skip "cmajflt"
                // utime
                char *pEdnd;
                utime = strtoul(mParts[13].c_str(), &pEdnd, 0);
                // stime
                stime = strtoul(mParts[14].c_str(), &pEdnd, 0);
                // cutime
                cutime = strtol(mParts[15].c_str(), &pEdnd, 0);
                // cstime
                cstime = strtol(mParts[16].c_str(), &pEdnd, 0);
                // Skip "priority"
                // Skip "nice"
                // Skip "num_threads"
                // Skip "itrealvalue"
                // Skip "starttime"
                // vsize
                vmSize = strtoul(mParts[22].c_str(), &pEdnd, 0);
                // rss
                vmRSS = strtol(mParts[23].c_str(), &pEdnd, 0);
                fclose (fp);
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to open %s. Skipping.", getpid(), pidStatFile.c_str());
                return 1;
            }
            return 0;
        }

        std::string getPid() { return pid; }
        std::string getPpid() { return ppid; }
        unsigned long getVmSize() { return vmSize; }
        long getVmRSS() { return vmRSS; }
        unsigned long getStime() { return stime; }
        unsigned long getUtime() { return utime; }
        long getCUtime() { return cutime; }
        long getCStime() { return cstime; }
};
#endif
