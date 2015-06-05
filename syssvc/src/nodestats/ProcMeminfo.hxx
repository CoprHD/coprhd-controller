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
#ifndef _ProcMeminfo_HXX
#define _ProcMeminfo_HXX

//
// File parser class for /proc/meminfo
//
class procMeminfo
{
    private:
        std::string procMeminfoFile;
        unsigned long memTot;
        unsigned long memFree;
        unsigned long memShared;
        unsigned long memBuf;
        unsigned long memCached;
        unsigned long swapTotal;
        unsigned long swapFree;
    public:
        procMeminfo()
        {
            memTot = memFree = memShared = memBuf = memCached = swapTotal = swapFree = 0;
        }

        void gatherProcMeminfoData(std::string baseProcDir)
        {
            memTot = memFree = memShared = memBuf = memCached = swapTotal = swapFree = 0;
            std::string procMeminfoFile;
            if (!baseProcDir.empty())
            {
                procMeminfoFile += baseProcDir;
            }
            else
            {
                procMeminfoFile += "/proc";
            }
            procMeminfoFile += "/meminfo";
            std::string line;
            FILE *fp = fopen(procMeminfoFile.c_str(), "rb");
            if (fp!=NULL)
            {
                int x = setvbuf(fp, (char *)NULL, _IOFBF, 512);
                char nextLine[2048];
                char *lineHeader, *pEdnd;
                lineHeader = pEdnd = NULL;
                while (fgets(nextLine, 2048, fp) != NULL)
                {
                    //*logFile << "The current line is:  " << nextLine << endl;
                    lineHeader = strtok(nextLine,":");
                    // Total physical memory.
                    if (!strcmp(lineHeader, "MemTotal"))
                    {
                        char *memTotS = strtok(NULL, " ");
                        trimWhiteSpace(memTotS);
                        memTot = strtoul(memTotS, &pEdnd, 0);
                    }
                    // Unallocated physical memory.
                    if (!strcmp(lineHeader, "MemFree"))
                    {
                        char *memFreeS = strtok(NULL, " ");
                        trimWhiteSpace(memFreeS);
                        memFree = strtoul(memFreeS, &pEdnd, 0);
                    }
                    // Shared memory.
                    if (!strcmp(lineHeader, "Shmem"))
                    {
                        char *memSharedS = strtok(NULL, " ");
                        trimWhiteSpace(memSharedS);
                        memShared = strtoul(memSharedS, &pEdnd, 0);
                    }
                    // The amount of memory used as buffers.
                    if (!strcmp(lineHeader, "Buffers"))
                    {
                        char *memBufS = strtok(NULL, " ");
                        trimWhiteSpace(memBufS);
                        memBuf = strtoul(memBufS, &pEdnd, 0);
                    }
                    // The amount of memory used as cache.
                    if (!strcmp(lineHeader, "Cached"))
                    {
                        char *memCachedS = strtok(NULL, " ");
                        trimWhiteSpace(memCachedS);
                        memCached = strtoul(memCachedS, &pEdnd, 0);
                    }
                    // Total swap.
                    if (!strcmp(lineHeader, "SwapTotal"))
                    {
                        char *swapTotalS = strtok(NULL, " ");
                        trimWhiteSpace(swapTotalS);
                        swapTotal = strtoul(swapTotalS, &pEdnd, 0);
                    }
                    // Free swap.
                    if (!strcmp(lineHeader, "SwapFree"))
                    {
                        char *swapFreeS = strtok(NULL, " ");
                        trimWhiteSpace(swapFreeS);
                        swapFree = strtoul(swapFreeS, &pEdnd, 0);
                    }
                }
                fclose (fp);
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to open %s. Exiting.", getpid(), procMeminfoFile.c_str());
                exit(10);
            }
            return;
        }

        char *trimWhiteSpace(char *str)
        {
            char *end;    
            // Trim leading space.
            while(isspace(*str))
                str++;
            if (*str == 0)
                // All spaces?
                return str;
            // Trim trailing space.
            end = str + strlen(str) - 1;
            while(end > str && isspace(*end))
                end--;
            // Write new null terminator.
            *(end+1) = 0;
            return str;
        }

        unsigned long getMemTot() { return memTot; }
        unsigned long getMemFree() { return memFree; }
        unsigned long getMemShared() { return memShared; }
        unsigned long getMemBuf() { return memBuf; }
        unsigned long getMemCached() { return memCached; }
        unsigned long getSwapTotal() { return swapTotal; }
        unsigned long getSwapFree() { return swapFree; }
};
#endif
