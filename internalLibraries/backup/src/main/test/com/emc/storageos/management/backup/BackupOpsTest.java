/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.backup;

import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import com.emc.storageos.coordinator.client.model.ProductName;
import com.emc.storageos.management.backup.exceptions.FatalBackupException;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This suite requires external services to be running, which is not the case on public build servers.
// For examples of a self-contained unit test, see DbServiceTestBase.  COP-19800
@Ignore
public class BackupOpsTest extends BackupTestBase {
    private static final Logger log = LoggerFactory.getLogger(BackupOpsTest.class);
    private static final String STANDALONE = "standalone";
    private static final String VIPR1 = "standalone";
    private static final String VIPR2 = "standalone";
    private static final String LOCALHOST = "127.0.0.1";
    private static BackupOps backupOps = new BackupOps();

    @BeforeClass
    public static void setUp() {
        Map<String, String> hosts = new TreeMap<>();
        hosts.put(STANDALONE, LOCALHOST);
        backupOps.setHosts(hosts);
        backupOps.setPorts(Arrays.asList(7199));
        backupOps.setCoordinatorClient(coordinatorClient);
        backupOps.setVdcList(Arrays.asList("vdc1"));
        ProductName name = new DummyProductName("vipr");
    }

    @Test
    public void testCreateBackup() {
        String backupName1 = "bk-standalone";
        backupOps.createBackup(backupName1);
        createBackup(backupName1, zkBackupHandler);
        createBackup(backupName1, geoDbBackupHandler);
    }

    @Test
    public void testCreateBackupWithInvalidBackupNames() {
        String invalidBackupName = "../abc";
        boolean expected = false;
        try {
            backupOps.createBackup(invalidBackupName);
        } catch (IllegalArgumentException e) {
            expected = true;
        }
        Assert.assertTrue(
                String.format("%s is not a valid backup name, IllegalArgumentException should be threw out", invalidBackupName),
                expected);

        invalidBackupName = "";
        expected = false;
        try {
            backupOps.createBackup(invalidBackupName);
        } catch (IllegalArgumentException e) {
            expected = true;
        }
        Assert.assertTrue(
                String.format("%s is not a valid backup name, IllegalArgumentException should be threw out", invalidBackupName),
                expected);
        // build a name of more than 200 characters
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 201; i++) {
            sb.append('a');
        }
        invalidBackupName = sb.toString();
        expected = false;
        try {
            backupOps.createBackup(invalidBackupName);
        } catch (IllegalArgumentException e) {
            expected = true;
        }
        Assert.assertTrue(
                String.format("%s is not a valid backup name, IllegalArgumentException should be threw out", invalidBackupName),
                expected);
    }

    @Test
    public void testListBackup() {
        // standalone cluster
        List<BackupSetInfo> backupSetList1 = backupOps.listBackup();
        Assert.assertNotNull(backupSetList1);
        for (BackupSetInfo backupset : backupSetList1) {
            log.info("Get backup info: {}", backupset.toString());
        }

        // multi-node cluster
        {
            Map<String, String> hosts = new TreeMap<>();
            hosts.put(VIPR1, LOCALHOST);
            hosts.put(VIPR2, LOCALHOST);
            backupOps.setHosts(hosts);
        }
        List<BackupSetInfo> backupSetList2 = null;
        try {
            backupSetList2 = backupOps.listBackup();
        } finally {
            Map<String, String> hosts = new TreeMap<>();
            hosts.put(STANDALONE, LOCALHOST);
            backupOps.setHosts(hosts);
        }
        Assert.assertNotNull(backupSetList2);
        for (BackupSetInfo backupset : backupSetList2) {
            log.info("Get backup info: {}", backupset.toString());
        }
    }

    @Test
    public void testDeleteBackup() {
        // standalone cluster
        String backupName1 = "bk-standalone";
        try {
            backupOps.deleteBackup(backupName1);
        } catch (FatalBackupException e) {
            log.info("Have deleted an non-existing backup");
        }
        List<BackupSetInfo> backupList1 = backupOps.listBackup();
        for (BackupSetInfo backupSetInfo1 : backupList1) {
            if (backupSetInfo1.getName().startsWith(backupName1)) {
                Assert.fail(String.format("Don't delete backup; %s", backupName1));
            }
        }
        boolean expected = false;
        try {
            backupOps.deleteBackup(backupName1);
        } catch (FatalBackupException e) {
            expected = true;
        }
        Assert.assertTrue("Can't detect non-existing backup", expected);
    }

    static class DummyProductName extends ProductName {
        public DummyProductName(String name) {
            super.setName(name);
        }
    }
}
