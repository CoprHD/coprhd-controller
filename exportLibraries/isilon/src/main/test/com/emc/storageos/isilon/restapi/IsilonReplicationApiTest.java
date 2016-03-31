/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import com.emc.storageos.isilon.restapi.IsilonApi.IsilonList;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicy.JobState;
import com.emc.storageos.services.util.EnvConfig;

/*
 * Test client for IsilonRESTClient
 */
public class IsilonReplicationApiTest {

    // private static final Logger _log =
    // LoggerFactory.getLogger(IsilonReplicationApiTest.class);
    private static volatile IsilonApi _client;
    private static volatile IsilonApiFactory _factory = new IsilonApiFactory();
    private static String ip = EnvConfig.get("sanity", "isilon.ip");
    private static String userName = EnvConfig.get("sanity", "isilon.username");
    private static String password = EnvConfig.get("sanity", "isilon.password");

    public static void setup() throws URISyntaxException {
        URI deviceURI = new URI("https", null, ip, 8080, "/",
                null, null);
        _factory = new IsilonApiFactory();
        _factory.init();
        _client = _factory.getRESTClient(deviceURI, userName, password);
    }

    public static void testlicenseInfo() throws Exception {
        System.out.println("Get license info.");
        System.out.println(" SyncIQ license info: "
                + _client.getReplicationLicenseInfo());
    }

    // Policy Tests
    // *************************************************************************

    public static void testCreateReplicationPolicy() throws Exception {
        IsilonSyncPolicy policy = new IsilonSyncPolicy();
        policy.setAction(IsilonSyncPolicy.Action.sync);
        policy.setEnabled(true);
        policy.setName("");
        policy.setSourceRootPath("");
        policy.setTargetHost("");
        policy.setTargetPath("");
        System.out.println("Replication policy: " + policy.toString());
        String policyID = _client.createReplicationPolicy(policy);
        System.out.println("Replication policy: " + policyID);
    }

    public static void testGetReplicationPolicy() throws Exception {
        System.out.println("Get Replication Policy info.");
        System.out.println(" Replication Policy : "
                + _client.getReplicationPolicy("testpolicy").toString());
    }

    public static void testGetTargetPolicy() throws Exception {
        System.out.println("Get Replication Policy info.");
        System.out.println(" Replication Policy : " + _client.getTargetReplicationPolicy("mudit_policy")
                .toString());
    }

    public static void testModifyReplicationPolicy() throws Exception {
        IsilonSyncPolicy policy = new IsilonSyncPolicy();
        policy.setName("");
        System.out.println("Replication policy: " + policy.toString());
        _client.modifyReplicationPolicy("policyID", policy);

    }

    public static void testdeleteReplicationPolicy() throws Exception {
        _client.deleteReplicationPolicy("policyID");

    }

    public static void teststartReplicationJob() throws Exception {
        IsilonSyncJob job = new IsilonSyncJob();
        job.setId("mudit_policy");
        String str = _client.modifyReplicationJob(job);
        System.out.println(str);
    }

    public static void testGetReplicationPolicyReport() throws Exception {
        String errorMessage = "";
        List<IsilonSyncPolicyReport> policyReports = _client.getReplicationPolicyReports("mudit_policy").getList();
        for (IsilonSyncPolicyReport report : policyReports) {
            if (report.getState().equals(JobState.failed) || report.getState().equals(JobState.needs_attention)) {
                errorMessage = report.getErrors()[0];
                break;
            } else {
                continue;
            }
        }
        System.out.println(errorMessage);

    }

    public static void testTargetGetReplicationPolicyReport() throws Exception {
        // System.out.println(" Replication Policy : " + _client.getReplicationPolicyTargetReport("vasutestrepl").toString());
        IsilonList<IsilonSyncPolicyReport> reports = _client.getTargetReplicationPolicyReports("mudit_policy");
        // String err[] = report.getErrors();
        // System.out.println(err[0]);

    }

    public static void main(String args[]) throws Exception {
        IsilonReplicationApiTest.setup();

        // IsilonReplicationApiTest.testlicenseInfo();
        // IsilonReplicationApiTest.testGetReplicationPolicy();
        // IsilonReplicationApiTest.testCreateReplicationPolicy();
        // IsilonReplicationApiTest.testModifyReplicationPolicy();
        // IsilonReplicationApiTest.testdeleteReplicationPolicy();
        // IsilonReplicationApiTest.testGetTargetPolicy();
        // IsilonReplicationApiTest.testGetReplicationPolicyReport();
        // IsilonReplicationApiTest.teststartReplicationJob();
    }
}
