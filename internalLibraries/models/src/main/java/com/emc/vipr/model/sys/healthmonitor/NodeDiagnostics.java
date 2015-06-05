/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
    private String ip;
    private List<DiagTest> diagTests;
    
    public NodeDiagnostics() {
        this.nodeId = HealthMonitorConstants.DIAG_UNKNOWN;
    }

    public NodeDiagnostics(String nodeId, String ip, List<DiagTest> diagTests) {
        this.nodeId = nodeId;
        this.ip = ip;
        this.diagTests = diagTests;
    }

    @XmlElement(name = "node_id")
    public String getNodeId() {
        return nodeId;
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

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setDiagTests(List<DiagTest> diagTests) {
        this.diagTests = diagTests;
    }
}
