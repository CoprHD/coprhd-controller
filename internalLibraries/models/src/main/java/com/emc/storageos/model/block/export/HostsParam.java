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

/**
 * Create parameter for host
 */
public class HostsParam {

    private List<URI> hosts;

    public HostsParam() {
    }

    public HostsParam(List<URI> hosts) {
        this.hosts = hosts;
    }

    @XmlElementWrapper(required = false)
    /**
     * List of host URIs.
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

}
