/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "snapshots")
public class SnapshotList {

    /**
     * List of Snapshots.
     * 
     */
    private List<NamedRelatedResourceRep> snapList;

    public SnapshotList() {
    }

    public SnapshotList(List<NamedRelatedResourceRep> snapList) {
        this.snapList = snapList;
    }

    @XmlElement(name = "snapshot")
    public List<NamedRelatedResourceRep> getSnapList() {
        if (snapList == null) {
            snapList = new ArrayList<NamedRelatedResourceRep>();
        }
        return snapList;
    }

    public void setSnapList(List<NamedRelatedResourceRep> snapList) {
        this.snapList = snapList;
    }
}
