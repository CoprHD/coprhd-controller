/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.vplex;

import com.iwave.ext.linux.command.LinuxResultsCommand;

/**
 * This class encapsulates accessing a specified VPlex perpetual performance log file and slurping its contents
 * into a VPlexPerpetualCSVFileData object
 */
public class ReadAndParseVPlexPerpetualCSVFile extends LinuxResultsCommand<VPlexPerpetualCSVFileData> {

    public static final String CAT_DATA_FILE_CMD = "cat %s";
    private String filepath;

    public ReadAndParseVPlexPerpetualCSVFile(String filepath) {
        this.filepath = filepath;
        setCommand(String.format(CAT_DATA_FILE_CMD, filepath));
    }

    @Override
    public void parseOutput() {
        String stdOut = getOutput().getStdout();
        // Slurp all the lines into an array
        String lines[] = stdOut.split("\n");

        VPlexPerpetualCSVFileData fileData = new VPlexPerpetualCSVFileData(filepath, lines.length);

        // The first list should be the header that indicates what the data point value is for.
        // Parse it and add to the VPlexPerpetualCSVFileData object
        String header = lines[0];
        fileData.addHeaders(header.split(","));
        lines[0] = null;

        // Starting at the line after the header, read all the lines and push them into
        // the VPlexPerpetualCSVFileData object
        for (int index = 1; index < lines.length; index++) {
            fileData.addDataLine(lines[index].split(","));
            lines[index] = null;
        }
        results = fileData;
    }
}
