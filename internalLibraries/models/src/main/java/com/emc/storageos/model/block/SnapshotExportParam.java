/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Snapshot export parameters
 */

@XmlRootElement(name = "snapshot_export")
public class SnapshotExportParam {

    private String protocol;
    private String initiatorPort;
    private String initiatorNode;
    private int lun;
    private String hostid;

    public SnapshotExportParam() {
    }

    public SnapshotExportParam(String protocol, String initiatorPort,
            String initiatorNode, int lun, String hostid) {
        this.protocol = protocol;
        this.initiatorPort = initiatorPort;
        this.initiatorNode = initiatorNode;
        this.lun = lun;
        this.hostid = hostid;
    }

    /**
     * Protocol
     * 
     * @valid FC = Fibre Channel
     * @valid iSCSI
     */
    @XmlElement(required = true)
    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * Initiator port address (WWPN)
     * 
     * @valid none
     */
    @XmlElement(required = true, name = "initiator_port")
    public String getInitiatorPort() {
        return initiatorPort;
    }

    public void setInitiatorPort(String initiatorPort) {
        this.initiatorPort = initiatorPort;
    }

    /**
     * Initiator node address (WWNN)
     * 
     * @valid none
     */
    @XmlElement(name = "initiator_node")
    public String getInitiatorNode() {
        return initiatorNode;
    }

    public void setInitiatorNode(String initiatorNode) {
        this.initiatorNode = initiatorNode;
    }

    /**
     * LUN identifier
     * 
     * @valid none
     */
    @XmlElement(required = true)
    public int getLun() {
        return lun;
    }

    public void setLun(int lun) {
        this.lun = lun;
    }

    /**
     * IP address or Fully Qualified Domain Name of host
     * 
     * @valid none
     */
    @XmlElement(name = "host_id")
    public String getHostid() {
        return hostid;
    }

    public void setHostid(String hostid) {
        this.hostid = hostid;
    }

}
