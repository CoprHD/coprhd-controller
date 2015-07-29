/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host.cluster;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Request POST parameter for cluster creation.
 */
@XmlRootElement(name = "cluster_create")
public class ClusterCreateParam extends ClusterParam {

    private String name;

    public ClusterCreateParam() {
    }

    public ClusterCreateParam(String name) {
        this.name = name;
    }

    /**
     * The name label for this cluster. It must be unique
     * within the tenant.
     * 
     * @valid none
     */
    @XmlElement(required = true)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String findName() {
        return name;
    }
}
