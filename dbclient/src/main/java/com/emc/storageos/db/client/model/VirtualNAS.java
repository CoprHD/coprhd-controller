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
    private String vNasState;

    // storageSystem, which it belongs
    private URI storageDeviceURI;

    // Set of Authentication providers for the VNasServer - set values will of type AunthnProvider
    private StringSet cifsServers;

    // Place holder for hosting storageDomain's information
    private StringSet storageDomain;

    // place holder for the Parent NAS server the Data Mover
    private URI parentNAS;

    // List of Storage Ports associated with this VDM - contains reference to StoragePort object type.
    private StringSet ipInterfaces;

    // List of supported protocols in Virutal NAS
    private StringSet protocols;

    // the Vritual NAS server's type either Local or Domain
    private String vNasType;


    // -- Max Qualification Limits for the Virtual NAS
    
    // Number of FSID for this Virtual NAS
    private String maxFSIDs;

    // Number of exports/shares for this Virtual NAS
    private String maxExports;

    // Provisioning Capacity of this Virtual NAS
    private String maxProvisionedCapacity;

    // Placeholder for storing list of Virtual NAS servers
    private StringSet containedVirtualNASservers;


    @Name("maxFSIDs")
    public String getMaxFSIDs() {
        return maxFSIDs;
    }

    public void setMaxFSIDs(String maxFSIDs) {
        this.maxFSIDs = maxFSIDs;
        setChanged("maxFSIDs");
    }

    @Name("maxExports")
    public String getTotalExports() {
        return maxExports;
    }

    public void setMaxExports(String maxExports) {
        this.maxExports = maxExports;
        setChanged("totalExports");
    }

    @Name("maxProvisionedCapacity")
    public String getMaxProvisionedCapacity() {
        return maxProvisionedCapacity;
    }

    public void setMaxProvisionedCapacity(String provisionedCapacity) {
        this.maxProvisionedCapacity = provisionedCapacity;
        setChanged("maxProvisionedCapacity");
    }


    @EnumType(vNasState.class)
    @Name("vNasState")
    public String getvNasState() {
        return vNasState;
    }

    public void setvNasState(String vNasState) {
        this.vNasState = vNasState;
        setChanged("vNasState");
    }

    @Name("storageDomain")
    public StringSet getStorageDomain() {
        return storageDomain;
    }

    public void setStorageDomain(StringSet storageDomain) {
        this.storageDomain = storageDomain;
        setChanged("storageDomain");
    }

    @Name("protocols")
    public StringSet getProtocols() {
        return protocols;
    }

    public void setProtocols(StringSet protocols) {
        this.protocols = protocols;
        setChanged("protocols");
    }

    @Name("vNasType")
    public String getvNasType() {
        return vNasType;
    }

    public void setvNasType(String vNasType) {
        this.vNasType = vNasType;
        setChanged("vNasType");
    }

    @Name("cifsServers")
    public StringSet getCifsServers() {
        return cifsServers;
    }

    public void setCifsServers(StringSet cifsServers) {
        this.cifsServers = cifsServers;
        setChanged("cifsServers");
    }

    @Name("parentNAS")
    public URI getParentNAS() {
        return parentNAS;
    }

    public void setParentNAS(URI parentNAS) {
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
        setChanged("vNAStag");
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
    
    // Defines different States of the NAS server.
    public static enum vNasState {
        Loaded("Loded"),
        Mounted("Mounted"),
        TempUnLoaded("Temporarily-unloaded"),
        PermUnLoaded("Permanently-unloaded"),
        UNKNOWN("N/A");

        private String vNasState;

        private vNasState(String state) {
            vNasState = state;
        }

        public String getNasState() {
            return vNasState;
        }

        private static vNasState[] copyValues = values();

        public static String getNasState(String name) {
            for (vNasState type : copyValues) {
                if (type.getNasState().equalsIgnoreCase(name)) {
                    return type.name();
                }
            }
            return UNKNOWN.toString();
        }
    };

    // Defines different vNAS types.
    public static enum vNasType {
        Local("Local"),
        Domain("Domain"),
        UNKNOWN("N/A");

        private String vNasType;

        private vNasType(String state) {
            vNasType = state;
        }

        public String getNasType() {
            return vNasType;
        }

        private static vNasType[] copyValues = values();

        public static String getNasType(String name) {
            for (vNasType type : copyValues) {
                if (type.getNasType().equalsIgnoreCase(name)) {
                    return type.name();
                }
            }
            return UNKNOWN.toString();
        }
    };

}
