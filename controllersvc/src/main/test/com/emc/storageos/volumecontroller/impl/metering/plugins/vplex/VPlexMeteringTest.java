/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.metering.plugins.vplex;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vplex.ListVPlexPerpetualCSVFileNames;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vplex.ReadAndParseVPlexPerpetualCSVFile;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vplex.VPlexPerpetualCSVFileData;
import com.google.common.base.Joiner;
import com.iwave.ext.linux.LinuxSystemCLI;

/**
 * Tester class for VPlex metering related classes and functions
 */
public class VPlexMeteringTest {
    public static final String SANITY = "sanity";
    public static final String VPLEX_HOST = "vplex.host";
    public static final String VPLEX_USERNAME = "vplex.username";
    public static final String VPLEX_PASSWORD = "vplex.password";
    private final static String HOST = EnvConfig.get(SANITY, VPLEX_HOST);
    private final static String USERNAME = EnvConfig.get(SANITY, VPLEX_USERNAME);
    private final static String PASSWORD = EnvConfig.get(SANITY, VPLEX_PASSWORD);
    private static Logger log = LoggerFactory.getLogger(VPlexMeteringTest.class);

    @Test
    public void testListingPerpetualDataFilenames() {
        LinuxSystemCLI cli = new LinuxSystemCLI(HOST, USERNAME, PASSWORD);
        ListVPlexPerpetualCSVFileNames listDataFileNamesCmd = new ListVPlexPerpetualCSVFileNames();
        cli.executeCommand(listDataFileNamesCmd);

        List<String> filenames = listDataFileNamesCmd.getResults();

        Assert.assertFalse("Expected to find file names", filenames.isEmpty());
        out("Following files were found {}", filenames);
    }

    @Test
    public void testReadingDataFiles() {
        LinuxSystemCLI cli = new LinuxSystemCLI(HOST, USERNAME, PASSWORD);
        ListVPlexPerpetualCSVFileNames listDataFileNamesCmd = new ListVPlexPerpetualCSVFileNames();
        cli.executeCommand(listDataFileNamesCmd);

        List<String> filenames = listDataFileNamesCmd.getResults();
        Assert.assertFalse("Expected to find file names", filenames.isEmpty());

        for (String filename : filenames) {
            ReadAndParseVPlexPerpetualCSVFile readDataFile = new ReadAndParseVPlexPerpetualCSVFile(filename);
            cli.executeCommand(readDataFile);
            VPlexPerpetualCSVFileData fileData = readDataFile.getResults();
            Assert.assertNotNull("Expect file data to be non-null", fileData);
            out("For file {} these are the headers:\n{}", filename, Joiner.on('\n').join(fileData.getHeaders()));
            List<Map<String, String>> dataLines = fileData.getDataLines();
            Assert.assertTrue("Expect file data to have data values", !dataLines.isEmpty());
            out("For file {} there are {} data lines", filename, dataLines.size());
        }
    }

    private void out(String format, Object... args) {
        log.info(format, args);
        // String modFormat = format.replaceAll("\\{\\}", "%s");
        // System.out.println(String.format(modFormat, args));
    }
}
