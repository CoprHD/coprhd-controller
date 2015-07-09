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
package com.emc.storageos.systemservices.impl.logsvc.performance;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import com.emc.storageos.systemservices.impl.logsvc.stream.LogFileStream;
import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.storageos.systemservices.impl.logsvc.LogStatusInfo;
import com.emc.storageos.systemservices.impl.logsvc.util.LogUtil;
import com.emc.vipr.model.sys.logging.LogRequest;

public class LogStreamPerfTest {
    /**
     * Test performance without filter
     */
    @Test
    @Ignore
    public void testPerformanceNoFilter() throws Exception {
        System.out.println("starting testPerformanceNoFilter");
        String svcName = "bigFile-dbsvc";
        LogRequest req = new LogRequest.Builder().build();
        LogStatusInfo status = new LogStatusInfo();
        LogFileStream stream = new LogFileStream(svcName,
                new ArrayList<File>(), req, status);
        long startTime = 0;
        long endTime = 0;
        startTime = System.nanoTime();
        while (true) {
            LogMessage log = stream.readNextLogMessage();
            if (log == null) {
                endTime = System.nanoTime();
                break;
            }
        }
        double fileSize = (double) stream.getTotalSizeCount() / (1024L * 1024L);
        double elapsedTime = (double) (endTime - startTime) / 1000000000.0;
        double speed = fileSize / elapsedTime;
        // System.out.println("Total files size(after compressed) is " +
        // fileSize + "MB. Log messages count is "
        // + stream.getTotalLogCount());
        // System.out.println("Speed of LogStream without Filter is: " + speed
        // + " MB/sed");
        System.out
                .println("Total files size(after decompressing) is 4286.8 MB. Log messages count is "
                        + stream.getTotalLogCount());
        System.out.println("Speed of LogStream without Filter is: " + 4286.8
                / elapsedTime + " MB/sec and " + stream.getTotalLogCount()
                / elapsedTime + " logs/sec");
        System.out.println("done testPerformanceNoFilter");
    }

