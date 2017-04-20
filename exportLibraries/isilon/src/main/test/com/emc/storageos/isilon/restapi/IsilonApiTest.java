/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.isilon.restapi;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.EnvConfig;

/*
 * Test client for IsilonRESTClient
 */
public class IsilonApiTest {
    private static final Logger _log = LoggerFactory.getLogger(IsilonApiTest.class);
    private static String dateSuffix = new SimpleDateFormat("yyyyMMddHHmm").format(new Date());
    private String _test_path = "/ifs/testDir_" + dateSuffix;
    private String _test_path10 = "/ifs/testDir10" + dateSuffix;
    private static String uri = EnvConfig.get("sanity", "isilon.uri");
    private static String userName = EnvConfig.get("sanity", "isilon.username");
    private static String password = EnvConfig.get("sanity", "isilon.password");
    private static volatile IsilonApi _client;
    private static volatile IsilonApiFactory _factory = new IsilonApiFactory();

    @BeforeClass
    public static void setup() throws Exception {
        if (_factory == null) {
            _factory = new IsilonApiFactory();
        }
        _factory.init();
        _client = _factory.getRESTClient(URI.create(uri), userName, password);    // 7.0 BETA 3
    }

    @Test
    public void testClusterInfo() throws Exception {
        System.out.println("Get Cluster info.");
        System.out.println("Cluster info: " + _client.getClusterInfo().toString());
    }

    @Test
    public void testClusterConfig() throws Exception {
        System.out.println("Get Cluster config.");
        System.out.println("Cluster config: " + _client.getClusterConfig().toString());
    }

    @Test
    public void testLists() throws Exception {
        IsilonApi.IsilonList<String> got = _client.listDir("/ifs", null);
        System.out.println("List dir: " + got.getList().toString());

        List<IsilonExport> exports = _client.listExports(null).getList();
        System.out.println("List exports: count: " + exports.size());
        for (int i = 0; i < exports.size(); i++) {
            System.out.println(exports.get(i).toString());
        }
    }

    @Test
    public void testSMBShares() throws Exception {

        String clusterName = _client.getClusterConfig().getName();
        // Create directory for SMB shares
        String testSMBDir = _test_path + "/testSMB01";
        _client.createDir(testSMBDir, true);

        if (!_client.existsDir(testSMBDir)) {
            throw new Exception("existsDir for " + testSMBDir + ": failed");
        }

        // SMB share tests
        String mapableShareName = (clusterName != null) ? "\\\\" + clusterName + "\\" + "smbShareTestER10" : "smbShareTestER10";
        System.out.println("SMB Share name: " + mapableShareName
                + "_" + dateSuffix);
        String shareId = _client
                .createShare(new IsilonSMBShare("smbShareTestER10_" + dateSuffix, testSMBDir, "smb test share", "allow", "full"));
        Assert.assertTrue("SMB share create failed.", (shareId != null && !shareId.isEmpty()));
        System.out.println("SMB Share created: id: " + shareId);

        IsilonSMBShare share = _client.getShare(shareId);
        Assert.assertTrue("SMB share create failed.", share != null);

        // modify share
        _client.modifyShare(shareId, new IsilonSMBShare("smbShareTestER10_" + dateSuffix, testSMBDir, "smb test share modify", "allow", "read"));

        List<IsilonSMBShare> lShares = _client.listShares(null).getList();
        System.out.println("listShares: count: " + lShares.size() + " : " + lShares.toString());

        _client.deleteShare(shareId);
        try {
            share = _client.getShare(shareId);
            Assert.assertTrue("Deleted SMB share still gettable.", false);
        } catch (IsilonException e) {
            _log.error(e.getMessage(), e);
        }

        // snapshot shares are not supported,

        // Test smb share for snapshots
        //
        // String snapName = "testSnap" + suffix;
        // String snapShareName = "testSnapShare" + suffix;
        // String fsPath = _test_path + "testFile01";
        // String snapPath = "//ifs//.snapshot/" + snapName + fsPath.substring("//ifs".length());
        // _client.createDir(fsPath, true);
        // String snap_id = _client.createSnapshot(snapName, fsPath);
        // String snapShareId = _client.createShare(new IsilonSMBShare(snapShareName, snapPath, "smb test snap share", "allow", "full"));
        // Assert.assertTrue("SMB share create failed.", (snapShareId != null && !snapShareId.isEmpty()));
        // System.out.println("SMB Share created: id: " + snapShareId);
        //
        // IsilonSMBShare snapShare = _client.getShare(snapShareId);
        // Assert.assertTrue("SMB share create failed.", snapShare != null);
        //
        // // modify share
        // _client.modifyShare(snapShareId, new IsilonSMBShare(snapShareName, snapPath, "smb test share modify", "allow",
        // "read"));
        //
        // lShares = _client.listShares(null).getList();
        // System.out.println("listShares: count: " + lShares.size() + " : " + lShares.toString());
        //
        // _client.deleteShare(snapShareId);
        //
        // try {
        // share = _client.getShare(snapShareId);
        // if (share != null) {
        // System.out.println("SMB Share name: " + share.getName());
        // }
        // Assert.assertTrue("Deleted SMB share still gettable.", false);
        // } catch (IsilonException e) {
        // _log.error(e.getMessage(), e);
        // }
        //
        // _client.deleteSnapshot(snap_id);
        // /* try to get deleted snapshot */
        // try {
        // IsilonSnapshot snap3 = _client.getSnapshot(snap_id);
        // Assert.assertTrue("deleted snapshot still exists", false);
        // } catch (IsilonException ie) {
        // _log.error(ie.getMessage(), ie);
        // }
        // _client.deleteDir(_test_path, true);
    }

