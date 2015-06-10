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
#ifndef _procVmstat_HXX
#define _procVmstat_HXX

//
// File parser class for /proc/vmstat
//
class procVmstat
{
    private:
        std::string procVmstatFile;
        bool lastStatsLoaded;
        unsigned long swapin;
        unsigned long swapout;
        unsigned long pagein;
        unsigned long pageout;
        unsigned long pagefault;
        unsigned long lastSwapin;
        unsigned long lastSwapout;
        unsigned long lastPagein;
        unsigned long lastPageout;
        unsigned long lastPagefault;

    public:
        procVmstat()
        {
            swapin = swapout = pagein = pageout = pagefault = lastSwapin = lastSwapout = lastPagein = lastPageout = lastPagefault = 0;
            lastStatsLoaded = false;
        }

        void gatherProcVmstatData(std::string baseProcDir)
        {
            std::string procVmstatFile;
            if (!baseProcDir.empty())
            {
                procVmstatFile += baseProcDir;
            }
            else
            {
                procVmstatFile += "/proc";
            }
            procVmstatFile += "/vmstat";
            std::string line;
            FILE *fp = fopen(procVmstatFile.c_str(), "rb");
            if (fp!=NULL)
            {
                int x = setvbuf(fp, (char *)NULL, _IOFBF, 512);
                char nextLine[2048];
                char *lineHeader, *pEdnd;
                lineHeader = pEdnd = NULL;
                while (fgets(nextLine, 2048, fp) != NULL)
                {
                    lineHeader = strtok(nextLine," ");
                    // Memory Swapped in/sec.
                    if (!strcmp(lineHeader, "pswpin"))
                    {
                        if (false == lastStatsLoaded)
                        {
                            char *lastSwapinS = strtok(NULL, " ");
                            lastSwapin = strtoul(lastSwapinS, &pEdnd, 0);
                            swapin = lastSwapin;
                        }
                        else
                        {
                            char *swapinS = strtok(NULL, " ");
                            lastSwapin = swapin;
                            swapin = strtoul(swapinS, &pEdnd, 0);
                        }
                    }
                    // Memory Swapped out/sec.
                    if (!strcmp(lineHeader, "pswpout"))
                    {
                        if (false == lastStatsLoaded)
                        {
                            char *lastSwapoutS = strtok(NULL, " ");
                            lastSwapout = strtoul(lastSwapoutS, &pEdnd, 0);
                            swapout = lastSwapout;
                        }
                        else
                        {
                            char *swapoutS = strtok(NULL, " ");
                            lastSwapout = swapout;
                            swapout = strtoul(swapoutS, &pEdnd, 0);
                        }
                    }
                    // Memory paged in in/sec.
                    if (!strcmp(lineHeader, "pgpgin"))
                    {
                        if (false == lastStatsLoaded)
                        {
                            char *lastPageinS = strtok(NULL, " ");
                            lastPagein = strtoul(lastPageinS, &pEdnd, 0);
                            pagein = lastPagein;
                        }
                        else
                        {
                            char *pageinS = strtok(NULL, " ");
                            lastPagein = pagein;
                            pagein = strtoul(pageinS, &pEdnd, 0);
                        }
                    }
                    // Total number of pages written by block devices.
                    if (!strcmp(lineHeader, "pgpgout"))
                    {
                        if (false == lastStatsLoaded)
                        {
                            char *lastPageoutS = strtok(NULL, " ");
                            lastPageout = strtoul(lastPageoutS, &pEdnd, 0);
                            pageout = lastPageout;
                        }
                        else
                        {
                            char *pageoutS = strtok(NULL, " ");
                            lastPageout = pageout;
                            pageout = strtoul(pageoutS, &pEdnd, 0);
                        }
                    }
                    // Page faults.
                    if (!strcmp(lineHeader, "pgfault"))
                    {
                        if (false == lastStatsLoaded)
                        {
                            char *lastPagefaultS = strtok(NULL, " ");
                            lastPagefault = strtoul(lastPagefaultS, &pEdnd, 0);
                            pagefault = lastPagefault;
                        }
                        else
                        {
                            char *pagefaultS = strtok(NULL, " ");
                            lastPagefault = pagefault;
                            pagefault = strtoul(pagefaultS, &pEdnd, 0);
                        }
                    }
                }
                lastStatsLoaded = true;
                fclose (fp);
            }
            else
            {
                syslog(LOG_ERR, "nodestats process [process ID %d], Unable to open %s. Exiting.", getpid(), procVmstatFile.c_str());
                exit(10);
            }
            return;
        }
        unsigned long getSwapin() { return swapin; }
        unsigned long getSwapout() { return swapout; }
        unsigned long getPagein() { return pagein; }
        unsigned long getPageout() { return pageout; }
        unsigned long getPagefault() { return pagefault; }
        unsigned long getLastSwapin() { return lastSwapin; }
        unsigned long getLastSwapout() { return lastSwapout; }
        unsigned long getLastPagein() { return lastPagein; }
        unsigned long getLastPageout() { return lastPageout; }
        unsigned long getLastPagefault() { return lastPagefault; }
};
#endif
