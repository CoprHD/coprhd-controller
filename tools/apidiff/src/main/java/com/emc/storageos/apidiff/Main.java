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

package com.emc.storageos.apidiff;

import com.emc.storageos.apidiff.serializer.AbstractSerializer;
import com.emc.storageos.apidiff.serializer.HtmlSerializer;
import com.emc.storageos.apidiff.serializer.HtmlSerializerMultiPages;
import com.emc.storageos.apidiff.util.Pair;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main class to generate API differences page
 */
public class Main {

    public static Map<String, List<Pair<String, String>>> serviceNamingMap =
            new HashMap<String, List<Pair<String, String>>>();

    public static void loadProperties(final String fileName) {
        try {
            String serviceName = null;
            for (String line : IOUtils.readLines(new FileInputStream(fileName))) {
                String lineStr = line.trim();
                if (lineStr.length() == 0 || lineStr.startsWith(Constants.COMMENT_MARKER))
                    continue;
                String[] items = lineStr.split(Constants.TITLE_MARKER);
                if (items[0].length() == 0) {
                    serviceName = items[1];
                } else if (serviceName != null && items[1].length() > 0) {
                    List<Pair<String, String>> serviceList = serviceNamingMap.get(serviceName);
                    if (serviceList == null) {
                        serviceList = new ArrayList<Pair<String, String>>();
                        serviceNamingMap.put(serviceName, serviceList);
                    }
                    serviceList.add(new Pair<String, String>(items[0], items[1]));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error reading Group File " + fileName, e);
        }
        // System.out.println("Service Naming Map Size: "+serviceNamingMap.size());
    }

    private static void usage() {
        System.out.println("Apidiff is a simple tool to compute REST API differences between two ViPR version.");
        System.out.println("Arguments: ");
        System.out.println("  <old-api-folder>: the folder stores old version of REST APIs");
        System.out.println("  <new-api-folder>: the folder stores new version of REST APIs");
        System.out.println("  [output folder]: the folder stores output diff file, if non, program will use <new-api-folder>");
        System.out.println("Example:");
        System.out.println("  java -cp .:... com.emc.storageos.apidiff.Main /tmp/oldApis /tmp/newApis /tmp/output");
        System.exit(-1);
    }

    public static void main(String[] args) {

        System.out.println("Arguments: " + Arrays.toString(args));
        if (args.length < 2)
            usage();

        File oldFolder = new File(args[0]);
        if (!oldFolder.exists() || !oldFolder.isDirectory())
            usage();
        File newFolder = new File(args[1]);
        if (!newFolder.exists() || !newFolder.isDirectory())
            usage();

        File outputFolder;
        if (args.length < 3) {
            outputFolder = newFolder;
        } else {
            outputFolder = new File(args[2]);
            if (!outputFolder.isDirectory())
                outputFolder.delete();
            if (!outputFolder.exists())
                outputFolder.mkdirs();
        }

        loadProperties(oldFolder.getAbsolutePath() + "/ApiReferenceGrouping.txt");
        List<ServiceCatalogDiff> diffList = DiffGenerator.generate(oldFolder, newFolder);
        AbstractSerializer serializer = new HtmlSerializer(diffList, outputFolder);
        serializer.output();
        System.out.println("Finished: " + serializer.getFile().getAbsolutePath());
        serializer = new HtmlSerializerMultiPages(diffList, outputFolder);
        serializer.output();
        System.out.println("Finished: " + serializer.getFile().getAbsolutePath());

        System.out.println("The presentation page includes: summary(API added, removed and changed" +
                " number), API component list and API change details(compare)");

    }
}
