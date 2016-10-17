/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Hosts input param for Host/Array affinity Discovery
 */
@XmlRootElement(name = "discover_host_array_affinity")
public class ArrayAffinityHostParam {

    private List<URI> hosts;

    public ArrayAffinityHostParam() {
    }

    public ArrayAffinityHostParam(List<URI> hosts) {
        this.hosts = hosts;
    }

    @XmlElementWrapper(required = true, name = "hosts")
    /**
     * List of Host Ids.
     *
     * Example: list of valid URIs
     */
    @XmlElement(required = true, name = "host")
    public List<URI> getHosts() {
        if (hosts == null) {
            hosts = new ArrayList<URI>();
        }
        return hosts;
    }

    public void setHosts(List<URI> hosts) {
        this.hosts = hosts;
    }
}
