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

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import com.emc.storageos.management.backup.util.ValidationUtil;
import com.emc.storageos.management.backup.util.ValidationUtil.*;

public class ValidationUtilTest {

    private static final Logger log = LoggerFactory.getLogger(ValidationUtilTest.class);
    private static final String FILE_NAME = "myfile";
    private static final String DIR_NAME = System.getProperty("java.io.tmpdir") + "/data/test/1/mydir";

    @BeforeClass
    public static void prepareData() throws IOException {
        File myDir = new File(DIR_NAME);
        File myFile = new File(DIR_NAME, FILE_NAME);
        myDir.mkdirs();
        myFile.createNewFile();
    }

    @Test
    public void testValidation() throws IOException {
        File testFile1 = new File(DIR_NAME);
        ValidationUtil.validateFile(testFile1, FileType.Dir, 
                NotExistEnum.NOT_EXSIT_ERROR);
        File testFile2 = new File(testFile1, FILE_NAME);
        ValidationUtil.validateFile(testFile2, FileType.File,
                NotExistEnum.NOT_EXSIT_ERROR);

        //test file
        File testFile3 = new File(testFile1, "testfile");
        boolean error = false;
        try {
            ValidationUtil.validateFile(testFile3, FileType.File, 
                NotExistEnum.NOT_EXSIT_ERROR);
        } catch (Exception e) {
            error = true;
        }
        Assert.assertTrue(error);

        error = false;
        try {
            ValidationUtil.validateFile(testFile3, FileType.File, 
                NotExistEnum.NOT_EXSIT_OK);
        } catch (Exception e) {
            error = true;
        }
        Assert.assertFalse(error);

        error = false;
        try {
            ValidationUtil.validateFile(testFile3, FileType.File, 
                NotExistEnum.NOT_EXSIT_CREATE);
        } catch (Exception e) {
            error = true;
        }
        Assert.assertFalse(error);

        error = false;
        try {
            ValidationUtil.validateFile(testFile3, FileType.File, 
                NotExistEnum.NOT_EXSIT_ERROR);
        } catch (Exception e) {
            error = true;
        }
        Assert.assertFalse(error);

        //test directory
        File testFile4 = new File(testFile1, "testDir");
        error = false;
        try {
            ValidationUtil.validateFile(testFile4, FileType.Dir, 
                NotExistEnum.NOT_EXSIT_ERROR);
        } catch (Exception e) {
            error = true;
        }
        Assert.assertTrue(error);

        error = false;
        try {
            ValidationUtil.validateFile(testFile4, FileType.Dir, 
                NotExistEnum.NOT_EXSIT_OK);
        } catch (Exception e) {
            error = true;
        }
        Assert.assertFalse(error);

        error = false;
        try {
            ValidationUtil.validateFile(testFile4, FileType.Dir, 
                NotExistEnum.NOT_EXSIT_CREATE);
        } catch (Exception e) {
            error = true;
        }
        Assert.assertFalse(error);

        error = false;
        try {
            ValidationUtil.validateFile(testFile4, FileType.Dir, 
                NotExistEnum.NOT_EXSIT_ERROR);
        } catch (Exception e) {
            error = true;
        }
        Assert.assertFalse(error);

        //test wrong type
        error = false;
        try {
            ValidationUtil.validateFile(testFile4, FileType.File, 
                NotExistEnum.NOT_EXSIT_OK);
        } catch (Exception e) {
            error = true;
        }
        Assert.assertTrue(error);
    }

    @AfterClass
    public static void cleanData() throws IOException {
        FileUtils.deleteDirectory(new File(DIR_NAME));
    }

}
