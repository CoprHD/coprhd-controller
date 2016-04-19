/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade;

import java.lang.annotation.Annotation;

import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

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
    public void process() throws MigrationCallbackException {
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
