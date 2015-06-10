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
package com.emc.storageos.systemservices.impl.logsvc.parse;

import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.vipr.model.sys.logging.LogRequest;

/**
 * Parse nginx_access.log
 * A typical log message is like:
 *
 * 10.10.191.121 - root [15/May/2014:06:16:21 +0000] "GET /login HTTP/1.1" 200 25 "-"
 * "python-requests/2.2.1 CPython/2.6.8 Linux/3.0.101-0.8-default"
 */
public class LogNginxAccessParser extends LogParser {

    private static final Logger logger = LoggerFactory.getLogger(LogNginxAccessParser.class);

    private final int TIME_LENGTH = 20;

    @Override
    public LogMessage parseLine(String line, LogRequest info) {
        Date date = null;
        String msg = null;


        String[] splitLine = line.split(" ");
        if(splitLine.length < 10){
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        // splitLine[3] is like "[15/May/2014:06:16:21"
        String timeStr = splitLine[3].substring(1);
        if(timeStr.length() != TIME_LENGTH){
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        DateFormat format = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss");
        try{
            date = format.parse(timeStr);
        } catch(Exception e){
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        int inTime = inTimeRange(date, info);
        if (inTime < 0) { // too early
            return LogMessage.REJECTED_LOGMESSAGE;
        } else if (inTime > 0) { // too late
            return LogMessage.REJECTED_LAST_LOGMESSAGE;
        }

        int logOffset = line.indexOf(splitLine[5]);
        int timeBytesStartIndex = line.indexOf(splitLine[3]);
        if (logOffset > Short.MAX_VALUE)
            return LogMessage.CONTINUATION_LOGMESSAGE;
        if (timeBytesStartIndex + 1 > Short.MAX_VALUE)
            return LogMessage.CONTINUATION_LOGMESSAGE;

        LogMessage log = new LogMessage(date.getTime(), line.getBytes());
        log.setLogOffset(logOffset);
        log.setTimeBytes(timeBytesStartIndex + 1, TIME_LENGTH);

        // put the initial message (IP and user) as a following line.
        log.appendMessage(line.substring(0, timeBytesStartIndex).getBytes());

        return log;
    }
} 
