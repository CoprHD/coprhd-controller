/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.logsvc.parse;

import static org.junit.Assert.*;

import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.storageos.systemservices.impl.logsvc.stream.LogReader;
import com.emc.storageos.systemservices.impl.logsvc.LogStatusInfo;
import com.emc.storageos.systemservices.impl.logsvc.util.LogUtil;
import com.emc.vipr.model.sys.logging.LogRequest;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;

import java.util.Calendar;
import java.util.Date;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;

public class ParserTest {

    private static final Logger log = LoggerFactory.getLogger(ParserTest.class);
    private static final String PATH = "build" + File.separator + "tmp";

    String nginxAccessLogPath = null;
    String nginxErrorLogPath = null;
    String particularLogPath = null;

    @Before
    public void before() {
        String nginxAccessLog1 = "10.247.101.185 - - [08/May/2014:02:51:16 +0000] \"GET /logs?severity=7&maxcount=3 HTTP/1.1\" 200 3709 \"-\" \"python-requests/0.14.1 CPython/2.6.8 Linux/3.0.101-0.8-default\"";
        String nginxAccessLog2 = "10.247.101.185 - - [08/May/2014:02:48:26 +0000] \"GET /login HTTP/1.1\" 503 373 \"-\" \"python-requests/0.14.1 CPython/2.6.8 Linux/3.0.101-0.8-default\"";

        String nginxErrorLog1 = "2014/05/08 02:49:22 [error] 5691#0: *116 no live upstreams while connecting to upstream, client: 10.247.101.185, server: localhost, request: \"GET /login HTTP/1.1\", upstream: \"https://authsvc/login\", host: \"10.247.98.71:4443\"";
        String nginxErrorLog2 = "2014/05/08 02:49:27 [error] 5691#0: *124 no live upstreams while connecting to upstream, client: 10.247.101.185, server: localhost, request: \"GET /login HTTP/1.1\", upstream: \"https://authsvc/login\", host: \"10.247.98.71:4443\"";

        String logLine1 = "2014-05-21 06:32:58,358 [PathChildrenCache-0]  ERROR  PathChildrenCache.java (line 537)";
        String logLine2 = "java.lang.IllegalStateException: instance must be started before calling this method";
        String logLine3 = "        at com.google.common.base.Preconditions.checkState(Preconditions.java:150)";
        String logLine4 = "        at org.apache.curator.framework.imps.CuratorFrameworkImpl.getChildren(CuratorFrameworkImpl.java:356)";

        File path = new File(PATH);
        if (!path.exists()) {
            path.mkdirs();
        }
        nginxAccessLogPath = PATH + File.separator + "nginx_access.log";
        nginxErrorLogPath = PATH + File.separator + "nginx_error.log";
        particularLogPath = PATH + File.separator + "particular.log";

        // Initialize log file.
        try (
                FileOutputStream fos = new FileOutputStream(nginxAccessLogPath);
                OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
                BufferedWriter out = new BufferedWriter(osw)) {

            out.write(nginxAccessLog1);
            out.newLine();
            out.write(nginxAccessLog2);
            out.flush();
        } catch (Exception e) {
            log.error("Exception in writing nginx access log file for syssvc test", e);
        }

        try (
                FileOutputStream fos = new FileOutputStream(nginxErrorLogPath);
                OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
                BufferedWriter out = new BufferedWriter(osw)) {

            out.write(nginxErrorLog1);
            out.newLine();
            out.write(nginxErrorLog2);
            out.flush();
        } catch (Exception e) {
            log.error("Exception in writing nginx error log file for syssvc test", e);
        }

        try (
                FileOutputStream fos = new FileOutputStream(particularLogPath);
                OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
                BufferedWriter out = new BufferedWriter(osw)) {

            out.write(logLine1);
            out.newLine();
            out.write(logLine2);
            out.newLine();
            out.write(logLine3);
            out.newLine();
            out.write(logLine4);
            out.newLine();
            out.flush();
        } catch (Exception e) {
            log.error("Exception in writing log file for syssvc test", e);
        }
    }

