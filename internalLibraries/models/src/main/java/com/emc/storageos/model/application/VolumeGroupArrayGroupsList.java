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
@XmlRootElement(name = "array_groups")
public class VolumeGroupArrayGroupsList {

    private List<String> arrayGroups;

    public VolumeGroupArrayGroupsList() {
    }

    public VolumeGroupArrayGroupsList(List<String> arrayGroups) {
        this.arrayGroups = arrayGroups;
    }

    /**
     * List of array group names within a volume group.
     * 
     */
    @XmlElement(name = "array_group")
    public List<String> getArrayGroups() {
        if (arrayGroups == null) {
            arrayGroups = new ArrayList<String>();
        }
        return arrayGroups;
    }

    public void setArrayGroups(List<String> arrayGroups) {
        this.arrayGroups = arrayGroups;
    }

}
