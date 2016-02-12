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
 * Response for getting a list of volume group
 */
@XmlRootElement(name = "full_copy_sets")
public class VolumeGroupFullCopySetList {

    private Set<String> fullCopySets;

    public VolumeGroupFullCopySetList() {
    }

    public VolumeGroupFullCopySetList(Set<String> fullCopySets) {
        this.fullCopySets = fullCopySets;
    }

    /**
     * List of full copy set names belonging to volume group.
     * 
     */
    @XmlElement(name = "full_copy_set")
    public Set<String> getFullCopySets() {
        if (fullCopySets == null) {
            fullCopySets = new HashSet<String>();
        }
        return fullCopySets;
    }

    public void setFullCopySets(Set<String> fullCopySets) {
        this.fullCopySets = fullCopySets;
    }

}