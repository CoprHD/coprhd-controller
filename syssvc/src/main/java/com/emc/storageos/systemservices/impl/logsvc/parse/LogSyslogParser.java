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

import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.vipr.model.sys.logging.LogRequest;
import com.emc.vipr.model.sys.logging.LogSeverity;

/**
 * Parse syslog line to LogMessage
 *
 * @author siy
 *
 */
public class LogSyslogParser extends LogParser {
    private static final int TIME_LENGTH = 21;
    /**
     * Parse line from file to LogMessage If line does not match log format(it
     * is the message part for multiple lines log), return
     * LogMessage.CONTINUATION_LOGMESSAGE; if line matches log formant, time is
     * too late for time filter, return LogMessage.REJECTED_LAST_LOGMESSAGE if
     * line matches log formant, but does not match all filters, return
     * LogMessage.REJECTED_LOGMESSAGE; if line matches log formant and all
     * filters, return LogMessage object
     */
    @Override
    public LogMessage parseLine(String line, LogRequest info) {
        // length of the time 2013-11-20 13:56:48 [
        int lineLength = line.length();
        if (lineLength <= TIME_LENGTH || line.charAt(4) != '-'
                || line.charAt(7) != '-' || line.charAt(10) != ' '
                || line.charAt(13) != ':' || line.charAt(16) != ':'
                || line.charAt(19) != ' ' || line.charAt(20) != '[') {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        String yearStr = line.substring(0, 4);
        int year = toNumber(yearStr);
        if (year < 0) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }
        String monthStr = line.substring(5, 7);
        int month = toNumber(monthStr);
        if (month < 0) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }
        String dayStr = line.substring(8, 10);
        int day = toNumber(dayStr);
        if (day < 0) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }
        String hourStr = line.substring(11, 13);
        int hour = toNumber(hourStr);
        if (hour < 0) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }
        String minStr = line.substring(14, 16);
        int min = toNumber(minStr);
        if (min < 0) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }
        String secStr = line.substring(17, 19);
        int sec = toNumber(secStr);
        if (sec < 0) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        final int endBracket = line.indexOf("]", TIME_LENGTH);
        if (endBracket < 0 || endBracket - TIME_LENGTH > Short.MAX_VALUE) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        final int priorityStartIndex = endBracket + 2; // endBracket + 1 is
                                                        // white space
        if (priorityStartIndex > lineLength || priorityStartIndex > Short.MAX_VALUE) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }
        final int priorityEndIndex = line.indexOf(" ", priorityStartIndex);
        if (priorityEndIndex + 1 > lineLength || priorityEndIndex + 1 > Short.MAX_VALUE ||
                priorityEndIndex - priorityStartIndex > Short.MAX_VALUE) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        // test time filter
        int inTime = inTimeRange(year, month, day, hour, min, sec, 0, info);
        if (inTime < 0) { // too early
            return LogMessage.REJECTED_LOGMESSAGE;
        } else if (inTime > 0) { // too late
            return LogMessage.REJECTED_LAST_LOGMESSAGE;
        }
        final String priority = line.substring(priorityStartIndex,
                priorityEndIndex);
        final int level = LogSeverity.toLevel(priority);
        if (level < 0) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }
        int matchLevel = matchLevelFilter(level, info);
        if (matchLevel > 0) { // level value bigger then request
            return LogMessage.REJECTED_LOGMESSAGE;
        }

        LogMessage log = new LogMessage(getTime(year, month, day, hour, min, sec, 0),
                line.getBytes());
        log.setLogOffset(priorityEndIndex + 1);
        log.setTimeBytes(0, TIME_LENGTH - 2);
        log.setThreadName(TIME_LENGTH, endBracket - TIME_LENGTH);
        log.setLevel(level);

        return log;
    }
}
