/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.upgrade;

import java.lang.annotation.Annotation;

/**
 * Default implementation of MigrationCallback, used by db engine to handle generic changes
 */
public class BaseDefaultMigrationCallback implements MigrationCallback {
    
    protected Class cfClass;
    protected String fieldName;
    protected Annotation annotation;
    protected String annotationValue;
    protected String name;
    protected InternalDbClient internalDbClient;
    
    public InternalDbClient getInternalDbClient() {
        return internalDbClient;
    }

    public void setInternalDbClient(InternalDbClient dbClient) {
        this.internalDbClient = dbClient;
    }

    public Class getCfClass() {
        return cfClass;
    }

    public void setCfClass(Class cfClass) {
        this.cfClass = cfClass;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public void setAnnotation(Annotation annotation) {
        this.annotation = annotation;
    }

    public String getAnnotationValue() {
        return annotationValue;
    }

    public void setAnnotationValue(String annotationValue) {
        this.annotationValue = annotationValue;
    }

    @Override
    public void process() {
        // no upgrade is needed
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
