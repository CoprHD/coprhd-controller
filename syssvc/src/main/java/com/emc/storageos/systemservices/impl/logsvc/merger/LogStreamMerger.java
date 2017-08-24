/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc.merger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.storageos.systemservices.impl.logsvc.LogSvcPropertiesLoader;
import com.emc.storageos.systemservices.impl.logsvc.stream.LogFileStream;
import com.emc.storageos.systemservices.impl.logsvc.util.LogFileFinder;
import com.emc.vipr.model.sys.logging.LogRequest;

/**
 * This class is responsible to read one line from each logstream and sort them based on timestamp.
 * This is done on per host basis.
 * 
 */
public class LogStreamMerger extends AbstractLogStreamMerger {
    // Logger reference.
    private static final Logger logger = LoggerFactory.getLogger(LogStreamMerger.class);

    private AtomicLong sizeCounter = new AtomicLong(0);

    /**
     * Merges all logs on this node based on time stamp
     * 
     * @param req
     * @param propertiesLoader
     */
    public LogStreamMerger(LogRequest req, LogSvcPropertiesLoader propertiesLoader) {
        logger.trace("LogStreamMerger()");
        this.request = req;
        LogFileFinder fileFinder = new LogFileFinder(propertiesLoader.getLogFilePaths(),
                propertiesLoader.getExcludedLogFilePaths());
        Map<String, List<File>> groupedLogFiles = fileFinder.findFilesGroupedByBaseName();

        List<String> groups = req.getBaseNames();
        if (groups == null || groups.isEmpty()) { // default set to all kinds of svcs
            groups = new ArrayList<>(groupedLogFiles.keySet());
        }

        logger.debug("log names: {}", groups);
        if (groups.retainAll(groupedLogFiles.keySet())) {
            logger.info("log names after filter: {}", groups);
            // TODO: what if groups become empty after filter
            // TODO: also this should probably go to log service
        }

        int size = groups.size();
        logStreamList = new LogFileStream[size];
        logHeads = new LogMessage[size];

        for (int i = 0; i < size; i++) {
            String service = groups.get(i);
            logStreamList[i] = new LogFileStream(service, groupedLogFiles.get(service), req,
                    status);
            logHeads[i] = null;// so that read next will continue
        }
    }

    protected void addFinishedStream(int i) {
        super.addFinishedStream(i);
        sizeCounter.addAndGet(((LogFileStream) logStreamList[i]).getTotalSizeCount());
    }

    public LogFileStream[] getStreamList() {
        LogFileStream[] logFileStreams = new LogFileStream[logStreamList.length];
        for (int i = 0; i < logStreamList.length; i++) {
            logFileStreams[i] = (LogFileStream) logStreamList[i];
        }
        return logFileStreams;
    }

    public long getFileSize() {
        return this.sizeCounter.get();
    }
}
