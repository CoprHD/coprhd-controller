/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.backup;

import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.Assert;

import java.io.File;
import java.io.FilenameFilter;
import java.util.UUID;

// This test fails on public build servers and coverage servers because it is not "self-contained" and relies on external
// services to be running.  Therefore it is Ignored by default. COP-19800
@Ignore
public class DbBackupHandlerTest extends BackupTestBase {

    private static DbBackupHandler dbBackupHandler;

    @BeforeClass
    public static void setUp() {
        // Suppress Sonar violation of Lazy initialization of static fields should be synchronized
        // Junit test will be called in single thread by default, it's safe to ignore this violation
        dbBackupHandler = (DbBackupHandler) backupManager.getBackupHandler(); // NOSONAR ("squid:S2444")
    }

    @Test
    public void testCreateBackup() {
        final String snapshotTag = UUID.randomUUID().toString();
        dbBackupHandler.createBackup(snapshotTag);
        for (String keyspace : dbBackupHandler.getKeyspaceList()) {
            File[] cfFolders = dbBackupHandler.getValidKeyspace(keyspace).listFiles();
            for (File cfFolder : FileUtil.toSafeArray(cfFolders)) {
                File[] snapshots = cfFolder.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.equals(DbBackupHandler.DB_SNAPSHOT_SUBDIR);
                    }
                });
                if (snapshots == null || snapshots.length == 0) {
                    continue;
                }
                for (File snapshot : snapshots) {
                    File[] subSnapshots = snapshot.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return name.startsWith(snapshotTag + BackupConstants.BACKUP_NAME_DELIMITER);
                        }
                    });
                    Assert.assertNotNull(subSnapshots);
                    Assert.assertEquals(1, subSnapshots.length);
                }
            }
        }
    }

    @Test
    public void testDumpBackup() {
        final String snapshotTag = UUID.randomUUID().toString();
        String fullBackupTag = dbBackupHandler.createBackup(snapshotTag);
        File dbBackup = null;
        try {
            dbBackup = dbBackupHandler.dumpBackup(snapshotTag, fullBackupTag);
            File[] backupDir = backupManager.getBackupContext().getBackupDir().listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return dir.isDirectory() && name.equals(snapshotTag);
                }
            });
            Assert.assertNotNull(backupDir);
            Assert.assertEquals(1, backupDir.length);
            Assert.assertTrue(backupDir[0].isDirectory());

            File[] backupFolder = backupDir[0].listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith(snapshotTag + BackupConstants.BACKUP_NAME_DELIMITER);
                }
            });
            Assert.assertNotNull(backupFolder);
            Assert.assertEquals(1, backupFolder.length);
            Assert.assertTrue(backupFolder[0].isDirectory());

            String[] subBackups = backupManager.getBackupContext().getBackupDir().list();
            Assert.assertNotNull(subBackups);
            Assert.assertTrue(subBackups.length > 0);
        } finally {
            if (dbBackup != null) {
                FileUtils.deleteQuietly(dbBackup);
            }
        }
    }
}
