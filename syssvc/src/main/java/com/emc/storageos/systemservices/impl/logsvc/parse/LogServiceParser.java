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

import java.util.Calendar;
import java.util.Date;

import com.emc.storageos.management.jmx.logging.ViPRHeaderPatternLayout;
import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.storageos.systemservices.impl.logsvc.util.LogUtil;

import com.emc.vipr.model.sys.logging.LogRequest;
import com.emc.vipr.model.sys.logging.LogSeverity;

/**
 * Parser class to parse lines from log file to LogMessage Objects
 * @author siy
 *
 */
public class LogServiceParser extends LogParser {
    private static Calendar logDate = Calendar.getInstance();

    // length of the time 2013-11-20 13:56:48,063 [
    private static final int TIME_LENGTH = 25;
    // milliseconds since epoch, will remain 13 digits for another 2 hundred years
    private static final int HEADER_TIMESTAMP_LENGTH = 13;
    /**
     * Parse line from file to LogMessage
     * If line does not match log format(it
     * is the message part for multiple lines log), return
     * LogMessage.CONTINUATION_LOGMESSAGE; if line matches log formant, time is
     * too late for time filter, return LogMessage.REJECTED_LAST_LOGMESSAGE if
     * line matches log formant, but does not match all filters, return
     * LogMessage.REJECTED_LOGMESSAGE; if line matches log formant and all
     * filters, return LogMessage object
     */
    @Override
    public LogMessage parseLine(String line, LogRequest info) {
        int lineLength = line.length();

        if (lineLength == ViPRHeaderPatternLayout.HEADER_START_LENGTH + HEADER_TIMESTAMP_LENGTH) {
            boolean isHeaderStart = true;
            for (int i = 0; i < ViPRHeaderPatternLayout.HEADER_START_LENGTH; i++ ) {
                if (line.charAt(i) != ViPRHeaderPatternLayout.HEADER_START_INDICATOR) {
                    isHeaderStart = false;
                    break;
                }
            }
            if (isHeaderStart) {
                // find the timestamp at the end of the header start line
                String timestampStr = line.substring(ViPRHeaderPatternLayout.HEADER_START_LENGTH);
                long timestamp = Long.parseLong(timestampStr);
                int inTime = LogUtil.timeInRange(new Date(timestamp), info.getStartTime(),
                        info.getEndTime());
                if (inTime < 0) { // too early
                    return LogMessage.REJECTED_LOGMESSAGE;
                } else if (inTime > 0) { // too late
                    return LogMessage.REJECTED_LAST_LOGMESSAGE;
                }
                LogMessage header = LogMessage.makeHeaderLog(timestamp);
                return header;
            }
        }
        
        if (lineLength <= TIME_LENGTH || line.charAt(4) != '-'
                || line.charAt(7) != '-' || line.charAt(10) != ' '
                || line.charAt(13) != ':' || line.charAt(16) != ':'
                || line.charAt(19) != ',' || line.charAt(23) != ' '
                || line.charAt(24) != '[') {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }
        /*
         * final String[] parts = line.substring(0,
         * timeLength).split("[\\s-:,]"); if (parts.length < 7) { return
         * LogMessage.CONTINUATION_LOGMESSAGE; }
         * 
         * int[] partsInt = new int[7]; for (int i = 0; i < 7; i++) {
         * partsInt[i] = toNumber(parts[i]); if (partsInt[i] < 0) { return
         * LogMessage.CONTINUATION_LOGMESSAGE; } }
         */
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
        String msStr = line.substring(20, 23);
        int ms = toNumber(msStr);
        if (ms < 0) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        final int endBracket = line.indexOf("]", TIME_LENGTH);
        if (endBracket < 0) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }
        if (endBracket - TIME_LENGTH > Short.MAX_VALUE) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        // Skip white spaces before level
        int levelStartIndex = endBracket + 1;
        while (levelStartIndex < lineLength
                && line.charAt(levelStartIndex) == ' ') {
            levelStartIndex++;
        }
        if (levelStartIndex >= lineLength || levelStartIndex > Short.MAX_VALUE) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        // Find the next white space after level
        int levelEndIndex = line.indexOf(' ', levelStartIndex);
        if (levelEndIndex < 0 || levelEndIndex - levelStartIndex > Short.MAX_VALUE) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }
        final String levelString = line.substring(levelStartIndex,
                levelEndIndex);
        final int level = LogSeverity.toLevel(levelString);
        if (level < 0) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        int fileNameStartIndex = levelEndIndex + 1;
        // Skip white spaces before file name
        while (fileNameStartIndex < lineLength
                && line.charAt(fileNameStartIndex) == ' ') {
            fileNameStartIndex++;
        }
        if (fileNameStartIndex >= lineLength || fileNameStartIndex > Short.MAX_VALUE) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }
        // Find the next white space after file name
        final int fileNameEndIndex = line.indexOf(' ', fileNameStartIndex);
        if (fileNameEndIndex < 0) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }
        if (fileNameEndIndex + 1 >= lineLength || fileNameEndIndex - fileNameStartIndex >
                Short.MAX_VALUE) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }
        if (line.charAt(fileNameEndIndex + 1) != '(') {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        int classNameStartIndex = fileNameStartIndex;
        int classNameEndIndex = fileNameEndIndex;
        if (line.charAt(fileNameEndIndex - 5) == '.') {
            //remove the trailing .java
            classNameEndIndex -= 5;
        }

        int rightParentheseIndex = line.indexOf(')', fileNameEndIndex + 1);
        if (rightParentheseIndex <= 0) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }
        String[] lineNumber = line.substring(fileNameEndIndex + 1,
                rightParentheseIndex).split(" ");
        if (lineNumber.length != 2) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }
        int lineNo = toNumber(lineNumber[1]);
        if (lineNo < 0) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        // rightParentheseIndex+1 is a white space
        int messageStartIndex = rightParentheseIndex + 2;
        if (rightParentheseIndex + 2 > lineLength) {
            // it could be true, like a stacktrace of an exception
            // in which case the first line is ""
            messageStartIndex = lineLength;
        }
        if (messageStartIndex > Short.MAX_VALUE) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        // test time filter
        // int inTime = inTimeRange(partsInt[0], partsInt[1], partsInt[2],
        // partsInt[3], partsInt[4], partsInt[5], partsInt[6],info);
        int inTime = inTimeRange(year, month, day, hour, min, sec, ms, info);
        if (inTime < 0) { // too early
            return LogMessage.REJECTED_LOGMESSAGE;
        } else if (inTime > 0) { // too late
            return LogMessage.REJECTED_LAST_LOGMESSAGE;
        }
        int matchLevel = matchLevelFilter(level, info);
        if (matchLevel > 0) { // level value bigger than request
            return LogMessage.REJECTED_LOGMESSAGE;
        }

        final int lineNumberStartIndex = line.indexOf(' ', fileNameEndIndex + 1) + 1;
        if (lineNumberStartIndex > Short.MAX_VALUE || rightParentheseIndex - lineNumberStartIndex > Short.MAX_VALUE) {
            return LogMessage.CONTINUATION_LOGMESSAGE;
        }

        LogMessage log = new LogMessage(getTime(year, month, day, hour, min, sec, ms), line.getBytes());
        log.setLogOffset(messageStartIndex); 
        log.setTimeBytes(0, TIME_LENGTH - 2);
        log.setThreadName(TIME_LENGTH, endBracket - TIME_LENGTH);
        log.setLevel(level);
        log.setFileName(classNameStartIndex, classNameEndIndex - classNameStartIndex);
        log.setLineNumber(lineNumberStartIndex, rightParentheseIndex - lineNumberStartIndex);

        return log;
    }
}
