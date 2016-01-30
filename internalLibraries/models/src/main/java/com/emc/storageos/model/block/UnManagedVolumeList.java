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
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "unmanaged_volumes")
public class UnManagedVolumeList {

    private List<RelatedResourceRep> unManagedVolumes;

    private List<NamedRelatedResourceRep> namedUnManagedVolumes;

    public UnManagedVolumeList() {
    }

    public UnManagedVolumeList(List<RelatedResourceRep> unManagedVolumes) {
        this.unManagedVolumes = unManagedVolumes;
    }

    /**
     * The list of unmanaged volumes which are available in a storage system.
     * Used primarily to ingest volumes into ViPR.
     * 
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

    /**
     * The list of unmanaged volumes with name which are available in a storage system.
     * Used primarily to ingest volumes into ViPR.
     * 
     */
    @XmlElement(name = "named_unmanaged_volume")
    public List<NamedRelatedResourceRep> getNamedUnManagedVolumes() {
        if (namedUnManagedVolumes == null) {
            namedUnManagedVolumes = new ArrayList<NamedRelatedResourceRep>();
        }
        return namedUnManagedVolumes;
    }

    public void setNamedUnManagedVolumes(List<NamedRelatedResourceRep> namedUnManagedVolumes) {
        this.namedUnManagedVolumes = namedUnManagedVolumes;
    }

}
