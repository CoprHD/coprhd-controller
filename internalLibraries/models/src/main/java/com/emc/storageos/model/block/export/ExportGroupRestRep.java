/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import org.codehaus.jackson.annotate.JsonProperty;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "block_export")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ExportGroupRestRep extends DataObjectRestRep {
    private List<ExportBlockParam> volumes;
    private List<InitiatorRestRep> initiators;
    private List<HostRestRep> hosts;
    private List<ClusterRestRep> clusters;
    private RelatedResourceRep project;
    private RelatedResourceRep tenant;
    private RelatedResourceRep virtualArray;
    private String generatedName;
    private String type;
    private List<ExportPathParametersRep> pathParams;

    /**
     * Name of the block export.
     * 
     * @valid none
     */
    @XmlElement(name = "generated_name")
    public String getGeneratedName() {
        return generatedName;
    }

    public void setGeneratedName(String generatedName) {
        this.generatedName = generatedName;
    }

    @XmlElementWrapper
    /**
     * List of initiators in the block export.
     * @valid none
     */
    @XmlElement(name = "initiator")
    public List<InitiatorRestRep> getInitiators() {
        if (initiators == null) {
            initiators = new ArrayList<InitiatorRestRep>();
        }
        return initiators;
    }

    public void setInitiators(List<InitiatorRestRep> initiators) {
        this.initiators = initiators;
    }

    /**
     * Virtual array of the block export.
     * 
     * @valid none
     */
    @XmlElement(name = "varray")
    @JsonProperty("varray")
    public RelatedResourceRep getVirtualArray() {
        return virtualArray;
    }

    public void setVirtualArray(RelatedResourceRep virtualArray) {
        this.virtualArray = virtualArray;
    }

    /**
     * Project of the block export.
     * 
     * @valid none
     */
    @XmlElement
    public RelatedResourceRep getProject() {
        return project;
    }

    public void setProject(RelatedResourceRep project) {
        this.project = project;
    }

    /**
     * Tenant of the block export.
     * 
     * @valid none
     */
    @XmlElement
    public RelatedResourceRep getTenant() {
        return tenant;
    }

    public void setTenant(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }

    @XmlElementWrapper
    /**
     * List of volumes in the block export.
     * @valid none
     */
    @XmlElement(name = "volume")
    public List<ExportBlockParam> getVolumes() {
        if (volumes == null) {
            volumes = new ArrayList<ExportBlockParam>();
        }
        return volumes;
    }

    public void setVolumes(List<ExportBlockParam> volumes) {
        this.volumes = volumes;
    }

    /**
     * Type of the block export.
     * 
     * @valid Host
     * @valid Cluster
     * @valid Initiator
     */
    @XmlElement(name = "type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @XmlElementWrapper
    /**
     * List of hosts in the block export.
     * @valid none
     */
    @XmlElement(name = "host")
    public List<HostRestRep> getHosts() {
        if (hosts == null) {
            hosts = new ArrayList<HostRestRep>();
        }
        return hosts;
    }

    public void setHosts(List<HostRestRep> hosts) {
        this.hosts = hosts;
    }

    @XmlElementWrapper
    /**
     * List of clusters in the block export.
     * @valid none
     */
    @XmlElement(name = "cluster")
    public List<ClusterRestRep> getClusters() {
        if (clusters == null) {
            clusters = new ArrayList<ClusterRestRep>();
        }
        return clusters;
    }

    public void setClusters(List<ClusterRestRep> clusters) {
        this.clusters = clusters;
    }

    @XmlElementWrapper(name = "path_params", required = false)
    @XmlElement(name = "path_param")
    public List<ExportPathParametersRep> getPathParams() {
        if (pathParams == null) {
            pathParams = new ArrayList<ExportPathParametersRep>();
        }
        return pathParams;
    }

    public void setPathParams(List<ExportPathParametersRep> pathParams) {
        this.pathParams = pathParams;
    }
}
