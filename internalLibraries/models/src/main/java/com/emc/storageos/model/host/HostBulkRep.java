/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_hosts")
public class HostBulkRep extends BulkRestRep {
    private List<HostRestRep> hosts;

    /**
     * List of host objects that exist in ViPR.
     * 
     */
    @XmlElement(name = "host")
    public List<HostRestRep> getHosts() {
        if (hosts == null) {
            hosts = new ArrayList<HostRestRep>();
        }
        return hosts;
    }

    public void setHosts(List<HostRestRep> cluster) {
        this.hosts = cluster;
    }

    public HostBulkRep() {
    }

    public HostBulkRep(List<HostRestRep> hosts) {
        this.hosts = hosts;
    }

}