    @Test
    public void testDirectoriesAndSnapshots() throws Exception {

        /* directory for api tests - start */
        // System.out.println("Start: " + System.currentTimeMillis());
        _client.createDir(_test_path);
        // System.out.println("End: " + System.currentTimeMillis());
        if (!_client.existsDir(_test_path)) {
            throw new Exception("Create directory --- " + _test_path + ": failed");
        }
        System.out.println("Created directory: " + _test_path);
        _client.deleteDir(_test_path, true);

        String dir = _test_path + "/test2";
        String dir1 = dir + "/dir1/dir2";
        _client.createDir(dir1, true);
        if (!_client.existsDir(dir1)) {
            throw new Exception("Create directory --- " + dir1 + ": failed");
        }
        _client.deleteDir(dir, true);
        // _client.deleteDir("/ifs/sos/urn:storageos:FileShare:2ab41fb8-c6e3-4b7c-b990-bd9a6b3ae365:", true);
        // System.out.println("End: " + System.currentTimeMillis());
        Assert.assertFalse("Directory delete failed.", _client.existsDir(dir));

        /* snapshot tests - start */
        // - create
        // we can not have same name for two snapshot event there path are different
        String snap_id = _client.createSnapshot("test_snap_" + dateSuffix, _test_path);
        // - list/get
        List<IsilonSnapshot> snaps = _client.listSnapshots(null).getList();
        System.out.println("listSnaps: count: " + snaps.size() + " : " + snaps.toString());
        IsilonSnapshot snap = _client.getSnapshot(snap_id);
        Assert.assertTrue(snap.getId().compareTo(snap_id) == 0
                && snap.getPath().compareTo(_test_path) == 0
                && snap.getName().compareTo("test_snap_" + dateSuffix) == 0);

        // - modify
        IsilonSnapshot renamed = new IsilonSnapshot();
        renamed.setName("test_snap_renamed_" + dateSuffix);
        _client.modifySnapshot(snap_id, renamed);
        IsilonSnapshot snap2 = _client.getSnapshot(snap_id);
        Assert.assertTrue(snap2.getId().compareTo(snap_id) == 0
                && snap2.getPath().compareTo(_test_path) == 0
                && snap2.getName().compareTo("test_snap_renamed_" + dateSuffix) == 0);

        // - delete
        _client.deleteSnapshot(snap_id);

        /* try to get deleted snapshot */
        try {
            IsilonSnapshot snap3 = _client.getSnapshot(snap_id);
            Assert.assertTrue("deleted snapshot still exists", false);
        } catch (IsilonException ie) {
            // success
            Assert.assertTrue("Getting Deleted snapshot result in excpetion ", true);
        }

        try {
            snaps.clear();
            snaps = _client.listSnapshots(null).getList();
            for (int i = 0; i < snaps.size(); i++) {
                if (snaps.get(i).getId().compareTo(snap_id) == 0) {
                    Assert.assertTrue("deleted snapshot still exists", false);
                }
            }
        } catch (IsilonException ex) {
            Assert.assertTrue("deleted snapshot still exists is failed", false);
        }
        /* snapshot tests - done */

        // existsDir - negative
        try {
            IsilonApi clientError = _factory.getRESTClient(URI.create("https://10.0.0.0:8080"), "root", "sos");
            clientError.existsDir(_test_path);  // expected to throw
            Assert.assertTrue("Attempt to use dummy client succeeded.", false);
        } catch (Exception ex) {
            // wea are expecting this exception as there is no isilon at 10.0.0.0:
            Assert.assertTrue("Attempt to use dummy client is failed", true);
        }

        _client.deleteDir(_test_path, true);
        Assert.assertFalse("Directory delete failed.", _client.existsDir(_test_path));

        try {
            _client.deleteDir("/ifs/dummy_delete");
        } catch (Exception ex) {
            Assert.assertTrue("Attempt to delete non existing directory failed.", false);
        }
    }

