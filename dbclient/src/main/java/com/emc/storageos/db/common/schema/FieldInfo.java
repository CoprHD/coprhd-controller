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

package com.emc.storageos.db.common.schema;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import com.google.common.base.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.common.DbSchemaScannerInterceptor;

public class FieldInfo implements SchemaObject {
    private static final Logger log = LoggerFactory.getLogger(FieldInfo.class);

    private RuntimeType runtimeType;

    // the following fields are used to distinguish instances
    private String name;
    private String type;
    private Annotations annotations;

    private SchemaObject parent;

    public FieldInfo() {
    }

    public FieldInfo(RuntimeType runtimeType, PropertyDescriptor pd, SchemaObject parent, DbSchemaScannerInterceptor scannerInterceptor) {
        this.runtimeType = new RuntimeType(runtimeType);
        this.runtimeType.setPropertyDescriptor(pd);

        this.parent = parent;

        this.name = pd.getName();
        Method readMethod = pd.getReadMethod();
        if (readMethod == null) {
            String msg = String.format("Could not find getter method for property %s in %s", this.getName(), runtimeType.getCfClass());
            log.error(msg);
            throw new IllegalStateException(msg);
        }
        Name nameAnnotation = readMethod.getAnnotation(Name.class);
        if (nameAnnotation != null) {
            this.name = nameAnnotation.value();
        }
        this.type = pd.getPropertyType().getName();

        annotations = new Annotations(this.runtimeType, pd.getReadMethod().getDeclaredAnnotations(), this, scannerInterceptor);
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
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Annotations getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Annotations annotations) {
        this.annotations = annotations;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FieldInfo)) {
            return false;
        }

        FieldInfo field = (FieldInfo) o;

        if (!name.equals(field.getName())) {
            return false;
        }

        if (!type.equals(field.getType())) {
            return false;
        }

        return annotations.equals(field.getAnnotations());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, type, annotations);
    }

    @XmlTransient
    public Class getCfClass() {
        return runtimeType.getCfClass();
    }

    @Override
    public String describe() {
        return "field: " + this.name + " (type:" + this.type + ") in " + parent.describe();
    }

    public void setParent(SchemaObject parent) {
        this.parent = parent;
    }
}
