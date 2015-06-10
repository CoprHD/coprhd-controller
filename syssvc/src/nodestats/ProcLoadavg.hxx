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
#ifndef _ProcLoadAvg_HXX
#define _ProcLoadAvg_HXX

//
// File parser class for /proc/loadavg
//
class procLoadavg
{
    private:
        std::string loadAvg1;
        std::string loadAvg5;
        std::string loadAvg15;
        std::string loadQue;
        std::string loadRunS;
        unsigned long loadRun;
    public:
        procLoadavg()
        {
            loadRun = 0;
        }

        void gatherProcLoadavgData(std::string baseProcDir)
        {
            loadRun = 0;
            std::string procLoadavgFile;
            if (!baseProcDir.empty())
            {
                procLoadavgFile += baseProcDir;
            }
            else
            {
                procLoadavgFile += "/proc";
            }
            procLoadavgFile += "/loadavg";
            std::string line;
            FILE *fp = fopen(procLoadavgFile.c_str(), "rb");
            if (fp!=NULL)
            {
                int x = setvbuf(fp, (char *)NULL, _IOFBF, 512);
                char fileBuf[2048];
                // Read the entire loadavg file.
                fread(fileBuf, 2048, 1, fp );
                // Tokenize the contents.
                std::vector<std::string> mParts;
                size_t mPartsCount;
                std::string tmpPath = fileBuf;
                mPartsCount = Utils::Tokenize(tmpPath, " ", mParts); 
                loadAvg1 = mParts[0];
                loadAvg5 = mParts[1];
		loadAvg15 = mParts[2];
                std::string loadProc = mParts[3];
                size_t found;
                char *pEdnd;
                if ((found = loadProc.find("/")) != std::string::npos) 
                {
                    loadRunS = loadProc.substr(0,found);
		    loadRun = strtoul(loadRunS.c_str(), &pEdnd, 0);
                    loadRun--; // Never count ourself, just like collectl
                    loadQue = loadProc.substr(found+1, std::string::npos); 
                }
                else
                {
                    syslog(LOG_ERR, "nodestats process [process ID %d], Problem encountered when parsing %s. Exiting.", getpid(), procLoadavgFile.c_str());
                    exit(10);
                }
		fclose (fp);
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to open %s. Exiting.", getpid(), procLoadavgFile.c_str());
                exit(10);
            }
            return;
        }
        std::string getLoadAvg1() { return loadAvg1; }
        std::string getLoadAvg5() { return loadAvg5; }
        std::string getLoadAvg15() { return loadAvg15; }
        long double getLoadRun() { return loadRun; }
        std::string getLoadQue() { return loadQue; }
};
#endif
