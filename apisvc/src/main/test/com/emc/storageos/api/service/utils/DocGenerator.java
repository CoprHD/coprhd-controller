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

package com.emc.storageos.api.service.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.codehaus.enunciate.config.EnunciateConfiguration;
import org.codehaus.enunciate.main.Enunciate;
import org.codehaus.enunciate.modules.DeploymentModule;
import org.codehaus.enunciate.modules.docs.DocumentationDeploymentModule;
import org.codehaus.enunciate.modules.xml.XMLDeploymentModule;


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

        DocumentationDeploymentModule module = new DocumentationDeploymentModule();
        module.setDisableRestMountpoint(true);
        module.setIncludeExampleJson(false);
        module.setIncludeExampleXml(true);
        module.setTitle("Bourne API");

        XMLDeploymentModule xmlModule = new XMLDeploymentModule();
        EnunciateConfiguration config = new EnunciateConfiguration(
                Arrays.asList(new DeploymentModule[] { module, xmlModule }));

        Enunciate e = new Enunciate(files.toArray(new String[] {}), config);
        File buildDir = new File(args[0]);
        if (!buildDir.exists()) {
            buildDir.mkdir();
        }
        File intermediateDir = new File(buildDir, "docs_xml");
        if (!intermediateDir.exists()) {
            intermediateDir.mkdir();
        }
        e.setTarget(Enunciate.Target.PACKAGE);
        e.getConfig().setAllowEmptyNamespace(true);
        e.setBuildDir(buildDir);
        e.setVerbose(true);
        e.setGenerateDir(intermediateDir);
        e.execute();
    }
}
