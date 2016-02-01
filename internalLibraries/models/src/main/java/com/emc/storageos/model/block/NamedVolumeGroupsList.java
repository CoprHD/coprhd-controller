/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "volume-groups")
public class NamedVolumeGroupsList {

    private List<NamedRelatedResourceRep> volumeGroups;

    public NamedVolumeGroupsList() {
    }

    public NamedVolumeGroupsList(List<NamedRelatedResourceRep> volumeGroups) {
        this.volumeGroups = volumeGroups;
    }

    /**
     * List of volume groups that make up this entry.
     *
     * Valid values: Maximum of 4000 volumes can be provided in this list.
     */
    @XmlElement(name = "volume-group")
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
