/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.vipr.model.sys.healthmonitor;

import com.emc.vipr.model.sys.healthmonitor.NodeHardwareInfo.NodeHardwareInfoType;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.Map;

/**
 * Rest response for diagnostics.
 */
@XmlRootElement(name = "node_hardware_info_list")
public class NodeHardwareInfoRestRep {
    private String nodeId;
    private String ip;
    private Map<NodeHardwareInfoType, Float> hardwareInfos;

    public NodeHardwareInfoRestRep() {
    }

    public NodeHardwareInfoRestRep(String nodeId, String ip, Map<NodeHardwareInfoType, Float> hardwareInfos) {
        this.nodeId = nodeId;
        this.ip = ip;
        this.hardwareInfos = hardwareInfos;
    }

    @XmlElement(name = "node_id")
    public String getNodeId() {
        return nodeId;
    }

    @XmlElement(name = "ip")
    public String getIp() {
        return ip;
    }

    @XmlElement(name = "hardware_infos")
    public Map<NodeHardwareInfoType, Float> getHardwareInfos() {
        return hardwareInfos;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setHardwareInfos(Map<NodeHardwareInfoType, Float> hardwareInfos) {
        this.hardwareInfos = hardwareInfos;
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Node ");
        stringBuilder.append(nodeId);
        stringBuilder.append(" has ");
        for (Map.Entry<NodeHardwareInfoType, Float> entry : hardwareInfos.entrySet()) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(";");
            }
            stringBuilder.append(entry.getKey());
            stringBuilder.append(": ");
            stringBuilder.append(entry.getValue());
        }
        return stringBuilder.toString();
    }
}
