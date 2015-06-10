/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.healthmonitor;

import com.emc.storageos.systemservices.exceptions.SyssvcInternalException;
import com.emc.storageos.systemservices.exceptions.SyssvcException;
import com.emc.storageos.services.util.Exec;
import com.emc.vipr.model.sys.healthmonitor.DiagTest;
import com.emc.vipr.model.sys.healthmonitor.TestParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class that executes diagtool shell script with options provided and returns the
 * result as string or as a list of DiagTest objects.
 */
public class DiagnosticsExec implements DiagConstants {
    private static final Logger _log = LoggerFactory.getLogger(DiagnosticsExec.class);

    /**
     * Gets results from "diagtool" shell script.
     */
    public static List<DiagTest> getDiagToolResults(String... args) {
        try {
            // Get stdout from diagtool script in linux.
            String stdOut = getDiagToolStdOutAsStr(args);
            _log.info("Output received from diagtool: {}", stdOut);
            return convertStringToDiagTestList(stdOut);
        } catch (Exception e) {
            _log.error("Exception occurred while getting diagtool results: {}", e);
        }
        return null;
    }

    /**
     * Gets status from node such as network mask, network status, ntp delay,
     * default gateway, remote repository http location, etc from Linux by running
     * diagtool shell script..
     */
    public static String getDiagToolStdOutAsStr(String... args) throws
            SyssvcInternalException {
        List<String> cmdList = new ArrayList(Arrays.asList(args));
        cmdList.add(0, DIAGTOOl_CMD);
        //remove blank args at the end
        int lastElement = cmdList.size()-1;
        if(cmdList.get(lastElement).trim().isEmpty()){
        	cmdList.remove(lastElement);
        }    
        final String[] cmd = cmdList.toArray(new String[cmdList.size()]);
        final Exec.Result result = Exec.sudo(DIAGTOOL_TIMEOUT, cmd);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            _log.warn("getDiagToolStdOutAsStr() is unsuccessful. Command exit " +
                    "value is: {}", result.getExitValue());
            throw SyssvcException.syssvcExceptions.syssvcInternalError("Command failed: " + result);
        }

        return result.getStdOutput();
    }

    /**
     * Converts the diagtool output string to DiagTest objects.
     * Expected stdOut format when verbose option is set:
     * * Network routing: [OK]
     * network_gw=10.247.96.1
     * * DNS: [OK]
     * network_nameserver=10.254.66.23 [OK]
     * network_nameserver=10.254.66.24 [OK]
     * * Remote Repository: [OK]
     */
    protected static List<DiagTest> convertStringToDiagTestList(String stdOut) {
        if (stdOut == null || stdOut.isEmpty()) {
            return null;
        }
        List<DiagTest> diagTests = new ArrayList<DiagTest>();
        Pattern paramValPattern = Pattern.compile("(.*)\\s+\\[(.*)\\]");
        Pattern testPattern = Pattern.compile("^\\*\\s+(\\S.*):\\s+\\[(.*)\\]");
        String[] tests = stdOut.split("\n");
        List<TestParam> paramStrs = null;
        for (String test : tests) {
            Matcher matcher = testPattern.matcher(test.trim());
            if (matcher.find()) {
                paramStrs = new ArrayList<TestParam>();
                diagTests.add(new DiagTest(matcher.group(1), matcher.group(2),
                        paramStrs));
            } else {
                String[] keyVal = test.split("=");
                if(keyVal.length >= 2){
                    Matcher paramValMatcher = paramValPattern.matcher(keyVal[1]);
                    if (paramValMatcher.find()) {
                        paramStrs.add(new TestParam(keyVal[0].trim(),
                                paramValMatcher.group(1), paramValMatcher.group(2)));
                    } else {
                        paramStrs.add(new TestParam(keyVal[0].trim(), keyVal[1]));
                    }
                }
            }
        }
        return diagTests;
    }
}
