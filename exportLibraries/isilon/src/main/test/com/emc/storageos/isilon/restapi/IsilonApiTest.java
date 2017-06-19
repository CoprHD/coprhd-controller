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
    private String testPath = "/ifs/junitTestDir/" + dateSuffix;
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

        // Step 1: Create directory
        String testSMBDirPath = testPath + "/testSMBDir01";
        String testSMBDirShareName = "testSMBShare" + dateSuffix;
        _client.createDir(testSMBDirPath, true);

        if (!_client.existsDir(testSMBDirPath)) {
            throw new Exception("existsDir for " + testSMBDirPath + ": failed");
        }

        // Step 2: Create the SMB share for directory
        String shareId = _client
                .createShare(new IsilonSMBShare(testSMBDirShareName, testSMBDirPath, "smb test share", "allow", "full"));
        Assert.assertTrue("SMB share create failed.", (shareId != null && !shareId.isEmpty()));
        System.out.println("SMB Share created: id: " + shareId);

        IsilonSMBShare share = _client.getShare(shareId);
        Assert.assertTrue("SMB share create failed.", share != null);

        // Step 2: modify SMB share
        _client.modifyShare(shareId, new IsilonSMBShare(testSMBDirShareName, testSMBDirPath, "smb test share modify", "allow", "read"));

        List<IsilonSMBShare> lShares = _client.listShares(null).getList();
        System.out.println("listShares: count: " + lShares.size() + " : " + lShares.toString());

        // Step 3: delete the SMB share

        _client.deleteShare(shareId);
        try {
            share = _client.getShare(shareId);
            Assert.assertTrue("Deleted SMB share still gettable.", false);
        } catch (IsilonException e) {
            _log.error(e.getMessage(), e);
        }

        // Step 3: delete the directory.

        _client.deleteDir(testSMBDirPath);
        Assert.assertFalse("Directory delete failed.", _client.existsDir(testSMBDirPath));
    }

    @Test
    public void testDirectoriesAndSnapshots() throws Exception {

        // Step 1: Create directory
        String testDirPath = testPath + "/testDir01";

        _client.createDir(testDirPath, true);
        if (!_client.existsDir(testDirPath)) {
            throw new Exception("Create directory --- " + testDirPath + ": failed");
        }
        System.out.println("Created directory: " + testDirPath);

        // Step 2 create a sub directory inside directory

        String subDir1 = testDirPath + "/dir1/dir2";
        _client.createDir(subDir1, true);
        if (!_client.existsDir(subDir1)) {
            throw new Exception("Createa sub directory --- " + subDir1 + ": failed");
        }

        System.out.println("Created directory: " + subDir1);
        /* snapshot tests - start */

        // Step 3 create a Snapshot with unique name
        String testSnapName = "testSnap01" + dateSuffix;
        String snapId = _client.createSnapshot(testSnapName, testDirPath);

        List<IsilonSnapshot> snaps = _client.listSnapshots(null).getList();
        System.out.println("listSnaps: count: " + snaps.size() + " : " + snaps.toString());
        IsilonSnapshot snap = _client.getSnapshot(snapId);
        Assert.assertTrue(snap.getId().compareTo(snapId) == 0 && snap.getPath().compareTo(testDirPath) == 0
                && snap.getName().compareTo(testSnapName) == 0);

        // Step 4 Modify the snapshot
        IsilonSnapshot renamed = new IsilonSnapshot();
        renamed.setName("testSnap01_Renamed_" + dateSuffix);
        _client.modifySnapshot(snapId, renamed);
        IsilonSnapshot snap2 = _client.getSnapshot(snapId);
        Assert.assertTrue(snap2.getId().compareTo(snapId) == 0
                && snap2.getPath().compareTo(testDirPath) == 0
                && snap2.getName().compareTo("testSnap01_Renamed_" + dateSuffix) == 0);

        // Step 5 delete the snapshot
        _client.deleteSnapshot(snapId);

        try {
            _client.getSnapshot(snapId);
            Assert.assertTrue("deleted snapshot still exists", false);
        } catch (IsilonException ie) {
            // success
            Assert.assertTrue("Getting Deleted snapshot result in excpetion ", true);
        }

        try {
            snaps.clear();
            snaps = _client.listSnapshots(null).getList();
            for (int i = 0; i < snaps.size(); i++) {
                if (snaps.get(i).getId().compareTo(snapId) == 0) {
                    Assert.assertTrue("deleted snapshot still exists", false);
                }
            }
        } catch (IsilonException ex) {
            Assert.assertTrue("deleted snapshot still exists is failed", false);
        }

        // negative test case

        // Step 6 create a directory where no isilon is runnning -
        try {
            IsilonApi clientError = _factory.getRESTClient(URI.create("https://10.0.0.0:8080"), "root", "sos");
            clientError.existsDir(testDirPath);  // expected to throw
            Assert.assertTrue("Attempt to use dummy client succeeded.", false);
        } catch (Exception ex) {
            // wea are expecting this exception as there is no isilon at 10.0.0.0:
            Assert.assertTrue("Attempt to use dummy client is failed", true);
        }

        // Step 7: Delete the directory with wrong path.
        try {
            _client.deleteDir("/ifs/dummy_delete");
        } catch (Exception ex) {
            Assert.assertTrue("Attempt to delete non existing directory failed.", false);
        }

        // Step 8 Delete the directory with sub dir without recursive flag

        try {
            _client.deleteDir(testDirPath);
            // The directory has data in it
            // if it got deleted, fail the test case!!
            String message = "Directory " + testDirPath + " got deleted even it is not empty";
            throw new Exception(message);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            String message = "Fail to delete directory " + testDirPath + " since it is not empty";
            Assert.assertTrue(message, true);
        }

        // Verify the directory is empty or not!!
        // testDirPath path has sub-directories
        String message = "fsDirHasData is fail to detect the files or folders in  " + testDirPath;
        Assert.assertTrue(message, _client.fsDirHasData(testDirPath));

        // Now remove all sub-directories
        // this test created "/testDir01/dir1/dir2";
        // so delete them

        String dirToBeDeleted = testDirPath + "/dir1/dir2";
        _client.deleteDir(dirToBeDeleted);
        message = "Fail to delete empty directory " + dirToBeDeleted;
        Assert.assertFalse(message, _client.existsDir(dirToBeDeleted));
        System.out.println("Deleted directory: " + dirToBeDeleted);

        dirToBeDeleted = testDirPath + "/dir1";
        _client.deleteDir(dirToBeDeleted);
        message = "Fail to delete empty directory " + dirToBeDeleted;
        Assert.assertFalse(message, _client.existsDir(dirToBeDeleted));
        System.out.println("Deleted directory: " + dirToBeDeleted);

        // finally delete the parent directory!!
        _client.deleteDir(testDirPath);
        message = "Fail to delete empty directory " + testDirPath;
        Assert.assertFalse(message, _client.existsDir(testDirPath));
        System.out.println("Deleted directory: " + testDirPath);

    }

    @Test
    public void testQuotas() throws Exception {

        // Step 1: Create directory
        String testQuotasDirPath = testPath + "/testQuotaDir01";
        String testQuotasDirPath2 = testPath + "/testQuotaDir02";

        _client.createDir(testQuotasDirPath);
        if (!_client.existsDir(testQuotasDirPath)) {
            throw new Exception("Create directory --- " + testQuotasDirPath + ": failed");
        }
        System.out.println("Created directory: " + testQuotasDirPath);

        /* SmartQuota tests - start */
        // test limit quota
        // - create
        String qid = _client.createQuota(testQuotasDirPath, 404800000000L); // quota in bytes --- 20MB
        System.out.println("Quota created: id: " + qid);

        // - get
        IsilonSmartQuota quota = _client.getQuota(qid);
        Assert.assertTrue(qid.compareTo(quota.getId()) == 0
                && testQuotasDirPath.compareTo(quota.getPath()) == 0
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
                && testQuotasDirPath.compareTo(quota.getPath()) == 0
                && quota.getThresholds().getHard() == 10240000
                && quota.getThresholds().getAdvisory() == 1024000
                && quota.getThresholds().getSoft() == 3072000
                && quota.getThresholds().getSoftGrace() == 86400);

        // test accounting quota
        _client.createDir(testQuotasDirPath2);
        if (!_client.existsDir(testQuotasDirPath2)) {
            throw new Exception("Create directory --- " + testQuotasDirPath2 + ": failed");
        }
        System.out.println("Created directory: " + testQuotasDirPath2);
        // - create
        String qid_acc = _client.createQuota(testQuotasDirPath2); // no limit --- accounting quota
        System.out.println("Quota created: id: " + qid_acc);

        // - get
        quota = _client.getQuota(qid_acc);
        Assert.assertTrue(qid_acc.compareTo(quota.getId()) == 0
                && testQuotasDirPath2.compareTo(quota.getPath()) == 0
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

        // delete the quota dir1 which is empty
        _client.deleteDir(testQuotasDirPath);
        Assert.assertFalse("Directory delete failed.", _client.existsDir(testQuotasDirPath));

        // delete the quota dir2 which is empty
        _client.deleteDir(testQuotasDirPath2);
        Assert.assertFalse("Directory delete failed.", _client.existsDir(testQuotasDirPath2));

    }

    @Test
    public void testNFSExports() throws Exception {

        // Step 1: Create directory
        String testExportDirPath = testPath + "/testExportDir01";
        _client.createDir(testExportDirPath, true);
        if (!_client.existsDir(testExportDirPath)) {
            throw new Exception("Create directory --- " + testExportDirPath + ": failed");
        }
        System.out.println("Created directory: " + testExportDirPath);

        // Step 2 create snapshot
        String snapName = "test_snap_" + dateSuffix;
        String snap_id = _client.createSnapshot(snapName, testExportDirPath);
        // - list/get
        List<IsilonSnapshot> snaps = _client.listSnapshots(null).getList();
        System.out.println("listSnaps: count: " + snaps.size() + " : " + snaps.toString());
        IsilonSnapshot snap = _client.getSnapshot(snap_id);
        Assert.assertTrue(snap.getId().compareTo(snap_id) == 0
                && snap.getPath().compareTo(testExportDirPath) == 0
                && snap.getName().compareTo(snapName) == 0);

        // Step 3 Create export with default settings: sys.rw.nobody
        IsilonExport e1 = new IsilonExport();
        e1.addPath(testExportDirPath);
        e1.addClient("www.amazon.com");
        e1.addClient("www.ford.com");
        ArrayList<String> securityFlavors1 = new ArrayList<String>();
        securityFlavors1.add("unix");
        e1.setSecurityFlavors(securityFlavors1);
        e1.setReadOnly();
        e1.setComment("New export: unix.rw.nobody");

        String export1Id = _client.createExport(e1);
        Assert.assertTrue(Integer.parseInt(export1Id) > 0);
        // Step 4 verify the created export

        IsilonExport exp1 = _client.getExport(export1Id);
        Assert.assertTrue(exp1.getId().toString().equals(export1Id));
        Assert.assertTrue(exp1.getSecurityFlavors().get(0).equals("unix"));
        Assert.assertTrue(exp1.getMap_root().getUser().equals("nobody"));
        System.out.println("Export created: " + exp1);

        // Step 5 modify file system export
        IsilonExport exp_modified = new IsilonExport();
        exp_modified.setComment("modified");
        _client.modifyExport(export1Id, exp_modified);
        Assert.assertTrue(_client.getExport(export1Id).getComment().equals("modified"));

        // Step 6 Create snap export with default settings: sys.rw.nobody
        IsilonExport snapEx1 = new IsilonExport();
        // build snap mount path
        snapEx1.addPath("/ifs/.snapshot/" + snapName + testExportDirPath.substring("/ifs".length()));
        snapEx1.addClient("www.emc.com");
        snapEx1.addClient("www.honda.com");
        snapEx1.setSecurityFlavors(securityFlavors1);
        snapEx1.setComment("New snapshot export: unix.rw.nobody");
        System.out.println("Request to create snap export: " + snapEx1);

        String snapExport1Id = _client.createExport(snapEx1);

        // Step 7 verify the created export
        Assert.assertTrue(Integer.parseInt(snapExport1Id) > 0);
        IsilonExport sExp1 = _client.getExport(snapExport1Id);
        Assert.assertTrue(sExp1.getId().toString().equals(snapExport1Id));
        Assert.assertFalse(sExp1.getReadOnly());
        Assert.assertTrue(sExp1.getSecurityFlavors().get(0).equals("unix"));
        Assert.assertTrue(sExp1.getMap_root().getUser().equals("nobody"));
        System.out.println("Snap Export created: " + sExp1);

        // Step 8 Create export with custom settings: krb5.root.root
        IsilonExport e2 = new IsilonExport();
        e2.addPath(testExportDirPath);
        e2.addClient("www.emc.com");
        e2.addClient("www.gmc.com");
        ArrayList<String> securityFlavors = new ArrayList<String>();
        securityFlavors.add("krb5");
        e2.setSecurityFlavors(securityFlavors);
        e2.setMapAll("root"); // to indicate that this export has root permissions (required by PAPI)
        e2.setComment("New export: krb5.root.root");
        String export2Id = _client.createExport(e2);

        // Step 9 verify the created export
        Assert.assertTrue(Integer.parseInt(export2Id) > 0);
        IsilonExport exp2 = _client.getExport(export2Id);
        Assert.assertTrue(exp2.getId().toString().equals(export2Id));
        Assert.assertFalse(exp2.getReadOnly());
        Assert.assertTrue(exp2.getSecurityFlavors().get(0).equals("krb5"));
        Assert.assertTrue(exp2.getMap_all().getUser().equals("root"));
        System.out.println("Export created: " + exp2);

        // Step 10 modify export
        exp_modified = new IsilonExport();
        exp_modified.setComment("modified export");
        _client.modifyExport(export2Id, exp_modified);
        Assert.assertTrue(_client.getExport(export2Id).getComment().equals("modified export"));

        // Step 13 clean up export and other resource
        _client.deleteExport(export1Id);
        try {
            _client.getExport(export1Id);
            Assert.assertTrue("Deleted export still gettable", false);
        } catch (IsilonException ex) {
            // if we get exception means export is not available.
            Assert.assertTrue("Getting Deleted export result in excpetion ", true);
        }

        _client.deleteExport(snapExport1Id);
        try {
            _client.getExport(snapExport1Id);
            Assert.assertTrue("Deleted snap export still gettable", false);
        } catch (IsilonException ex) {
            _log.error(ex.getMessage(), ex);
        }

        _client.deleteExport(export2Id);
        try {
            _client.getExport(export2Id);
            Assert.assertTrue("Deleted export still gettable", false);

        } catch (IsilonException ex) {
            // if we get exception means export is not available.
            Assert.assertTrue("Getting Deleted export result in  excpetion", true);
        }

        _client.deleteSnapshot(snap_id);
        _client.deleteDir(testExportDirPath);
        Assert.assertFalse("Directory delete failed.", _client.existsDir(testExportDirPath));

    }

    @Test
    public void testStats() throws Exception {

        // test protocols
        ArrayList<IsilonStats.Protocol> protocols = _client.getStatsProtocols();
        Assert.assertTrue("Get stat protocols failed", protocols != null && protocols.isEmpty() == false);

    }
}