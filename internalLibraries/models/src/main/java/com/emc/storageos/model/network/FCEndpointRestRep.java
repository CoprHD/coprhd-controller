/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

public class FCEndpointRestRep extends DataObjectRestRep {
    private String fabricId;
    private String fabricWwn;
    private String switchName;
    private String switchInterface;
    private String switchPortName;
    private String fcid;
    private String remoteNodeName;
    private String remotePortName;
    private String remotePortAlias;
    private RelatedResourceRep networkDevice;

    public FCEndpointRestRep() {
    }

    public FCEndpointRestRep(String fabricId, String fabricWwn,
            String switchName, String switchInterface, String switchPortName,
            String fcid, String remoteNodeName, String remotePortName,
            RelatedResourceRep networkDevice) {
        this.fabricId = fabricId;
        this.fabricWwn = fabricWwn;
        this.switchName = switchName;
        this.switchInterface = switchInterface;
        this.switchPortName = switchPortName;
        this.fcid = fcid;
        this.remoteNodeName = remoteNodeName;
        this.remotePortName = remotePortName;
        this.networkDevice = networkDevice;
    }

    /**
     * The VSAN (Virtual Storage Area Network) ID.
     * 
     */
    @XmlElement(name = "fabric_id")
    public String getFabricId() {
        return fabricId;
    }

    public void setFabricId(String fabricId) {
        this.fabricId = fabricId;
    }

    /**
     * The WWN (World Wide Name) of the VSAN (Virtual Storage Area Network).
     * 
     */
    @XmlElement(name = "fabric_wwn")
    public String getFabricWwn() {
        return fabricWwn;
    }

    public void setFabricWwn(String fabricWwn) {
        this.fabricWwn = fabricWwn;
    }

    /**
     * The FC (Fibre Channel) ID.
     * 
     */
    @XmlElement(name = "fcid")
    public String getFcid() {
        return fcid;
    }

    public void setFcid(String fcid) {
        this.fcid = fcid;
    }

    /**
     * The parent FC (Fibre Channel) switch where the port was discovered.
     * 
     */
    @XmlElement
    public RelatedResourceRep getNetworkDevice() {
        return networkDevice;
    }

    public void setNetworkDevice(RelatedResourceRep networkDevice) {
        this.networkDevice = networkDevice;
    }

    /**
     * The name of the remote node of the connection (WWNN).
     * 
     */
    @XmlElement(name = "remote_node_name")
    public String getRemoteNodeName() {
        return remoteNodeName;
    }

    public void setRemoteNodeName(String remoteNodeName) {
        this.remoteNodeName = remoteNodeName;
    }

    /**
     * The name of the remote port of the connection (WWPN).
     * 
     */
    @XmlElement(name = "remote_port_name")
    public String getRemotePortName() {
        return remotePortName;
    }

    public void setRemotePortName(String remotePortName) {
        this.remotePortName = remotePortName;
    }

    /**
     * The alias of the remote port of the connection
     * 
     */
    @XmlElement(name = "remote_port_alias")
    public String getRemotePortAlias() {
        return remotePortAlias;
    }

    public void setRemotePortAlias(String remotePortAlias) {
        this.remotePortAlias = remotePortAlias;
    }

    /**
     * The name of the switch (local) interface of the port.
     * 
     */
    @XmlElement(name = "switch_interface")
    public String getSwitchInterface() {
        return switchInterface;
    }

    public void setSwitchInterface(String switchInterface) {
        this.switchInterface = switchInterface;
    }

    /**
     * The hostname of the switch the port is in.
     * 
     */
    @XmlElement(name = "switch_name")
    public String getSwitchName() {
        return switchName;
    }

    public void setSwitchName(String switchName) {
        this.switchName = switchName;
    }

    /**
     * The name of the local port.
     * 
     */
    @XmlElement(name = "switch_port_name")
    public String getSwitchPortName() {
        return switchPortName;
    }

    public void setSwitchPortName(String switchPortName) {
        this.switchPortName = switchPortName;
    }
}
