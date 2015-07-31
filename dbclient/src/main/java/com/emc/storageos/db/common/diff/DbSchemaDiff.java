/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common.diff;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.common.schema.AnnotationType;
import com.emc.storageos.db.common.schema.AnnotationValue;
import com.emc.storageos.db.common.schema.DbSchema;
import com.emc.storageos.db.common.schema.FieldInfo;

public class DbSchemaDiff extends Diff {
    private static final Logger log = LoggerFactory.getLogger(DbSchemaDiff.class);

    private String type;

    private CollectionChangeTracker<FieldInfo, FieldInfoDiff> fieldCT;

    private CollectionChangeTracker<AnnotationType, AnnotationTypeDiff> annotationCT;

    private DbSchemaDiff() {
    }

    public DbSchemaDiff(DbSchema src, DbSchema tgt) {
        type = tgt.getType();

        if (src.getFields() != null) {
            for (FieldInfo fi : src.getFields()) {
                fi.setParent(src);
            }
        }

        for (AnnotationType at : src.getAnnotations().getAnnotations()) {
            at.setParent(src);
        }

        fieldCT = CollectionChangeTracker.<FieldInfo, FieldInfoDiff> newInstance(
                FieldInfo.class, FieldInfoDiff.class, src.getFields(), tgt.getFields());

        annotationCT = CollectionChangeTracker.<AnnotationType, AnnotationTypeDiff> newInstance(
                AnnotationType.class, AnnotationTypeDiff.class, src.getAnnotations().getAnnotations(),
                tgt.getAnnotations().getAnnotations());
    }

    @XmlAttribute
    public String getType() {
        return type;
    }

    @XmlElement(name = "field_changes")
    public CollectionChangeTracker<FieldInfo, FieldInfoDiff> getFieldCT() {
        return fieldCT;
    }

    @XmlElement(name = "annotation_changes")
    public CollectionChangeTracker<AnnotationType, AnnotationTypeDiff> getAnnotationCT() {
        return annotationCT;
    }

    public boolean isUpgradable() {
        if (fieldCT != null && !fieldCT.isUpgradable()) {
            return false;
        }

        if (annotationCT != null && !annotationCT.isUpgradable()) {
            return false;
        }

        return true;
    }

    public boolean isChanged() {
        if (fieldCT != null && fieldCT.isChanged()) {
            return true;
        }

        if (annotationCT != null && annotationCT.isChanged()) {
            return true;
        }

        return false;
    }

    /**
     * Return a list of new fields from the target schemas with parent CF information
     * 
     * Note that it only includes new fields of EXISITING CF schemas, not fields of
     * new CF schemas
     */
    public List<FieldInfo> getNewFields() {
        if (fieldCT != null) {
            return fieldCT.getNewList();
        }

        return new ArrayList<FieldInfo>();
    }

    /**
     * Return a list of new class annotations from the target schemas with parent CF
     * information
     * 
     * Note that it only includes new annotations of EXISITING CF schemas, not annotations
     * of new CF schemas
     */
    public List<AnnotationType> getNewClassAnnotations() {
        List<AnnotationType> annoList = new ArrayList<AnnotationType>();

        if (annotationCT != null) {
            annoList.addAll(annotationCT.getNewList());
        }

        return annoList;
    }

    /**
     * Return a list of new field annotations from the target schemas with parent CF/field
     * information
     * 
     * Note that it only includes new annotations of EXISITING fields, not annotations of
     * new fields
     */
    public List<AnnotationType> getNewFieldAnnotations() {
        List<AnnotationType> annoList = new ArrayList<AnnotationType>();

        if (fieldCT != null) {
            for (FieldInfoDiff field : fieldCT.getDiff()) {
                annoList.addAll(field.getNewFieldAnnotations());
            }
        }

        return annoList;
    }

    /**
     * Return a list of new annotation values from the target schemas with parent
     * CF/field/annotation information
     * 
     * Note that it only includes new annotation values of EXISITING annotations, not
     * annotation values of new annotations
     */
    public List<AnnotationValue> getNewAnnotationValues() {
        List<AnnotationValue> valueList = new ArrayList<AnnotationValue>();

        // field annotations
        if (fieldCT != null) {
            for (FieldInfoDiff field : fieldCT.getDiff()) {
                valueList.addAll(field.getNewAnnotationValues());
            }
        }

        // class annotations
        if (annotationCT != null) {
            for (AnnotationTypeDiff annotation : annotationCT.getDiff()) {
                valueList.addAll(annotation.getNewAnnotationValues());
            }
        }

        return valueList;
    }
}
