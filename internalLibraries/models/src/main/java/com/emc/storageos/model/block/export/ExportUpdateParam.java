/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

/**
 * 
 * Parameter for block export update.
 */
@XmlRootElement(name = "block_export_update")
public class ExportUpdateParam {

    // /**
    // * User assigned name for block export.
    // */
    // @XmlElement()
    // public String name;

    private VolumeUpdateParam volumes;
    private InitiatorsUpdateParam initiators;
    private HostsUpdateParam hosts;
    private ClustersUpdateParam clusters;
    // Export path parameters can be specified for any included volumes.
    private ExportPathParameters exportPathParameters;

    public ExportUpdateParam() {
    }

    public ExportUpdateParam(VolumeUpdateParam volumes,
            InitiatorsUpdateParam initiators, HostsUpdateParam hosts,
            ClustersUpdateParam clusters) {
        this.volumes = volumes;
        this.initiators = initiators;
        this.hosts = hosts;
        this.clusters = clusters;
    }

    /**
     * Add or remove a list of clusters
     * from the export
     * 
     * @valid none
     */
    @XmlElement(name = "cluster_changes")
    public ClustersUpdateParam getClusters() {
        return clusters;
    }

    public void setClusters(ClustersUpdateParam clusters) {
        this.clusters = clusters;
    }

    /**
     * Add or remove a list of hosts
     * from the export
     * 
     * @valid none
     */
    @XmlElement(name = "host_changes")
    public HostsUpdateParam getHosts() {
        return hosts;
    }

    public void setHosts(HostsUpdateParam hosts) {
        this.hosts = hosts;
    }

    /**
     * Add or remove a list of initiators
     * from the export
     * 
     * @valid none
     */
    @XmlElement(name = "initiator_changes")
    public InitiatorsUpdateParam getInitiators() {
        return initiators;
    }

    public void setInitiators(InitiatorsUpdateParam initiators) {
        this.initiators = initiators;
    }

    /**
     * Add or remove a list of volume or
     * volume snapshots from the export
     * 
     * @valid none
     */
    @XmlElement(name = "volume_changes")
    public VolumeUpdateParam getVolumes() {
        if (volumes == null) {
            volumes = new VolumeUpdateParam();
        }
        return volumes;
    }

    public void setVolumes(VolumeUpdateParam volumes) {
        this.volumes = volumes;
    }

    // Convenience methods

    public void addVolume(URI volumeId) {
        if (volumes == null) {
            volumes = new VolumeUpdateParam();
        }
        volumes.addVolume(volumeId);
    }

    public void removeVolume(URI volumeId) {
        if (volumes == null) {
            volumes = new VolumeUpdateParam();
        }
        volumes.removeVolume(volumeId);
    }

    @XmlElement(name="path_parameters", required=false)
    public ExportPathParameters getExportPathParameters() {
                return exportPathParameters;
            }

    public void setExportPathParameters(ExportPathParameters exportPathParameters) {
                this.exportPathParameters = exportPathParameters;
            }
}