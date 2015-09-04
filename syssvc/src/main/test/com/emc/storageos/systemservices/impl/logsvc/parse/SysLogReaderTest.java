/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc.parse;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.FileReader;

import org.junit.Ignore;
import org.junit.Test;

import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.storageos.systemservices.impl.logsvc.stream.LogReader;
import com.emc.storageos.systemservices.impl.logsvc.LogStatusInfo;
import com.emc.vipr.model.sys.logging.LogRequest;
import com.emc.vipr.model.sys.logging.LogSeverity;

public class SysLogReaderTest {

    /**
     * Test if readMessage() can read and parse syssvc file which has different
     * format logs correctly
     */
    @Test
    public void testAllLogFormat_SmallFile_NoFilter_SysParser()
            throws Exception {
        System.out
                .println("starting testAllLogFormat_SmallFile_NoFilter_SysParser");
        LogStatusInfo status = new LogStatusInfo();
        String filePath = "src/main/data/testReaderData/testSyslogParser.log";
        LogReader reader = null;
        LogRequest req = new LogRequest.Builder().build();
        reader = new LogReader(filePath, req, status, null);
        BufferedReader br = new BufferedReader(new FileReader(
                "src/main/data/testReaderData/testSyslogParser.log"));
        while (true) {
            LogMessage log = reader.readNextLogMessage();
            String line = br.readLine();
            if (line == null) {
                assertNull("Log should be null", log);
                break;
            }
            assertEquals("whole log message mismatch",
                    log.toStringOriginalFormatSysLog(), line);
        }
        br.close();
        System.out
                .println("done testAllLogFormat_SmallFile_NoFilter_SysParser");
    }

    /**
     * Test if reader could recognize the correct count of system logs
     */
    @Test
    public void testLogCount_SysParser() throws Exception {
        System.out.println("starting testLogCount_SysParser");
        LogStatusInfo status = new LogStatusInfo();
        final int WARN = 5;
        // four kinds of different patterns in file
        String filePath = "src/main/data/testReaderData/testSyslogParser.log";
        long logCount = 47;
        LogReader reader = null;
        LogRequest req = new LogRequest.Builder().logLevel(WARN).build();
        reader = new LogReader(filePath, req, status, null);
        while (true) {
            LogMessage log = reader.readNextLogMessage();
            if (log == null) {
                assertEquals("Log count should match", logCount,
                        reader.getLogCount());
                break;
            }
        }
        System.out.println("done testLogCount_SysParser");
    }

    /**
     * Test if reader could record correct status information
     */
    @Test
    public void testLogStatusInfo() throws Exception {
        System.out.println("starting testLogStatusInfo");
        LogStatusInfo status = new LogStatusInfo();
        String filePath = "src/main/data/testReaderData/testStatus.log";
        long logCount = 42;
        LogReader reader = null;
        LogRequest req = new LogRequest.Builder().build();
        reader = new LogReader(filePath, req, status, null);
        while (true) {
            LogMessage log = reader.readNextLogMessage();
            if (log == null) {
                assertEquals("Log count should match", logCount,
                        reader.getLogCount());
                break;
            }
        }
        BufferedReader br = new BufferedReader(new FileReader(
                "src/main/data/testReaderData/testStatusResult.log"));
        String line = br.readLine();
        int index = 0;
        while (line != null) {
            assertTrue("status's size is not correct!", status.getStatus()
                    .size() > index);
            assertEquals("status's contect is not correct!", line, status
                    .getStatus().get(index));
            index++;
            line = br.readLine();
        }
        assertEquals("status's size is not correct!",
                status.getStatus().size(), index);
        br.close();
        System.out.println("done testLogStatusInfo");
    }

    /**
     * Test if priority value is corrtect
     */
    @Test
    public void testPriority() throws Exception {
        System.out.println("starting testPriority");
        LogSyslogParser parser = new LogSyslogParser();
        LogRequest req = new LogRequest.Builder().build();
        String log1 = "2014-03-13 15:12:56 [auth] notice sudo: storageos : ";
        String log2 = "2014-03-13 15:36:26 [syslog] info syslog-ng[4043]";
        String log3 = "2014-03-13 16:27:54 [local7] warning NTP: [CONFIGURED, DEGRADED]";
        String log4 = "2014-03-11 17:12:30 [user] err auditd: ";
        String log5 = "2014-03-12 00:00:02 [mail] crit sendmail[10183]: NOQUEUE: SYSERR(root)";
        assertTrue(LogSeverity.toLevel(new String(parser.parseLine(log1, req).getLevel())) == 6);
        assertTrue(LogSeverity.toLevel(new String(parser.parseLine(log2, req).getLevel())) == 7);
        assertTrue(LogSeverity.toLevel(new String(parser.parseLine(log3, req).getLevel())) == 5);
        assertTrue(LogSeverity.toLevel(new String(parser.parseLine(log4, req).getLevel())) == 4);
        assertTrue(LogSeverity.toLevel(new String(parser.parseLine(log5, req).getLevel())) == 3);
        System.out.println("done testPriority");
    }

}
