/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.valid.Endpoint;

/**
 * Request POST parameter for multiple host provisioning.
 */
@XmlRootElement(name = "provision_bare_metal_hosts")
public class ProvisionBareMetalHostsParam {

    private URI tenant;
    private URI computeVpool;
    private URI cluster;
    private List<String> hostNames;

    private URI varray;

    public ProvisionBareMetalHostsParam() {
    }

    @XmlElement(name = "varray", required = true)
    @JsonProperty("varray")
    public URI getVarray() {
        return varray;
    }

    public void setVarray(URI varray) {
        this.varray = varray;
    }

    @XmlElement(name = "tenant", required = true)
    @JsonProperty("tenant")
    public URI getTenant() {
        return tenant;
    }

    public void setTenant(URI tenant) {
        this.tenant = tenant;
    }

    @XmlElement(name = "compute_vpool", required = true)
    @JsonProperty("compute_vpool")
    public URI getComputeVpool() {
        return computeVpool;
    }

    public void setComputeVpool(URI computeVpool) {
        this.computeVpool = computeVpool;
    }

    @XmlElementWrapper(name = "host_names")
    @XmlElement(name = "host_name", required = true)
    @JsonProperty("host_name")
    @Endpoint(type = Endpoint.EndpointType.HOST)
    public List<String> getHostNames() {
        if (hostNames == null) {
            hostNames = new ArrayList<String>();
        }
        return hostNames;
    }

    public void setHostNames(List<String> hostNames) {
        this.hostNames = hostNames;
    }

    @XmlElement(name = "cluster")
    @JsonProperty("cluster")
    public URI getCluster() {
        return cluster;
    }

    public void setCluster(URI cluster) {
        this.cluster = cluster;
    }

}
