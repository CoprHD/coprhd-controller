package com.emc.storageos.isilon.restapi;

import java.net.URI;
import java.net.URISyntaxException;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.volumecontroller.impl.isilon.IsilonFileStorageDeviceReplicationTest;

/*
 * Test client for IsilonRESTClient
 */
public class IsilonReplicationApiTest {

    // private static final Logger _log = LoggerFactory.getLogger(IsilonReplicationApiTest.class);
    private static volatile IsilonApi _client;
    private static volatile IsilonApiFactory _factory = new IsilonApiFactory();
    private static String ip = EnvConfig.get("sanity", "isilon.ip");
    private static String userName = EnvConfig.get("sanity", "isilon.username");
    private static String password = EnvConfig.get("sanity", "isilon.password");

    public static void setup() throws URISyntaxException {
        URI deviceURI = new URI("https", null, ip, 8080, "/", null, null);
        _factory = new IsilonApiFactory();
        _factory.init();
        _client = _factory.getRESTClient(deviceURI, userName, password);
    }

    public static void testlicenseInfo() throws Exception {
        System.out.println("Get license info.");
        System.out.println(" SyncIQ license info: " + _client.getReplicationLicenseInfo());
    }

    // Policy Tests *************************************************************************

    public static void testCreateReplicationPolicy() throws Exception {
        IsilonSyncPolicy policy = new IsilonSyncPolicy();
        policy.setAction(IsilonSyncPolicy.Action.sync);
        policy.setEnabled(true);
        policy.setName("");
        policy.setSource_root_path("");
        policy.setTarget_host("");
        policy.setTarget_path("");
        System.out.println("Replication policy: " + policy.toString());
        String policyID = _client.createReplicationPolicy(policy);
        System.out.println("Replication policy: " + policyID);
    }

    public static void testGetReplicationPolicy() throws Exception {
        System.out.println("Get Replication Policy info.");
        System.out.println(" Replication Policy : " + _client.getReplicationPolicy("").toString());
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

    // JOB TESTs *****************************************************************************************

    public static void testGetReplicationJob() throws Exception {
        System.out.println("Get Replication Job info.");
        System.out.println(" Replication Job : " + _client.getReplicationJob("b347a5cfe174440db7d845c3189aa4b2").toString());
    }

    public static void testModifyReplicationJob(String state) throws Exception {
        String policyID = "testpolicy";
        IsilonSshApi sshDmApi = new IsilonSshApi();
        sshDmApi.setConnParams(ip, userName, password);
        IsilonXMLApiResult result = sshDmApi.executeSsh("sync jobs" + "" + state + "" + policyID, "");
        if (result.isCommandSuccess()) {
            System.out.println("successfully changed the job state");
        }
    }

    public static void main(String args[]) throws Exception {
        // IsilonReplicationApiTest.setup();

        // IsilonReplicationApiTest.testlicenseInfo();
        // IsilonReplicationApiTest.testGetReplicationPolicy();
        // IsilonReplicationApiTest.testCreateReplicationPolicy();
        // IsilonReplicationApiTest.testModifyReplicationPolicy();
        // IsilonReplicationApiTest.testdeleteReplicationPolicy();

        // IsilonReplicationApiTest.testGetReplicationJob();
        // IsilonReplicationApiTest.testModifyReplicationJob("start");
        System.out.println("hh");
        IsilonFileStorageDeviceReplicationTest.setUp();

    }
}
