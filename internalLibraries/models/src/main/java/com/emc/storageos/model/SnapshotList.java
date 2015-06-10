/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.model;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="snapshots")
public class SnapshotList {

    /**
     * List of Snapshots.
     * @valid none
     */
    private List<NamedRelatedResourceRep> snapList;

    public SnapshotList() {
    }
    
    public SnapshotList(List<NamedRelatedResourceRep> snapList) {
        this.snapList = snapList;
    }
    
    @XmlElement(name="snapshot")
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

