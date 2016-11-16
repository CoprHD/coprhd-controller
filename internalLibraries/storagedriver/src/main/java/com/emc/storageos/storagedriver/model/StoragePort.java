/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model;

import java.util.List;

import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;

public class StoragePort extends StorageObject {

    // Defines the supported port types.
    public enum PortType {
        frontend,
        backend,
        Unknown
    };

    public enum TransportType {
        FC,         // fibre channel networks
        IP,         // IP networks for iSCSI, NFS, CIFS
        Ethernet,   // Ethernet networks for FCoE
        ScaleIO,    // ScaleIO Data Clients
        Ceph,       // Ceph Data Clients
    }

    public static enum OperationalStatus {
        OK, NOT_OK, UNKNOWN
    };

    // storage port name
    private String portName;

    // storage port network identifier e.g. FC - port wwn, IP - network interface identifier
    private String portNetworkId;

    // storage device this storage port belongs to
    private String storageSystemId;

    // port type
    private TransportType transportType;

    // network this storage port is attached to
    private String networkId;

    // port speed set this to Gbps (Giga bits per sec).
    private Long portSpeed;

    // port container tag, e.g. for front-end director
    private String portGroup;

    // port container subgroup tag
    private String portSubGroup;

    // port failure domain name (high availability zone name)
    private String portHAZone;

    // average bandwidth through the port
    private Long avgBandwidth;

    // indicates utilization of the port 0-100%
    private Double utilizationMetric;

    private OperationalStatus operationalStatus = OperationalStatus.UNKNOWN;

    private Long tcpPortNumber;

    private String ipAddress;

    // port end point id
    private String endPointID;

    private PortType portType = PortType.frontend;

    private List<CapabilityInstance> capabilities;

    public String getPortName() {
        return portName;
    }

    public void setPortName(String portName) {
        this.portName = portName;
    }

    public String getPortNetworkId() {
        return portNetworkId;
    }

    public void setPortNetworkId(String portNetworkId) {
        this.portNetworkId = portNetworkId;
    }

    public String getStorageSystemId() {
        return storageSystemId;
    }

    public void setStorageSystemId(String storageSystemId) {
        this.storageSystemId = storageSystemId;
    }

    public String getTransportType() {
        return transportType.name();
    }

    public void setTransportType(TransportType transportType) {
        this.transportType = transportType;
    }

    public String getNetworkId() {
        return networkId;
    }

    public void setNetworkId(String networkId) {
        this.networkId = networkId;
    }

    public Long getPortSpeed() {
        return portSpeed;
    }

    public void setPortSpeed(Long portSpeed) {
        this.portSpeed = portSpeed;
    }

    public String getPortGroup() {
        return portGroup;
    }

    public void setPortGroup(String portGroup) {
        this.portGroup = portGroup;
    }

    public String getPortSubGroup() {
        return portSubGroup;
    }

    public void setPortSubGroup(String portSubGroup) {
        this.portSubGroup = portSubGroup;
    }

    public String getPortHAZone() {
        return portHAZone;
    }

    public void setPortHAZone(String portHAZone) {
        this.portHAZone = portHAZone;
    }

    public Long getAvgBandwidth() {
        return avgBandwidth;
    }

    public void setAvgBandwidth(Long avgBandwidth) {
        this.avgBandwidth = avgBandwidth;
    }

    public Double getUtilizationMetric() {
        return utilizationMetric;
    }

    public void setUtilizationMetric(Double usageMetric) {
        this.utilizationMetric = usageMetric;
    }

    public String getOperationalStatus() {
        return operationalStatus.name();
    }

    public void setOperationalStatus(OperationalStatus operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public Long getTcpPortNumber() {
        return tcpPortNumber;
    }

    public void setTcpPortNumber(Long tcpPortNumber) {
        this.tcpPortNumber = tcpPortNumber;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getEndPointID() {
        return endPointID;
    }

    public void setEndPointID(String endPointID) {
        this.endPointID = endPointID;
    }

    public String getPortType() {
        return portType.name();
    }

    public void setPortType(PortType portType) {
        this.portType = portType;
    }

    public List<CapabilityInstance> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<CapabilityInstance> capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public boolean equals(Object port) {
        if (port != null && (port instanceof StoragePort) && storageSystemId.equals(((StoragePort)port).getStorageSystemId()) &&
                getNativeId().equals(((StoragePort) port).getNativeId())) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        return "StoragePort_"+storageSystemId+"---"+getNativeId();
    }

}
