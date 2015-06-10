/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc.performance;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.junit.Test;

import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.storageos.systemservices.impl.logsvc.stream.LogReader;
import com.emc.storageos.systemservices.impl.logsvc.LogStatusInfo;
import com.emc.vipr.model.sys.logging.LogRequest;

public class LogReaderPerfTest {
	private static final String PATH = "/opt/storageos/logs/testData/coordinatorsvc.log";
	
    /**
     * Test the performance of regular BufferedReader
     */
    @Test
//    @Ignore
    public void testBufferedReaderPerformance() throws Exception{
        String filePath = PATH;
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        File file = new File(filePath);
        double fileSize = (double) file.length() / (1024L * 1024L);
        long startTime = System.nanoTime();
        while (true) {
            String line = br.readLine();
            if (line == null) {
                break;
            }
        }
        double elapsedTime = (double) (System.nanoTime() - startTime) / 1000000000.0;
        br.close();
        double speed = fileSize / elapsedTime;
        System.out.println("Performance for BufferedReader");
        System.out
                .println("Total read " + fileSize + " MB;" + " Average " + speed + " MB/sec.");
    }
    
    @Test
    public void testPerformance() throws Exception{
        LogStatusInfo status = new LogStatusInfo();
        String filePath = PATH;
        File file = new File(filePath);
        double fileSize = (double) file.length() / (1024L * 1024L);
        LogRequest req = new LogRequest.Builder().build();
        LogReader reader = new LogReader(filePath, req,status,null); // empty service list
        long startTime = System.nanoTime();
        while (true) {
            LogMessage log = reader.readNextLogMessage();
            if (log == null) {
                break;
            }
        }
        double elapsedTime = (double) (System.nanoTime() - startTime) / 1000000000.0;
        double speed = fileSize / elapsedTime;
        System.out.println("Performance for LogReader");
        System.out.println("Total read " + fileSize + " MB;" + " Used " + elapsedTime +
                " sec; Average " + speed + " MB/sec.");
    }
}
