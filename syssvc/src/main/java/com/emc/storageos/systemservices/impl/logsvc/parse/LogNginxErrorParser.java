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
import com.emc.vipr.model.sys.logging.LogSeverity;

/**
 * Parse nginx_error.log
 * A typical log message is like:
 *
 * 2014/05/15 03:34:32 [error] 3575#0: *4 no live upstreams while connecting to upstream,
 * client: 152.62.40.124, server: localhost, request: "GET /security/authenticated HTTP/1.1",
 * upstream: "https://portal/security/authenticated", host: "10.247.101.162", referrer:
 * "https://10.247.101.162/maintenance?targetUrl=%2Fsetup%2Flicense"
 */
public class LogNginxErrorParser extends LogParser {

    private static final Logger logger = LoggerFactory.getLogger(LogNginxErrorParser.class);

    private final int TIME_LENGTH = 19;

    @Override
    public LogMessage parseLine(String line, LogRequest info) {
        Date date = null;
        String msg = null;

        int lineLength = line.length();
        if (lineLength <= TIME_LENGTH || line.charAt(4) != '/'
                || line.charAt(7) != '/' || line.charAt(10) != ' '
                || line.charAt(13) != ':' || line.charAt(16) != ':'
                || line.charAt(19) != ' ') {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        String timeStr = line.substring(0, TIME_LENGTH);
        DateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        try {
            date = format.parse(timeStr);
        } catch (Exception e) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        int inTime = inTimeRange(date, info);
        if (inTime < 0) { // too early
            return LogMessage.REJECTED_LOGMESSAGE;
        } else if (inTime > 0) { // too late
            return LogMessage.REJECTED_LAST_LOGMESSAGE;
        }

        int levelStartIndex = line.indexOf("[", TIME_LENGTH) + 1;
        if (levelStartIndex >= lineLength || levelStartIndex > Short.MAX_VALUE) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        int levelEndIndex = line.indexOf("]", levelStartIndex);
        if (levelEndIndex < 0 || levelEndIndex - levelStartIndex > Short.MAX_VALUE) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        final String levelString = line.substring(levelStartIndex, levelEndIndex);
        final int level = LogSeverity.toLevel(levelString);
        if (level < 0) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }
        int matchLevel = matchLevelFilter(level, info);
        if (matchLevel > 0) { // level value bigger than request
            return LogMessage.REJECTED_LOGMESSAGE;
        }

        LogMessage log = new LogMessage(date.getTime(), line.getBytes());
        log.setLogOffset(TIME_LENGTH + 1);
        log.setTimeBytes(0, TIME_LENGTH);
        log.setLevel(level);

        return log;
    }
}
