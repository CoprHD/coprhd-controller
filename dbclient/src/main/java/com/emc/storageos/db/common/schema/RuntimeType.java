/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common.schema;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;

import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.upgrade.MigrationCallback;

/**
 * this class contains information of reflection type of a schema class
 * this information can be used to locate the specified class/field/annotation
 * this class is named such because this information is avaiable only in the 
 * runtime schema instances, not those unmarshalled from XML
 */
public class RuntimeType {
    /**
     * the class that this cf/field/annotation schema instance belongs to
     */
    private Class cfClass;

    /**
     * the field that this field/annotation schema instance belongs to
     */
    private PropertyDescriptor pd;

    /**
     * the annotation that this annotation schema instance belongs to
     */
    private Annotation annoClass;

    /**
     * the migration callback class retrieved from the annotation value
     */
    private Class<? extends MigrationCallback> callbackClass;

    public RuntimeType() {
    }

    public RuntimeType(RuntimeType runtimeType) {
        this.cfClass = runtimeType.cfClass;
        this.pd = runtimeType.pd;
        this.annoClass = runtimeType.annoClass;
        this.callbackClass = runtimeType.callbackClass;
    }

    public Class getCfClass() {
        return cfClass;
    }

    public void setCfClass(Class cfClass) {
        this.cfClass = cfClass;
    }

    public PropertyDescriptor getPropertyDescriptor() {
        return pd;
    }

    public void setPropertyDescriptor(PropertyDescriptor pd) {
        this.pd = pd;
    }

    public String getFieldName() {
        if (pd == null) {
            return null;
        } else {
            Name nameAnnotation = pd.getReadMethod().getAnnotation(Name.class);
            if (nameAnnotation != null) {
                return nameAnnotation.value();
            }
            return pd.getName();
        }
    }

    public Annotation getAnnotation() {
        return annoClass;
    }

    public void setAnnotation(Annotation annoClass) {
        this.annoClass = annoClass;
    }

    public Class<? extends MigrationCallback> getMigrationCallback() {
        return callbackClass;
    }

    public void setMigrationCallback(Class<? extends MigrationCallback> callbackClass) {
        this.callbackClass = callbackClass;
    }
}
