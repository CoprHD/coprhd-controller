/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2012. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.model.valid.EnumType;

@Cf("StorageHADomain")
public class StorageHADomain extends DiscoveredDataObject {
    // storageSystem, which it belongs
    private URI _storageDeviceURI;
    // Name of the Adapter (Clariion+APM156345420001+SP_A)
    private String _haDomainName;
    // Serial Number of Adapter
    private String _serialNumber;
    // Slot Number
    private String _slotNumber;
    // Number of Ports
    private String _numberofPorts;
    // Protocol
    private String _protocol;
    // SP_A
    private String _name;

    private String adapterType;

    private StringSet _fileSharingProtocols;

    // Virtual or Physical
    private Boolean _virtual;

    // parent Domain if it is virtual
    private URI _parentDomainURI;

    // Defines the supported port types.
    public static enum HADomainType {
        FRONTEND("Front End"),
        BACKEND("Back End"),
        REMOTE("Remote"),
        VIRTUAL("Virtual"),
        UNKNOWN("N/A");

        private String haDomainType;

        private HADomainType(String haDomType) {
            haDomainType = haDomType;
        }

        public String getHaDomainType() {
            return haDomainType;
        }

        private static HADomainType[] copyValues = values();

        public static String getHADomainTypeName(String name) {
            for (HADomainType type : copyValues) {
                if (type.getHaDomainType().equalsIgnoreCase(name)) {
                    return type.name();
                }
            }
            return UNKNOWN.toString();
        }

    };

    private StringMap _metrics;

    /**********************************************
     * AlternateIDIndex - HADomainName *
     * RelationIndex - StorageDevice *
     * *
     **********************************************/

    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("storageDevice")
    public URI getStorageDeviceURI() {
        return _storageDeviceURI;
    }

    public void setStorageDeviceURI(URI storageDeviceURI) {
        _storageDeviceURI = storageDeviceURI;
        setChanged("storageDevice");
    }

    @Name("haDomainName")
    public String getName() {
        return _haDomainName;
    }

    public void setName(String haDomainName) {
        _haDomainName = haDomainName;
        setChanged("haDomainName");
    }

    public void setSerialNumber(String serialNumber) {
        _serialNumber = serialNumber;
        setChanged("serialNumber");
    }

    @Name("serialNumber")
    public String getSerialNumber() {
        return _serialNumber;
    }

    public void setSlotNumber(String slotNumber) {
        _slotNumber = slotNumber;
        setChanged("slotNumber");
    }

    @Name("slotNumber")
    public String getSlotNumber() {
        return _slotNumber;
    }

    public void setNumberofPorts(String numberofPorts) {
        _numberofPorts = numberofPorts;
        setChanged("ports");
    }

    @Name("ports")
    public String getNumberofPorts() {
        return _numberofPorts;
    }

    public void setProtocol(String protocol) {
        _protocol = protocol;
        setChanged("protocol");
    }

    @Name("protocol")
    public String getProtocol() {
        return _protocol;
    }

    public void setFileSharingProtocols(StringSet fileSharingProtocols) {
        _fileSharingProtocols = fileSharingProtocols;
        setChanged("fileSharingProtocols");
    }

    @Name("fileSharingProtocols")
    public StringSet getFileSharingProtocols() {
        return _fileSharingProtocols;
    }

    public void setAdapterName(String name) {
        this._name = name;
        setChanged("adapterName");
    }

    @Name("adapterName")
    public String getAdapterName() {
        return _name;
    }

    @EnumType(HADomainType.class)
    @Name("adapterType")
    public String getAdapterType() {
        return adapterType;
    }

    public void setAdapterType(String type) {
        this.adapterType = type;
        setChanged("adapterType");
    }

    @Name("virtual")
    public Boolean getVirtual() {
        return (_virtual != null) && _virtual;
    }

    public void setVirtual(Boolean virtual) {
        this._virtual = virtual;
        setChanged("virtual");
    }

    // @RelationIndex(cf = "RelationIndex", type = StorageHADomain.class)
    @Name("parentHADomain")
    public URI getParentHADomainURI() {
        return _parentDomainURI;
    }

    public void setParentHADomainURI(URI parentDomainURI) {
        _parentDomainURI = parentDomainURI;
        setChanged("parentHADomain");
    }

    @Name("metrics")
    public StringMap getMetrics() {
        if (_metrics == null)
            _metrics = new StringMap();
        return _metrics;
    }

    public void setMetrics(StringMap metrics) {
        this._metrics = metrics;
        setChanged("metrics");
    }

}
