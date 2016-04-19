/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.ports;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.varray.VirtualArrayResourceRestRep;

@XmlRootElement(name = "storage_port")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class StoragePortRestRep extends VirtualArrayResourceRestRep {
    private String portName;
    private String portAlias;
    private String ipAddress;
    private Long tcpPortNumber;
    private String portNetworkId;
    private String portEndPointId;
    private String transportType;
    private RelatedResourceRep network;
    private RelatedResourceRep storageDevice;
    private Long portSpeed;
    private String portType;
    private String portGroup;
    private Long avgBandwidth;
    private Long staticLoad;
    private String registrationStatus;
    private String operationalStatus;
    private String compatibilityStatus;
    private Double allocationMetric;
    private Double portPercentBusy;
    private Double cpuPercentBusy;
    private Long initiatorLoad;
    private Long volumeLoad;
    private Boolean allocationDisqualified;
    private String discoveryStatus;
    private String adapterName;

    public StoragePortRestRep() {
    }

    /**
     * The adapter name of the Storage Port
     * 
     * @return Adapter name of Storage Port
     */
    @XmlElement(name = "adapter_name")
    public String getAdapterName() {
        return adapterName;
    }

    public void setAdapterName(String adapterName) {
        this.adapterName = adapterName;
    }

    /**
     * The average bandwidth through the port (Gbps)
     * 
     */
    @XmlElement(name = "avg_band_width")
    public Long getAvgBandwidth() {
        return avgBandwidth;
    }

    public void setAvgBandwidth(Long avgBandwidth) {
        this.avgBandwidth = avgBandwidth;
    }

    /**
     * The port's IP address (for IP-based transport)
     * 
     */
    @XmlElement(name = "ip_address")
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * ID of the endpoint with which this port is associated.
     * 
     */
    @XmlElement(name = "port_endpoint_id")
    public String getPortEndPointId() {
        return portEndPointId;
    }

    public void setPortEndPointId(String portEndPointId) {
        this.portEndPointId = portEndPointId;
    }

    /**
     * The name of this port's group. A port group is a mapping
     * that is configured on a storage system between a collection
     * of ports, a collection of volumes and a collection of hosts
     * that use those volumes. This name is what the storage system
     * uses to identify the port group.
     * mapped known by Vipr to this port
     * 
     */
    @XmlElement(name = "port_group")
    public String getPortGroup() {
        return portGroup;
    }

    public void setPortGroup(String portGroup) {
        this.portGroup = portGroup;
    }

    /**
     * The native name of the port. This name consists of identifiers that
     * are used by the hosting storage system. It may also follow a naming
     * convention that is in common use for that make and model of storage
     * system.
     * 
     */
    @XmlElement(name = "port_name")
    public String getPortName() {
        return portName;
    }

    public void setPortName(String portName) {
        this.portName = portName;
    }

    /**
     * The alias represents port's wwn id
     * 
     */
    @XmlElement(name = "port_alias")
    public String getPortAlias() {
        return portAlias;
    }

    public void setPortAlias(String portAlias) {
        this.portAlias = portAlias;
    }

    /**
     * The network address of the port. When Fibre-Channel (FC) is the
     * transport protocol, the address is a World Wide Name (WWN).
     * If the port is using an IP-based protocol, this is an IP address,
     * or name of the port that a storage system uses to identify the port.
     * 
     */
    @XmlElement(name = "port_network_id")
    public String getPortNetworkId() {
        return portNetworkId;
    }

    public void setPortNetworkId(String portNetworkId) {
        this.portNetworkId = portNetworkId;
    }

    /**
     * The port's clock speed (Gbps)
     * 
     */
    @XmlElement(name = "port_speed_gbps")
    public Long getPortSpeed() {
        return portSpeed;
    }

    public void setPortSpeed(Long portSpeed) {
        this.portSpeed = portSpeed;
    }

    /**
     * How the port connects its storage controller to its network
     * Valid values:
     *  backend = connects a VPLEX storage controller to another array
     *  frontend = connects the storage controller to the hosts
     */
    @XmlElement(name = "port_type")
    public String getPortType() {
        return portType;
    }

    public void setPortType(String portType) {
        this.portType = portType;
    }

    /**
     * The number of exports on the storage port
     * 
     */
    @XmlElement(name = "static_load")
    public Long getStaticLoad() {
        return staticLoad;
    }

    public void setStaticLoad(Long staticLoad) {
        this.staticLoad = staticLoad;
    }

    /**
     * The storage system that hosts this port
     * 
     */
    @XmlElement(name = "storage_system")
    public RelatedResourceRep getStorageDevice() {
        return storageDevice;
    }

    public void setStorageDevice(RelatedResourceRep storageDevice) {
        this.storageDevice = storageDevice;
    }

    /**
     * The port's TCP port number (for IP-based transport)
     * 
     */
    @XmlElement(name = "tcp_port_number")
    public Long getTcpPortNumber() {
        return tcpPortNumber;
    }

    public void setTcpPortNumber(Long tcpPortNumber) {
        this.tcpPortNumber = tcpPortNumber;
    }

    /**
     * The protocol that this port uses to transport disk commands
     * and responses across its network
     * Valid values:
     *  FC
     *  IP
     * 
     */
    @XmlElement(name = "transport_type")
    public String getTransportType() {
        return transportType;
    }

    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }

    /**
     * The ViPR network that connects to this port
     * 
     */
    @XmlElement(name = "network")
    public RelatedResourceRep getNetwork() {
        return network;
    }

    public void setNetwork(RelatedResourceRep network) {
        this.network = network;
    }

    /**
     * Whether or not this port is registered with ViPR. A
     * port must be registered before it can be managed by
     * ViPR. Valid values:
     * 	REGISTERED
     * 	UNREGISTERED
     * 
     */
    @XmlElement(name = "registration_status")
    public String getRegistrationStatus() {
        return registrationStatus;
    }

    public void setRegistrationStatus(String registrationStatus) {
        this.registrationStatus = registrationStatus;
    }

    /**
     * The operational status of the port
     * Valid values:
     *  OK
     *  NOT_OK
     *  UNKNOWN
     */
    @XmlElement(name = "operational_status")
    public String getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(String operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    /**
     * Whether or not this storage port is compatible with ViPR
     * Valid values:
     *  COMPATIBLE
     *  INCOMPATIBLE
     *  UNKNOWN
     */
    @XmlElement(name = "compatibility_status")
    public String getCompatibilityStatus() {
        return compatibilityStatus;
    }

    public void setCompatibilityStatus(String compatibilityStatus) {
        this.compatibilityStatus = compatibilityStatus;
    }

    /**
     * The metric for the Storage Port Allocator.
     * Lower metric numbers are preferred over higher metric numbers for allocation.
     */
    @XmlElement(name = "allocation_metric")
    public Double getAllocationMetric() {
        return allocationMetric;
    }

    public void setAllocationMetric(Double allocationMetric) {
        this.allocationMetric = allocationMetric;
    }

    /**
     * The port percent busy as computed by the kbytes transferred per a given
     * unit of time versus the maximum number of kbytes that could be transferred
     * as computed from the port speed.
     */
    @XmlElement(name = "port_percent_busy")
    public Double getPortPercentBusy() {
        return portPercentBusy;
    }

    public void setPortPercentBusy(Double portPercentBusy) {
        this.portPercentBusy = portPercentBusy;
    }

    /**
     * The number of non-idle ticks for the cpu that is hosting the port
     * versus the total number of possible ticks.
     */
    @XmlElement(name = "cpu_percent_busy")
    public Double getCpuPercentBusy() {
        return cpuPercentBusy;
    }

    public void setCpuPercentBusy(Double cpuPercentBusy) {
        this.cpuPercentBusy = cpuPercentBusy;
    }

    /**
     * The number of Initiators known by ViPR to be mapped to this port.
     */
    @XmlElement(name = "initiator_load")
    public Long getInitiatorLoad() {
        return initiatorLoad;
    }

    public void setInitiatorLoad(Long initiatorLoad) {
        this.initiatorLoad = initiatorLoad;
    }

    /**
     * The number of Volumes known by ViPR to be accessible via this port.
     */
    @XmlElement(name = "volume_load")
    public Long getVolumeLoad() {
        return volumeLoad;
    }

    public void setVolumeLoad(Long volumeLoad) {
        this.volumeLoad = volumeLoad;
    }

    /**
     * True if at least one of the port metrics is above its limit value.
     * This will prevent allocation of the port.
     */
    @XmlElement(name = "allocation_disqualified")
    public Boolean getAllocationDisqualified() {
        return allocationDisqualified;
    }

    public void setAllocationDisqualified(Boolean allocationDisqualified) {
        this.allocationDisqualified = allocationDisqualified;
    }

    /**
     * Whether or not this storage pool is visible in discovery
     * Valid values:
     *  VISIBLE
     *  NOTVISIBLE
     */
    @XmlElement(name = "discovery_status")
    public String getDiscoveryStatus() {
        return discoveryStatus;
    }

    public void setDiscoveryStatus(String discoveryStatus) {
        this.discoveryStatus = discoveryStatus;
    }

}
