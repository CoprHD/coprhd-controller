/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs.generating;

import com.emc.apidocs.KnownPaths;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;

/**
 * Loads an example and formats it ready for the page
 */
public class ExampleLoader {
    private static final String RESPONSE_MARKER = "==RESPONSE";
    private static final String RESPONSE_MARKER_LOWERCASE = "==Response";

    public static String[] loadExample(String exampleFileName) {
        File exampleFile = KnownPaths.getExampleFile(exampleFileName);
        if (!exampleFile.exists()) {
            return null;
        }

        try {
            String content = IOUtils.toString(new FileInputStream(exampleFile));
            content = content.replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("\r", "");
            String[] splitContent = content.split(RESPONSE_MARKER);
            if (splitContent.length != 2) {
                splitContent = content.split(RESPONSE_MARKER_LOWERCASE);
            }

            if (splitContent.length != 2) {
                throw new RuntimeException("Unable to load example as Response marker not found: " + exampleFile);
            }

            splitContent[0] = splitContent[0].trim();
            splitContent[1] = splitContent[1].trim();

            return splitContent;
        } catch (Exception e) {
            throw new RuntimeException("Error reading example " + exampleFile, e);
        }
    }
}
