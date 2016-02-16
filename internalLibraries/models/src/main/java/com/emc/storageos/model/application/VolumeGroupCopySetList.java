/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.application;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Response for getting a list of copy sets for a volume group
 */
@XmlRootElement(name = "copy_sets")
public class VolumeGroupCopySetList {

    private Set<String> copySets;

    public VolumeGroupCopySetList() {
    }

    public VolumeGroupCopySetList(Set<String> copySets) {
        this.copySets = copySets;
    }

    /**
     * List of copy set names belonging to volume group.
     * 
     */
    @XmlElement(name = "copy_set")
    public Set<String> getCopySets() {
        if (copySets == null) {
            copySets = new HashSet<String>();
        }
        return copySets;
    }

    public void setCopySets(Set<String> copySets) {
        this.copySets = copySets;
    }

}