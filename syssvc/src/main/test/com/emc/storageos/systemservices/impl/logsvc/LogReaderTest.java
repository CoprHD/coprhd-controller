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
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.io.FileOutputStream;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.emc.storageos.systemservices.impl.logsvc.stream.LogReader;
import com.emc.storageos.systemservices.impl.logsvc.util.LogUtil;
import com.emc.vipr.model.sys.logging.LogRequest;

public class LogReaderTest {

    private static final Logger logger = LoggerFactory.getLogger(LogReaderTest.class);
    
    private static volatile File logDir;
    
    String regularSvcLogPath = null;
    String superLongSvcLogPath = null;
    String multipleLineINFOLogPath = null;
    
    /**
     * Creates a temporary log dir to use within this test.
     */
    @BeforeClass
    public static void createLogDir() {
        logDir = new File(FileUtils.getTempDirectory(), LogReaderTest.class.getSimpleName());
        logDir.mkdirs();
    }

    /**
     * Deletes the temporary log dir and all files within.
     */
    @AfterClass
    public static void deleteLogDir() {
        if (logDir != null) {
            FileUtils.deleteQuietly(logDir);
        }
    }

    @Before
    public void before() {
    	String svcLog1 = "2014-01-16 17:07:57,561 [LogLevelResetter]  INFO  LoggingMBean.java (line 322) Starting log level config reset, lastResetTime = 0";
    	String superLongLog = "2014-01-16 19:00:01,519 [pool-35-thread-1]  INFO  DefaultSingletonBeanRegistry.java (line 433) Destroying singletons in org.springframework.beans.factory.support.DefaultListableBeanFactory@25f13769: defining beans [namespaces,scanner,registeredProfiles,reference-profile,profile-prop,profileProcessor,providerVersionSupport,resultClass-softwareIdentity,softwareIdentity-prop,softwareIdentityProcessor,system,resultClass-system,system-prop,scannerProcessor,model,reference-comp,resultClass-chassis,model-prop,modelProcessor,argscreator,smiutility,cimClient,block,commandgenerator,executor,null,bool,bool-true]; root of factory hierarchy";
    	String multipleLineINFOLog = "2014-01-16 18:58:24,025 [pool-10-thread-1]  INFO  ProcessMonitor.java (line 34) \nMemory Usage Metrics \nTotal  Memory: 379MB; \nAvailable Free Memory: 146MB; \nAvailable Maximum Memory : 910MB; \nUsed Memory: 233MB; \nMax used Memory : 366MB at 2014-01-01 23:03:24.025 UTC; ";
    	
    	regularSvcLogPath = new File(logDir, "regular_log.log").getAbsolutePath();
    	superLongSvcLogPath = new File(logDir, "super_long_log.log").getAbsolutePath();
    	multipleLineINFOLogPath = new File(logDir, "multiple_line_INFO_log.log").getAbsolutePath();	
    	
    	//initialize log file
    	try(FileOutputStream fos = new FileOutputStream(regularSvcLogPath);
    		OutputStreamWriter osw = new OutputStreamWriter(fos,"UTF-8");
    		BufferedWriter out = new BufferedWriter(osw)) {
    			out.write(svcLog1);
    			out.flush();
    	} catch(Exception e) {
    		logger.error("Exception in writing regular log file for syssvc test", e);
    	}
    	
    	try(FileOutputStream fos = new FileOutputStream(superLongSvcLogPath);
        		OutputStreamWriter osw = new OutputStreamWriter(fos,"UTF-8");
        		BufferedWriter out = new BufferedWriter(osw)) {
        			out.write(superLongLog);
        			out.flush();
    	} catch(Exception e) {
        		logger.error("Exception in writing regular log file for syssvc test", e);
    	}
    	
    	try(FileOutputStream fos = new FileOutputStream(multipleLineINFOLogPath);
        		OutputStreamWriter osw = new OutputStreamWriter(fos,"UTF-8");
        		BufferedWriter out = new BufferedWriter(osw)) {
        			out.write(multipleLineINFOLog);
        			out.flush();
    	} catch(Exception e) {
        		logger.error("Exception in writing regular log file for syssvc test", e);
    	}
    }
    
    /**
     * Test if readMessage() can read regular service log from file and parse it correctly
     */
    @Test
    public void testReadRegularLogNoFilterSvcParser() throws Exception {
        LogStatusInfo status = new LogStatusInfo();
        LogRequest req = new LogRequest.Builder().build();
        LogReader reader = new LogReader(regularSvcLogPath, req,status,null);
        LogMessage l = reader.readNextLogMessage();
        Calendar calendar = Calendar.getInstance();
        calendar.set(2014, 0, 16, 17, 7, 57); // month starts from 0;
        calendar.set(Calendar.MILLISECOND, 561);
        Date date = calendar.getTime();
        long time = date.getTime();
        assertEquals("Time is wrong", l.getTime(), time);
        assertTrue("Thread name is wrong", Arrays.equals(LogUtil.stringToBytes("LogLevelResetter"),l.getThreadName()));
        assertEquals("Log level is wrong", new String(l.getLevel()), "INFO");
        assertTrue("File name is wrong", Arrays.equals(LogUtil.stringToBytes("LoggingMBean"), l.getFileName()));
        assertTrue("Line number is wrong", Arrays.equals(l.getLineNumber(), LogUtil.stringToBytes("322")));
        assertTrue("Log message contact is wrong",Arrays.equals(
        		 LogUtil.stringToBytes("Starting log level config reset, lastResetTime = 0"), l.getLogContent()));
    }
    
