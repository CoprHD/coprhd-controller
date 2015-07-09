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
package com.emc.storageos.systemservices.impl.logsvc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.emc.storageos.systemservices.impl.logsvc.stream.LogFileStream;
import com.emc.vipr.model.sys.logging.LogRequest;
import com.emc.vipr.model.sys.logging.LogSeverity;

public class LogStreamTest {
    private static final Logger logger = LoggerFactory.getLogger(LogStreamTest.class);
    private static final String HARD_CODE_PATH = File.separator +"data" + File.separator + "shared-logging" + 
			File.separator + "internalLibraries" + File.separator + "logging";
    private static final String PATH = File.separator + "opt" + File.separator + "storageos" + File.separator + "logs";
   
    String timeStampFilePath = null;
    String timeRangeFilePath = null;
    
    @Before
    public void before() {
       String log1 = "2014-01-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       String log2 = "2014-01-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       String log3 = "2014-01-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       String log4 = "2014-01-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       String log5 = "2014-01-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       String log6 = "2014-02-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       String log7 = "2014-02-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       String log8 = "2014-02-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       String log9 = "2014-02-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       String log10 = "2014-02-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       String log11 = "2014-02-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       String log12 = "2014-02-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       String log13 = "2014-02-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       String log14 = "2014-02-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       String log15 = "2014-02-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       String log16 = "2014-02-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       
       String log17 = "2013-11-19 16:38:15,683 [pool-6-thread-1]  WARN  AlertsLogger.java (line 80) NTP: [CONFIGURED, INVALID].Details: network_ntpserver=128.221.12.10 [CONFIGURED, UNREACHABLE";
       String log18 = "2013-11-21 16:38:15,683 [pool-6-thread-1]  WARN  AlertsLogger.java (line 80) NTP: [CONFIGURED, INVALID].Details: network_ntpserver=128.221.12.10 [CONFIGURED, UNREACHABLE";
       String log19 = "2014-01-15 17:07:58,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       String log20 = "2014-01-17 17:07:59,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
       
       timeStampFilePath = PATH + File.separator + "timeStamp.log";
       timeRangeFilePath = PATH + File.separator + "timeRange.log";
       
       //initialize log file
       try(FileOutputStream fos = new FileOutputStream(timeStampFilePath);
    		   OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF8");
    		   BufferedWriter out  = new BufferedWriter(osw)) {
    	   			out.write(log1); out.newLine();
    	   			out.write(log2); out.newLine();
    	   			out.write(log3); out.newLine();
    	   			out.write(log4); out.newLine();
    	   			out.write(log5); out.newLine();
    	   			out.write(log6); out.newLine();
    	   			out.write(log7); out.newLine();
    	   			out.write(log8); out.newLine();
    	   			out.write(log9); out.newLine();
    	   			out.write(log10); out.newLine();
    	   			out.write(log11); out.newLine();
    	   			out.write(log12); out.newLine();
    	   			out.write(log13); out.newLine();
    	   			out.write(log14); out.newLine();
    	   			out.write(log15); out.newLine();
    	   			out.write(log16); out.flush();
       } catch(Exception e) {
    	   logger.error("Exception in writing nginx access log file for syssvc test", e);
       }
       
       try(FileOutputStream fos = new FileOutputStream(timeRangeFilePath);
    		   OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF8");
    		   BufferedWriter out  = new BufferedWriter(osw)) {
    	   			out.write(log17); out.newLine();
    	   			out.write(log18); out.newLine();
    	   			out.write(log19); out.newLine();
    	   			out.write(log1); out.newLine();
    	   			out.write(log20);out.flush();
       } catch(Exception e) {
    	   logger.error("Exception in writing nginx access log file for syssvc test", e);
       }
    }

