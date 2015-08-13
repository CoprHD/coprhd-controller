/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
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

public class NASServer extends VirtualArrayTaggedResource implements Comparable <NASServer>  {

    // NAS Server name
    private String nasName;
    
    
    // storageSystem, which it belongs
    private URI storageDeviceURI;
    private String maxFSID="0";
    private String maxExports="0";
    private String maxProvisionedCapacity="0";
    private StringSet protocols;
    
    // Set of Authentication providers for the VNasServer - set values will of type AunthnProvider
    private StringSet cifsServers;
    
    // List of Storage Ports associated with this Nas Server
    private StringSet storagePorts;
    
    // State of the NAS server
    private String nasState;
    
    
    // Place holder for hosting storageDomain's information
    private StringSet storageDomain;
    
    private String registrationStatus = RegistrationStatus.REGISTERED.toString();
    private String compatibilityStatus = CompatibilityStatus.UNKNOWN.name();
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

    @Name("storageDeviceURI")
    public URI getStorageDeviceURI() {
        return storageDeviceURI;
    }

    public void setStorageDeviceURI(URI stroageDeviceURi) {
        this.storageDeviceURI = stroageDeviceURi;
        setChanged("stroageDeviceURI");
    }

    @Name("maxFSID")
    public String getMaxFSID() {
        return maxFSID;
    }

    public void setMaxFSID(String maxFSID) {
        this.maxFSID = maxFSID;
        setChanged("this.maxFSID");
    }

    @Name("maxExports")
    public String getMaxExports() {
        return maxExports;
    }

    public void setMaxExports(String maxExports) {
        this.maxExports = maxExports;
        setChanged("this.maxExports");
    }

    @Name("maxProvisionedCapacity")
    public String getMaxProvisionedCapacity() {
        return maxProvisionedCapacity;
    }

    public void setMaxProvisionedCapacity(String maxProvisionedCapacity) {
        this.maxProvisionedCapacity = maxProvisionedCapacity;
        setChanged("this.maxProvisionedCapacity");
    }

    @Name("protocols")
    public StringSet getProtocols() {
        return protocols;
    }

    public void setProtocols(StringSet protocols) {
        this.protocols = protocols;
        setChanged("this.protocols");
    }

    @Name("cifsServers")
    public StringSet getCifsServers() {
        return cifsServers;
    }

    public void setCifsServers(StringSet cifsServers) {
        this.cifsServers = cifsServers;
        setChanged("this.cifsServers");
    }

    @Name("storagePorts")
    public StringSet getStoragePorts() {
        return storagePorts;
    }

    public void setStoragePorts(StringSet storagePorts) {
        this.storagePorts = storagePorts;
        setChanged("storagePorts");
    }

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
        setChanged("StorageDomain");
    }

    @Name("registrationStatus")
    public String getRegistrationStatus() {
        return registrationStatus;
    }

    public void setRegistrationStatus(String _registrationStatus) {
        this.registrationStatus = _registrationStatus;
        setChanged("registrationStatus");
    }

    @Name("compatibilityStatus")
    public String getCompatibilityStatus() {
        return compatibilityStatus;
    }

    public void setCompatibilityStatus(String _compatibilityStatus) {
        this.compatibilityStatus = _compatibilityStatus;
        setChanged("compatibilityStatus");
    }

    @Name("discoveryStatus")
    public String getDiscoveryStatus() {
        return discoveryStatus;
    }

    public void setDiscoveryStatus(String _discoveryStatus) {
        this.discoveryStatus = _discoveryStatus;
        setChanged("discoveryStatus");
    }

    
    @Name("metrics")
    public StringMap getMetrics() {
        return metrics;
    }

    public void setMetrics(StringMap _metrics) {
        this.metrics = _metrics;
        setChanged("metrics");
    }

    
    @Name("storageDeviceURI")
    public URI getStroageDeviceURI() {
        return storageDeviceURI;
    }

    public void setStroageDeviceURI(URI stroageDeviceURI) {
        this.storageDeviceURI = stroageDeviceURI;
        setChanged("stroageDeviceURI");
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