    @Test
    public void testQuotas() throws Exception {

        _client.createDir(_test_path);
        if (!_client.existsDir(_test_path)) {
            throw new Exception("Create directory --- " + _test_path + ": failed");
        }
        System.out.println("Created directory: " + _test_path);

        /* SmartQuota tests - start */
        // test limit quota
        // - create
        String qid = _client.createQuota(_test_path, 404800000000L); // quota in bytes --- 20MB
        System.out.println("Quota created: id: " + qid);

        // - get
        IsilonSmartQuota quota = _client.getQuota(qid);
        Assert.assertTrue(qid.compareTo(quota.getId()) == 0
                && _test_path.compareTo(quota.getPath()) == 0
                && quota.getThresholds().getHard() == 404800000000L); // 20480000);
        Assert.assertTrue("Usage Physical is greater than hard quota", quota.getUsagePhysical() <= quota.getThresholds().getHard());

        // - list
        IsilonApi.IsilonList<IsilonSmartQuota> quotas = _client.listQuotas(null);
        System.out.println("listQuotas: count: " + quotas.getList().size());
        while (quotas.getToken() != null && !quotas.getToken().isEmpty()) {
            quotas = _client.listQuotas(quotas.getToken());
            System.out.println("listQuotas(Resumed): count: " + quotas.size());
        }

        // - modify
        IsilonSmartQuota quota1 = new IsilonSmartQuota();
        quota1.setThresholds(10240000L, 1024000L, 3072000L, 86400L); // quota in bytes --- 10MB
        _client.modifyQuota(qid, quota1);
        quota = _client.getQuota(qid);
        System.out.println("Modified quota: " + quota);
        Assert.assertTrue(qid.compareTo(quota.getId()) == 0
                && _test_path.compareTo(quota.getPath()) == 0
                && quota.getThresholds().getHard() == 10240000
                && quota.getThresholds().getAdvisory() == 1024000
                && quota.getThresholds().getSoft() == 3072000
                && quota.getThresholds().getSoftGrace() == 86400);

        // test accounting quota
        _client.createDir(_test_path10);
        if (!_client.existsDir(_test_path10)) {
            throw new Exception("Create directory --- " + _test_path10 + ": failed");
        }
        System.out.println("Created directory: " + _test_path10);
        // - create
        String qid_acc = _client.createQuota(_test_path10); // no limit --- accounting quota
        System.out.println("Quota created: id: " + qid_acc);

        // - get
        quota = _client.getQuota(qid_acc);
        Assert.assertTrue(qid_acc.compareTo(quota.getId()) == 0
                && _test_path10.compareTo(quota.getPath()) == 0
                && quota.getThresholds().getHard() == null);

        // end of accounting quota test

        // - delete
        _client.deleteQuota(qid);
        try {
            quota = _client.getQuota(qid);
            Assert.assertTrue("deleted quota still gettable", false);
        } catch (IsilonException ex) {
            // if exception mean quota already deleted.
            Assert.assertTrue("exception in getting deleted quota  ", true);
        }

        _client.deleteQuota(qid_acc);
        try {
            quota = _client.getQuota(qid_acc);
            Assert.assertTrue("deleted quota still gettable", false);
        } catch (IsilonException ex) {
            // if exception mean quota already deleted.
            Assert.assertTrue("exception in getting deleted quota  ", true);
        }

        _client.deleteDir(_test_path, true);
        Assert.assertFalse("Directory delete failed.", _client.existsDir(_test_path));

        _client.deleteDir(_test_path10, true);
        Assert.assertFalse("Directory delete failed.", _client.existsDir(_test_path10));

    }

