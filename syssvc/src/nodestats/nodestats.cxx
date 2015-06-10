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
#include <stdio.h>
#include <stdlib.h>
#include <sstream>
#include <cstdint>  // need to be enabled with -std=c++0x or -std=gnu++0x
#include <iostream>
#include <iomanip>
#include <fstream>
#include <string.h>
#include <fcntl.h>
#include <math.h>
#include <errno.h>
#include <dirent.h>
#include <time.h>
#include <sys/wait.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <syslog.h>
#include <sys/resource.h>
#include <sys/param.h>
#include "Utils.hxx"
#include "ProcessCmdAndArgs.hxx"
#include "CpuStatistics.hxx"
#include "MemoryStatistics.hxx"
#include "DiskStatistics.hxx"
#include "ProcessStatistics.hxx"

using namespace std;

void setSignalHandlers();
static void sigTermHandler(int sigNum);
static void setCatchSignals(int sigNum);
static void setIgnoreSignals(int sigNum);

ProcessCmdAndArgs *procCmdArgs;

int processStats(ProcessCmdAndArgs &processArgs)
{
    int statCount = 0;
    int sampleCount = processArgs.getSampleCount();

    std::ostringstream headerStream;
    std::ostringstream dataStream;
    syslog(LOG_INFO, "nodestats process [process ID %d], This is a test to see if it works.", getpid());

    if (true == processArgs.getDaemonFlag())
        syslog(LOG_INFO, "nodestats process [process ID %d], Nodestats started as a daemon.", getpid());

    // Object initializatrion for all four statistic types: process, cpu, memory and disk
    processStatistics procPidStats = processStatistics(dataStream, headerStream);
    cpuStatistics cpuStats = cpuStatistics(dataStream, headerStream);
    memoryStatistics memStats = memoryStatistics(dataStream, headerStream);
    diskStatistics diskStats = diskStatistics(dataStream, headerStream);

    // Get the output filename, if specified.
    std::string outFileName = processArgs.getOutputFileName();

    char timeBuf[80];
    memset(timeBuf, NULL, sizeof(timeBuf));
    std::string procDir = processArgs.getBaseProcDir();
    bool timePrint = false;
    if (procDir.empty())
        timePrint = true;
    // If an output file was specified, write to it. Otherwise write to stdout.
    // Note that an output file must be specified when nodestats is run as a daemon.
    // Since many of the statistics shown are the delta change in value from for the
    // specified time interval, an initial set of results must be gathered without
    // displaying them to the user by terminal or output file.
    bool firstTime = true;
    bool fileOutput, pidStats = false;
    if (processArgs.getNumPidsToList())
        pidStats = true;
    std::ofstream outFile;
    if (outFileName.size())
    {
        fileOutput = true;
        outFile.open(outFileName.c_str());
    }
    while (1)
    {
        headerStream.str("");
        dataStream.str("");
        Utils::getCurrentTimeString(timeBuf, sizeof(timeBuf), DATETIME_STAT_FORMAT);
        if (true == pidStats)
        {
            procPidStats.fetchProcessStatistics(processArgs.getNumPidsToList(), timeBuf, procDir, processArgs.getWaitInterval(), timePrint);
        }
        else
        {
            cpuStats.fetchCpuStatistics(procDir);
            memStats.fetchMemoryStatistics(procDir, processArgs.getWaitInterval());
            diskStats.fetchDiskStatistics(procDir, processArgs.getWaitInterval());
        }
        if (true == firstTime)
        {
            if (true == fileOutput)
                outFile << "Date,Time," << headerStream.str() << endl;
            else
                cout << "Date,Time," << headerStream.str() << endl;
            firstTime = false;
        } else {
            if (true == pidStats)
                if (true == fileOutput)
                    outFile << dataStream.str();
                else
                    cout << dataStream.str();
            else
            {
                if (true == timePrint)
                {
                    if (true == fileOutput)
                        outFile << timeBuf << dataStream.str();
                    else
                        cout << timeBuf << dataStream.str();
                }
                else
                {
                    if (true == fileOutput)
                        outFile << dataStream.str();
                    else
                        cout << dataStream.str();
                        
                }
            }
            if (true == fileOutput)
                outFile.flush();
            if (sampleCount)
            {
                statCount++;
                if (statCount == sampleCount)
                    break;
            }
            if (false == pidStats)
            {
                sleep(processArgs.getWaitInterval());
            }
            // Check for shutdown signal and exit of one has been received.
            if (true == processArgs.getTimeToShutdown())
            {
                syslog(LOG_INFO, "nodestats process [process ID %d], Gracefully exiting.", getpid());
                break;
            }
        }
    }
    if (true == fileOutput)
        outFile.close();
    return 0;
}

int main(int argc, char **argv, char **envp)
{
    // Parse the command and its arguments
    ProcessCmdAndArgs processArgs = ProcessCmdAndArgs(argc, argv);
    procCmdArgs = &processArgs;

    // This program can only be run by root.
    if (getuid() != 0)
    {
        cerr << "You must have root privileges to run this program. Exiting." << endl;
        return 10;
    }

    // Run as daemon process if argument specifed.
    if (true == processArgs.getDaemonFlag())
    {
        pid_t pid, sid;

        // detach from the main process
        if ((pid = fork()) < 0)
        { // 0 returned in child process, negative value if problem occurred.
            cerr << "Problem encountered when attempting go start daemon process. Exiting." << endl;
            exit(10);
        }
        else if (pid != 0)
        {
            exit(0); // exit parent process - non-zero child PID returned here. 
        }

        // Child processing continues.

        // Close any open (inherited) file descriptors, exit on failure.
        close(STDIN_FILENO);
        close(STDOUT_FILENO);
        close(STDERR_FILENO);

        // Clear the file mode creation mask.
        umask(0);

        // Become the session leader.
        sid = setsid();
        if (sid < 0)
        {
            syslog(LOG_ERR, "nodestats process [process ID %d], Unable to create a new session after starting child process. Exiting.", getpid());
            exit(10);
        }

        // Working directory is not set here because user is allowed to use 
        // either relative or full file paths when specifying output file path.

        // Setup signals to catch and ignore.
        setSignalHandlers();

        processStats(processArgs);
    }
    else
    {
        processStats(processArgs);
    }

    return 0;
}

static void sigTermHandler(int sigNum)
{
    syslog(LOG_INFO, "nodestats process [process ID %d], Received a shutdown signal. Setting graceful shutdown flag.", getpid());
    procCmdArgs->setTimeToShutdown(true);
}

static void setCatchSignals(int sigNum)
{
    if (sigNum == SIGTERM)
    {
        if (signal(SIGTERM, sigTermHandler) == SIG_ERR)
        {
            syslog(LOG_ERR, "nodestats process [process ID %d], Unable to set signal handler for SIGTERM. Exiting.", getpid());
            exit(10);
        }
    }
}

static void setIgnoreSignals(int sigNum)
{
    if (signal(sigNum, SIG_IGN) == SIG_ERR)
    {
        syslog(LOG_ERR, "nodestats process [process ID %d], Unable to ignore signal %d. Exiting.", getpid(), sigNum);
        exit(10);
    }
}

//
// Set signal handlers for SIGINT,SIGHUP,SIGQUIT,SIGABRT,SIGTERM,SIGTSTP
//
void setSignalHandlers()
{
    setIgnoreSignals(SIGINT);
    setIgnoreSignals(SIGHUP);
    setIgnoreSignals(SIGQUIT);
    setIgnoreSignals(SIGABRT);
    setIgnoreSignals(SIGTSTP);
    setCatchSignals(SIGTERM);
}
