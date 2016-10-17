/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.ipsec;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement(name = "ipsec_status")
public class IPsecStatus {

    private String status;
    private List<String> disconnectedNodes;
    private String updatedTime;

    @XmlElement(name = "version")
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    private String version;

    @XmlElement(name = "status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @XmlElementWrapper(name = "disconnected_nodes")
    @XmlElement(name = "ip")
    public List<String> getDisconnectedNodes() {
        return disconnectedNodes;
    }

    public void setDisconnectedNodes(List<String> nodes) {
        this.disconnectedNodes = nodes;
    }

    @XmlElement(name = "update_time")
    public String getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(String updatedTime) {
        this.updatedTime = updatedTime;
    }
}
