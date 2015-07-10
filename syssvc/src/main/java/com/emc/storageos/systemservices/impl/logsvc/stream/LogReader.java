/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.logsvc.stream;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.compress.compressors.CompressorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.storageos.systemservices.impl.logsvc.LogStatusInfo;
import com.emc.storageos.systemservices.impl.logsvc.parse.LogNginxAccessParser;
import com.emc.storageos.systemservices.impl.logsvc.parse.LogNginxErrorParser;
import com.emc.storageos.systemservices.impl.logsvc.parse.LogParser;
import com.emc.storageos.systemservices.impl.logsvc.parse.LogServiceParser;
import com.emc.storageos.systemservices.impl.logsvc.parse.LogSyslogParser;
import com.emc.storageos.systemservices.impl.logsvc.util.LogUtil;
import com.emc.vipr.model.sys.logging.LogRequest;

/**
 * Reader that reads logs from the specific file, regular or compressed file.
 * Parse each log to LogMessage
 * @author siy
 */
public class LogReader implements LogStream {
    private BufferedReader reader;
    private LogRequest request;
    private long logCount;
    private LogMessage currentLog = null;
    private LogParser parser =  null;
    private static List<LogParser> parserTable = new LinkedList<>();
    private LogStatusInfo status = null;
    private final String filePath;
    private int fileLineNumber = 0;
    private String service;
    private Pattern pattern;
    
     // Logger reference.
    private static final Logger logger = LoggerFactory.getLogger(LogReader.class);
    
    static {
        LogParser logSvcParser = new LogServiceParser();
        LogParser logSyslogParser = new LogSyslogParser();
        LogParser logNginxAccessParser = new LogNginxAccessParser();
        LogParser logNginxErrorParser = new LogNginxErrorParser();
        parserTable.add(logSvcParser);
        parserTable.add(logSyslogParser);
        parserTable.add(logNginxAccessParser);
        parserTable.add(logNginxErrorParser);
    }
    
    public LogReader(String path, LogRequest req, LogStatusInfo status, String service) throws IOException,
            CompressorException {
        if (LogUtil.logFileZipped(path)) {
            reader = LogUtil.getBufferedReaderForBZ2File(path);
        } else {
            reader = new BufferedReader(new FileReader(path));
        }
        request = req;
        if (req.getRegex() != null)
            pattern = Pattern.compile(req.getRegex(), Pattern.DOTALL|Pattern.MULTILINE);
        this.status = status;
        this.filePath = path;
        this.service = service;
    }

    /**
     * Read one log message from log file
     * 
     * @return
     * @throws IOException
     */
    @Override
    public LogMessage readNextLogMessage() {
//        logger.info("readMessage()");
        try{
            if (reader == null) {
                return null;
            }
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    close();
                    if (currentLog != null && matchRegex(currentLog)) {
                        incrementLogCount(currentLog);
                        return currentLog;
                    }
                    return null;
                }
                fileLineNumber++;
                LogMessage nextLog = null;
                if(parser != null) { // already find the match parser
                    nextLog = parser.parseLine(line, request);
                } else { // match parser
                    for(LogParser parser : parserTable) {
                        nextLog = parser.parseLine(line, request);
                        if(!nextLog.isContinuation()) {
                            this.parser  = parser;
                            break;
                        }
                    }
                    // this line does not match all parsers, skip it and write it into status
                    if(nextLog == null || nextLog.isContinuation()) {
                        status.appendInfo(this.filePath, fileLineNumber);
                        continue;
                    }
                }
                if (nextLog.isContinuation()) {
                    if (currentLog != null) {
                        currentLog.appendMessage(LogUtil.stringToBytes(line));
                    }
                } else if (nextLog.isRejected()) {
                    // matches the log pattern, but does not match filters
                    // logs are only rejected in the first line
                    if (currentLog != null) {
                        LogMessage returnedLog = currentLog;
                        currentLog = null;

                        if (matchRegex(returnedLog)) {
                            incrementLogCount(returnedLog);
                            return returnedLog;
                        }
                        // else read the next line
                    }
                } else if (nextLog.isRejectedLast()) { // log is too late
                    if (currentLog != null) {
                        LogMessage returnedLog = currentLog;
                        currentLog = null;

                        if (matchRegex(returnedLog)) {
                            incrementLogCount(returnedLog);
                            return returnedLog;
                        }
                        // else break from the loop
                    }
                    break;
                } else { // accepted as the first line of a new log message
                    if (currentLog != null) {
                        LogMessage returnedLog = currentLog;
                        currentLog = nextLog;

                        // in case of multiple adjacent header logs
                        // skip all but the last one
                        if (returnedLog.isHeader() && currentLog.isHeader())
                            continue;

                        if (matchRegex(returnedLog)) {
                            incrementLogCount(returnedLog);
                            return returnedLog;
                        }
                        // else read the next line
                    } else
                        currentLog = nextLog;
                }
            }
        } catch (IOException e) {
            status.appendErrFileName(filePath);
        }
        return null;
    }

    private void incrementLogCount(LogMessage returnedLog) {
        if (!returnedLog.isHeader())
            logCount++;
    }

    private void close() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (Exception e) {
            logger.error("failed to close LogReader:", e);
        } finally {
            reader = null;
        }
    }

    private boolean matchRegex(LogMessage message) {
        if (pattern == null)
            return true;

        if (message == null)
            return false;

        return pattern.matcher(new String(message.getLogContent())).matches();
    }

    public LogRequest getRequest() {
        return request;
    }

    public void setRequest(LogRequest request) {
        this.request = request;
    }

    public long getLogCount() {
        return this.logCount;
    }

}
