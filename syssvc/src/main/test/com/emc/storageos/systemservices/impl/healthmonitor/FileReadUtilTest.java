/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.healthmonitor;

import org.junit.Assert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class FileReadUtilTest {
    private static final String INVALID_FILE_PATH = "/proc/0/xyz";
    private static final String TEST_FILE_DIR = File.separator + "tmp" + File.separator;
    private static final String TEST_FILE_PATH = TEST_FILE_DIR + "test.log";
    private static final String TEST_EMPTY_FILE_PATH = TEST_FILE_DIR + "testempty.log";
    private static final String FILE_DATA = "Sample data in file";
    private static final String FILE_DATA1 = "Another line in the file";
    private static volatile File _testFile = null;
    private static volatile File _testEmptyFile = null;

    @BeforeClass
    public static void createTestFile() {
        _testFile = new File(TEST_FILE_PATH);
        _testEmptyFile = new File(TEST_EMPTY_FILE_PATH);
        BufferedWriter bw = null;
        try {
            _testFile.createNewFile();
            _testEmptyFile.createNewFile();
            FileWriter fw = new FileWriter(_testFile);
            bw = new BufferedWriter(fw);
            bw.write(FILE_DATA);
            bw.newLine();
            bw.write(FILE_DATA1);
        } catch (Exception e) {
            Assert.fail("Cannot create test file");
        } finally {
            if (bw != null) {
                try {
                    bw.close();
                } catch (Exception e) {
                    Assert.fail("Cannot close file writer");
                }
            }
        }
    }

    @Test
    public void testReadLines() {
        try {
            String[] fileData = FileReadUtil.readLines(TEST_FILE_PATH);
            Assert.assertNotNull(fileData);
            Assert.assertTrue(fileData.length == 2);
            Assert.assertTrue(fileData[0].equals(FILE_DATA) && fileData[1].equals
                    (FILE_DATA1));
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void testNegReadLines() {
        try {
            FileReadUtil.readLines(INVALID_FILE_PATH);
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testEmptyFileReadLines() {
        try {
            FileReadUtil.readLines(TEST_EMPTY_FILE_PATH);
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testReadFile() {
        try {
            String fileData = FileReadUtil.readFirstLine(TEST_FILE_PATH);
            Assert.assertNotNull(fileData);
            Assert.assertEquals(fileData, FILE_DATA);
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void testReadEmptyFile() {
        try {
            FileReadUtil.readFirstLine(TEST_EMPTY_FILE_PATH);
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(true);
        }
    }

    @AfterClass
    public static void deleteTestClass() {
        if (_testFile != null) {
            Assert.assertTrue(_testFile.delete());
            Assert.assertFalse(_testFile.exists());
        }
        if (_testEmptyFile != null) {
            Assert.assertTrue(_testEmptyFile.delete());
            Assert.assertFalse(_testEmptyFile.exists());
        }
    }
}
