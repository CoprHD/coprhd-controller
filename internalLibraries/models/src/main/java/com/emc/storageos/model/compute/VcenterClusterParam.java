/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.compute;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Vcenter Cluster export params
 */
@XmlRootElement(name = "cluster")
public class VcenterClusterParam {

    private URI id;
    private List<URI> addHosts;
    private List<URI> removeHosts;

    public VcenterClusterParam() {}

    public VcenterClusterParam(URI id) {
        this.id = id;
    }
    
    /**
     * Cluster to be exported to vCenter.
     */     
    @XmlElement(required = true)
    public URI getId() {
        return id;
    }

    public void setId(URI id) {
        this.id = id;
    }

    /**
     * List of host IDs to add
     */
    @XmlElementWrapper(name = "add_hosts")
    @XmlElement(name = "add_host")
    public List<URI> getAddHosts() {
        if (addHosts == null) {
            addHosts = new ArrayList<URI>();
        }
        return addHosts;
    }

    public void setAddHosts(List<URI> addHosts) {
        this.addHosts = addHosts;
    }

    /**
     * List of host IDs to remove
     */
    @XmlElementWrapper(name = "remove_hosts")
    @XmlElement(name = "remove_host")
    public List<URI> getRemoveHosts() {
        if (removeHosts == null) {
            removeHosts = new ArrayList<URI>();
        }
        return removeHosts;
    }

    public void setRemoveHosts(List<URI> removeHosts) {
        this.removeHosts = removeHosts;
    }

}
