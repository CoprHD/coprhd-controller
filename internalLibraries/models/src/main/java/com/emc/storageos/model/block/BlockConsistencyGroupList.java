/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "consistency_groups")
public class BlockConsistencyGroupList {

    /**
     * List of block consistency groups
     * 
     * @valid none
     */
    private List<NamedRelatedBlockConsistencyGroupRep> consistencyGroupList;

    public BlockConsistencyGroupList() {
    }

    public BlockConsistencyGroupList(
            List<NamedRelatedBlockConsistencyGroupRep> consistencyGroupList) {
        super();
        this.consistencyGroupList = consistencyGroupList;
    }

    @XmlElement(name = "consistency_group")
    public List<NamedRelatedBlockConsistencyGroupRep> getConsistencyGroupList() {
        if (consistencyGroupList == null) {
            consistencyGroupList = new ArrayList<NamedRelatedBlockConsistencyGroupRep>();
        }
        return consistencyGroupList;
    }

    public void setConsistencyGroupList(
            List<NamedRelatedBlockConsistencyGroupRep> consistencyGroupList) {
        this.consistencyGroupList = consistencyGroupList;
    }
}