    /**
     * Test if LogStream can find all files need to read correctly with time
     * range filter
     * This test can only be tested on root@10.10.191.121
     */
    @Test
    @Ignore
    public void testGetFilePathTimeFilter() {
        String svcName = "controllersvc";
        List<File> files = new ArrayList<File>();
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140110-231105.gz"));
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140114-151216.gz"));
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140112-175108.gz"));
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140116-031010.gz"));
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log"));
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140109-101159.gz"));
        Calendar calendar = Calendar.getInstance();
        calendar.set(2014, 0, 10, 0, 0);
        Date startTimeFilter = calendar.getTime();
        calendar.set(2014, 0, 14, 16, 38);
        Date endTimeFilter = calendar.getTime();
        LogRequest req = new LogRequest.Builder().startTime(startTimeFilter)
                .endTime(endTimeFilter).build();
        LogFileStream stream = new LogFileStream(svcName, files, req, null);
        List<String> paths = stream.getLogPaths();
        List<String> result = new ArrayList<String>();
        result.add(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140110-231105.gz");
        result.add(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140112-175108.gz");
        result.add(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140114-151216.gz");
        result.add(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140116-031010.gz");
        int i = 0;
        assertTrue("Time range and svc filters are not correct",
                paths.size() == result.size());
        for (String str : paths) {
            assertEquals("Time range and svc filters are not correct", str,
                    result.get(i));
            i++;
        }
    }

    /**
     * Test if LogStream can find all files need to read correctly And test if
     * LogStream can deal null time filter value correctly
     * This test can only be tested on root@10.10.191.121
     */
    @Test
    @Ignore
    public void testGetFilePathNullTimeFilters() {
    	String svcName = "controllersvc";
        List<File> files = new ArrayList<File>();
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140110-231105.gz"));
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140114-151216.gz"));
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140112-175108.gz"));
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140116-031010.gz"));
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log"));
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140109-101159.gz"));
        LogRequest req = new LogRequest.Builder().build();
        LogStatusInfo status = new LogStatusInfo();
        LogFileStream stream = new LogFileStream(svcName,files, req, status);
        List<String> paths = stream.getLogPaths();
        List<String> result = new ArrayList<String>();
        result.add(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140109-101159.gz");
        result.add(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140110-231105.gz");
        result.add(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140112-175108.gz");
        result.add(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140114-151216.gz");
        result.add(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140116-031010.gz");
        result.add(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log");

        int i = 0;
        assertEquals("Time range and svc filters are not correct",
                paths.size(), result.size());
        for (String str : paths) {
            assertEquals("Time range and svc filters are not correct", str,
                    result.get(i));
            i++;
        }
    }

    /**
     * Test if logCounter in LogStream can record number of logs correctly And
     * test if sizeCounter in LogStream can record count of total file size
     * correctly Test if fileCounter in LogStream can record file count
     * correctly
     * @throws Exception
     * This test can only be tested on root@10.10.191.121
     */
    @Test
    @Ignore
    public void testReadLineNoFilter() throws Exception {
        String svcName = "dbsvc-";
        // dbsvc-0.log + dbsvc-1.log + dbsvc-2.log
        final long lineNumber = 500000 + 500000 + 10;
        File f1 = new File(HARD_CODE_PATH + "/src/main/data/streamData/dbsvc-0.log");
        File f2 = new File(HARD_CODE_PATH + "/src/main/data/streamData/dbsvc-1.log");
        File f3 = new File(HARD_CODE_PATH + "/src/main/data/streamData/dbsvc-2.log");
        List<File> files = new ArrayList<>();
        files.add(f1);
        files.add(f2);
        files.add(f3);
        final long totalBytes = f1.length() + f2.length() + f3.length();
        LogStatusInfo status = new LogStatusInfo();
        LogRequest req = new LogRequest.Builder().build();
        LogFileStream stream = new LogFileStream(svcName,files, req, status);
        // file count
        final int fileNumber = stream.getLogPaths().size();
        while (true) {
            LogMessage log = stream.readNextLogMessage();
            if (log == null) {
                assertEquals("Total logs number should match", lineNumber,
                        stream.getTotalLogCount());
                assertEquals("Total file size should match", totalBytes,
                        stream.getTotalSizeCount());
                assertEquals("Total file number should match", fileNumber,
                        stream.getFileCount());
                break;
            }
        }
    }

    /**
     * Test max count filter totalCount < maxCount + MAX_OVERFLOW
     * 
     * @throws Exception
     */
    @Test
    @Ignore
    public void testReadNextLogMaxCountFilterLessThanMAXCOUNTOVERFLOW() throws Exception {
        List<File> files = new ArrayList<>();
        files.add(new File(timeStampFilePath));
        long maxCount = 1; // should get 5 log Messages
        int count = 0;
        LogRequest req = new LogRequest.Builder().maxCont(maxCount).build();
        LogStatusInfo status = new LogStatusInfo();
        LogFileStream stream = new LogFileStream("",files, req, status);
        while (true) {
            LogMessage log = stream.readNextLogMessage();
            if (log == null) {
                assertEquals("Total line number should match max count limit", 5, count);
                break;
            }
            count++;
        }
    }

    /**
     * Test max count filter totalCount > maxCount + MAX_OVERFLOW
     * 
     * @throws Exception
     */
    @Test
    @Ignore
    public void testReadNextLogMaxCountFilterGreaterThanMAXCOUNTOVERFLOW() throws Exception {
    	List<File> files = new ArrayList<File>();
        files.add(new File(timeStampFilePath));
        long maxCount = 10; // should get 16 log Messages
        int count = 0;
        LogRequest req = new LogRequest.Builder().maxCont(maxCount).build();
        LogStatusInfo status = new LogStatusInfo();
        LogFileStream stream = new LogFileStream("",files, req, status);
        while (true) {
            LogMessage log = stream.readNextLogMessage();
            if (log == null) {
            	System.out.println("count=" + count);
                assertEquals("Total line number should match max count limit", 16, count);
                break;
            }
            count++;
        }
    }

    /**
     * Test if logs read from one file are in time range specified in LogRequest
     */
    @Test
    public void testSmallFileTimeRangeFilter() throws Exception {
    	File file = new File(timeRangeFilePath);
    	List<File> files = new ArrayList<File>();
        files.add(file);        
        Calendar calendar = Calendar.getInstance();
        calendar.set(2013, 10, 20, 16, 38);
        Date startTime = calendar.getTime();
        long startTimeLong = startTime.getTime();
        calendar.set(2014, 0, 16, 16, 38);
        Date endTime = calendar.getTime();
        long endTimeLong = endTime.getTime();
        LogRequest req = new LogRequest.Builder().startTime(startTime)
                .endTime(endTime).build();
        LogStatusInfo status = new LogStatusInfo();
        LogFileStream stream = new LogFileStream("", files, req, status);

        while (true) {
            LogMessage log = stream.readNextLogMessage();
            if (log == null) {
                break;
            }
            long time = log.getTime();
            // System.out.println(startTimeLong + " " + endTimeLong + " " +
            // time);
            assertTrue(
                    "Lines read from LogStream should in time range(one file)",
                    time >= startTimeLong && time <= endTimeLong);
        }
    }

    /**
     * Test if logs read from files are in time range specified in LogRequest
     * This test can only be tested on root@10.10.191.121
     */
    @Test
    @Ignore
    public void testMultipleFilesTimeRangeFilter() throws Exception {
    	List<File> files = new ArrayList<File>();
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140110-231105.gz"));
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140114-151216.gz"));
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140112-175108.gz"));
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140116-031010.gz"));
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log"));
        files.add(new File(HARD_CODE_PATH + "/src/main/data/streamData/controllersvc.log.20140109-101159.gz"));
        Calendar calendar = Calendar.getInstance();
        calendar.set(2013, 10, 20, 16, 38);
        Date startTime = calendar.getTime();
        long startTimeLong = startTime.getTime();
        calendar.set(2014, 0, 16, 16, 38);
        Date endTime = calendar.getTime();
        long endTimeLong = endTime.getTime();
        LogRequest req = new LogRequest.Builder().startTime(startTime)
                .endTime(endTime).build();
        LogStatusInfo status = new LogStatusInfo();
        LogFileStream stream = new LogFileStream("", files, req, status);
        while (true) {
            LogMessage log = stream.readNextLogMessage();
            if (log == null) {
                break;
            }
            long time = log.getTime();
            assertTrue(
                    "Lines read from LogStream should in time range(multiple files)",
                    time >= startTimeLong && time <= endTimeLong);
        }
    }

    /**
     * Test if logs read from LogStream match pattern(keywords) specified in
     * LogRequest
     */
    @Test
    public void testSmallFilePatternFilter() throws Exception {
    	File file = new File(timeRangeFilePath);
    	List<File> files = new ArrayList<File>();
    	files.add(file);
        String pattern = "reset";
        LogRequest req = new LogRequest.Builder().regex(pattern).build();
        LogStatusInfo status = new LogStatusInfo();
        LogFileStream stream = new LogFileStream("", files, req, status);
        while (true) {
            LogMessage log = stream.readNextLogMessage();
            if (log == null) {
                break;
            }
            assertTrue("log should have the keywords", toStringOriginalFormatStr(log)
                    .indexOf(pattern) >= 0);
        }
    }

    /**
     * Test if logs read from LogStream match level specified in LogRequest
     */
    @Test
    public void testSmallFileLevelFilter() throws Exception {
    	File file = new File(timeRangeFilePath);
    	List<File> files = new ArrayList<File>();
    	files.add(file);
        int level = 5; // "WARN"
        LogRequest req = new LogRequest.Builder().logLevel(level).build();
        LogStatusInfo status = new LogStatusInfo();
        LogFileStream stream = new LogFileStream("", files, req, status);

        while (true) {
            LogMessage log = stream.readNextLogMessage();
            if (log == null) {
                break;
            }
            assertTrue("Log level should match", LogSeverity.toLevel(new String(
                    log.getLevel())) <= level);
        }
    }
    
    @After
    public void after() {
        File timeStampFile = new File(timeStampFilePath);
        File timeRangeFile = new File(timeRangeFilePath);
        
        if(timeStampFile.exists()){
        	timeStampFile.delete();
        }

        if(timeRangeFile.exists()){
        	timeRangeFile.delete();
        }
    }
    
    /*
     * Only used for test
     */
    public String toStringOriginalFormatStr(LogMessage msg){
        StringBuilder sb = new StringBuilder();

        sb.append(new String(msg.getTimeBytes())).append(" ").append("[").append(new String(msg.getThreadName())).append("]")
                .append(" ").append(new String(msg.getLevel())).append(" ")
                .append(new String(msg.getFileName())).append(" ").append("(line ").append(new String(msg.getLineNumber()))
                .append(") ").append(" service ").append(new String(msg.getService())).append(" ")
                .append(new String(msg.getLogContent()));
        return sb.toString();
    }
}
