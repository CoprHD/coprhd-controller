/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "consistency_groups")
public class BlockConsistencyGroupList {

    /**
     * List of block consistency groups
     * 
     */
    private List<NamedRelatedResourceRep> consistencyGroupList;

    public BlockConsistencyGroupList() {
    }

    public BlockConsistencyGroupList(
            List<NamedRelatedResourceRep> consistencyGroupList) {
        super();
        this.consistencyGroupList = consistencyGroupList;
    }

    @XmlElement(name = "consistency_group")
    public List<NamedRelatedResourceRep> getConsistencyGroupList() {
        if (consistencyGroupList == null) {
            consistencyGroupList = new ArrayList<NamedRelatedResourceRep>();
        }
        return consistencyGroupList;
    }

    public void setConsistencyGroupList(
            List<NamedRelatedResourceRep> consistencyGroupList) {
        this.consistencyGroupList = consistencyGroupList;
    }
}
