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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.emc.storageos.systemservices.impl.logsvc.LogSvcPropertiesLoader;
import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.storageos.systemservices.impl.logsvc.merger.LogStreamMerger;
import com.emc.vipr.model.sys.logging.LogRequest;

public class LogStreamMergerPerfTest {

    private volatile static LogSvcPropertiesLoader propertiesLoader;

    @BeforeClass
    public static void setup() {
        propertiesLoader = new LogSvcPropertiesLoader() {
            public List<String> getLogFilePaths() {
                return Arrays.asList("/opt/storageos/logs/testData/*.log");
            }

            public List<String> getExcludedLogFilePaths() {
                return Arrays.asList("/opt/storageos/logs/*native.log");
            }
        };
    }
    
    @Test
    @Ignore
    public void testMergePerformanceTimeRangeFilter() throws Exception{
        List<String> svcs = new ArrayList<String>() {{
            add("controllersvc");
            add("coordinatorsvc");
            add("apisvc");
        }};
        Calendar calendar = Calendar.getInstance();
        calendar.set(2013, 10, 20, 16, 38,16);
        Date startTimeFilter = calendar.getTime();
        calendar.set(2014, 0, 16, 16, 38,0);
        Date endTimeFilter = calendar.getTime();
        LogRequest req = new LogRequest.Builder().startTime(startTimeFilter).endTime(endTimeFilter)
                .baseNames(svcs).build();
        LogStreamMerger merger = new LogStreamMerger(req, propertiesLoader);
        long startTime = System.nanoTime();
        while (true) {
            LogMessage log = merger.readNextMergedLogMessage();
            if (log == null) {
                break;
            }
        }
        long endTime = System.nanoTime();
        int num = merger.getStreamList().length;
        long totalSize = 0;
        for (int i = 0; i < num; i++) {
            totalSize += merger.getStreamList()[i].getTotalSizeCount();
        }
        totalSize = totalSize / (1024L * 1024L);
        double elapsedTime = (double) (endTime - startTime) / 1000000000.0;
        System.out.println("Performance with time range filter");
        System.out.println("Total read " + totalSize + " MB;" + " Average "
                + (totalSize / elapsedTime) + " MB/sec.");
    }

    @Test
    @Ignore
    public void testMergePerformanceMultipleFilters() throws Exception{
    	 List<String> svcs = new ArrayList<String>() {{
             add("controllersvc");
             add("coordinatorsvc");
             add("apisvc");
         }};
        String pattern = "Memory";
        Calendar calendar = Calendar.getInstance();
        calendar.set(2013, 10, 20, 16, 38,16);
        Date startTimeFilter = calendar.getTime();
        calendar.set(2014, 0, 16, 16, 38,0);
        Date endTimeFilter = calendar.getTime();
        LogRequest req = new LogRequest.Builder().startTime(startTimeFilter).endTime(endTimeFilter)
                .logLevel(7).regex(pattern).baseNames(svcs).build();
        LogStreamMerger merger = new LogStreamMerger(req, propertiesLoader);
        long startTime = System.nanoTime();
        while (true) {
            LogMessage log = merger.readNextMergedLogMessage();
            if (log == null) {
                break;
            }
        }
        long endTime = System.nanoTime();
        int num = merger.getStreamList().length;
        long totalSize = 0;
        for (int i = 0; i < num; i++) {
            totalSize += merger.getStreamList()[i].getTotalSizeCount();
        }
        totalSize = totalSize / (1024L * 1024L);
        double elapsedTime = (double) (endTime - startTime) / 1000000000.0;
        System.out.println("Performance with time, level, pattern filters");
        System.out.println("Total read " + totalSize + " MB;" + " Average "
                + (totalSize / elapsedTime) + " MB/sec.");
    }
    
    @Test
    public void testMergePerformance() throws Exception{
        List<String> svcs = new ArrayList<String>() {{
            add("controllersvc");
            add("coordinatorsvc");
            add("apisvc");
        }};
        LogRequest req = new LogRequest.Builder().baseNames(svcs).build();
        LogStreamMerger merger = new LogStreamMerger(req, propertiesLoader);
        long startTime = System.nanoTime();
        while (true) {
            LogMessage log = merger.readNextMergedLogMessage();
            if (log == null) {
                break;
            }
        }
        long endTime = System.nanoTime();
        int num = merger.getStreamList().length;
        long totalSize = 0;
        for (int i = 0; i < num; i++) {
            totalSize += merger.getStreamList()[i].getTotalSizeCount();
        }
        totalSize = totalSize / (1024L * 1024L);
        double elapsedTime = (double) (endTime - startTime) / 1000000000.0;
        System.out.println("Total read " + totalSize + " MB;" + " Used " + elapsedTime +
                " sec; Average " + (totalSize / elapsedTime) + " MB/sec.");
    } 
}
