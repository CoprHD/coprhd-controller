/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs;

import org.apache.commons.compress.compressors.FileNameUtil;

import java.io.File;

/**
 * WARNING, This is a static class rather than pass it around all over.  It MUST be initialised first thing.
 */
public class KnownPaths {
    private static String HTML_DIR = "apidocs";

    private static String contentDir;
    private static String outputDir;

    public static void init(String contentDir, String outputDir) {
        KnownPaths.contentDir = contentDir;
        KnownPaths.outputDir = outputDir;
    }

    public static File getTemplateFile(String filename) {
        return new File(contentDir+"templates/"+filename);
    }

    public static File getTemplatePartFile(String filename) {
        return new File(contentDir+"templates/parts/"+filename);
    }

    public static File getHtmlOutputFile(String filename) {
        return new File(outputDir+"/"+HTML_DIR+"/"+filename);
    }

    public static File getOutputFile(String filename) {
        return new File(outputDir+filename);
    }

    public static File getExampleFile(String filename) {
        return new File(contentDir + "examples/"+filename);
    }

    public static File getReferenceFile(String filename) {
        return new File(contentDir + "reference/"+filename);
    }

    public static File getApiDiffFile(String filename) {
        return new File(outputDir + filename);
    }

    public static File getPageDir() {
        return new File(contentDir + "pages/");
    }

    public static File getHTMLDir() {
        return new File(outputDir +"/"+HTML_DIR);
    }

    public static File getMetaDataFile(String name) {
        return new File(contentDir + "apiMetaData/"+name);
    }
}
