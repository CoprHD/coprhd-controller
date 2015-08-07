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

@Cf("PhysicalNAS")
public class PhysicalNAS extends DiscoveredDataObject {

    // vNAS Server name
    private String pNASServerName;

    // State of the NAS server
    // can be "passive" or "active"
    private String nasState;

    // storageSystem, which it belongs
    private URI storageDeviceURI;

    // Set of Authentication providers for the VNasServer - set values will of type AunthnProvider
    private StringSet cifsServers;

    // Place holder for hosting storageDomain's information
    private StringSet storageDomain;

    // List of Storage Ports associated with this VDM - contains reference to StoragePort object type.
    private StringSet ipInterfaces;

    // -- Max Qualification Limits for the Physical NAS
    
    // Number of FSID for this Physical Data Mover
    private String maxFSIDs;

    // Number of exports/shares for this Physical NAS
    private String maxExports;

    // Provisioning Capacity of this Physical NAS
    private String maxProvisionedCapacity;

    // Placeholder for storing list of Physical NAS servers
    private StringSet containedVirtualNASservers;

    // Protocols for the Physical NAS
    private StringSet protocols;

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

    @Name("protocols")
    public StringSet getProtocols() {
        return protocols;
    }

    public void setProtocols(StringSet protocols) {
        this.protocols = protocols;
        setChanged("protocols");
    }

    @Name("pNASServerName")
    public String getpNASServerName() {
        return pNASServerName;
    }

    public void setpNASServerName(String pNASServerName) {
        this.pNASServerName = pNASServerName;
        setChanged("pNASServerName");
    }

    @Name("containedVirtualNASServers")
    public StringSet getContainedVirtualNASservers() {
        return containedVirtualNASservers;
    }

    public void setContainedVirtualNASservers(StringSet containedVirtualNASservers) {
        this.containedVirtualNASservers = containedVirtualNASservers;
        setChanged("containedVirtualNASservers");
    }

    @Name("storageDomain")
    public StringSet getStorageDomain() {
        return storageDomain;
    }

    public void setStorageDomain(StringSet storageDomain) {
        this.storageDomain = storageDomain;
        setChanged("storageDomain");
    }

    @Name("cifsServers")
    public StringSet getCifsServers() {
        return cifsServers;
    }

    public void setCifsServers(StringSet cifsServers) {
        this.cifsServers = cifsServers;
        setChanged("cifsServers");
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
        Active("Active"),
        Passive("Passive"),
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

    @EnumType(NasState.class)
    @Name("nasState")
    public String getNasState() {
        return nasState;
    }

    public void setNasState(String _nasState) {
        this.nasState = _nasState;
        setChanged("nasState");
    }

    @Name("pNASserverName")
    public String getPNASServerName() {
        return pNASServerName;
    }

    public void setPNASServerName(String _pNASServerName) {
        this.pNASServerName = _pNASServerName;
        setChanged("pNASserverName");
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