    /**
     * Test performance without filter
     */
    @Test
    @Ignore
    public void testReadCompressedFilePerformance() throws Exception {
        System.out.println("starting testReadCompressedFilePerformance");
        List<String> fileNames = new ArrayList<String>();
        fileNames
                .add("/data/shared-logging/internalLibraries/logging/src/main/data/streamData/bigFile-dbsvc.log.20131120-163815.gz");
        fileNames
                .add("/data/shared-logging/internalLibraries/logging/src/main/data/streamData/bigFile-dbsvc.log.20131120-163816.gz");
        fileNames
                .add("/data/shared-logging/internalLibraries/logging/src/main/data/streamData/bigFile-dbsvc.log.20131120-163817.gz");
        fileNames
                .add("/data/shared-logging/internalLibraries/logging/src/main/data/streamData/bigFile-dbsvc.log.20131120-163818.gz");
        fileNames
                .add("/data/shared-logging/internalLibraries/logging/src/main/data/streamData/bigFile-dbsvc.log.20140116-091650.gz");
        fileNames
                .add("/data/shared-logging/internalLibraries/logging/src/main/data/streamData/bigFile-dbsvc.log.20140116-181956.gz");
        fileNames
                .add("/data/shared-logging/internalLibraries/logging/src/main/data/streamData/bigFile-dbsvc.log");
        BufferedReader reader = null;
        long startTime = System.nanoTime();
        for (String file : fileNames) {
            System.out.println("Reading " + file);
            if (LogUtil.logFileZipped(file)) {
                reader = LogUtil.getBufferedReaderForBZ2File(file);
            } else {
                reader = new BufferedReader(new FileReader(file));
            }
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
            }
        }
        long endTime = System.nanoTime();
        reader.close();
        double elapsedTime = (double) (endTime - startTime) / 1000000000.0;
        System.out
                .println("Total files size(after decompressed) is 4286.8 MB.");
        System.out.println("Speed of reading compressed file is: " + 4286.8
                / elapsedTime + " MB/sec");
        System.out.println("done testReadCompressedFilePerformance");
    }

    /**
     * Test performance with time range filter
     */
    @Test
    @Ignore
    public void testPerformanceTimeFilter() throws Exception {
        System.out.println("starting testPerformanceTimeFilter");
        String svcName = "bigFile-dbsvc";
        Calendar calendar = Calendar.getInstance();
        calendar.set(2013, 10, 20, 16, 38, 16);
        Date startTimeFilter = calendar.getTime();
        calendar.set(2014, 0, 16, 16, 38, 0);
        Date endTimeFilter = calendar.getTime();
        LogRequest req = new LogRequest.Builder().startTime(startTimeFilter)
                .endTime(endTimeFilter).build();
        LogStatusInfo status = new LogStatusInfo();
        LogFileStream stream = new LogFileStream(svcName,
                new ArrayList<File>(), req, status);
        long startTime = 0;
        long endTime = 0;
        startTime = System.nanoTime();
        while (true) {
            LogMessage log = stream.readNextLogMessage();
            if (log == null) {
                endTime = System.nanoTime();
                break;
            }
        }
        double fileSize = (double) stream.getTotalSizeCount() / (1024L * 1024L);
        double elapsedTime = (double) (endTime - startTime) / 1000000000.0;
        double speed = fileSize / elapsedTime;
        System.out.println("Total files size is " + fileSize
                + "MB. Log messages count is " + stream.getTotalLogCount());
        System.out.println("Speed of LogStream with time range Filter is: "
                + speed + " Mbs");
        System.out
                .println("Total files size(after decompressing) is 3436.7 MB. Log messages count is "
                        + stream.getTotalLogCount());
        System.out.println("Speed of LogStream with time filter is: " + 3436.7
                / elapsedTime + " MB/sec");
        System.out.println("done testPerformanceTimeFilter");
    }

    /**
     * Test performance with time, level and pattern filters, the speed should
     * be faster than 50 Mbs
     */
    @Test
    @Ignore
    public void testPerformanceMultipleFilters() throws Exception {
        System.out.println("starting testPerformanceMultipleFilters");
        String svcName = "bigFile-dbsvc";
        String pattern = "Memory";
        Calendar calendar = Calendar.getInstance();
        calendar.set(2013, 10, 20, 16, 38, 16);
        Date startTimeFilter = calendar.getTime();
        calendar.set(2014, 0, 16, 16, 38, 0);
        Date endTimeFilter = calendar.getTime();
        LogRequest req = new LogRequest.Builder().startTime(startTimeFilter)
                .endTime(endTimeFilter).logLevel(7).regex(pattern).build();
        LogStatusInfo status = new LogStatusInfo();
        LogFileStream stream = new LogFileStream(svcName,
                new ArrayList<File>(), req, status);
        long startTime = 0;
        long endTime = 0;
        startTime = System.nanoTime();
        while (true) {
            LogMessage log = stream.readNextLogMessage();
            if (log == null) {
                endTime = System.nanoTime();
                break;
            }
        }
        double fileSize = (double) stream.getTotalSizeCount() / (1024L * 1024L);
        double elapsedTime = (double) (endTime - startTime) / 1000000000.0;
        double speed = fileSize / elapsedTime;
        System.out.println("Total files size is " + fileSize
                + "MB. Log messages count is " + stream.getTotalLogCount());
        System.out.println("Speed of LogStream with multiple Filters is: "
                + speed + " Mbs");
        System.out
                .println("Total files size(after decompressing) is 3436.7 MB. Log messages count is "
                        + stream.getTotalLogCount());
        System.out.println("Speed of LogStream with multiple Filters is: "
                + 3436.7 / elapsedTime + " MB/sec");
        System.out.println("done testPerformanceMultipleFilters");
    }

    @Test
    public void testPerformance() throws Exception {
        List<File> files = new ArrayList<>();
        files.add(new File("/opt/storageos/logs/testData/controllersvc.log"));
        files.add(new File("/opt/storageos/logs/testData/controllersvc.log.20131120-163817.gz"));
        LogRequest req = new LogRequest.Builder().build();
        LogStatusInfo status = new LogStatusInfo();
        LogFileStream stream = new LogFileStream("",files, req, status);
        long startTime = 0;
        long endTime = 0;
        startTime = System.nanoTime();
        while (true) {
            LogMessage log = stream.readNextLogMessage();
            if (log == null) {
                endTime = System.nanoTime();
                break;
            }
        }
        double elapsedTime = (double) (endTime - startTime) / 1000000000.0;
        double speed = 1510.8 / elapsedTime;
        System.out.println("Total read 1510.8 MB; Used " + elapsedTime +
                " sec; Average " + speed + " MB/sec.");
    }
}
