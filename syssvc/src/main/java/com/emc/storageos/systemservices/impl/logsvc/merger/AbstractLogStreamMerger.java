/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc.merger;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.compress.compressors.CompressorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.storageos.systemservices.impl.logsvc.LogStatusInfo;
import com.emc.storageos.systemservices.impl.logsvc.stream.LogStream;
import com.emc.storageos.systemservices.impl.logsvc.util.LogUtil;
import com.emc.vipr.model.sys.logging.LogRequest;

/**
 * This class is responsible to read one line from each logstream and sort them based on timestamp.
 * This is done on per host basis.
 * 
 */
public abstract class AbstractLogStreamMerger {
    protected LogStream[] logStreamList;
    protected LogMessage[] logHeads;
    protected LogRequest request;
    protected LogStatusInfo status = new LogStatusInfo();
    private Set<Integer> finishedList = new HashSet<>();

    private boolean finished = false;
    private AtomicLong logCounter = new AtomicLong(0);
    private int finishedCount = 0; // finished streams
    private long prevLogTime; // defaults to 0

    // Logger reference.
    private static final Logger logger = LoggerFactory.getLogger(AbstractLogStreamMerger.class);

    /**
     * This is the routine handles the request, sends back the response(outputstream)
     * 
     * @throws java.io.IOException
     * @throws org.apache.commons.compress.compressors.CompressorException
     */
    public LogMessage readNextMergedLogMessage() throws IOException, CompressorException {
        LogMessage oldestResult = null;
        LogMessage oldest = null;
        int index = -1;
        if (finishedCount == logStreamList.length) {
            setFinished(true);
            return null;
        }
        // poll the streams to get the oldest log message
        for (int i = 0; i < logStreamList.length; i++) {
            if (finishedList.contains(i)) {
                continue;
            }
            if (logHeads[i] == null) {
                logHeads[i] = logStreamList[i].readNextLogMessage();
                if (logHeads[i] == null) { // finished
                    addFinishedStream(i);
                    finishedCount++;
                    logger.debug("merger counter={}", logCounter);
                    continue;
                }
            }
            // logs[i] should not be null now
            if (oldest == null || logHeads[i].getTime() < oldest.getTime()) {
                oldest = logHeads[i];
                index = i;
            }
        }
        if (oldest != null) {
            logHeads[index] = null;
            logCounter.addAndGet(1);
            if (LogUtil.permitCurrentLog(request.getMaxCount(), logCounter.get(),
                    oldest.getTime(), prevLogTime)) {
                oldestResult = oldest;
            }
            prevLogTime = oldest.getTime();
        }
        return oldestResult;
    }

    protected void addFinishedStream(int i) {
        finishedList.add(i);
    }

    public LogRequest getRequest() {
        return request;
    }

    public void setRequest(LogRequest request) {
        this.request = request;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public long getLogCount() {
        return this.logCounter.get();
    }

    public LogStatusInfo getStatus() {
        return this.status;
    }

    public void clearStatus() {
        this.status.clear();
    }
}
