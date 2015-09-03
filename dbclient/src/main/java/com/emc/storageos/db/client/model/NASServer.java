/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * VirtualNAS Server will contain the details of NAS server depending on StorageArray type
 * e.g. VDM, vFiler, vServer or AccessZone or NasServer.
 * It will hold information about the IP interfaces, CIFS Server & NFS servers mapped to NasServer
 * 
 * @author ganeso
 * 
 */

public class NASServer extends VirtualArrayTaggedResource implements Comparable<NASServer> {

    // NAS Server name
    private String nasName;

    private String nativeId;

    // storageSystem, which it belongs
    private URI storageDeviceURI;
    private StringSet protocols;

    // Set of Authentication providers for the VNasServer - set values will of type AunthnProvider
    private CifsServerMap cifsServersMap;

    // List of Storage Ports associated with this Nas Server
    private StringSet storagePorts;

    // State of the NAS server
    private String nasState;

    // Place holder for hosting storageDomain's information
    private StringSet storageDomain;

    private String registrationStatus = RegistrationStatus.REGISTERED.toString();
    private String compatibilityStatus = CompatibilityStatus.COMPATIBLE.name();
    private String discoveryStatus = DiscoveryStatus.VISIBLE.name();

    // Place holder for Tag
    private StringSet nasTag;

    private StringMap metrics;

    @Override
    public int compareTo(NASServer arg0) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Name("nasName")
    public String getNasName() {
        return nasName;
    }

    public void setNasName(String nasName) {
        this.nasName = nasName;
        setChanged("nasName");
    }

    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("storageDevice")
    public URI getStorageDeviceURI() {
        return storageDeviceURI;
    }

    public void setStorageDeviceURI(URI stroageDeviceURi) {
        this.storageDeviceURI = stroageDeviceURi;
        setChanged("storageDevice");
    }

    @Name("nativeId")
    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    @Name("protocols")
    public StringSet getProtocols() {
        return protocols;
    }

    public void setProtocols(StringSet protocols) {
        this.protocols = protocols;
        setChanged("protocols");
    }

    @Name("cifsServers")
    public CifsServerMap getCifsServersMap() {
        return cifsServersMap;
    }

    public void setCifsServersMap(CifsServerMap cifsServersMap) {
        this.cifsServersMap = cifsServersMap;
    }

    @Name("storagePorts")
    @AlternateId("AssignedPortsAltIdIndex")
    public StringSet getStoragePorts() {
        if (storagePorts == null) {
            storagePorts = new StringSet();
        }
        return storagePorts;
    }

    public void setStoragePorts(StringSet storagePorts) {
        this.storagePorts = storagePorts;
        setChanged("storagePorts");
    }

    @Name("nasState")
    public String getNasState() {
        return nasState;
    }

    public void setNasState(String nasState) {
        this.nasState = nasState;
        setChanged("nasState");
    }

    @Name("storageDomain")
    public StringSet getStorageDomain() {
        return storageDomain;
    }

    public void setStorageDomain(StringSet storageDomain) {
        this.storageDomain = storageDomain;
        setChanged("storageDomain");
    }

    @Name("registrationStatus")
    public String getRegistrationStatus() {
        return registrationStatus;
    }

    public void setRegistrationStatus(String registrationStatus) {
        this.registrationStatus = registrationStatus;
        setChanged("registrationStatus");
    }

    @Name("compatibilityStatus")
    public String getCompatibilityStatus() {
        return compatibilityStatus;
    }

    public void setCompatibilityStatus(String compatibilityStatus) {
        this.compatibilityStatus = compatibilityStatus;
        setChanged("compatibilityStatus");
    }

    @Name("discoveryStatus")
    public String getDiscoveryStatus() {
        return discoveryStatus;
    }

    public void setDiscoveryStatus(String discoveryStatus) {
        this.discoveryStatus = discoveryStatus;
        setChanged("discoveryStatus");
    }

    @Name("metrics")
    public StringMap getMetrics() {
        if (metrics == null) {
            metrics = new StringMap();
        }
        return metrics;
    }

    public void setMetrics(StringMap metrics) {
        this.metrics = metrics;
        setChanged("metrics");
    }

    @Name("nasTag")
    public StringSet getNAStag() {
        return nasTag;
    }

    public void setNAStag(StringSet vNAStag) {
        this.nasTag = vNAStag;
        setChanged("nasTag");
    }

}
