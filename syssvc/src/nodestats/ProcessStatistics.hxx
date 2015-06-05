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
#ifndef _ProcessStatistics_HXX
#define _ProcessStatistics_HXX

#include <list>
#include "PidStat.hxx"
#include "PidCmdline.hxx"

// List entry struct
struct cpuPidStr
{
    unsigned long totalUTimeNow;
    unsigned long totalSTimeNow;
    unsigned long totalUTimeLast;
    unsigned long totalSTimeLast;
    unsigned long intervalCpuUsed;
    char* pid;
    std::string pPid;
    unsigned long vmSize;
    long vmRSS;
    cpuPidStr (unsigned long a, unsigned long b, unsigned long c, unsigned long d, unsigned long e, char* f, std::string g, unsigned long h, long i) : totalUTimeNow(a), totalSTimeNow(b), totalUTimeLast(c), totalSTimeLast(d), intervalCpuUsed(e), pid(f), pPid(g), vmSize(h), vmRSS(i) {}
};

// Identify numeric numbered directories.
int isNumeric(const char* procDirName)
{
    for ( ; *procDirName; procDirName++)
        if (*procDirName < '0' || *procDirName > '9')
            return 0; // false
    return 1; // true
}

// Compare list entries.
bool compareListEntries(cpuPidStr first, cpuPidStr second)
{
    unsigned int i=0;
    if (first.intervalCpuUsed < second.intervalCpuUsed) return true;
    else if (first.intervalCpuUsed > second.intervalCpuUsed) return false;
}

