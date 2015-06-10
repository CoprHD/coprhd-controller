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

import org.apache.commons.lang.RandomStringUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class FileUtil {

    private FileUtil() {}

    /**
     * Creates file by specified name and length, the content of file is random string.
     * @param folder
     *          The folder which contains file
     * @param name
     *          The name of file
     * @param length
     *          The length of file
     * @return Instance of File
     * @throws IOException
     */
    public static File createRandomFile(File folder, String name, final int length)
            throws IOException {
        File file = new File(folder, name);
        if (file.exists())
            file.delete();
        try (BufferedWriter output = new BufferedWriter(new FileWriter(file))) {
            output.write(RandomStringUtils.random(length));
        }
        return file;
    }

    /**
     * Creates file with specified length
     * @param file
     *          The file instance to accept data
     * @param length
     *          The size of file data
     * @throws IOException
     */
    public static void createEmptyFile(File file, long length) throws IOException {
        if (file == null || length <= 0)
            throw new IllegalArgumentException("File instance is null or length is not positive");
        try (FileOutputStream fOut = new FileOutputStream(file);
                FileChannel fChannel = fOut.getChannel()) {
            fChannel.write(ByteBuffer.allocate(1), length-1);
        }
    }
}
