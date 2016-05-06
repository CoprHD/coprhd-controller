/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.dbcli.wrapper;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.db.client.model.ScopedLabelSet;

@XmlRootElement(name = "wrapper")
@XmlAccessorType(XmlAccessType.FIELD)
public class ScopedLabelSetWrapper extends Wrapper<ScopedLabelSet> {

    @XmlElement(name = "scopedlabelSet")
    private ScopedLabelSet value = new ScopedLabelSet();

    public ScopedLabelSet getValue() {
        return value;
    }

    public void setValue(ScopedLabelSet scopedLabelSet) {
        value = scopedLabelSet;
    }

}
