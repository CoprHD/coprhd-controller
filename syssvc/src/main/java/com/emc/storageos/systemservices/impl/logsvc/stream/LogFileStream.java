/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc.stream;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.compress.compressors.CompressorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.storageos.systemservices.impl.logsvc.LogStatusInfo;
import com.emc.storageos.systemservices.impl.logsvc.util.LogUtil;
import com.emc.vipr.model.sys.logging.LogRequest;

public class LogFileStream implements LogStream {
    // Logger reference.
    private static final Logger logger = LoggerFactory.getLogger(LogFileStream.class);

    private final String basename;
    private final List<String> logPaths;
    private LogReader reader;
    private LogRequest request;
    private LogStatusInfo status;

    // number of logs get from LogReader
    private AtomicLong logCounter = new AtomicLong(0);
    // finished files number
    private AtomicInteger fileCounter = new AtomicInteger(0);
    // file size counter, sum up all read files
    private AtomicLong sizeCounter = new AtomicLong(0); // file size counter, sum up all read files
    private List<LogMessage> logs = new LinkedList<>(); // all logMessages which has the same timeStamp
    private LogMessage currentLog; // defaults to null
    private long prevLogTime; // defaults to 0

    public LogFileStream(String basename, List<File> logFiles, LogRequest req,
            LogStatusInfo status) {
        logger.trace("LogFileStream()");
        this.request = req;
        this.basename = basename;
        this.logPaths = setLogPaths(logFiles);
        reader = null;
        this.status = status;
    }

    /**
     * Set log files related to a service, sorted by last modification time.
     * 
     * @param logFiles
     * @return list of log file paths for a given basename sorted by time
     * @throws IOException
     */
    private List<String> setLogPaths(List<File> logFiles) {
        logger.trace("set path");
        List<String> names = new ArrayList<String>();
        try {
            names = LogUtil.fileNameWildCardSearch(logFiles, request.getStartTime(),
                    request.getEndTime());
            for (String name : names) {
                logger.debug("Found - " + name);
            }
        } catch (IOException e) {
            logger.error("IOException in setLogPaths:", e);
        }
        return names;
    }

    public List<String> getLogPaths() {
        return this.logPaths;
    }

    /**
     * Read log files one by one and, line by line from the data folder.
     * 
     * @return
     * @throws IOException
     * @throws CompressorException
     */
    @Override
    public LogMessage readNextLogMessage() {
        while (true) {
            if (fileCounter.get() >= logPaths.size()) {
                break;
            }

            // open new log file and new Reader
            if (reader == null) {
                String filePath = logPaths.get(fileCounter.get());
                try {
                    reader = new LogReader(filePath, request, status, basename);
                } catch (Exception e) {
                    status.append(String.format("Failed to open log file %s", e.getMessage()));
                    logger.error("Failed to generate log reader for {}", e.getMessage());
                    return null;
                }
                logger.debug("Reading file - " + filePath);
                File f = new File(filePath);
                sizeCounter.addAndGet(f.length());
            }

            if (currentLog != null) {
                prevLogTime = currentLog.getTime();
            }

            currentLog = reader.readNextLogMessage();
            if (currentLog != null) {
                logCounter.incrementAndGet();
                currentLog.setService(LogUtil.serviceToBytes(basename));
                // we cannot determine until the current log message has been read out
                if (!LogUtil.permitCurrentLog(request.getMaxCount(), logCounter.get(),
                        currentLog.getTime(), prevLogTime)) {
                    break;
                }

                return currentLog;
            } else {
                reader = null;
                fileCounter.incrementAndGet();
            }
        }
        return null;
    }

    public long getTotalLogCount() {
        return logCounter.get();
    }

    public long getTotalSizeCount() {
        return sizeCounter.get();
    }

    public int getFileCount() {
        return this.fileCounter.get();
    }

    public LogRequest getRequest() {
        return request;
    }

    public void setRequest(LogRequest request) {
        this.request = request;
    }
}
