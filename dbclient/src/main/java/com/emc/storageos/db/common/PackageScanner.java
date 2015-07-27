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
package com.emc.storageos.db.common;

import java.lang.annotation.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.core.spi.scanning.PackageNamesScanner;
import com.sun.jersey.spi.scanning.AnnotationScannerListener;

/**
 *  Scanner for sweeping all DataObject types defined and creates:
 *  - CF Map for building db schema
 *  - DependencyTracker - with all the dependency information between the types
 */
public abstract class PackageScanner {
    protected DbSchemaScannerInterceptor _scannerInterceptor = null;
    private PackageNamesScanner _scanner;

    public PackageScanner(String... packages) {
        _scanner = new PackageNamesScanner(packages);
    }

    /**
     * Package where model classes are defined
     *
     * @param packages
     */
    public void setPackages(String... packages) {
        _scanner = new PackageNamesScanner(packages);
    }

    /**
     * set schema scanner interceptor - use only for unit testing schema changes
     * @param scannerInterceptor
     */
    public void setScannerInterceptor(DbSchemaScannerInterceptor scannerInterceptor) {
        _scannerInterceptor = scannerInterceptor;
    }


    /**
    * Scan model classes and load up CF information from them
    */
    @SuppressWarnings("unchecked")
    public void scan(Class<? extends Annotation>... annotations) {
        AnnotationScannerListener scannerListener = new AnnotationScannerListener(annotations);
        _scanner.scan(scannerListener);

        Iterator<Class<?>> it = scannerListener.getAnnotatedClasses().iterator();
        while (it.hasNext()) {
            processClass(it.next());
        }
    }

    /**
     * Processes class
     *
     * @param clazz 
     */
    abstract protected void processClass(Class clazz);
}
