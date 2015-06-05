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
#ifndef _procStat_HXX
#define _procStat_HXX

//
// File parser class for /proc/stat
//
class procStat
{
    private:
        bool lastStatsLoaded;
        long timeInUserMode;
        long timeInNiceMode;
        long timeInSystemTime;
        long idleTime;
        long ioWaitTime;
        long irqTime;
        long softIrqTime;
        long stealTime;
        long numProcesses;
        long numProcessesBlocked;
        long numProcessesRunning;
        long lastTimeInUserMode;
        long lastTimeInNiceMode;
        long lastTimeInSystemTime;
        long lastIdleTime;
        long lastIoWaitTime;
        long lastIrqTime;
        long lastSoftIrqTime;
        long lastStealTime;
        long lastNumProcesses;
        long lastNumProcessesBlocked;
        long lastNumProcessesRunning;

    public:
        procStat()
        {
            lastStatsLoaded = false;
            timeInUserMode = timeInNiceMode = timeInSystemTime = idleTime = ioWaitTime = irqTime = softIrqTime = \
            stealTime = numProcesses = numProcessesBlocked = numProcessesRunning = lastTimeInUserMode = \
            lastTimeInNiceMode = lastTimeInSystemTime = lastIdleTime = lastIoWaitTime = lastIrqTime = lastSoftIrqTime = \
            lastStealTime = lastNumProcesses = lastNumProcessesBlocked = lastNumProcessesRunning = 0;
        }

