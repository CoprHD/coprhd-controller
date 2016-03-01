/*
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.model.valid.EnumType;

/**
 * Storage port data object, represents a port of storage device.
 * Storage port is connected to a transport zone and belongs
 * to a storage port group of a storage device.
 */
@Cf("StoragePort")
public class StoragePort extends VirtualArrayTaggedResource implements Comparable<StoragePort>, Cloneable {

    // Defines the supported port types.
    public static enum PortType {
        frontend,
        backend,
        rdf,
        Unknown
    };
    
    public static enum TransportType {
    	FC,
    	IP;
    }
    
    public static enum OperationalStatus{
        OK, NOT_OK, UNKNOWN
    };
    
    // storage port name used when communicating with the storage system
    private String _portName;

    // device native ID
    private String _nativeId;

    // storage port network identifier e.g. FC - port wwn, IP - network interface identifier
    private String _portNetworkId;

    // storage device this storage port belongs to
    private URI _storageDevice;
    //storageHADomain, to which this port belongs
    private URI _storageHADomain;

    // port type
    private String _transportType;

    // network this storage port is attached to
    private URI _network;

    // port speed
    // wherever possible, we will try and set this to Gbps, Giga bits per sec.
    private Long _portSpeed;

    // port container tag, e.g. for front-end director
    private String _portGroup;

    // average bandwidth through the port
    private Long _avgBandwidth;

    // static load (number of exports) on the storage port
    private Long _staticLoad;
    
    private String _operationalStatus = OperationalStatus.UNKNOWN.name();
    
    private Long _tcpPortNumber;
    
    private String _ipAddress;
    
    private String _endPointID;
    
    private String _portType = PortType.frontend.name();

    private String _registrationStatus = RegistrationStatus.REGISTERED.toString();
    
    //used in finding out whether or not the port is Compatible
    private String _compatibilityStatus = CompatibilityStatus.UNKNOWN.name();
    
    private String _discoveryStatus = DiscoveryStatus.VISIBLE.name();
    
    private StringMap _metrics;

    /*************************************************
     * AlternateIDIndex - portNetworkID,transportZone*
     * RelationIndex - StorageDevice                 *
     *                                               *
     ************************************************/
   
    @Name("portEndPointID")
    public String getPortEndPointID() {
        return _endPointID;
    }

    public void setPortEndPointID(String endPointID) {
        _endPointID = endPointID;
        setChanged("portEndPointID");
    }
    
    @Name("portName")
    public String getPortName() {
        return _portName;
    }

    public void setPortName(String portName) {
        _portName = portName;
        setChanged("portName");
    }

    @Name("nativeId")
    public String getNativeId() {
        return _nativeId;
    }

    public void setNativeId(String nativeId) {
        _nativeId = nativeId;
        setChanged("nativeId");
    }

    public void setTcpPortNumber(Long tcpPortNumber) {
        _tcpPortNumber = tcpPortNumber;
        setChanged("tcpPortNumber");
    }
    
    @Name("tcpPortNumber")
    public Long getTcpPortNumber() {
        return _tcpPortNumber;
    }
    
    public void setIpAddress(String ipAddress) {
        _ipAddress = ipAddress;
        setChanged("ipAddress");
    }
    
    @Name("ipAddress")
    public String getIpAddress() {
        return _ipAddress;
    }

    @AlternateId("AltIdIndex")
    @Name("portNetworkId")
    public String getPortNetworkId() {
        return _portNetworkId;
    }

    public void setPortNetworkId(String portNetworkId) {
        _portNetworkId = portNetworkId;
        setChanged("portNetworkId");
    }

    @Name("transportType")
    public String getTransportType() {
        return _transportType;
    }

    public void setTransportType(String transportType) {
        _transportType = transportType;
        setChanged("transportType");
    }

    @AlternateId("SecondaryAltIdIndex")
    @Name("network")
    public URI getNetwork() {
        return _network;
    }

    public void setNetwork(URI network) {
        _network = network;
        setChanged("network");
    }

    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("storageDevice")
    public URI getStorageDevice() {
        return _storageDevice;
    }

    public void setStorageDevice(URI storageDevice) {
        _storageDevice = storageDevice;
        setChanged("storageDevice");
    }

    @Name("portSpeed")
    public Long getPortSpeed() {
        return _portSpeed;
    }

    public void setPortSpeed(Long portSpeed) {
        _portSpeed = portSpeed;
        setChanged("portSpeed");
    }

    @Name("portGroup")
    public String getPortGroup() {
        return _portGroup;
    }

    public void setPortGroup(String portGroup) {
        _portGroup = portGroup;
        setChanged("portGroup");
    }

    @Name("avgBandwidth")
    public Long getAvgBandwidth() {
        return _avgBandwidth;
    }

    public void setAvgBandwidth(Long avgBandwidth) {
        _avgBandwidth = avgBandwidth;
        setChanged("avgBandwidth");
    }

    @Name("staticLoad")
    public Long getStaticLoad() {
        return _staticLoad;
    }

    public void setStaticLoad(Long staticLoad) {
        _staticLoad = staticLoad;
        setChanged("staticLoad");
    }

