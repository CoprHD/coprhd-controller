/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.project;

import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * VirtualNasParam will contain the list of Virtual NAS parameters assign to a project.
 * 
 * @author prasaa9
 * 
 */

@XmlRootElement(name = "vnas_servers")
public class VirtualNasParam {

    /**
     * Returned list of Virtual NAS Servers.
     * 
     * @valid none
     */
    private Set<String> vnasServers;

    public VirtualNasParam() {
    }

    public VirtualNasParam(Set<String> vnasServers) {
        this.vnasServers = vnasServers;
    }

    /**
     * List of Virtual NAS. A Virtual NAS represents a
     * virtual NAS server of a storage device.
     * 
     * @valid none
     */
    @XmlElement(name = "vnas_server")
    @JsonProperty("vnas_server")
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