    // ER.
    /**
     * Small function to clean exports form cluster.
     * Do not run as a test.
     * 
     * @throws Exception
     */
    // @Test
    public void testExportDelete() throws Exception {

        String parentDirectory = "/ifs/";
        int dirCount = _client.listDir(parentDirectory, null).size();
        System.out.println("Directories count in " + parentDirectory + " is: " + dirCount);

        // String exportId = null;
        //
        // for (int i = 1039; i < 1080; i++) {
        // exportId = String.valueOf(i);
        // try {
        // _client.deleteExport(exportId);
        // System.out.println("Deleted export: " + exportId);
        // } catch (Exception ex) {
        // System.out.println("Exception when deleting export: " + exportId);
        // }
        // }
    }

    //

    @Test
    public void testNFSExports() throws Exception {

        boolean force = false;

        _client.createDir(_test_path);
        if (!_client.existsDir(_test_path)) {
            throw new Exception("Create directory --- " + _test_path + ": failed");
        }
        System.out.println("Created directory: " + _test_path);

        // create snapshot
        String snapName = "test_snap_" + dateSuffix;
        String snap_id = _client.createSnapshot(snapName, _test_path);
        // - list/get
        List<IsilonSnapshot> snaps = _client.listSnapshots(null).getList();
        System.out.println("listSnaps: count: " + snaps.size() + " : " + snaps.toString());
        IsilonSnapshot snap = _client.getSnapshot(snap_id);
        Assert.assertTrue(snap.getId().compareTo(snap_id) == 0
                && snap.getPath().compareTo(_test_path) == 0
                && snap.getName().compareTo(snapName) == 0);

        /* nfs exports tests - start */
        // Create export with default settings: sys.rw.nobody
        IsilonExport e1 = new IsilonExport();
        e1.addPath(_test_path);
        e1.addClient("www.amazon.com");
        e1.addClient("www.ford.com");
        ArrayList<String> securityFlavors1 = new ArrayList<String>();
        securityFlavors1.add("unix");
        e1.setSecurityFlavors(securityFlavors1);
        e1.setReadOnly();
        e1.setComment("New export: unix.rw.nobody");

        String export1Id = _client.createExport(e1, force);
        Assert.assertTrue(Integer.parseInt(export1Id) > 0);
        IsilonExport exp1 = _client.getExport(export1Id);
        Assert.assertTrue(exp1.getId().toString().equals(export1Id));
        // Assert.assertFalse(exp1.getReadOnly());
        Assert.assertTrue(exp1.getSecurityFlavors().get(0).equals("unix"));
        Assert.assertTrue(exp1.getMap_root().getUser().equals("nobody"));
        System.out.println("Export created: " + exp1);

        // Create snap export with default settings: sys.rw.nobody
        IsilonExport snapEx1 = new IsilonExport();
        // build snap mount path
        snapEx1.addPath("/ifs/.snapshot/" + snapName + _test_path.substring("/ifs".length()));
        snapEx1.addClient("www.emc.com");
        snapEx1.addClient("www.honda.com");
        snapEx1.setSecurityFlavors(securityFlavors1);
        snapEx1.setComment("New snapshot export: unix.rw.nobody");
        System.out.println("Request to create snap export: " + snapEx1);

        String snapExport1Id = _client.createExport(snapEx1, force);
        Assert.assertTrue(Integer.parseInt(snapExport1Id) > 0);
        IsilonExport sExp1 = _client.getExport(snapExport1Id);
        Assert.assertTrue(sExp1.getId().toString().equals(snapExport1Id));
        Assert.assertFalse(sExp1.getReadOnly());
        Assert.assertTrue(sExp1.getSecurityFlavors().get(0).equals("unix"));
        Assert.assertTrue(sExp1.getMap_root().getUser().equals("nobody"));
        System.out.println("Snap Export created: " + sExp1);

        // delete snap export
        _client.deleteExport(snapExport1Id);
        try {
            _client.getExport(snapExport1Id);
            Assert.assertTrue("Deleted snap export still gettable", false);
        } catch (IsilonException ex) {
            _log.error(ex.getMessage(), ex);
        }

        // modify file system export
        IsilonExport exp_modified = new IsilonExport();
        exp_modified.setComment("modified");
        _client.modifyExport(export1Id, exp_modified, force);
        Assert.assertTrue(_client.getExport(export1Id).getComment().equals("modified"));

        // Create export with custom settings: krb5.root.root
        IsilonExport e2 = new IsilonExport();
        e2.addPath(_test_path);
        e2.addClient("www.emc.com");
        e2.addClient("www.gmc.com");
        ArrayList<String> securityFlavors = new ArrayList<String>();
        securityFlavors.add("krb5");
        e2.setSecurityFlavors(securityFlavors);
        e2.setMapAll("root"); // to indicate that this export has root permissions (required by PAPI)
        e2.setComment("New export: krb5.root.root");

        String export2Id = _client.createExport(e2, force);
        Assert.assertTrue(Integer.parseInt(export2Id) > 0);
        IsilonExport exp2 = _client.getExport(export2Id);
        Assert.assertTrue(exp2.getId().toString().equals(export2Id));
        Assert.assertFalse(exp2.getReadOnly());
        Assert.assertTrue(exp2.getSecurityFlavors().get(0).equals("krb5"));
        Assert.assertTrue(exp2.getMap_all().getUser().equals("root"));
        System.out.println("Export created: " + exp2);

        // modify export
        exp_modified = new IsilonExport();
        exp_modified.setComment("modified export");
        _client.modifyExport(export2Id, exp_modified, force);
        Assert.assertTrue(_client.getExport(export2Id).getComment().equals("modified export"));

        /* nfs exports tests - with fqdn bypass */
        IsilonExport ie3 = new IsilonExport();
        ie3.addPath(_test_path);
        ie3.addClient("abcd" + dateSuffix);
        ArrayList<String> securityFlavors3 = new ArrayList<String>();
        securityFlavors3.add("krb5i");
        ie3.setSecurityFlavors(securityFlavors3);
        ie3.setReadOnly();
        ie3.setComment("New export: unix.rw.nobody");

        String export3Id = _client.createExport(ie3, true);
        Assert.assertTrue(Integer.parseInt(export3Id) > 0);
        IsilonExport exp3 = _client.getExport(export3Id);
        Assert.assertTrue(exp3.getId().toString().equals(export3Id));
        Assert.assertTrue(exp3.getSecurityFlavors().get(0).equals("krb5i"));
        Assert.assertTrue(exp3.getMap_root().getUser().equals("nobody"));
        System.out.println("Export created: " + exp3);

        // list exports
        List<IsilonExport> exports = _client.listExports(null).getList();
        Assert.assertTrue("List exports failed.", exports.size() >= 2);

        // delete export
        _client.deleteExport(export1Id);
        try {
            _client.getExport(export1Id);
            Assert.assertTrue("Deleted export still gettable", false);
        } catch (IsilonException ex) {
            // if we get exception means export is not available.
            Assert.assertTrue("Getting Deleted export result in excpetion ", true);
        }

        // delete export
        _client.deleteExport(export2Id);
        try {
            _client.getExport(export2Id);
            Assert.assertTrue("Deleted export still gettable", false);

        } catch (IsilonException ex) {
            // if we get exception means export is not available.
            Assert.assertTrue("Getting Deleted export result in  excpetion", true);
        }

        // - delete snap
        _client.deleteSnapshot(snap_id);

        _client.deleteDir(_test_path, true);
        Assert.assertFalse("Directory delete failed.", _client.existsDir(_test_path));
        /* nfs exports tests - done */



        // delete export
        _client.deleteExport(export3Id);
        try {
            _client.getExport(export3Id);
            Assert.assertTrue("Deleted export still gettable", false);
        } catch (IsilonException ex) {
            // if we get exception means export is not available.
            Assert.assertTrue("Getting Deleted export result in excpetion ", true);
        }

    }

