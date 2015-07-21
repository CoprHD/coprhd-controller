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

public class AnnotationTypeDiff extends Diff {
    private static final Logger log = LoggerFactory.getLogger(AnnotationTypeDiff.class);

    private String type;

    private CollectionChangeTracker<AnnotationValue, AnnotationValueDiff> valueCT;

    private AnnotationTypeDiff() {
    }

    public AnnotationTypeDiff(AnnotationType src, AnnotationType tgt) {
        type = src.getType();
        
        for (AnnotationValue av : src.getValueList()) {
            av.setParent(src);
        }

        valueCT = CollectionChangeTracker.<AnnotationValue, AnnotationValueDiff>newInstance(
                AnnotationValue.class, AnnotationValueDiff.class, src.getValueList(), 
                tgt.getValueList());
    }

    @XmlAttribute
    public String getType() {
        return type;
    }

    @XmlElement(name = "annotation_value_changes")
    public CollectionChangeTracker<AnnotationValue, AnnotationValueDiff> getValueCT() {
        return valueCT;
    }

    public boolean isUpgradable() {
        if (valueCT != null && !valueCT.isUpgradable())
            return false;

        return true;
    }

    public boolean isChanged() {
        if (valueCT != null && valueCT.isChanged())
            return true;

        return false;
    }

    /**
     * Return a list of new annotation values from the target schemas with parent
     * CF/field/annotation information
     *
     * Note that it only includes new annotation values of EXISITING annotations, not
     * annotation values of new annotations
     */
    public List<AnnotationValue> getNewAnnotationValues() {
        if (valueCT != null) 
            return valueCT.getNewList();

        return new ArrayList<AnnotationValue>();
    }
}