    /**
     * Test if readMessage() can read super long service log from file and parse it correctly
     */
    @Test
    public void testSuperLongLogNoFilterSVCParser() throws Exception {
        LogStatusInfo status = new LogStatusInfo();
        LogRequest req = new LogRequest.Builder().build();
        LogReader reader = new LogReader(superLongSvcLogPath, req,status,null);
        LogMessage l = reader.readNextLogMessage();
        Calendar calendar = Calendar.getInstance();
        calendar.set(2014, 0, 16, 19, 00, 1); // month starts from 0;
        calendar.set(Calendar.MILLISECOND, 519);
        Date date = calendar.getTime();
        long time = date.getTime();
        assertEquals("Time is wrong", l.getTime(), time);
        assertTrue("Thread name is wrong", Arrays.equals(LogUtil.stringToBytes("pool-35-thread-1"), l.getThreadName()));
        assertEquals("Log level is wrong",new String(l.getLevel()), "INFO");
        assertTrue("File name is wrong", Arrays.equals(LogUtil.stringToBytes("DefaultSingletonBeanRegistry"), l.getFileName()));
        assertTrue("Line number is wrong", Arrays.equals(l.getLineNumber(), LogUtil.stringToBytes("433")));
        assertTrue("Log message contact is wrong",
                Arrays.equals(LogUtil.stringToBytes("Destroying singletons in org.springframework.beans.factory.support.DefaultListableBeanFactory@25f13769: defining beans [namespaces,scanner,registeredProfiles,reference-profile,profile-prop,profileProcessor,providerVersionSupport,resultClass-softwareIdentity,softwareIdentity-prop,softwareIdentityProcessor,system,resultClass-system,system-prop,scannerProcessor,model,reference-comp,resultClass-chassis,model-prop,modelProcessor,argscreator,smiutility,cimClient,block,commandgenerator,executor,null,bool,bool-true]; root of factory hierarchy")
        		, l.getLogContent()));
    }

    /**
     * Test if readMessage() can read multiple lines INFO service log from file and parse it correctly
     * Test log whose first line's message field is null.
     */
    @Test
    public void testMultipleLinesInfoNoFilterSVCParser() throws Exception{
        LogStatusInfo status = new LogStatusInfo();
        LogRequest req = new LogRequest.Builder().build();
        LogReader reader = new LogReader(multipleLineINFOLogPath, req,status,null);
        LogMessage l = reader.readNextLogMessage();
        Calendar calendar = Calendar.getInstance();
        calendar.set(2014, 0, 16, 18, 58, 24); // month starts from 0;
        calendar.set(Calendar.MILLISECOND, 25);
        Date date = calendar.getTime();
        long time = date.getTime();
        assertEquals("Time is wrong", l.getTime(), time);
        assertTrue("Thread name is wrong", Arrays.equals(LogUtil.stringToBytes("pool-10-thread-1"), l.getThreadName()));
        assertEquals("Log level is wrong", new String(l.getLevel()), "INFO");
        assertTrue("File name is wrong", Arrays.equals(LogUtil.stringToBytes("ProcessMonitor"), l.getFileName()));
        assertTrue("Line number is wrong", Arrays.equals(l.getLineNumber(), LogUtil.stringToBytes("34")));
        assertTrue("Log message content is wrong", Arrays.equals(LogUtil.stringToBytes("" + '\n' + "Memory Usage Metrics "
                + '\n' + "Total  Memory: 379MB; " + '\n' + "Available Free Memory: 146MB; " + '\n'
                + "Available Maximum Memory : 910MB; " + '\n' + "Used Memory: 233MB; " + '\n'
                + "Max used Memory : 366MB at 2014-01-01 23:03:24.025 UTC; "), l.getLogContent()));
    }

    @After
    public void after() {

        File regularLogFile = new File(regularSvcLogPath);
        File superLongLogFile = new File(superLongSvcLogPath);
        File multiLineLogFile = new File(multipleLineINFOLogPath);
        
        if(regularLogFile.exists()){
        	regularLogFile.delete();
        }

        if(superLongLogFile.exists()){
        	superLongLogFile.delete();
        }
        
        if(multiLineLogFile.exists()){
        	multiLineLogFile.delete();
        }
    }
}
