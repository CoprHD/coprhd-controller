/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.webcohesion.enunciate.EnunciateConfiguration;
import com.webcohesion.enunciate.Enunciate;

/**
 * Main to drive Enunciator
 */
public class DocGenerator {
    // relevant resources and objects for documentation
    private static final String[] packages = {
            "apisvc/src/main/java/com/emc/storageos/model",
            "apisvc/src/main/java/com/emc/storageos/api/service/impl",
            "apisvc/src/main/java/com/emc/storageos/api/mapper",
            "dbsvc/src/main/java/com/emc/storageos/db/client/model",
            "internalLibraries/models/src/main/java/com/emc/storageos/model"
    };

    /**
     * Collect java source code under a directory
     * 
     * @param start
     * @param file
     */
    public static void collectFiles(File start, final List<String> file) {
        if (start.isDirectory()) {
            File[] files = start.listFiles();
            for (int i = 0; i < files.length; i++) {
                File f = files[i];
                if (f.isDirectory()) {
                    collectFiles(f, file);
                } else if (f.getName().endsWith(".java")) {
                    file.add(f.getAbsolutePath());
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        List<String> files = new ArrayList<String>();
        for (int i = 0; i < packages.length; i++) {
            collectFiles(new File(packages[i]), files);
        }

        EnunciateConfiguration config = new EnunciateConfiguration();
        config.setDefaultTitle("Bourne API");

        Enunciate e = new Enunciate();
        File buildDir = new File(args[0]);
        if (!buildDir.exists()) {
            buildDir.mkdir();
        }
        File intermediateDir = new File(buildDir, "docs_xml");
        if (!intermediateDir.exists()) {
            intermediateDir.mkdir();
        }
        e.addSourceDir(buildDir);
        e.getApiRegistry();
        e.run();
    }
}
