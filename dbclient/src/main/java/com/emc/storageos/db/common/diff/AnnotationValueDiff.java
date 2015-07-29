/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.common.diff;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.common.schema.AnnotationValue;

public class AnnotationValueDiff extends Diff {
    private static final Logger log = LoggerFactory.getLogger(AnnotationValueDiff.class);

    private String name;

    // CTRL-2872, permits cf change of annotation RelationIndex
    private boolean isCfValueOfRelationIndex;

    private PrimitiveChangeTracker<String> valueCT;

    private AnnotationValueDiff() {
    }

    public AnnotationValueDiff(AnnotationValue src, AnnotationValue tgt) {
        name = src.getName();

        valueCT = PrimitiveChangeTracker.newInstance(src.getValue(), tgt.getValue(), tgt);

        if ((RelationIndex.class.equals(tgt.getAnnoClass()) && tgt.getName().equals("cf"))
                || (AlternateId.class.equals(tgt.getAnnoClass()) && tgt.getName().equals("value"))) {
            isCfValueOfRelationIndex = true;
            if (valueCT != null && valueCT.isChanged()) {
                log.info("Cf value of index {} has changed from {} to {}", new Object[] {
                        tgt.describe(), valueCT.getOldValue(), valueCT.getNewValue() });
            }
        }
    }

    @XmlAttribute
    public String getName() {
        return name;
    }

    @XmlElement(name = "value_change")
    public PrimitiveChangeTracker<String> getValueCT() {
        return valueCT;
    }

    public boolean isUpgradable() {
        if (valueCT == null || isCfValueOfRelationIndex) {
            return true;
        }
        return valueCT.isUpgradable();
    }

    public boolean isChanged() {
        if (valueCT == null) {
            return false;
        }
        return valueCT.isChanged();
    }
}
