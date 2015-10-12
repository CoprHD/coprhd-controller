package com.emc.storageos.storagedriver.model;

import com.emc.storageos.storagedriver.CapabilityInstance;

import java.util.List;

public class StoragePort extends StorageObject {

    // Defines the supported port types.
    public static enum PortType {
        frontend,
        backend,
        Unknown
    };

    public static enum TransportType {
        FC,
        IP;
    }

    public static enum OperationalStatus {
        OK, NOT_OK, UNKNOWN
    };

    // storage port name
    private String portName;

    // storage port network identifier e.g. FC - port wwn, IP - network interface identifier
    private String portNetworkId;

    // storage device this storage port belongs to
    private String storageDeviceId;

    // port type
    private String transportType;

    // network this storage port is attached to
    private String networkId;

    // port speed set this to Gbps (Giga bits per sec).
    private Long portSpeed;

    // port container tag, e.g. for front-end director
    private String portGroup;

    // port container subgroup tag
    private String portSubGroup;

    // average bandwidth through the port
    private Long avgBandwidth;

    // indicates utilization of the port
    private Long usageMetric;

    private String operationalStatus = OperationalStatus.UNKNOWN.name();

    private Long tcpPortNumber;

    private String ipAddress;

    // port end point id
    private String endPointID;

    private String portType = PortType.frontend.name();

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

    public String getStorageDeviceId() {
        return storageDeviceId;
    }

    public void setStorageDeviceId(String storageDeviceId) {
        this.storageDeviceId = storageDeviceId;
    }

    public String getTransportType() {
        return transportType;
    }

    public void setTransportType(String transportType) {
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

    public Long getAvgBandwidth() {
        return avgBandwidth;
    }

    public void setAvgBandwidth(Long avgBandwidth) {
        this.avgBandwidth = avgBandwidth;
    }

    public Long getUsageMetric() {
        return usageMetric;
    }

    public void setUsageMetric(Long usageMetric) {
        this.usageMetric = usageMetric;
    }

    public String getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(String operationalStatus) {
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
        return portType;
    }

    public void setPortType(String portType) {
        this.portType = portType;
    }

    public List<CapabilityInstance> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<CapabilityInstance> capabilities) {
        this.capabilities = capabilities;
    }
}
