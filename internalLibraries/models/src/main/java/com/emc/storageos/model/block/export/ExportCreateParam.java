/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.valid.Length;

/**
 * Parameter for block export creation.
 */
@XmlRootElement(name = "block_export_create")
public class ExportCreateParam {

    private URI project;
    private URI varray;
    private String name;
    private String type;
    private List<VolumeParam> volumes;
    private ExportPathParameters exportPathParameters;

    // The fiber channel SAN membership
    // of Fiber Channel initiators must be known. For iSCSI initiators, either a default IP network
    // must be present in the virtual array or an explicit IP network in the virtual array should be chosen.
    private List<URI> initiators;
    private List<URI> hosts;
    private List<URI> clusters;

    public ExportCreateParam() {
    }

    public ExportCreateParam(URI project, URI varray, String name, String type,
            List<VolumeParam> volumes, List<URI> initiators, List<URI> hosts,
            List<URI> clusters) {
        this.project = project;
        this.varray = varray;
        this.name = name;
        this.type = type;
        this.volumes = volumes;
        this.initiators = initiators;
        this.hosts = hosts;
        this.clusters = clusters;
    }

    @XmlElementWrapper(required = false)
    /**
     * The clusters to which the volumes will be exported.
     * @valid none
     */
    @XmlElement(name = "cluster")
    public List<URI> getClusters() {
        if (clusters == null) {
            clusters = new ArrayList<URI>();
        }
        return clusters;
    }

    public void setClusters(List<URI> clusters) {
        this.clusters = clusters;
    }

    @XmlElementWrapper(required = false)
    /**
     * The hosts to which the volumes will be exported.
     * @valid none
     */
    @XmlElement(name = "host")
    public List<URI> getHosts() {
        if (hosts == null) {
            hosts = new ArrayList<URI>();
        }
        return hosts;
    }

    public void setHosts(List<URI> hosts) {
        this.hosts = hosts;
    }

    @XmlElementWrapper(required = false)
    /**
     * List of initiators to which the shared storage is made 
     * visible.
     * @valid none
     */
    @XmlElement(name = "initiator")
    public List<URI> getInitiators() {
        if (initiators == null) {
            initiators = new ArrayList<URI>();
        }
        return initiators;
    }

    public void setInitiators(List<URI> initiators) {
        this.initiators = initiators;
    }

    /**
     * User assigned name for the export.
     */
    @XmlElement(required = true)
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The ViPR project to which this export will belong.
     * 
     * @valid example: a valid URI of a ViPR project
     */
    @XmlElement(required = true)
    public URI getProject() {
        return project;
    }

    public void setProject(URI project) {
        this.project = project;
    }

    /**
     * The type of export group which, in turn, shall dictate
     * how masking views or storage groups will be created.
     * 
     * @valid none
     */
    // @EnumType(ExportGroupType.class)
    @XmlElement(required = true)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * The virtual array where this export is to be created.
     * 
     * @valid example: a valid URI of a varray
     */
    @XmlElement(name = "varray", required = true)
    @JsonProperty("varray")
    public URI getVarray() {
        return varray;
    }

    public void setVarray(URI varray) {
        this.varray = varray;
    }

    @XmlElementWrapper(required = false)
    /**
     * List of volume or snapshot URIs that make up this export. 
     * They must belong to the same virtual array of the export.
     * @valid none
     */
    @XmlElement(name = "volume")
    public List<VolumeParam> getVolumes() {
        if (volumes == null) {
            volumes = new ArrayList<VolumeParam>();
        }
        return volumes;
    }

    public void setVolumes(List<VolumeParam> volumes) {
        this.volumes = volumes;
    }

    // Convenience methods

    public void addVolume(URI volumeId) {
        getVolumes().add(new VolumeParam(volumeId));
    }

    public void addHost(URI hostId) {
        getHosts().add(hostId);
    }

    public void addCluster(URI clusterId) {
        getClusters().add(clusterId);
    }

    @XmlElement(name="path_parameters", required=false)
    /**
     * Optional path parameters that will over-ride the Vpool path parameters.
     * @return ExportPathParameters
     * @valid none
     */
    public ExportPathParameters getExportPathParameters() {
        return exportPathParameters;
    }

    public void setExportPathParameters(ExportPathParameters pathParam) {
        this.exportPathParameters = pathParam;
    }
}
