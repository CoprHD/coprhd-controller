/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.backup;

import com.emc.storageos.management.backup.exceptions.FatalBackupException;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

//This suite requires external services to be running, which is not the case on public build servers.
//For examples of a self-contained unit test, see DbServiceTestBase.  COP-19800
@Ignore
public class BackupManagerTest extends BackupTestBase {

    private static final Logger log = LoggerFactory.getLogger(BackupManagerTest.class);

    private static String invalidChars[] = new String[] { null, "", " ", "  " };
    private static File backupFolder;

    @BeforeClass
    public static void setUp() {
        Assert.assertNotNull(backupManager.getBackupHandler());
        Assert.assertNotNull(backupManager.getBackupContext().getBackupDir());
        // Suppress Sonar violation of Lazy initialization of static fields should be synchronized
        // Junit test will be called in single thread by default, it's safe to ignore this violation
        backupFolder = backupManager.getBackupContext().getBackupDir(); // NOSONAR ("squid:S2444")
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateParameter() {
        for (String str : invalidChars) {
            backupManager.create(str);
        }
    }

    @Test
    public void testCreate() {
        final String backupName = UUID.randomUUID().toString();
        backupManager.create(backupName);
        createBackup(backupName, zkBackupHandler);
        createBackup(backupName, geoDbBackupHandler);
        File backupDir = new File(backupFolder, backupName);
        if (!backupDir.exists()) {
            Assert.assertTrue(backupDir.mkdirs());
        }
        File[] backupFiles = backupDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(backupName) && name.endsWith(BackupConstants.COMPRESS_SUFFIX);
            }
        });
        Assert.assertNotNull(backupFiles);
        log.info("Backup {} files: {}", backupName, Arrays.toString(backupFiles));
        Assert.assertEquals(2, backupFiles.length);
        Assert.assertTrue(backupFiles[0].isFile());
        Assert.assertTrue(backupFiles[0].getName().endsWith("zip"));
    }

    @Test
    public void testList() throws IOException {
        if (!backupFolder.exists()) {
            Assert.assertTrue(backupFolder.mkdirs());
        }
        String backupName = UUID.randomUUID().toString();
        File zipFile = null;
        File randomFile = null;
        try {
            File backupDir = new File(backupFolder, backupName);
            if (!backupDir.exists()) {
                Assert.assertTrue(backupDir.mkdirs());
            }
            zipFile = FileUtil.createRandomFile(backupDir, backupName +
                    BackupConstants.BACKUP_NAME_DELIMITER + BackupConstants.COMPRESS_SUFFIX, 1024);
            randomFile = FileUtil.createRandomFile(backupDir, backupName, 1024);
            List<BackupSetInfo> fileList = backupManager.list();
            Assert.assertTrue(!fileList.isEmpty());
            boolean found = false;
            for (BackupSetInfo backupSetInfo : fileList) {
                if (backupSetInfo.getName().equals(zipFile.getName())) {
                    found = true;
                    break;
                }
            }
            Assert.assertTrue("Can't list all backup files", found);
        } finally {
            if (zipFile != null && zipFile.exists()) {
                Assert.assertTrue(zipFile.delete());
            }
            if (randomFile != null && randomFile.exists()) {
                Assert.assertTrue(randomFile.delete());
            }
        }
    }

    @Test
    public void testListWithEmptyDir() throws IOException {
        // Test delete method when target directory is not exist
        if (backupFolder.exists()) {
            FileUtils.deleteDirectory(backupFolder);
        }
        List<BackupSetInfo> fileList = backupManager.list();
        Assert.assertNotNull(fileList);
        Assert.assertEquals(0, fileList.size());

        // Test delete method when target directory is empty
        if (!backupFolder.exists()) {
            Assert.assertTrue(backupFolder.mkdirs());
        }
        fileList = backupManager.list();
        Assert.assertNotNull(fileList);
        Assert.assertEquals(0, fileList.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteParameter() {
        for (String str : invalidChars) {
            backupManager.delete(str);
        }
    }

    @Test
    public void testDelete() throws IOException {
        String backupName = UUID.randomUUID().toString();
        File zipFile = null;
        try {
            if (!backupFolder.exists()) {
                Assert.assertTrue(backupFolder.mkdirs());
            }
            File backupDir = new File(backupFolder, backupName);
            if (!backupDir.exists()) {
                Assert.assertTrue(backupDir.mkdirs());
            }
            zipFile = FileUtil.createRandomFile(backupDir, backupName +
                    BackupConstants.BACKUP_NAME_DELIMITER + BackupConstants.COMPRESS_SUFFIX, 1024);
            Assert.assertTrue(zipFile.exists());
            backupManager.delete(backupName);
            Assert.assertFalse(zipFile.exists());
        } finally {
            if (zipFile != null && zipFile.exists()) {
                Assert.assertTrue(zipFile.delete());
            }
        }
    }

    @Test(expected = FatalBackupException.class)
    public void testDeleteWithEmptyDir() throws IOException {
        String backupName = UUID.randomUUID().toString();
        // Test delete method when target directory is not exist
        if (backupFolder.exists()) {
            FileUtils.deleteDirectory(backupFolder);
        }
        backupManager.delete(backupName);

        // Test delete method when target directory is empty
        Assert.assertTrue(backupFolder.mkdir());
        backupManager.delete(backupName);
    }
}
