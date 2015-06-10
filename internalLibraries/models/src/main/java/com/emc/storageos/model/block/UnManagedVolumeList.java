/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import com.emc.storageos.model.RelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "unmanaged_volumes")
public class UnManagedVolumeList {

    private List<RelatedResourceRep> unManagedVolumes;

    public UnManagedVolumeList() {}
            
    public UnManagedVolumeList(List<RelatedResourceRep> unManagedVolumes) {
        this.unManagedVolumes = unManagedVolumes;
    }

    /**
     * The list of unmanaged volumes which are available in a storage system.  
     * Used primarily to ingest volumes into ViPR.  
     * @valid none
     */    
    @XmlElement(name = "unmanaged_volume")
    public List<RelatedResourceRep> getUnManagedVolumes() {
        if (unManagedVolumes == null) {
            unManagedVolumes = new ArrayList<RelatedResourceRep>();
        }
        return unManagedVolumes;
    }

    public void setUnManagedVolumes(List<RelatedResourceRep> unManagedVolumes) {
        this.unManagedVolumes = unManagedVolumes;
    }
    
}
