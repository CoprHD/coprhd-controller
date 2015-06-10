/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Response to a bulk query for the list of unmanaged volumes.
 * 
 */
@XmlRootElement(name = "bulk_unmanaged_volumes")
public class UnManagedBulkRep extends BulkRestRep {
    private List<UnManagedVolumeRestRep> unManagedVolumes;

    /** 
     * List of unmanaged volumes.  UnManaged volumes are volumes that are
     * present within ViPR, but are not under ViPR management.  ViPR provides
     * an ingest capability that enables users to bring the unmanaged
     * volumes under ViPR management.
     * @valid none
     */
    @XmlElement(name = "unmanaged_volume")
    public List<UnManagedVolumeRestRep> getUnManagedVolumes() {
        if (unManagedVolumes == null) {
            unManagedVolumes = new ArrayList<UnManagedVolumeRestRep>();
        }
        return unManagedVolumes;
    }

    public void setUnManagedVolumes(List<UnManagedVolumeRestRep> unManagedVolumes) {
        this.unManagedVolumes = unManagedVolumes;
    }

    public UnManagedBulkRep() {}


    public UnManagedBulkRep(List<UnManagedVolumeRestRep> unManagedVolumes) {
        this.unManagedVolumes = unManagedVolumes;
    }
}