    @Test
    public void testNginxErrorLog() throws Exception {
        LogReader reader = null;

        String regexStr = ".*server: localhost, request:.*";
        Calendar calendar = Calendar.getInstance();
        calendar.set(2014, 1, 20, 16, 38, 16);
        Date startTimeFilter = calendar.getTime();
        calendar.set(2014, 6, 16, 16, 38, 0);
        Date endTimeFilter = calendar.getTime();

        LogRequest request = new LogRequest.Builder().regex(regexStr).startTime(startTimeFilter).endTime(endTimeFilter).build();

        LogStatusInfo status = new LogStatusInfo();

        String filePath = nginxErrorLogPath;// "/opt/storageos/logs/nginx_error.log";
        String baseName = "nginx";

        reader = new LogReader(filePath, request, status, baseName);
        final LogMessage log = reader.readNextLogMessage();

        assertTrue("log is null", log != null);
        assertTrue("log message is null", !new String(log.getLogContent()).equals("null"));
        assertTrue("log message does not match regex", Pattern.compile(regexStr,
                Pattern.DOTALL | Pattern.MULTILINE).matcher(LogUtil.bytesToString(
                log.getLogContent())).matches());
        assertTrue("log time does not fit time filter",
                log.getTime() >= startTimeFilter.getTime() && log.getTime() <= endTimeFilter.getTime());

        assertTrue("log file name is not null", new String(log.getFileName()).equals("null"));
        assertTrue("log thread name not is null", new String(log.getThreadName()).equals("null"));
        assertTrue("log level is not Error", new String(log.getLevel()).equalsIgnoreCase("ERROR"));
        assertTrue("log line is not -1", new String(log.getLineNumber()).equals("-1"));
    }

    @Test
    public void testNginxAccessLog() throws Exception {
        LogReader reader = null;

        String regexStr = ".*login HTTP.*";
        Calendar calendar = Calendar.getInstance();
        calendar.set(2014, 1, 20, 16, 38, 16);
        Date startTimeFilter = calendar.getTime();
        calendar.set(2014, 6, 16, 16, 38, 0);
        Date endTimeFilter = calendar.getTime();

        LogRequest request = new LogRequest.Builder().regex(regexStr).startTime(startTimeFilter).endTime(endTimeFilter).build();

        LogStatusInfo status = new LogStatusInfo();

        String filePath = nginxAccessLogPath; // "/opt/storageos/logs/nginx_access.log";
        String baseName = "nginx";

        reader = new LogReader(filePath, request, status, baseName);
        final LogMessage log = reader.readNextLogMessage();

        assertTrue("log is null", log != null);
        assertTrue("log message is null", !new String(log.getLogContent()).equals("null"));
        assertTrue("log message does not match regex", Pattern.compile(regexStr,
                Pattern.DOTALL | Pattern.MULTILINE).matcher(LogUtil.bytesToString(
                log.getLogContent())).matches());
        assertTrue("log time does not fit time filter",
                log.getTime() >= startTimeFilter.getTime() && log.getTime() <= endTimeFilter.getTime());

        assertTrue("log file name is not null", new String(log.getFileName()).equals("null"));
        assertTrue("log thread name not is null", new String(log.getThreadName()).equals("null"));
        assertTrue("log level is not null", new String(log.getLevel()).equals("null"));
        assertTrue("log line is not -1", new String(log.getLineNumber()).equals("-1"));

    }

    @Test
    public void testParticularLog() throws Exception {
        LogReader reader = null;

        LogRequest request = new LogRequest.Builder().build();

        LogStatusInfo status = new LogStatusInfo();

        String filePath = particularLogPath;
        String baseName = "log";

        reader = new LogReader(filePath, request, status, baseName);
        final LogMessage log = reader.readNextLogMessage();

        assertTrue("log is null", log != null);
        // assertTrue("log message is null", log.getLogContent() != null);
        assertTrue("log message is null", log.getLogContent() != null);

        assertTrue("log file name is null", log.getFileName() != null);
        assertTrue("log thread name is null", log.getThreadName() != null);
        assertTrue("log level is not Error", new String(log.getLevel()).equals("ERROR"));
        assertTrue("log line is null", log.getLineNumber() != null);
    }

    @After
    public void after() {

        File nginxAccessFile = new File(nginxAccessLogPath);
        File nginxErrorFile = new File(nginxErrorLogPath);
        File particularFile = new File(particularLogPath);

        if (nginxAccessFile.exists()) {
            nginxAccessFile.delete();
        }

        if (nginxErrorFile.exists()) {
            nginxErrorFile.delete();
        }

        if (particularFile.exists()) {
            particularFile.delete();
        }
    }
}
