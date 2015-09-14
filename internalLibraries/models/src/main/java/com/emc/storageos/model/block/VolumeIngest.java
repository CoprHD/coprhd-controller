/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Volume Ingest parameters
 * 
 * UnManaged Volumes are Volumes, which are present within ViPR Storage Systems, but not managed in ViPR.
 * Use GET /vdc/storage-systems/{id}/unmanaged/volumes to get list of unmanaged volume ids on a Storage System basis.
 * Use GET /vdc/unmanaged/volumes/bulk to get list of all unManaged volume ids.
 * Use POST /vdc/unmanaged/volumes/bulk to get unManaged volume data.
 * Volume Ingest provides flexibility to the user in bringing UnManaged Volumes under ViPR management.
 * User need to associate VirtualPool, Project, and VirtualArray in order to move these under ViPR Management.
 * 
 * List of Supported virtual pools for each UnManagedVolume is being exposed using /vdc/unmanaged/volumes/bulk.
 */
@XmlRootElement(name = "volume_ingest")
public class VolumeIngest {

    private URI vpool;
    private URI varray;
    private URI project;
    private List<URI> unManagedVolumes;
    private String vplexIngestionMethod;

    public VolumeIngest() {
    }

    public VolumeIngest(URI vpool, URI varray, URI project,
            List<URI> unManagedVolumes) {
        this.vpool = vpool;
        this.varray = varray;
        this.project = project;
        this.unManagedVolumes = unManagedVolumes;

    }

    /**
     * VirtualPool to be associated with a list of unmanaged volumes to be ingested.
     * 
     * @valid example: a valid URI of a vpool
     */
    @XmlElement(required = true)
    public URI getVpool() {
        return vpool;
    }

    public void setVpool(URI vpool) {
        this.vpool = vpool;
    }

    /**
     * VirtualArray to be associated with a list of unmanaged volumes to be ingested.
     * 
     * @valid example: a valid URI of a varray
     */
    @XmlElement(required = true)
    public URI getVarray() {
        return varray;
    }

    public void setVarray(URI varray) {
        this.varray = varray;
    }

    /**
     * Project to be associated with a list of unmanaged volumes to be ingested.
     * 
     * @valid example: a valid URI of a Project
     */
    @XmlElement(required = true)
    public URI getProject() {
        return project;
    }

    public void setProject(URI project) {
        this.project = project;
    }

    /**
     * List of unmanaged volumes to be ingested.
     * 
     * @valid none
     */
    @XmlElement(name = "unmanaged_volume_list", required = true)
    public List<URI> getUnManagedVolumes() {
        if (unManagedVolumes == null) {
            unManagedVolumes = new ArrayList<URI>();
        }
        return unManagedVolumes;
    }

    public void setUnManagedVolumes(List<URI> unManagedVolumes) {
        this.unManagedVolumes = unManagedVolumes;
    }

    /**
     * The ingestion method for VPLEX volumes.
     * 
     * @valid "Full" or "VirtualVolumesOnly"
     */
    @XmlElement(required = false)
    public String getVplexIngestionMethod() {
        return vplexIngestionMethod;
    }

    public void setVplexIngestionMethod(String type) {
        this.vplexIngestionMethod = type;
    }

}