    @Test
    public void testEvents() throws Exception {
        // list
        List<IsilonEvent> events = _client.listEvents(null).getList();
        for (IsilonEvent e : events) {
            _log.info(e.toString());

            // System.out.println(e.toString());
            Assert.assertTrue("Event type is null", e.getEventId() != null);
            Assert.assertTrue("Event unique id is null", e.getInstanceId() != null);
            Assert.assertTrue("Latest time is not positive", e.getLatestTime() > 0);
            Assert.assertTrue("Device Id is negative", e.getDevId() >= 0);
        }
    }

    @Test
    public void testStats() throws Exception {

        // test protocols
        ArrayList<IsilonStats.Protocol> protocols = _client.getStatsProtocols();
        Assert.assertTrue("Get stat protocols failed", protocols != null && protocols.isEmpty() == false);

        /*
         * // TODO: commented out. Need to rewrite to work with new Isilon statistics API
         * // get "node.clientstats.proto.nfs" stat
         * HashMap<String, IsilonStats.StatValueCurrent<ArrayList<IsilonStats.StatsClientProto>>> currentAll =
         * _client.getStatsCurrent("node.clientstats.proto.nfs",
         * new TypeToken<ArrayList<IsilonStats.StatsClientProto>>() {}.getType());
         * ArrayList<IsilonStats.StatsClientProto> values = currentAll.get("1").getValue();
         * for (IsilonStats.StatsClientProto value: values) {
         * String client = value.getClientAddr();
         * float outBw = value.getOutBW();
         * float inBw = value.getInBW();
         * long readOps = value.getReadOps();
         * long writeOps = value.getWriteOps();
         * _log.info(String.format("%s: outBW(%s), inBW(%s), ops(%s)",
         * client, outBw, inBw, readOps+writeOps));
         * }
         * // To Do - set begin as last polled time
         * HashMap<String, IsilonStats.StatValueHistory<ArrayList<IsilonStats.StatsClientProto>>> hist =
         * _client.getStatsHistory("node.clientstats.proto.nfs", 0,
         * new TypeToken<ArrayList<IsilonStats.StatsClientProto>>() {}.getType());
         * if (!hist.isEmpty()) {
         * HashMap<Long, ArrayList<IsilonStats.StatsClientProto>> hValues = hist.get("1").getValues();
         * // add up the last hr
         * class HostStat {
         * public long reads = 0;
         * public long writes = 0;
         * ArrayList<Float> outBw = new ArrayList<Float>();
         * ArrayList<Float> inBw = new ArrayList<Float>();
         * private float getAvg(ArrayList<Float> inArray) {
         * float sum = 0;
         * for (int i = 0; i < inArray.size(); i++) {
         * sum += inArray.get(i);
         * }
         * return sum/inArray.size();
         * }
         * public float getOutBWAvg() {
         * return getAvg(outBw);
         * }
         * public float getInBWAvg() {
         * return getAvg(inBw);
         * }
         * }
         * HashMap<String, HostStat> hostStats = new HashMap<String, HostStat>();
         * Iterator<Map.Entry<Long, ArrayList<IsilonStats.StatsClientProto>>> it =
         * hValues.entrySet().iterator();
         * while(it.hasNext()) {
         * Map.Entry <Long, ArrayList<IsilonStats.StatsClientProto>> entry = it.next();
         * // To Do - check timestamp
         * ArrayList<IsilonStats.StatsClientProto> entryValues = entry.getValue();
         * for (IsilonStats.StatsClientProto value: entryValues) {
         * String client = value.getClientAddr();
         * float outBw = value.getOutBW();
         * float inBw = value.getInBW();
         * if (!hostStats.containsKey(client)) {
         * hostStats.put(client, new HostStat());
         * }
         * HostStat stat = hostStats.get(client);
         * if (outBw > 0)
         * stat.outBw.add(outBw);
         * if (inBw > 0)
         * stat.inBw.add(inBw);
         * stat.reads += value.getReadOps();
         * stat.writes += value.getWriteOps();
         * }
         * }
         * Iterator<Map.Entry <String, HostStat>> resultsIt = hostStats.entrySet().iterator();
         * while(it.hasNext()){
         * Map.Entry <String, HostStat> result = resultsIt.next();
         * _log.info(String.format("%s: OutBW(%s), InBW(%s), ops(%s)",
         * result.getKey(), result.getValue().getOutBWAvg(), result.getValue().getInBWAvg(),
         * result.getValue().reads + result.getValue().writes));
         * }
         * }
         * // generic stats queries
         * _log.info("Current stats detail: " + _client.getStatsCurrent("node.uptime",
         * new TypeToken<Integer>() {}.getType()).toString());
         */
        _log.info("Done");
    }
}
