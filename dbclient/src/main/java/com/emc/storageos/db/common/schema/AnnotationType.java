/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common.schema;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.DecommissionedIndex;
import com.emc.storageos.db.client.model.uimodels.Order;

public class AnnotationType implements SchemaObject {
    private static final Logger log = LoggerFactory.getLogger(AnnotationType.class);

    private RuntimeType runtimeType;

    // the following field is used in unmarshalled instances
    private String type;

    // the following fields are used to distinguish instances
    private String name;
    private List<AnnotationValue> valueList = new ArrayList<AnnotationValue>();

    SchemaObject parent;

    public AnnotationType() {
    }

    public AnnotationType(RuntimeType runtimeType, Annotation annotation, SchemaObject parent) {
        this.runtimeType = new RuntimeType(runtimeType);
        this.runtimeType.setAnnotation(annotation);

        this.parent = parent;

        this.name = annotation.annotationType().getSimpleName();

        Method[] methods = annotation.annotationType().getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            this.valueList.add(new AnnotationValue(this.runtimeType, methods[i], this));
        }
    }

    @XmlAttribute
    public String getType() {
        if (runtimeType == null || runtimeType.getAnnotation() == null) {
            return type;
        }

        return runtimeType.getAnnotation().annotationType().getCanonicalName();
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

    @XmlElement(name = "annotation_value")
    public List<AnnotationValue> getValueList() {
        return valueList;
    }

    public void setValueList(List<AnnotationValue> valueList) {
        this.valueList = valueList;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AnnotationType)) {
            return false;
        }

        AnnotationType annotationType = (AnnotationType) o;

        if (!annotationType.getName().equals(getName())) {
            return false;
        }

        return Objects.equal(this.valueList, annotationType.valueList);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, valueList);
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
    public Annotation getAnnotation() {
        return runtimeType.getAnnotation();
    }

    @XmlTransient
    public Class<? extends Annotation> getAnnoClass() {
        return runtimeType.getAnnotation().annotationType();
    }

    @Override
    public String describe() {
        return "annotation: " + this.name + " on " + parent.describe();
    }

    public void setParent(SchemaObject parent) {
        this.parent = parent;
    }

    public boolean canBeIgnore() {
        if (parent instanceof FieldInfo) {
            FieldInfo fieldInfo = (FieldInfo)parent;
            if (name.equals(DecommissionedIndex.class.getSimpleName()) && fieldInfo.getName().equals(Order.SUBMITTED)) {
                log.info("ignore {} of field {}", DecommissionedIndex.class.getSimpleName(), Order.SUBMITTED);
                return true;
            }

            if (name.equals(AlternateId.class.getSimpleName()) && fieldInfo.getName().equals(Order.SUBMITTED_BY_USER_ID)) {
                log.info("ignore {} of field {}", AlternateId.class.getSimpleName(), Order.SUBMITTED_BY_USER_ID);
                return true;
            }
        }
        return false;
    }
}
