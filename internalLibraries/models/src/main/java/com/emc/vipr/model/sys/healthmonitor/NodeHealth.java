/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.healthmonitor;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents node health
 */
@XmlRootElement(name = "node_health")
public class NodeHealth {

    private String nodeId;
    private String nodeName;
    private String ip;
    private String status;
    private List<ServiceHealth> serviceHealthList;

    // Default constructor for JAXB
    public NodeHealth() {
        this.nodeId = HealthMonitorConstants.UNKNOWN;
    }

    public NodeHealth(String nodeId, String nodeName, String ip, String status) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.ip = ip;
        this.status = status;
    }

    public NodeHealth(String nodeId, String nodeName, String ip, String status,
                      List<ServiceHealth> serviceHealthList) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.ip = ip;
        this.status = status;
        this.serviceHealthList = serviceHealthList;
    }

    @XmlElement(name = "node_id")
    public String getNodeId() {
        return nodeId;
    }

    @XmlElement(name = "node_name")
    public String getNodeName() {
        return nodeName;
    }

    @XmlElement(name = "status")
    public String getStatus() {
        return status;
    }

    @XmlElementWrapper(name = "service_health_list")
    @XmlElement(name = "service_health")
    public List<ServiceHealth> getServiceHealthList() {
        if (serviceHealthList == null) {
            serviceHealthList = new ArrayList<ServiceHealth>();
        }
        return serviceHealthList;
    }

    @XmlElement(name = "ip")
    public String getIp() {
        return ip;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setServiceHealthList(List<ServiceHealth> serviceHealthList) {
        this.serviceHealthList = serviceHealthList;
    }
}
