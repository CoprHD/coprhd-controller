/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved 
 *
 * This software contains the intellectual property of EMC Corporation 
 * or is licensed to EMC Corporation from third parties.  Use of this 
 * software and the intellectual property contained therein is expressly 
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.management.backup;

import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Assert;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.UUID;

public class DbBackupHandlerTest extends BackupTestBase {

    private static DbBackupHandler dbBackupHandler;

    @BeforeClass
    public static void setUp() {
        dbBackupHandler = (DbBackupHandler) backupManager.getBackupHandler();
    }

    @Test
    public void testCreateBackup() {
        final String snapshotTag = UUID.randomUUID().toString();
        dbBackupHandler.createBackup(snapshotTag);
        for (File cfFolder : dbBackupHandler.getValidKeyspace().listFiles()) {
            File[] snapshots = cfFolder.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.equals(DbBackupHandler.DB_SNAPSHOT_SUBDIR);
                }
            });
            if (snapshots == null || snapshots.length == 0)
                continue;
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

    @Test
    public void testDumpBackup() {
        final String snapshotTag = UUID.randomUUID().toString();
        String fullBackupTag = dbBackupHandler.createBackup(snapshotTag);
        File dbBackup = null;
        try {
            dbBackup = dbBackupHandler.dumpBackup(snapshotTag, fullBackupTag);
            File[] backupDir = backupManager.getBackupDir().listFiles(new FilenameFilter() {
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

            String[] subBackups = backupManager.getBackupDir().list();
            Assert.assertNotNull(subBackups);
            Assert.assertTrue(subBackups.length > 0);
        } finally {
            if (dbBackup != null)
                FileUtils.deleteQuietly(dbBackup);
        }
    }
}
