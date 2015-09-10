/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.healthmonitor;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * Represents diagnostics for each node as returned by diagtool.
 */
@XmlRootElement(name = "node_diagnostics")
public class NodeDiagnostics {

    private String nodeId;
    private String nodeName;
    private String ip;
    private List<DiagTest> diagTests;

    public NodeDiagnostics() {
        this.nodeId = HealthMonitorConstants.DIAG_UNKNOWN;
        this.nodeName = HealthMonitorConstants.DIAG_UNKNOWN;
    }

    public NodeDiagnostics(String nodeId, String nodeName, String ip, List<DiagTest> diagTests) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.ip = ip;
        this.diagTests = diagTests;
    }

    @XmlElement(name = "node_id")
    public String getNodeId() {
        return nodeId;
    }

    @XmlElement(name = "node_name")
    public String getNodeName() {
        return nodeName;
    }

    @XmlElement(name = "ip")
    public String getIp() {
        return ip;
    }

    @XmlElementWrapper(name = "tests")
    @XmlElement(name = "test")
    public List<DiagTest> getDiagTests() {
        return diagTests;
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

    public void setDiagTests(List<DiagTest> diagTests) {
        this.diagTests = diagTests;
    }
}
