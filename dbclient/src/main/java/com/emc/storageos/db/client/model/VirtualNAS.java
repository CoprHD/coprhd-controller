/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.db.client.model.StorageHADomain.HADomainType;
import com.emc.storageos.model.valid.EnumType;

/**
 * VirtualNAS Server will contain the details of NAS server depending on StorageArray type
 * eg. VDM, vFiler, vServer or AccessZone or NasServer.
 * It will hold information about the Ip interfaces, cifs Server & NFS servers mapped to NasServer
 * 
 * @author ganeso
 *
 */

@Cf("VirtualNASServer")
public class VirtualNAS extends VirtualArrayTaggedResource {

    // vNAS Server name
    private String vNASServerName;

    // Virtual or Physical
    private Boolean isVirtual;

    // Project name which this VNAS belongs to
    private String project;

    // Base directory Path for the VNAS applicable in AccessZones & vFiler device types
    private String baseDirPath;

    // Place holder for Tag
    private StringSet vNAStag;

    // State of the vNAS server
    private String nasState;

    // storageSystem, which it belongs
    private URI storageDeviceURI;

    // Set of Authentication providers for the VNasServer - set values will of type AunthnProvider
    private StringSet cifsServers;

    // place holder for the Parent NAS server the Data Mover
    private String parentNAS;

    // List of Storage Ports associated with this VDM - contains reference to StoragePort object type.
    private StringSet ipInterfaces;

    @Name("cifsServers")
    public StringSet getCifsServers() {
        return cifsServers;
    }

    public void setCifsServers(StringSet cifsServers) {
        this.cifsServers = cifsServers;
        setChanged("cifsServers");
    }

    @Name("parentNAS")
    public String getParentNAS() {
        return parentNAS;
    }

    public void setParentNAS(String parentNAS) {
        this.parentNAS = parentNAS;
        setChanged("parentNAS");
    }

    @Name("isVirtual")
    public Boolean getIsVirtual() {
        return isVirtual;
    }

    public void setIsVirtual(Boolean isVirtual) {
        this.isVirtual = isVirtual;
        setChanged("isVirtual");
    }

    @Name("vNASServerName")
    public String getvNASServerName() {
        return vNASServerName;
    }

    public void setvNAStag(StringSet vNAStag) {
        this.vNAStag = vNAStag;
        setChanged("vNASServerName");
    }

    @RelationIndex(cf = "RelationIndex", type = StoragePort.class)
    @Name("ipInterfaces")
    public StringSet getIpInterfaces() {
        return ipInterfaces;
    }

    public void setIpInterfaces(StringSet ipInterfaces) {
        this.ipInterfaces = ipInterfaces;
        setChanged("ipInterfaces");
    }

    @Name("vNAStag")
    public StringSet getvNAStag() {
        return vNAStag;
    }

    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("storageDeviceURI")
    public URI getStorageDeviceURI() {
        return storageDeviceURI;
    }

    public void setStorageDeviceURI(URI storageDeviceURI) {
        this.storageDeviceURI = storageDeviceURI;
        setChanged("storageDeviceURI");
    }

    // Defines different States of the NAS server.
    public static enum NasState {
        Loaded("Loded"),
        Mounted("Mounted"),
        TempUnLoaded("Temporarily-unloaded"),
        PermUnLoaded("Permanently-unloaded"),
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

    @EnumType(NasState.class)
    @Name("nasState")
    public String getNasState() {
        return nasState;
    }

    public void setNasState(String _nasState) {
        this.nasState = _nasState;
        setChanged("nasState");
    }

    @Name("vNASserverName")
    public String getVNASServerName() {
        return vNASServerName;
    }

    public void setvNASServerName(String _vNASServerName) {
        this.vNASServerName = _vNASServerName;
        setChanged("vNASserverName");
    }

    @RelationIndex(cf = "RelationIndex", type = Project.class)
    @Name("project")
    public String getProject() {
        return project;
    }

    public void setProject(String _project) {
        this.project = _project;
        setChanged("project");
    }

    @Name("baseDirPath")
    public String getBaseDirPath() {
        return baseDirPath;
    }

    public void setBaseDirPath(String _baseDirPath) {
        this.baseDirPath = _baseDirPath;
        setChanged("baseDirPath");
    }

    @Name("tag")
    public StringSet getVNASTag() {
        return vNAStag;
    }

    public void setVNASTag(StringSet _tag) {
        this.vNAStag = _tag;
        setChanged("vNAStag");
    }

}