    public void setStorageHADomain(URI storageHADomain) {
        _storageHADomain = storageHADomain;
        setChanged("storageHADomain");
    }

    @RelationIndex(cf = "RelationIndex", type = StorageHADomain.class)
    @Name("storageHADomain")
    public URI getStorageHADomain() {
        return _storageHADomain;
    }
    
    @EnumType(PortType.class)
    @Name("portType")
    public String getPortType() {
        return _portType;
    }

    public void setPortType(String portType) {
        _portType = portType;
        setChanged("portType");
    }
    
    @EnumType(OperationalStatus.class)
    @Name("operationalStatus")
    public String getOperationalStatus() {
        return _operationalStatus;
    }

    public void setOperationalStatus(String operationalStatus) {
        _operationalStatus = operationalStatus;
        setChanged("operationalStatus");
    }

    public void setRegistrationStatus(String registrationStatus) {
        _registrationStatus = registrationStatus;
        setChanged("registrationStatus");
    }

    @EnumType(RegistrationStatus.class)
    @Name("registrationStatus")
    public String getRegistrationStatus() {
        return _registrationStatus;
    }
    
    @EnumType(CompatibilityStatus.class)
    @Name("compatibilityStatus")
    public String getCompatibilityStatus() {
        return _compatibilityStatus;
    }

    public void setCompatibilityStatus(String compatibilityStatus) {
        _compatibilityStatus = compatibilityStatus;
        setChanged("compatibilityStatus");
    }
    
    @Override
    public String toString() {
        StringBuilder toPrint = new StringBuilder();
        toPrint.append("portName=").append(_portName);
        toPrint.append(",portNetworkId=").append(_portNetworkId);
        toPrint.append(",storageDevice=").append(_storageDevice);
        toPrint.append(",storageHADomain=").append(_storageHADomain);
        toPrint.append(",transportType=").append(_transportType);
        toPrint.append(",network=").append(_network);
        toPrint.append(",portSpeed=").append(_portSpeed);
        toPrint.append(",portGroup=").append(_portGroup);
        toPrint.append(",avgBandwidth=").append(_avgBandwidth);
        toPrint.append(",operationalStatus=").append(_operationalStatus);
        toPrint.append(",registrationStatus=").append(_registrationStatus);
        toPrint.append(",tcpPortNumber=").append(_tcpPortNumber);
        toPrint.append(",ipAddress=").append(_ipAddress);
        toPrint.append(",endPointID=").append(_endPointID);
        toPrint.append(",portType=").append(_portType);
        return toPrint.toString();
    }

    // TODO: This should perhaps be done in a base class
    @Override
    public int compareTo(StoragePort storagePort) {
        int result = 0;
        if (this == storagePort) {
            return result;
        }
        if (getNativeGuid() == null) {
            result = -1;
        } else if (storagePort == null || storagePort.getNativeGuid() == null) {
            result = 1;
        } else {
            result = getNativeGuid().compareTo(storagePort.getNativeGuid());
        }
        return result;
    }

    // TODO: This should perhaps be done in a base class
    @Override
    public boolean equals(Object obj) {
        boolean result = false;
        if (obj instanceof StoragePort) {
            StoragePort storagePort = (StoragePort) obj;
            if (getNativeGuid() != null && storagePort.getNativeGuid() != null) {
                result = getNativeGuid().equals(storagePort.getNativeGuid());
            }
        }
        return result;
    }

    public boolean taggedToVirtualArray(URI virtualArrayURI) {
        boolean result = false;
        StringSet taggedVirtualArraysSet = getTaggedVirtualArrays();
        if (taggedVirtualArraysSet != null) {
            result = taggedVirtualArraysSet.contains(virtualArrayURI.toString());
        }
        return result;
    }
    
    // TODO: This should perhaps be done in a base class
    @Override
    public int hashCode() {
        if (getNativeGuid() != null) {
            return getNativeGuid().hashCode();
        } else {
            return 0;
        }
    }

    @Name("metrics")
    public StringMap getMetrics() {
        if (_metrics == null) {
            _metrics = new StringMap();
        }
        return _metrics;
    }

    public void setMetrics(StringMap metrics) {
        this._metrics = metrics;
        setChanged("metrics");
    }

    @EnumType(DiscoveryStatus.class)
    @Name("discoveryStatus")
    public String getDiscoveryStatus() {
        return _discoveryStatus;
    }

    public void setDiscoveryStatus(String discoveryStatus) {
        this._discoveryStatus = discoveryStatus;
        setChanged("discoveryStatus");
    }
    
    public StoragePort clone() {
        StoragePort port = null;
        try {
            port = (StoragePort) super.clone();
        } catch (Exception e) {
            // Do Nothing
        }
        return port;
    }

    /**
     * Returns a port name guaranteed to have the director identification.
     * 
     * @param port
     * @return
     */
    public String qualifiedPortName() {
        if (getPortName().startsWith(getPortGroup())) {
            return getPortName();
        } else {
            return getPortGroup() + ":" + getPortName();
        }
    }
}
