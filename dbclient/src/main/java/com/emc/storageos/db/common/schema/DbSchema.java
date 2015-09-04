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

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import com.emc.storageos.db.common.DbSchemaScannerInterceptor;
import com.google.common.base.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.model.Cf;

public class DbSchema implements SchemaObject {
    private static final Logger log = LoggerFactory.getLogger(DbSchema.class);

    private RuntimeType runtimeType;

    // the following field is used in unmarshalled instances
    private String type;

    // the following fields are used to distinguish instances
    private String name;
    private List<FieldInfo> fields = new ArrayList<FieldInfo>();
    private Annotations annotations;

    public DbSchema() {
    }

    public DbSchema(Class clazz) {
        this(clazz, null);
    }

    public DbSchema(Class clazz, DbSchemaScannerInterceptor scannerInterceptor) {
        runtimeType = new RuntimeType();
        runtimeType.setCfClass(clazz);

        this.name = clazz.getSimpleName();
        Cf cfAnnotation = (Cf) clazz.getAnnotation(Cf.class);
        if (cfAnnotation != null) {
            this.name = cfAnnotation.value();
        }

        BeanInfo bInfo;
        try {
            bInfo = Introspector.getBeanInfo(clazz);
        } catch (IntrospectionException ex) {
            log.error("Failed to get bean info:", ex);
            throw new IllegalStateException(ex.getMessage());
        }

        PropertyDescriptor[] pds = bInfo.getPropertyDescriptors();
        for (int i = 0; i < pds.length; i++) {
            PropertyDescriptor pd = pds[i];
            if (!DataObjectType.isColumnField(bInfo.getBeanDescriptor().getBeanClass().getName(), pd)) {
                continue;
            }
            pd.setShortDescription(this.name + "." + pd.getShortDescription());
            this.fields.add(new FieldInfo(runtimeType, pd, this, scannerInterceptor));
        }

        annotations = new Annotations(runtimeType, clazz.getDeclaredAnnotations(), this, scannerInterceptor);
    }

    @XmlAttribute
    public String getType() {
        if (runtimeType == null)
            return type;

        return runtimeType.getCfClass().getCanonicalName();
    }

    public void setType(String type) {
        this.type = type;
    }

    @Key
    @XmlAttribute
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement
    public Annotations getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Annotations annotations) {
        this.annotations = annotations;
    }

    @XmlElementWrapper(name = "fields")
    @XmlElement(name = "field")
    public List<FieldInfo> getFields() {
        return fields;
    }

    public void setFields(List<FieldInfo> fields) {
        this.fields = fields;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DbSchema))
            return false;

        DbSchema schema = (DbSchema) o;

        if (!schema.getName().equals(getName()))
            return false;

        if (!annotations.equals(schema.getAnnotations()))
            return false;

        return Objects.equal(getFields(), schema.getFields());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, annotations, fields);
    }

    @Override
    public String describe() {
        return "column family: " + name;
    }

    public boolean hasDuplicateField() {
        Set<String> uniqueFields = new HashSet<String>();

        for (FieldInfo fieldInfo : this.fields) {
            if (uniqueFields.contains(fieldInfo.getName())) {
                return true;
            } else {
                uniqueFields.add(fieldInfo.getName());
            }
        }
        return false;
    }

    public List<FieldInfo> getDuplicateFields() {
        Set<String> uniqueFields = new HashSet<String>();
        List<FieldInfo> duplicateFields = new ArrayList<FieldInfo>();

        for (FieldInfo fieldInfo : this.fields) {
            if (uniqueFields.contains(fieldInfo.getName())) {
                duplicateFields.add(fieldInfo);
            } else {
                uniqueFields.add(fieldInfo.getName());
            }
        }
        return duplicateFields;
    }
}
