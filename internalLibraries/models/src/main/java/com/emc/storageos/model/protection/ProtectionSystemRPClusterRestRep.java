/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.protection;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "cluster")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ProtectionSystemRPClusterRestRep {
    private String clusterId;
    private String clusterName;
    private List<String> assignedVarrays;

    public ProtectionSystemRPClusterRestRep() {
    }

    @XmlElement(name = "cluster_name")
    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    @XmlElement(name = "cluster_id")
    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    @XmlElement(name = "assigned_virtual_array")
    public List<String> getAssignedVarrays() {
        return assignedVarrays;
    }

    public void setAssignedVarrays(List<String> assignedVarrays) {
        this.assignedVarrays = assignedVarrays;
    }

}
