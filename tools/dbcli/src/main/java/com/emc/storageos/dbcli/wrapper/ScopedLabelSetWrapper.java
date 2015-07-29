/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.dbcli.wrapper;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.db.client.model.ScopedLabelSet;

@XmlRootElement(name = "wrapper")
public class ScopedLabelSetWrapper extends Wrapper<ScopedLabelSet> {

    private ScopedLabelSet scopedLabelSet = new ScopedLabelSet();

    @XmlElement(name = "scopedlabelSet")
    public ScopedLabelSet getValue() {
        return scopedLabelSet;
    }

    public void setValue(ScopedLabelSet scopedLabelSet) {
        this.scopedLabelSet = scopedLabelSet;
    }

}
