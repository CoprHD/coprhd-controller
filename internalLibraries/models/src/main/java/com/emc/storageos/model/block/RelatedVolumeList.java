/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import com.emc.storageos.model.NamedRelatedResourceRep;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot creation response
 */
@XmlRootElement(name = "consistent_volumes")
public class RelatedVolumeList {

    private List<NamedRelatedResourceRep> volumeList;

    public RelatedVolumeList() {
    }

    public RelatedVolumeList(List<NamedRelatedResourceRep> volumeList) {
        this.volumeList = volumeList;
    }

    /**
     * List of volumes.
     * 
     */
    @XmlElement(name = "consistent_volume")
    public List<NamedRelatedResourceRep> getVolumeList() {
        if (volumeList == null) {
            volumeList = new ArrayList<NamedRelatedResourceRep>();
        }
        return volumeList;
    }

    public void setVolumeList(List<NamedRelatedResourceRep> volumeList) {
        this.volumeList = volumeList;
    }
}
