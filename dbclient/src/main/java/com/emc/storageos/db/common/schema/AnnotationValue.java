/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common.schema;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import com.google.common.base.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.upgrade.MigrationCallback;
import com.emc.storageos.db.exceptions.DatabaseException;

public class AnnotationValue implements SchemaObject {
    private static final Logger log = LoggerFactory.getLogger(AnnotationType.class);

    private RuntimeType runtimeType;

    // the following field is used in unmarshalled instances
    private String type;

    // the following fields are used to distinguish instances
    private String name;
    private String value;

    private SchemaObject parent;

    public AnnotationValue() {
    }

    public AnnotationValue(RuntimeType runtimeType, Method method, SchemaObject parent) {
        this.runtimeType = new RuntimeType(runtimeType);

        this.parent = parent;

        this.name = method.getName();
        try {
            Object val = method.invoke(runtimeType.getAnnotation());
            if (val instanceof Class) {
                this.value = ((Class) val).getSimpleName();
                this.runtimeType.setMigrationCallback((Class<? extends MigrationCallback>) val);
            } else if (val instanceof Enum[]) {
                Enum[] vals = (Enum[]) val;
                for (int i = 0; i < vals.length; i++) {
                    this.value = vals[i].name();
                }
                this.runtimeType.setMigrationCallback(null);
            } else if (val instanceof Class[]) {
            	Class[] vals = (Class[]) val;
            	StringBuffer sb = new StringBuffer();
            	for (int i=0; i < vals.length; i++) {
            		sb.append(vals[i].getSimpleName() + ",");
            	}
            	this.value = sb.toString();
            }
            else {
                this.value = val.toString();
                this.runtimeType.setMigrationCallback(null);
            }
        } catch (InvocationTargetException e) {
            throw DatabaseException.fatals.failedDuringUpgrade("Failed to parse annotation value:", e);
        } catch (IllegalAccessException e) {
            throw DatabaseException.fatals.failedDuringUpgrade("Failed to parse annotation value:", e);
        }
    }

    @Key
    @XmlAttribute
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlAttribute
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @XmlAttribute
    public String getType() {
        if (runtimeType == null) {
            return type;
        }

        if (runtimeType.getMigrationCallback() == null) {
            return null;
        }

        return runtimeType.getMigrationCallback().getCanonicalName();
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AnnotationValue)) {
            return false;
        }

        AnnotationValue annotationValue = (AnnotationValue) o;

        if (!annotationValue.getName().equals(getName())) {
            return false;
        }

        if (!annotationValue.getValue().equals(getValue())) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, value);
    }

    @XmlTransient
    public Class getCfClass() {
        return runtimeType.getCfClass();
    }

    @XmlTransient
    public String getFieldName() {
        return runtimeType.getFieldName();
    }

    @XmlTransient
    public Class<? extends Annotation> getAnnoClass() {
        return runtimeType.getAnnotation().annotationType();
    }

    @Override
    public String describe() {
        return "annotation field: " + this.name + " for " + parent.describe();
    }

    public void setParent(SchemaObject parent) {
        this.parent = parent;
    }
}
