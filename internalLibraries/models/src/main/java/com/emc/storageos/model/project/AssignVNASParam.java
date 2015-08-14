/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.project;

import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

@XmlRootElement(name = "assign_vnas_servers")
public class AssignVNASParam {

    /**
     * Returned list of Virtual NAS Servers.
     * 
     * @valid none
     */
    private Set<String> vnasServers;

    public AssignVNASParam() {
    }

    public AssignVNASParam(Set<String> vnasServers) {
        this.vnasServers = vnasServers;
    }

    /**
     * List of Virtual NAS. A Virtual NAS represents a
     * virtual NAS server of a storage device.
     * 
     * @valid none
     */
    @XmlElement(name = "vnas_servers")
    @JsonProperty("vnas_servers")
    public Set<String> getVnasServers() {
        return vnasServers;
    }

    /**
     * @param vnasServers the vnasServers to set
     */
    public void setVnasServers(Set<String> vnasServers) {
        this.vnasServers = vnasServers;
    }

}
