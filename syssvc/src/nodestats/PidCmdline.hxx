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
#ifndef _pidCmdline_HXX
#define _pidCmdline_HXX

//
// File parser class for /proc/[pid]/cmdline
//
class pidCmdline
{
    private:
        char cmdline[2048];
    public:
        pidCmdline()
        {
            memset(cmdline, NULL, sizeof(cmdline));
        }

        int gatherPidCmdlineData(char* pidDir, std::string baseProcDir)
        {
            memset(cmdline, NULL, sizeof(cmdline));
            std::string pidCmdlineFile;
            if (!baseProcDir.empty())
            {
                pidCmdlineFile += baseProcDir;
            }
            else
            {
                pidCmdlineFile += "/proc";
            }
            pidCmdlineFile += "/";
            pidCmdlineFile += pidDir;
            pidCmdlineFile += "/cmdline";
            std::string line;
            FILE *fp = fopen(pidCmdlineFile.c_str(), "rb");
            if (fp!=NULL)
            {
                int x = setvbuf(fp, (char *)NULL, _IOFBF, 512);
                // Here we get one line. Commad lines can sometimes be made up of multiple lines, which
                // can make nodestats output look messy. Unless there is an explicit request in the future
                // for the full command line, only capture the first line for now.
                fread(cmdline, 2048, 1, fp );
                fclose (fp);
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to open %s. Skipping.", getpid(), pidCmdlineFile.c_str());
                return 1;
            }
            return 0;
        }

        char* getCmdline() { return cmdline; }
};
#endif
