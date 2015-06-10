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
#ifndef _ProcessCmdAndArgs_HXX
#define _ProcessCmdAndArgs_HXX

using namespace std;

void usage(char *imageName);

        struct userInput {
            int sleepSec;
            int sampleCount;
            int printInterval;
            std::string outputFileName;
            bool daemonFlag;
            int numPidsToList;
            std::string baseProcDir;
        };

class ProcessCmdAndArgs
{
    private:
        int argCount;
        char *imageName;
        char **args;
        struct userInput inputInfo;
        bool timeToShutdown;

    public:
        ProcessCmdAndArgs(int argCount, char** cmdargs)
        :argCount(argCount), args(cmdargs)
        {
            parseCmdArgs();
            timeToShutdown = false;
        }

        ~ProcessCmdAndArgs() {
        }

        const char* const getProcName() const 
            { return this->imageName; }

        const std::string& getOutputFileName() const
            { return this->inputInfo.outputFileName; }

        void setOutputFileName(const std::string& fileName) 
            { this->inputInfo.outputFileName = fileName; }

        const int getWaitInterval() const
            { return this->inputInfo.sleepSec; }

        void setWaitInterval(int secs) 
            { this->inputInfo.sleepSec = secs; }

        const int getSampleCount() const
            { return this->inputInfo.sampleCount; }

        void setSampleCount(int count) 
            { this->inputInfo.sampleCount = count; }

        const bool getDaemonFlag() const
            { return this->inputInfo.daemonFlag; }

        void setDaemonFlag(bool setting) 
            { this->inputInfo.daemonFlag = setting; }

        const int getNumPidsToList() const
            { return this->inputInfo.numPidsToList; }

        void setNumPidsToList(int num) 
            { this->inputInfo.numPidsToList = num; }

        const bool getTimeToShutdown() const
            { return this->timeToShutdown; }

        void setTimeToShutdown(bool boolVal)
            { this->timeToShutdown = boolVal; }

        const std::string& getBaseProcDir() const
            { return this->inputInfo.baseProcDir; }

        void parseCmdArgs()
        {
            int ch;
            char *procToks = strtok(this->args[0], "/");
            imageName = strtok(NULL, "/");
            this->inputInfo.sleepSec = 1;
            this->inputInfo.sampleCount = 0;
            this->inputInfo.numPidsToList = 0;
            this->inputInfo.daemonFlag = false;
            while ((ch = getopt(argCount, args, "i:f:p:b:c:hdsk")) != -1) 
            {
                switch (ch) {
                    case 'i':
                        if (!(this->inputInfo.sleepSec = atoi(optarg))) {
                            cerr << "Problem encountered capturing specified print interval value. Exiting." << endl;
                            exit(10);
                        } else {
                            cout << "Specified print interval is " << this->inputInfo.sleepSec << " seconds." << endl;
                        }
                        break;
                    case 'f': 
                        this->inputInfo.outputFileName = optarg;
                        cout << "Output for this session will be stored in " << this->inputInfo.outputFileName << endl;
                        break;
                    case 'h': 
                        usage(imageName);
                        break;
                    case 'd': 
                        this->inputInfo.daemonFlag = true;
                        cout << "Daemon option specified. Node statisticss will be continuously gathered from this process until it is killed." << endl;
                        break;
                    case 'p':
                        if (!(this->inputInfo.numPidsToList = atoi(optarg))) {
                            cerr << "Problem encountered capturing specified number of top CPU usage processes." << endl;
                            exit(10);
                        } else {
                            cout << "Specified number of top CPU usage processes is " << this->inputInfo.numPidsToList << "." << endl;
                        }
                        break;
                    case 's':
                        // List all active daemon nodestats processes on this system.
                        system("ps -F --ppid 1 | grep nodestats");
                        exit(0);
                        break;
                    case 'k':
                        // Kill all existing nodestats processes that are daemons (PPID ia 1)
                        system("pkill -P1 nodestats");
                        exit(0);
                        break;
                    case 'b':
                        this->inputInfo.baseProcDir = optarg;
                        cout << "Specified alternate base directory for proc is " << this->inputInfo.baseProcDir << endl;
                        break;
                    case 'c':
                        if (!(this->inputInfo.sampleCount = atoi(optarg))) {
                            cerr << "Problem encountered capturing specified number of samples. Exiting." << endl;
                            exit(10);
                        } else {
                            cout << "Specified sample number is " << this->inputInfo.sampleCount << "." << endl;
                        }
                        break;
                    default:
                        usage(imageName);
                        break;
                } // End of switch statement to process command line options
            } // End of while loop processing command line arguments
            cout << "All error and informational logging messages will be stored in the system log (/var/log/messages)" << endl << endl;
            if (this->inputInfo.daemonFlag == true) {
                if (this->inputInfo.outputFileName.empty()) {
                    cerr << "The -f option must be specified with -d. Exiting." << endl;
                    exit(10);
                }
            }
        }
};

void usage(char *imageName)
{
    cerr << endl;
    cerr << "Usage : " << imageName << " [-i <seconds>] [-f <outputfile>] [-l <logfile>]" << endl;
    cerr << "Supported options are:" << endl;
    cerr << " -i <seconds> -- if specified, interval at which system statistics will be gathered and displayed. Default interval is 1 second." << endl;
    cerr << " -f <outputfile> -- redirect standard output of this utility to the specified file. Default is to direct standard output to the user terminal." << endl;
    cerr << " -d -- run " << imageName << " as a daemon process. Option -f must also be specified when this option is selected. Default is to run interactively." << endl;
    cerr << " -p <num> -- display process-specific statistics for the specified number of top CPU usage processes on the system." << endl;
    cerr << " -s -- display all existing " << imageName << " daemon processes on this node." << endl;
    cerr << " -k -- shutdown all existing " << imageName << " daemon processes on this node." << endl;
    cerr << " -c <count> -- collect this number of samples and exit." << endl << endl;
    cerr << "Note: All error and informational logging messages will be stored in the system log (/var/log/messages)" << endl;
    cerr << endl << endl;
    cerr << "Example: diskstats -i 60 -p 2 -f ./myOutputFile.log" << endl;
    exit(10);
}

#endif
