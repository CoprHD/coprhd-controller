/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.cimadapter.consumers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Hashtable;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * JUnit test class for {@link FileCimIndicationConsumer}.
 */
public class FileCimIndicationConsumerTest {

    private static final String TEST_KEY1 = "key1";
    private static final String TEST_KEY2 = "key2";
    private static final String TEST_VALUE1 = "value1";
    private static final String TEST_VALUE2 = "value2";
    
    /**
     * Make sure indication file doesn't exist.
     */
    @BeforeClass
    public static void deleteIndicationsFile() {
        boolean wasException = false;
        try {
            StringBuffer fileNameBuff = new StringBuffer(
                System.getProperty(FileCimIndicationConsumer.WORKING_DIR_SYSTEM_VARIABLE));
            fileNameBuff.append(File.separator);
            fileNameBuff.append(FileCimIndicationConsumer.INDICATIONS_FILE_NAME);
            File outFile = new File(fileNameBuff.toString());
            if (outFile.exists()) {
                Assert.assertTrue(outFile.delete());
                Assert.assertFalse(outFile.exists());
            }
        } catch (Exception e) {
            wasException = true;
        }
        finally {
            Assert.assertFalse(wasException);
        }
    }

    /**
     * Tests the consumeIndication method when the passed indication is null.
     */
    @Test
    public void testConsumeIndication_Null() {
        FileCimIndicationConsumer consumer = new FileCimIndicationConsumer();
        consumer.consumeIndication(null);

        StringBuffer fileNameBuff = new StringBuffer(
            System.getProperty(FileCimIndicationConsumer.WORKING_DIR_SYSTEM_VARIABLE));
        fileNameBuff.append(File.separator);
        fileNameBuff.append(FileCimIndicationConsumer.INDICATIONS_FILE_NAME);
        File outFile = new File(fileNameBuff.toString());
        Assert.assertFalse(outFile.exists());
    }

    /**
     * Tests the consumeIndication method when the passed indication is not a
     * Hashtable<String, String>.
     */
    @Test
    public void testConsumeIndication_NotHashtable() {
        FileCimIndicationConsumer consumer = new FileCimIndicationConsumer();
        consumer.consumeIndication(new ArrayList<String>());

        StringBuffer fileNameBuff = new StringBuffer(
            System.getProperty(FileCimIndicationConsumer.WORKING_DIR_SYSTEM_VARIABLE));
        fileNameBuff.append(File.separator);
        fileNameBuff.append(FileCimIndicationConsumer.INDICATIONS_FILE_NAME);
        File outFile = new File(fileNameBuff.toString());
        Assert.assertFalse(outFile.exists());
    }

    /**
     * Tests the consumeIndication method when the passed indication is a
     * Hashtable<String, String> and the data is written to the file.
     */
    @Test
    public void testConsumeIndication() {
        // Create some data to consume.
        Hashtable<String, String> indicationData = new Hashtable<String, String>();
        indicationData.put(TEST_KEY1, TEST_VALUE1);
        indicationData.put(TEST_KEY2, TEST_VALUE2);

        // Create the file consumer and consume the indication.
        FileCimIndicationConsumer consumer = new FileCimIndicationConsumer();
        consumer.consumeIndication(indicationData);

        // Verify the indication was written.
        StringBuffer fileNameBuff = new StringBuffer(
            System.getProperty(FileCimIndicationConsumer.WORKING_DIR_SYSTEM_VARIABLE));
        fileNameBuff.append(File.separator);
        fileNameBuff.append(FileCimIndicationConsumer.INDICATIONS_FILE_NAME);
        File outFile = new File(fileNameBuff.toString());
        Assert.assertTrue(outFile.exists());

        // Read and verify the content.
        boolean wasException = false;
        BufferedReader bufferedFileReader = null;
        try {
            FileReader fileReader = new FileReader(outFile);
            bufferedFileReader = new BufferedReader(fileReader);
            String line = null;
            while ((line = bufferedFileReader.readLine()) != null) {
                if (line.length() != 0) {
                    String[] lineComponents = line.split(": ");
                    Assert.assertEquals(lineComponents.length, 2);
                    String key = lineComponents[0];
                    String value = lineComponents[1];
                    Assert.assertTrue(indicationData.containsKey(key));
                    Assert.assertEquals(indicationData.get(key), value);
                }
            }
        } catch (Exception e) {
            wasException = true;
        } finally {
            Assert.assertFalse(wasException);
            try {
                bufferedFileReader.close();
            } catch (Exception e) {
            }
        }

        // Clean up the file.
        Assert.assertTrue(outFile.delete());
        Assert.assertFalse(outFile.exists());
    }
}