        void gatherProcStatData(std::string baseProcDir)
        {
            std::string procStatFile;
            if (!baseProcDir.empty())
            {
                procStatFile += baseProcDir;
            }
            else
            {
                procStatFile += "/proc";
            }
            procStatFile += "/stat";
            std::string line;
            FILE *fp = fopen(procStatFile.c_str(), "rb");
            if (fp!=NULL)
            {
                int x = setvbuf(fp, (char *)NULL, _IOFBF, 512);
                char nextLine[2048];
                char *pEdnd = NULL;;
                std::vector<std::string> mParts;
                size_t mPartsCount;
                std::string tmpPath = nextLine;
                while (fgets(nextLine, 2048, fp) != NULL)
                {
                    mParts.clear();
                    tmpPath = nextLine;
                    mPartsCount = Utils::Tokenize(tmpPath, " ", mParts);
                    // Parse the "cpu" line. This line represents totals for all CPUs on the system.
                    if (!strcmp(mParts[0].c_str(), "cpu"))
                    {
                        // user
                        if (false == lastStatsLoaded)
                        {
                            lastTimeInUserMode = strtol(mParts[1].c_str(), &pEdnd, 0);
                            timeInUserMode = lastTimeInUserMode;
                        }
                        else
                        {
                            lastTimeInUserMode = timeInUserMode;
                            timeInUserMode = strtol(mParts[1].c_str(), &pEdnd, 0);
                        }
                        // nice
                        if (false == lastStatsLoaded)
                        {
                            lastTimeInNiceMode = strtol(mParts[2].c_str(), &pEdnd, 0);
                            timeInNiceMode = lastTimeInNiceMode;
                        }
                        else
                        {
                            lastTimeInNiceMode = timeInNiceMode;
                            timeInNiceMode = strtol(mParts[2].c_str(), &pEdnd, 0);
                        }
                        // system
                        if (false == lastStatsLoaded)
                        {
                            lastTimeInSystemTime = strtol(mParts[3].c_str(), &pEdnd, 0);
                            timeInSystemTime = lastTimeInSystemTime;
                        }
                        else
                        {
                            lastTimeInSystemTime = timeInSystemTime;
                            timeInSystemTime = strtol(mParts[3].c_str(), &pEdnd, 0);
                        }
                        // idle
                        if (false == lastStatsLoaded)
                        {
                            lastIdleTime = strtol(mParts[4].c_str(), &pEdnd, 0);
                            idleTime = lastIdleTime;
                        }
                        else
                        {
                            lastIdleTime = idleTime;
                            idleTime = strtol(mParts[4].c_str(), &pEdnd, 0);
                        }
                        // iowait
                        if (false == lastStatsLoaded)
                        {
                            lastIoWaitTime = strtol(mParts[5].c_str(), &pEdnd, 0);
                            ioWaitTime = lastIoWaitTime;
                        }
                        else
                        {
                            lastIoWaitTime = ioWaitTime;
                            ioWaitTime = strtol(mParts[5].c_str(), &pEdnd, 0);
                        }
                        // irq
                        if (false == lastStatsLoaded)
                        {
                            lastIrqTime = strtol(mParts[6].c_str(), &pEdnd, 0);
                            irqTime = lastIrqTime;
                        }
                        else
                        {
                            lastIrqTime = irqTime;
                            irqTime = strtol(mParts[6].c_str(), &pEdnd, 0);
                        }
                        // softirq
                        if (false == lastStatsLoaded)
                        {
                            lastSoftIrqTime = strtol(mParts[7].c_str(), &pEdnd, 0);
                            softIrqTime = lastSoftIrqTime;
                        }
                        else
                        { 
                            lastSoftIrqTime = softIrqTime;
                            softIrqTime = strtol(mParts[7].c_str(), &pEdnd, 0);
                        }
                        // steal
                        if (false == lastStatsLoaded)
                        {
                            lastStealTime = strtol(mParts[8].c_str(), &pEdnd, 0);
                            stealTime = lastStealTime;
                        }
                        else
                        {
                            lastStealTime = stealTime;
                            stealTime = strtol(mParts[8].c_str(), &pEdnd, 0);
                        }
                    }
                    // Total number of processes on the system.
                    if (!strcmp(mParts[0].c_str(), "processes"))
                    {
                        if (false == lastStatsLoaded)
                        {
                            lastNumProcesses = strtol(mParts[0].c_str(), &pEdnd, 0);
                            numProcesses = lastNumProcesses;
                        }
                        else
                        {
                            lastNumProcesses = numProcesses;
                            numProcesses = strtol(mParts[0].c_str(), &pEdnd, 0);
                        }
                    }
                    // Number of processes in the run queue.
                    if (!strcmp(mParts[0].c_str(), "procs_blocked"))
                    {
                        if (false == lastStatsLoaded)
                        {
                            lastNumProcessesBlocked = strtol(mParts[0].c_str(), &pEdnd, 0);
                            numProcessesBlocked = lastNumProcessesBlocked;
                        }
                        else
                        {
                            lastNumProcessesBlocked = numProcessesBlocked;
                            numProcessesBlocked = strtol(mParts[0].c_str(), &pEdnd, 0);
                        }
                    }
                    // Number of processes in the run state.
                    if (!strcmp(mParts[0].c_str(), "procs_running"))
                    {
                        if (false == lastStatsLoaded)
                        {
                            lastNumProcessesRunning = strtol(mParts[0].c_str(), &pEdnd, 0);
                            numProcessesRunning = lastNumProcessesRunning;
                        }
                        else
                        {
                            lastNumProcessesRunning = numProcessesRunning;
                            numProcessesRunning = strtol(mParts[0].c_str(), &pEdnd, 0);
                        }
                    }
                    lastStatsLoaded = true;
                }
                fclose (fp);
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to open %s. Exiting.", getpid(), procStatFile.c_str());
                exit(10);
            }
            return;
        }

        long getTimeInUserMode() { return timeInUserMode; }
        long getTimeInNiceMode() { return timeInNiceMode; }
        long getTimeInSystemTime() { return timeInSystemTime; }
        long getIdleTime() { return idleTime; }
        long getIoWaitTime() { return ioWaitTime; }
        long getIrqTime() { return irqTime; }
        long getSoftIrqTime() { return softIrqTime; }
        long getStealTime() { return stealTime; }
        long getNumProcesses() { return numProcesses; }
        long getNumProcessesBlocked() { return numProcessesBlocked; }
        long getNumProcessesRunning() { return numProcessesRunning; }
        long getLastTimeInUserMode() { return lastTimeInUserMode; }
        long getLastTimeInNiceMode() { return lastTimeInNiceMode; }
        long getLastTimeInSystemTime() { return lastTimeInSystemTime; }
        long getLastIdleTime() { return lastIdleTime; }
        long getLastIoWaitTime() { return lastIoWaitTime; }
        long getLastIrqTime() { return lastIrqTime; }
        long getLastSoftIrqTime() { return lastSoftIrqTime; }
        long getLastStealTime() { return lastStealTime; }
        long getLastNumProcesses() { return lastNumProcesses; }
        long getLastNumProcessesBlocked() { return lastNumProcessesBlocked; }
        long getLastNumProcessesRunning() { return lastNumProcessesRunning; }
};
#endif