//
// Gather process-specific information from /proc FS and calculate and display all values, percentages
// and interval totals in the exact fashion as collectl when invoked with the "-sZ" argument.
//
// PID: from the "pid" field of the /proc/[pid]/stat file.
// PPID: from the "ppid" field of the /proc/[pid]/stat file.
// VmSize: from the "vsize" field of the /proc/[pid]/stat file.
// VmRSS: from the "rss" field of the /proc/[pid]/stat file.
// UsrCPU: from the "utime" field of the /proc/[pid]/stat file.
// SysCPU: from the "stime" field of the /proc/[pid]/stat file.
// TotalCPU: interval total of UsrCPU and SysCPU.
// ProcessName (command): from the /proc/[pid]/cmdline file.
//
class processStatistics
{
    private:
        std::ostringstream *headerPrintStream;
        std::ostringstream *dataPrintStream;
        pidStat pidInfo;
        pidCmdline pidCmd;
    public:
        processStatistics(ostringstream& dpStream, ostringstream& hpStream)
        {
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

        void fetchProcessStatistics(int numToList, char* currentTime, std::string baseProcDir, int secInterval, bool timePrint)
        {
            list<cpuPidStr> cpuAndPidList;
            double tmpUSecs = 0;
            double tmpSSecs = 0;
            unsigned long totalUtime = 0; 
            unsigned long totalStime = 0; 
            unsigned long totalCpu = 0; 
            struct dirent* dirEntry = NULL;
            DIR* procDirP = NULL;
            list<cpuPidStr>::iterator listIt;
            int lumToListCount = 0;
            unsigned long tmpVmSize;
            long clockTicksPerSec = sysconf(_SC_CLK_TCK);

            // Append the process statistics to the output stream.
            if (headerPrintStream->good())
            {
                *headerPrintStream << "PID,";
                *headerPrintStream << "PPID,";
                *headerPrintStream << "VmSize,";
                *headerPrintStream << "VmRSS,";
                *headerPrintStream << "UsrCPU,";
                *headerPrintStream << "SysCPU,";
                *headerPrintStream << "TotalCPU,";
                *headerPrintStream << "ProcessName(command)";
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to perform write operations to header print stream. Exiting.", getpid());
                exit(10);
            }

            // Open the /proc dir
            if (!baseProcDir.empty())
            {
                procDirP = opendir(baseProcDir.c_str());
            }
            else
            {
                procDirP = opendir("/proc");
            }
        
            if (procDirP == NULL)
            {
                if (!baseProcDir.empty())
                {
                    syslog(LOG_ERR, "nodestats process [process ID %d], Unable to open %s directory. Exiting.", getpid(), baseProcDir.c_str());
                }
                else
                {
                    syslog(LOG_ERR, "nodestats process [process ID %d], Unable to open /proc directory. Exiting.", getpid());
                }
                exit(10);
           }

            // Loop through all files in the /proc directory.
            while ((dirEntry = readdir(procDirP)))
            {
                // If the file is a directory.
                if (dirEntry->d_type == DT_DIR)
                {
                    // Check to see if it is numeric.
                    if (isNumeric(dirEntry->d_name))
                    {
                        // Need to protect against situations where a process exists (and /proc/[pid]
                        // directory associated with it vanishes). Just move on to the next directory
                        // when this happens.
                        if (pidInfo.gatherPidStatData(dirEntry->d_name,baseProcDir))
                        {
                            continue;
                        }

                        // Construct the list entry.
                        cpuPidStr listEnt = cpuPidStr(0,0,pidInfo.getUtime(),pidInfo.getStime(),0,dirEntry->d_name,"",0,0);
                        // Add the entry to the list.
                        cpuAndPidList.push_back(listEnt);
                    }
                }
            }

            // If the list is empty (which should never happen if there are processes running
            // in the system), simply exit.
            if (cpuAndPidList.empty())
            { 
                if (!baseProcDir.empty())
                {
                    syslog(LOG_ERR, "nodestats process [process ID %d], No pid directories found in diretory %s. Exiting.", getpid(), baseProcDir.c_str());
                }
                else
                {
                    syslog(LOG_ERR, "nodestats process [process ID %d], No pid directories found in diretory /proc. Exiting.", getpid());
                }
                closedir(procDirP);
                exit(10);
            }

            // Get the current time
            char timeBuf[80];
            memset(timeBuf, NULL, sizeof(timeBuf));
            Utils::getCurrentTimeString(timeBuf, sizeof(timeBuf), DATETIME_STAT_FORMAT);

            sleep(secInterval);

            // Traverse the list, gatherng the user and system CPU time information again so that we can 
            // calculate and capture the CPU time interval change.
            for (listIt=cpuAndPidList.begin(); listIt!=cpuAndPidList.end(); ++listIt)
            {
                // Need to protect against situations where a process exists (and /proc/[pid]
                // directory associated with it vanishes). Just move on to the next directory
                // when this happens.
                if (pidInfo.gatherPidStatData(listIt->pid,baseProcDir))
                {
                    continue;
                }

                tmpVmSize = pidInfo.getVmSize();
                if (tmpVmSize > 1024) tmpVmSize = tmpVmSize/1024;

                totalUtime = pidInfo.getUtime();
                totalStime = pidInfo.getStime();
                totalCpu = (totalUtime-listIt->totalUTimeLast+totalStime-listIt->totalSTimeLast)/secInterval;

                // Update the list entry with the total CPU time change for this interval. 
                cpuPidStr & listEnt2(*listIt);

                listEnt2 = cpuPidStr(totalUtime,totalStime,listIt->totalUTimeLast,listIt->totalSTimeLast,totalCpu,listIt->pid,pidInfo.getPpid(),tmpVmSize,pidInfo.getVmRSS()*4);

            }
 
            // Now sort the list and reverse it so that the highest CPU usage PIDs are listed first.
            cpuAndPidList.sort(compareListEntries);
            cpuAndPidList.reverse();

            if (dataPrintStream->good()) {
                // Now traverse the list one more time to append information from it and additional process statistics
                // to the output stream.
                for (listIt=cpuAndPidList.begin(); listIt!=cpuAndPidList.end(); ++listIt) {
                    lumToListCount++;
                    // Need to protect against situations where a process exists (and /proc/[pid]
                    // directory associated with it vanishes). Just move on to the next directory
                    // when this happens.
                    if (pidCmd.gatherPidCmdlineData(listIt->pid,baseProcDir)) {
                        lumToListCount--;
                        continue;
                    }
                    // Current Date/Time
                    if (true == timePrint)
                        *dataPrintStream << timeBuf;
                    // PID
                    *dataPrintStream << listIt->pid << ",";
                    // PPID
                    *dataPrintStream << listIt->pPid << ",";
                    // VmSize - represented in KB like collectl.
                    *dataPrintStream << listIt->vmSize << ",";
                    // VmRSS - value multiplied by 4 like collectl.
                    *dataPrintStream << listIt->vmRSS << ",";
                    // UsrCPU
                    // User time CPU used by this PID for the interval.
                    tmpUSecs = 0;
                    tmpUSecs = (double)(listIt->totalUTimeNow-listIt->totalUTimeLast)/clockTicksPerSec;
                    *dataPrintStream << fixed << showpoint << setprecision(2) << tmpUSecs << ",";
                    // SysCPU
                    // System time CPU used by this PID for the interval.
                    tmpSSecs = 0;
                    tmpSSecs = (double)(listIt->totalSTimeNow-listIt->totalSTimeLast)/clockTicksPerSec;
                    *dataPrintStream << fixed << showpoint << setprecision(2) << tmpSSecs << ",";
                    // TotalCPU
                    // Total system and user CPU time used by this PID for the interval.
                    *dataPrintStream << fixed << showpoint << setprecision(2) << tmpUSecs+tmpSSecs << ",";
                    // ProcessName(command)
                    *dataPrintStream << pidCmd.getCmdline() << endl;
                    if (lumToListCount >= numToList) break;
                }
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to perform write operations to Process data print stream. Exiting.", getpid());
                closedir(procDirP);
                exit(10);
            }
            cpuAndPidList.clear();
            closedir(procDirP);
            return;
        }
};
#endif
