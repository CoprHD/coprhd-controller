/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.geniso;

import com.emc.storageos.systemservices.impl.iso.ISOBuffer;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Generate the ISO image that contains an 'empty' ovf-env.xml
 */
public class Main {
    private static void usage() {
        System.out.println("Usage: ");
        System.out.println("Main --label label [--header iso-header-filename --trailer iso-trailer-filename] [-f input-file -o output-file] config-filename config-file-size");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 8) {
            usage();
            return;
        }

        String label = null;
        String isoHeaderFileName = null;
        String isoTrailerFileName = null;
        String inputFileName = null;
        String outputFileName = null;
        String configFileName = null;
        int size = 4096; //default 4K

        for (int i =0; i < args.length; i++) {
            if ( args[i].equals("--label") ) {
                i++;
                label = args[i];
                continue;
            }

            if ( args[i].equals("--header") ) {
                i++;
                isoHeaderFileName = args[i];
                continue;
            }

            if ( args[i].equals("--trailer") ) {
                i++;
                isoTrailerFileName = args[i];
                continue;
            }

            if ( args[i].equals("-f") ) {
                i++;
                inputFileName = args[i];
                continue;
            }

            if ( args[i].equals("-o") ) {
                i++;
                outputFileName = args[i];
                continue;
            }

            configFileName = args[i];
            i++;
            size = Integer.parseInt(args[i]);
        }

        if (isoHeaderFileName != null) {
            generateISOHeaderAndTrailer(label, isoHeaderFileName, isoTrailerFileName, configFileName, size);
        }else if (inputFileName != null) {
            generateISOFile(label, inputFileName, outputFileName, configFileName, size);
        }
    }

    private static void generateISOHeaderAndTrailer(String label, String isoHeader, String isoTrailer, String configFileName, int size) {
        try (FileOutputStream header = new FileOutputStream(isoHeader);
             FileOutputStream trailer = new FileOutputStream(isoTrailer)) {

            byte[] dummyData = new byte[size];

            ISOBuffer isoBuffer = new ISOBuffer();
            isoBuffer.addFile(configFileName, dummyData);
            byte[] data = isoBuffer.createISO(label);

            int dataStartPosition = isoBuffer.getDataStartPosition();
            int trailerOffset = isoBuffer.getDataEndPosition();
            int trailerLength = data.length - trailerOffset;

            System.out.println(String.format("ISO header size= %d ISO trailer offset=%d trailer length=%d",
                    dataStartPosition, trailerOffset, trailerLength));
            header.write(data, 0, dataStartPosition);
            trailer.write(data, trailerOffset, trailerLength);
        } catch (IOException e) {
            System.err.println(String.format("Failed to create the ISO header %s and trailer %s:",
                    isoHeader, isoTrailer, e.getMessage()));
            return;
        }

        System.out.println("The ISO image header and trailer files have been created");
    }

    private static void generateISOFile(String label, String inputFileName, String outputFileName , String configFileName, int size) throws Exception {
        byte[] buffer = new byte[size];
        try (FileInputStream input = new FileInputStream(inputFileName);
             FileOutputStream out = new FileOutputStream(outputFileName)) {
            int count = input.read(buffer);
            Arrays.fill(buffer, count, buffer.length, (byte)('\n'));

            ISOBuffer isoBuffer = new ISOBuffer();
            isoBuffer.addFile(configFileName, buffer);
            byte[] data = isoBuffer.createISO(label);
            out.write(data);
        }
    }
}
