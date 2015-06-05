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
#ifndef _procDiskstats_HXX
#define _procDiskstats_HXX

//
// File parser class for /proc/diskstats
//
class procDiskstats
{
    private:
        std::string procDiskstatsFile;
        bool lastStatsLoaded;
        unsigned long dskRead;
        unsigned long dskReadKB;
        unsigned long dskWrite;
        unsigned long dskWriteKB;
        unsigned long lastDskRead;
        unsigned long lastDskReadKB;
        unsigned long lastDskWrite;
        unsigned long lastDskWriteKB;
    public:
        procDiskstats()
        {
            dskRead = dskReadKB = dskWrite = dskWriteKB = lastDskRead = lastDskReadKB = lastDskWrite = lastDskWriteKB = 0;
            lastStatsLoaded = false;
        }

        void gatherProcDiskstatsData(std::string baseProcDir)
        {
            std::string procDiskstatsFile;
            if (!baseProcDir.empty())
            {
                procDiskstatsFile += baseProcDir;
            }
            else
            {
                procDiskstatsFile += "/proc";
            }
            procDiskstatsFile += "/diskstats";
            std::string line;
            FILE *fp = fopen(procDiskstatsFile.c_str(), "rb");
            if (fp!=NULL)
            {
                int x = setvbuf(fp, (char *)NULL, _IOFBF, 512);
                char fileBuf[2048];
                // Read the entire diskstats file.
                fread(fileBuf, 2048, 1, fp );
                // Tokenize the contents.
                std::vector<std::string> mParts;
                size_t mPartsCount;
                std::string tmpPath = fileBuf;
                mPartsCount = Utils::Tokenize(tmpPath, " ", mParts);
                char *pEdnd;
                // Skip the major and minor numbers and disk name.
                // dskRead
                if (false == lastStatsLoaded)
                {
                    lastDskRead = strtoul(mParts[3].c_str(), &pEdnd, 0);
                    dskRead = lastDskRead;
                }
                else
                {
                    lastDskRead = dskRead;
                    dskRead = strtoul(mParts[3].c_str(), &pEdnd, 0);
                }
                // Skip dskReadMrg
                // dskReadKB
                if (false == lastStatsLoaded)
                {
                    lastDskReadKB = strtoul(mParts[5].c_str(), &pEdnd, 0);
                    dskReadKB = lastDskReadKB;
                }
                else
                {
                    lastDskReadKB = dskReadKB;
                    dskReadKB = strtoul(mParts[5].c_str(), &pEdnd, 0);
                }
                // Skip dskReadTicks
                // dskWrite
                if (false == lastStatsLoaded)
                {
                    lastDskWrite = strtoul(mParts[7].c_str(), &pEdnd, 0);
                    dskWrite = lastDskWrite;
                }
                else
                {
                    lastDskWrite = dskWrite;
                    dskWrite = strtoul(mParts[7].c_str(), &pEdnd, 0);
                }
                // Skip dskWriteMrg
                // dskWriteKB
                if (false == lastStatsLoaded)
                {
                    lastDskWriteKB = strtoul(mParts[9].c_str(), &pEdnd, 0);
                    dskWriteKB = lastDskWriteKB;
                }
                else
                {
                    lastDskWriteKB = dskWriteKB;
                    dskWriteKB = strtoul(mParts[9].c_str(), &pEdnd, 0);
                }
                // Skip dskWriteTicks
                // Skip dskInProg
                // Skip dskTicks
                // Skip dskWeighted
                lastStatsLoaded = true;
                fclose (fp);
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to open %s. Exiting.", getpid(), procDiskstatsFile.c_str());
                exit(10);
            }
            return;
        }
        unsigned long getDskRead() { return dskRead; }
        unsigned long getDskReadKB() { return dskReadKB; }
        unsigned long getDskWrite() { return dskWrite; }
        unsigned long getDskWriteKB() { return dskWriteKB; }
        unsigned long getLastDskRead() { return lastDskRead; }
        unsigned long getLastDskReadKB() { return lastDskReadKB; }
        unsigned long getLastDskWrite() { return lastDskWrite; }
        unsigned long getLastDskWriteKB() { return lastDskWriteKB; }
};
#endif
