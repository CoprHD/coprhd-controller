/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "volumes")
public class NamedVolumesList {

    private List<NamedRelatedResourceRep> volumes;

    public NamedVolumesList() {}
    
    public NamedVolumesList(List<NamedRelatedResourceRep> volumes) {
        this.volumes = volumes;
    }

    /**
     * List of volumes that make up this entry.  Used primarily to ingest volumes into ViPR.  
     * @valid Maximum of 4000 volumes can be provided in this list.
     */
    @XmlElement(name = "volume")
    public List<NamedRelatedResourceRep> getVolumes() {
        if (volumes == null) {
             volumes = new ArrayList<NamedRelatedResourceRep>();
        }
        return volumes;
    }

    public void setVolumes(List<NamedRelatedResourceRep> volumes) {
        this.volumes = volumes;
    }
    
}
