/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.application;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

/**
 * Response for getting a list of volume group
 */
@XmlRootElement(name = "volume_groups")
public class VolumeGroupList {

    private List<NamedRelatedResourceRep> volumeGroups;

    public VolumeGroupList() {
    }

    public VolumeGroupList(List<NamedRelatedResourceRep> volumeGroups) {
        this.volumeGroups = volumeGroups;
    }

    /**
     * List of volume group objects that exist in ViPR. Each
     * application contains an id, name, and link.
     * 
     */
    @XmlElement(name = "volume_group")
    public List<NamedRelatedResourceRep> getVolumeGroups() {
        if (volumeGroups == null) {
            volumeGroups = new ArrayList<NamedRelatedResourceRep>();
        }
        return volumeGroups;
    }

    public void setVolumeGroups(List<NamedRelatedResourceRep> volumeGroups) {
        this.volumeGroups = volumeGroups;
    }

}
