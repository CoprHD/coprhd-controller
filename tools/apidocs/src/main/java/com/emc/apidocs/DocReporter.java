/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.apidocs;

import com.sun.javadoc.DocErrorReporter;

/**
 */
public class DocReporter {
    private static DocErrorReporter docErrorReporter;

    public static void init(DocErrorReporter docErrorReporter) {
        DocReporter.docErrorReporter = docErrorReporter;
    }

    public static void printWarning(String msg) {
        docErrorReporter.printWarning(msg);
    }

    public static void printError(String msg) {
        docErrorReporter.printError(msg);
    }
}
