/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.healthmonitor;

import com.emc.storageos.systemservices.impl.resource.HealthMonitorService;
import com.emc.vipr.model.sys.healthmonitor.DiagTest;
import com.emc.vipr.model.sys.healthmonitor.DiagnosticsRestRep;
import com.emc.vipr.model.sys.healthmonitor.NodeDiagnostics;
import com.emc.vipr.model.sys.healthmonitor.TestParam;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DiagnosticsExecTest extends DiagnosticsExec {
    private static final String STDOUT_EMPTY = "";
    private static final String TEST_NAME1 = "Network interface";
    private static final String TEST_STATUS1 = "OK";
    private static final String TEST_NAME2 = "NTP";
    private static final String TEST_STATUS2 = "OK, DEGRADED";
    private static final String STDOUT = "* " + TEST_NAME1 + ": [" + TEST_STATUS1 + "]\n* " + TEST_NAME2 + ": " +
            "[" + TEST_STATUS2 + "]";
    private static final Set<String> DIAG_TESTS = new HashSet<String>() {
        {
            add("Network interface");
            add("Network routing");
            add("NTP");
            add("DNS");
            add("Remote Repository");
        }
    };

    @Test
    public void testDiagToolStdOut() {
        try {
            String stdout = getDiagToolStdOutAsStr();
            Assert.assertNotNull(stdout);
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void testDiagToolStdOutVerbose() {
        try {
            String stdout = getDiagToolStdOutAsStr("-v");
            Assert.assertNotNull(stdout);
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void testConversionEmptyStdout() {
        List<DiagTest> diagTests = convertStringToDiagTestList(STDOUT_EMPTY);
        Assert.assertTrue(diagTests == null || diagTests.isEmpty());
    }

    @Test
    public void testConversion() {
        List<DiagTest> diagTests = convertStringToDiagTestList(STDOUT);
        System.out.println("STDOUT: " + STDOUT);
        System.out.println("diagTests: " + diagTests);
        Assert.assertTrue(diagTests != null && diagTests.size() == 2);
        for (DiagTest test : diagTests) {
            if (test.getName().equals(TEST_NAME1)) {
                Assert.assertTrue(test.getStatus().equals(TEST_STATUS1));
            }
            else if (test.getName().equals(TEST_NAME2)) {
                Assert.assertTrue(test.getStatus().equals(TEST_STATUS2));
            }
            else {
                Assert.fail();
            }
        }
    }

    @Test
    public void testNodeDiagnostics() {
        HealthMonitorService healthMonitorService = new HealthMonitorService();
        DiagnosticsRestRep resp = healthMonitorService.getDiagnostics(null,"1",null);
        Assert.assertNotNull(resp);
        Assert.assertNotNull(resp.getNodeDiagnosticsList());
        Set<String> testNames = new HashSet<String>();
        for (NodeDiagnostics diag : resp.getNodeDiagnosticsList()) {
            Assert.assertTrue(diag.getNodeId() != null && !diag.getNodeId().isEmpty());
            Assert.assertTrue(diag.getIp() != null && !diag.getIp().isEmpty());
            Assert.assertNotNull(diag.getDiagTests());
            for (DiagTest test : diag.getDiagTests()) {
                testNames.add(test.getName());
                Assert.assertTrue(test.getStatus() != null && !test.getStatus().isEmpty());
                if (test.getTestParams() != null) {
                    for (TestParam param : test.getTestParams()) {
                        Assert.assertTrue(param.getKey() != null && !param.getKey()
                                .isEmpty());
                        Assert.assertTrue(param.getValue() != null && !param.getValue()
                                .isEmpty());
                    }
                }
            }
            Assert.assertTrue(testNames.containsAll(DIAG_TESTS));
        }
    }
}
