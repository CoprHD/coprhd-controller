/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host.cluster;

import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonProperty;

import java.net.URI;

/**
 * Captures POST/PUT data for a cluster.
 */
public abstract class ClusterParam {

    private URI VcenterDataCenter;
    private URI project;
    
    public ClusterParam() {}
    
    public ClusterParam(URI VcenterDataCenter, URI project) {
        this.VcenterDataCenter = VcenterDataCenter;
        this.project = project;
    }

    /** 
     * The name of data center in vCenter for an ESX cluster. 
     * 
     * @valid none
     */
    @XmlElement(name = "vcenter_data_center")
    @JsonProperty("vcenter_data_center")
    public URI getVcenterDataCenter() {
        return VcenterDataCenter;
    }

    public void setVcenterDataCenter(URI vcenterDataCenter) {
        VcenterDataCenter = vcenterDataCenter;
    }

    /** 
     * This field is currently not used. Any values passed into it will be ignored. 
     * 
     * @valid none
     */
    @XmlElement()
    public URI getProject() {
        return project;
    }

    public void setProject(URI project) {
        this.project = project;
    }

    /** Gets the cluster name */
    public abstract String findName();
}
