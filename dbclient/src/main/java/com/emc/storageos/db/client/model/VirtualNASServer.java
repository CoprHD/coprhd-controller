/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.db.client.model.StorageHADomain.HADomainType;
import com.emc.storageos.model.valid.EnumType;

@Cf("VirtualNASServer")
public class VirtualNASServer extends DiscoveredDataObject {

    // vNAS Server name
    private String vNASServerName;

    // Virtual or Physical
    private Boolean virtual;

    // Project name which this VNAS belongs to
    private String project;

    // Base directory Path for the VNAS applicable in AccessZones & vFiler device types
    private String baseDirPath;

    // Place holder for Tag
    private StringSet vNAStag;

    // NetWork Interface of VNas Map should contain InterfaceName, IPAddress, IP Mask, IP Broadcast Address
    private StringMap netWorkInterface;

    // State of the vNAS server
    private String nasState;

    // Technology Type of the NAS server
    private String technologyType;

    // storageSystem, which it belongs
    private URI storageDeviceURI;

    // Serial Number of Adapter
    private String serialNumber;
    // Slot Number
    private String slotNumber;
    // Number of Ports
    private String numberofPorts;
    // Protocol
    private String protocol;

    private String adapterType;

    private StringSet fileSharingProtocols;

    @Name("vNAStag")
    public StringSet getvNAStag() {
        return vNAStag;
    }

    public void setvNAStag(StringSet vNAStag) {
        this.vNAStag = vNAStag;
    }

    @Name("storageDeviceURI")
    public URI getStorageDeviceURI() {
        return storageDeviceURI;
    }

    public void setStorageDeviceURI(URI storageDeviceURI) {
        this.storageDeviceURI = storageDeviceURI;
    }

    @Name("serialNumber")
    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    @Name("slotNumber")
    public String getSlotNumber() {
        return slotNumber;
    }

    public void setSlotNumber(String slotNumber) {
        this.slotNumber = slotNumber;
    }

    @Name("numberofPorts")
    public String getNumberofPorts() {
        return numberofPorts;
    }

    public void setNumberofPorts(String numberofPorts) {
        this.numberofPorts = numberofPorts;
    }

    @Name("protocol")
    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Name("fileSharingProtocols")
    public StringSet getFileSharingProtocols() {
        return fileSharingProtocols;
    }

    public void setFileSharingProtocols(StringSet fileSharingProtocols) {
        this.fileSharingProtocols = fileSharingProtocols;
    }

    // Defines the NAS technology types.
    public static enum SupportedStorageSystemType {
        VDM("VDM"),
        vFILER("vFiler"),
        vServer("vServer"),
        AccessZone("AccessZone"),
        NasServer("NasServer"),
        UNKNOWN("N/A");

        private String supportedStorageSytemType;

        private SupportedStorageSystemType(String haDomType) {
            supportedStorageSytemType = haDomType;
        }

        public String getSupportedStorageSystemType() {
            return supportedStorageSytemType;
        }

        private static SupportedStorageSystemType[] copyValues = values();

        public static String getSupportedStorageSystemType(String name) {
            for (SupportedStorageSystemType type : copyValues) {
                if (type.getSupportedStorageSystemType().equalsIgnoreCase(name)) {
                    return type.name();
                }
            }
            return UNKNOWN.toString();
        }

    };

    // Defines diffrent States of the NAS server.
    public static enum NasState {
        VdmLoaded("Loded"),
        VdmMounted("Mounted"),
        VdmTempUnLoaded("Temporarily-unloaded"),
        VdmPermUnLoaded("Permanently-unloaded"),
        UNKNOWN("N/A");

        private String NasState;

        private NasState(String state) {
            NasState = state;
        }

        public String getNasState() {
            return NasState;
        }

        private static NasState[] copyValues = values();

        public static String getNasState(String name) {
            for (NasState type : copyValues) {
                if (type.getNasState().equalsIgnoreCase(name)) {
                    return type.name();
                }
            }
            return UNKNOWN.toString();
        }
    };

    @Name("vNASServerName")
    public String getName() {
        return vNASServerName;
    }

    public void setName(String haDomainName) {
        vNASServerName = haDomainName;
        setChanged("haDomainName");
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

    @EnumType(NasState.class)
    @Name("nasState")
    public String getNasState() {
        return nasState;
    }

    public void setNasState(String _nasState) {
        this.nasState = _nasState;
    }

    @Name("virtual")
    public Boolean getVirtual() {
        return (virtual != null) && virtual;
    }

    public void setVirtual(Boolean virtual) {
        this.virtual = virtual;
        setChanged("virtual");
    }

    @Name("vNASserverName")
    public String getVNASServerName() {
        return vNASServerName;
    }

    public void setvNASServerName(String _vNASServerName) {
        this.vNASServerName = _vNASServerName;
    }

    @Name("project")
    public String getProject() {
        return project;
    }

    public void setProject(String _project) {
        this.project = _project;
    }

    @Name("baseDirPath")
    public String getBaseDirPath() {
        return baseDirPath;
    }

    public void setBaseDirPath(String _baseDirPath) {
        this.baseDirPath = _baseDirPath;
    }

    @Name("tag")
    public StringSet getVNASTag() {
        return vNAStag;
    }

    public void setVNASTag(StringSet _tag) {
        this.vNAStag = _tag;
    }

    @Name("netWorkInterface")
    public StringMap getNetWorkInterface() {
        return netWorkInterface;
    }

    public void setNetWorkInterface(StringMap netWorkInterface) {
        this.netWorkInterface = netWorkInterface;
    }

    @Name("technologyType")
    public String getTechnologyType() {
        return technologyType;
    }

    public void setTechnologyType(String _technologyType) {
        this.technologyType = _technologyType;
    }

}
