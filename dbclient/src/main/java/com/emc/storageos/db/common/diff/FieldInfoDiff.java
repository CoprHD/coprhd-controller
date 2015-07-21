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
import com.emc.storageos.db.common.schema.FieldInfo;

public class FieldInfoDiff extends Diff {
    private static final Logger log = LoggerFactory.getLogger(FieldInfoDiff.class);

    private String name;

    private PrimitiveChangeTracker<String> typeCT;

    private CollectionChangeTracker<AnnotationType, AnnotationTypeDiff> annotationCT;

    private FieldInfoDiff() {
    }

    public FieldInfoDiff(FieldInfo src, FieldInfo tgt) {
        name = src.getName();
        
        for (AnnotationType at : src.getAnnotations().getAnnotations()) {
        	at.setParent(src);
        }

        typeCT = PrimitiveChangeTracker.newInstance(src.getType(), tgt.getType(), tgt);

        annotationCT = CollectionChangeTracker.<AnnotationType, AnnotationTypeDiff>newInstance(
                AnnotationType.class, AnnotationTypeDiff.class, src.getAnnotations().getAnnotations(), 
                tgt.getAnnotations().getAnnotations());
    }

    @XmlAttribute
    public String getName() {
        return name;
    }

    @XmlElement(name = "type_change")
    public PrimitiveChangeTracker<String> getTypeCT() {
        return typeCT;
    }

    @XmlElement(name = "annotation_changes")
    public CollectionChangeTracker<AnnotationType, AnnotationTypeDiff> getAnnotationCT() {
        return annotationCT;
    }

    public boolean isUpgradable() {
        if (typeCT != null && !typeCT.isUpgradable())
            return false;

        if (annotationCT != null && !annotationCT.isUpgradable())
            return false;

        return true;
    }

    public boolean isChanged() {
        if (typeCT != null && typeCT.isChanged())
            return true;

        if (annotationCT != null && annotationCT.isChanged())
            return true;

        return false;
    }

    /**
     * Return a list of new field annotations from the target schemas with parent CF/field
     * information
     *
     * Note that it only includes new annotations of EXISITING fields, not annotations of
     * new fields
     */
    public List<AnnotationType> getNewFieldAnnotations() {
        if (annotationCT != null)
            return annotationCT.getNewList();

        return new ArrayList<AnnotationType>();
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

        if (annotationCT != null) {
            for (AnnotationTypeDiff annotation : annotationCT.getDiff()) {
                valueList.addAll(annotation.getNewAnnotationValues());
            }
        }

        return valueList;
    }
}
