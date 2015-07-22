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

package com.emc.storageos.management.backup.util;

import com.emc.storageos.management.backup.FileUtil;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.zip.Deflater;

public class ZipUtilTest {

    private static final Logger log = LoggerFactory.getLogger(ZipUtilTest.class);

    private static final int DEFAULT_FILE_SIZE = 1024;
    private static final int DEFAULT_DIRECTORY_NUM = 3;
    private static final String TEST_FOLDER = "zipTestFolder";
    private static File testDir;

    @BeforeClass
    public static void setUp() throws IOException {
    	//Suppress Sonar violation of Lazy initialization of static fields should be synchronized
    	//Junit test will be called in single thread by default, it's safe to ignore this violation
        testDir = new File(TEST_FOLDER);  //NOSONAR ("squid:S2444")
        tearDown();
        if (testDir.exists())
            FileUtils.deleteDirectory(testDir);
        Assert.assertTrue(testDir.mkdir());
    }

    private enum DirectoryLevel {
        EMPTY,
        FULL,
        MIX
    }

    private long execute(int comressionLevel, DirectoryLevel directoryLevel) throws IOException {

        // 1. Prepare directory and file
        String zipName = directoryLevel.toString() + "-" + UUID.randomUUID().toString();
        File zipDir = new File(testDir, zipName);
        if (zipDir.exists())
            FileUtils.deleteDirectory(zipDir);
        Assert.assertTrue(zipDir.mkdir());
        File prepareFile = FileUtil.createRandomFile(testDir, zipName + ".txt", DEFAULT_FILE_SIZE);
        long checksum = FileUtils.checksumCRC32(prepareFile);
        for ( int i = 0; i < DEFAULT_DIRECTORY_NUM; i++) {
            File folder = new File(zipDir, TEST_FOLDER + "-" + i);
            Assert.assertTrue(folder.mkdir());
            if ((directoryLevel.equals(DirectoryLevel.FULL))
                    || (directoryLevel.equals(DirectoryLevel.MIX) && (i <= DEFAULT_DIRECTORY_NUM/2)))
                FileUtils.copyFile(prepareFile, new File(folder, prepareFile.getName()));
        }

        // 2. pack directory to zip file
        File targetZip = new File(testDir, zipName+".zip");
        if (targetZip.exists())
            Assert.assertTrue(targetZip.delete());
        Assert.assertFalse(targetZip.exists());
        ZipUtil.pack(zipDir, targetZip, comressionLevel);
        Assert.assertTrue(targetZip.exists());

        // 3. unpack zip file
        FileUtils.deleteDirectory(zipDir);
        Assert.assertFalse(zipDir.exists());
        File tmpFolder = new File(testDir, UUID.randomUUID().toString());
        if (!tmpFolder.exists())
            Assert.assertTrue(tmpFolder.mkdir());
        ZipUtil.unpack(targetZip, tmpFolder);
        File[] folders = tmpFolder.listFiles();
        
        for (File folder : FileUtil.toSafeArray(folders)) {
            Assert.assertTrue(folder.exists());
            Assert.assertTrue(folder.isDirectory());
            File[] files = folder.listFiles();
            for (File subFolder : FileUtil.toSafeArray(files)) {
                Assert.assertTrue(subFolder.isDirectory());
                File[] tmpFiles = subFolder.listFiles();
                for (File tmpFile : FileUtil.toSafeArray(tmpFiles)) {
                    Assert.assertTrue(tmpFile.isFile());
                    Assert.assertEquals(checksum, FileUtils.checksumCRC32(tmpFile));
                }
            }
        }
        FileUtils.deleteDirectory(tmpFolder);
        return targetZip.length();
    }

    @Test
    public void testZipWithDefaultLevel() throws IOException {
        long zipSize = execute(Deflater.DEFAULT_COMPRESSION, DirectoryLevel.FULL);
        log.info("Compress Level: default\tSize: {}", zipSize);
    }

    @Test
    public void testZipWithEmptyDirectory() throws IOException {
        execute(Deflater.DEFAULT_COMPRESSION, DirectoryLevel.EMPTY);
    }

    @Test
    public void testZipWithMixedDirectory() throws IOException {
        execute(Deflater.DEFAULT_COMPRESSION, DirectoryLevel.MIX);
    }

    @Test
    public void testZipWithAllLevels() throws IOException {
        //      NO_COMPRESSION = 0;
        //      BEST_SPEED = 1;
        //      BEST_COMPRESSION = 9;
        //      DEFAULT_COMPRESSION = -1;
        for (int i = 0; i < 10; i++) {
            long zipSize = execute(i, DirectoryLevel.FULL);
            log.info("Compress Level: {}\tSize: {}", i, zipSize);
        }
    }

    @AfterClass
    public static void tearDown() throws IOException {
        if (testDir != null && testDir.exists())
            FileUtils.deleteDirectory(testDir);
    }

}
