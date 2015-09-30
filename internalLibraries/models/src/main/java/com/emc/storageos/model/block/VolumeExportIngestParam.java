/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "exported_volumes_ingest")
public class VolumeExportIngestParam {
    private URI vpool;
    private URI varray;
    private URI host;
    private URI cluster;
    private URI project;
    private List<URI> unManagedVolumes;
    private String vplexIngestionMethod;

    public VolumeExportIngestParam() {
    }

    public VolumeExportIngestParam(URI vpool, URI varray, URI project,
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

    @XmlElement(required = false)
    public URI getHost() {
        return host;
    }

    public void setHost(URI host) {
        this.host = host;
    }

    @XmlElement(required = false)
    public URI getCluster() {
        return cluster;
    }

    public void setCluster(URI cluster) {
        this.cluster = cluster;
    }

    /**
     * The ingestion method for VPLEX volumes.
     * Defaults to "Full" if not specificed.
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
