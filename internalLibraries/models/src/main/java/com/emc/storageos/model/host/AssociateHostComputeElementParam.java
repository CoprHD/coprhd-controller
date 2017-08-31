/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

@XmlRootElement(name = "associate_host_compute_element")
public class AssociateHostComputeElementParam {

    private URI computeSystemId;
    private URI computeElementId;
    private URI computeVPoolId;

    public AssociateHostComputeElementParam(){
    }

    /**
     * @return computeVPoolId
     */
    @XmlElement(name = "compute_vpool")
    @JsonProperty("compute_vpool")
    public URI getComputeVPoolId() {
        return computeVPoolId;
    }

    public void setComputeVPoolId(URI computeVPoolId) {
        this.computeVPoolId = computeVPoolId;
    }

    /**
     * @return computeElementId
     */
    @XmlElement(name = "compute_element")
    @JsonProperty("compute_element")
    public URI getComputeElementId() {
        return computeElementId;
    }

    public void setComputeElementId(URI computeElementId) {
        this.computeElementId = computeElementId;
    }

    /**
     * @return computeSystemId
     */
    @XmlElement(name = "compute_system")
    @JsonProperty("compute_system")
    public URI getComputeSystemId() {
        return computeSystemId;
    }

    public void setComputeSystemId(URI computeSystemId) {
        this.computeSystemId = computeSystemId;
    }
}
