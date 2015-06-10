/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Response for getting a list of tenant hosts
 */
@XmlRootElement(name = "hosts")
public class HostList {
    private List<NamedRelatedResourceRep> hosts;

    public HostList() {}
    
    public HostList(List<NamedRelatedResourceRep> hosts) {
        this.hosts = hosts;
    }

    /**
     * List of host objects that exist in ViPR. Each   
     * host contains an id, name, and link.
     * @valid none
     */
    @XmlElement(name = "host")
    public List<NamedRelatedResourceRep> getHosts() {
        if (hosts == null) {
            hosts = new ArrayList<NamedRelatedResourceRep>();
        }
        return hosts;
    }

    public void setHosts(List<NamedRelatedResourceRep> hosts) {
        this.hosts = hosts;
    }
}
