/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.vnas;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.varray.VirtualArrayResourceRestRep;

/**
 * Virtual NAS server and returned as a response to a REST request.
 * 
 * @author prasaa9
 * 
 */

@XmlRootElement(name = "virtual_nas_server")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class VirtualNASRestRep extends VirtualArrayResourceRestRep {

    // NAS Server name
    private String nasName;

    // storageSystem, which it belongs
    private RelatedResourceRep storageDeviceURI;

    // List of Protocols (CIFS/NFS) associated with Virtual NAS Server
    private Set<String> protocols;

    // Set of Authentication providers for the VNasServer - set values will of type AunthnProvider
    private Set<String> cifsServers;

    // List of Storage Ports associated with this NAS Server
    private List<RelatedResourceRep> storagePorts;

    // State of the NAS server
    private String nasState;

    // Place holder for hosting storageDomain's information
    private Set<String> storageDomain;

    // Registration status of Virtual NAS server
    private String registrationStatus;

    // Compatibility status of Virtual NAS server
    private String compatibilityStatus;

    // Discovery status of Virtual NAS server
    private String discoveryStatus;

    // Place holder for Tag
    private Set<String> nasTag;

    // Project name which this VNAS belongs to
    private RelatedResourceRep project;

    // Base directory Path for the VNAS applicable in AccessZones & vFiler device types
    private String baseDirPath;

    // place holder for the Parent NAS server the Data Mover
    private NamedRelatedResourceRep parentNASURI;

    // Limits on vNAS
    private String maxStorageObjects;
    private String maxStorageCapacity;

    // Static Load on vNAS
    private String storageObjects;
    private String usedStorageCapacity;
    private String percentLoad;

    // Dynamic load on vNAS
    private String avgPercentagebusy;
    private String avgEmaPercentagebusy;

    // Indicate, whether the vNAS is overloaded or not!!!
    private Boolean isOverloaded;

    public VirtualNASRestRep() {
    }

    @XmlElement(name = "nas_name")
    public String getNasName() {
        return nasName;
    }

    public void setNasName(String nasName) {
        this.nasName = nasName;
    }

    @XmlElement(name = "storage_device")
    public RelatedResourceRep getStorageDeviceURI() {
        return storageDeviceURI;
    }

    public void setStorageDeviceURI(RelatedResourceRep storageDeviceURI) {
        this.storageDeviceURI = storageDeviceURI;
    }

    @XmlElementWrapper(name = "protocols")
    @XmlElement(name = "protocol")
    public Set<String> getProtocols() {
        return protocols;
    }

    public void setProtocols(Set<String> protocols) {
        this.protocols = protocols;
    }

    @XmlElementWrapper(name = "cifs_servers")
    @XmlElement(name = "cifs_server")
    public Set<String> getCifsServers() {
        return cifsServers;
    }

    public void setCifsServers(Set<String> cifsServers) {
        this.cifsServers = cifsServers;
    }

    @XmlElementWrapper(name = "storage_ports")
    @XmlElement(name = "storage_port")
    public List<RelatedResourceRep> getStoragePorts() {
        if (storagePorts == null) {
            storagePorts = new ArrayList<RelatedResourceRep>();
        }
        return storagePorts;
    }

    public void setStoragePorts(List<RelatedResourceRep> storagePorts) {
        this.storagePorts = storagePorts;
    }

    @XmlElement(name = "nas_state")
    public String getNasState() {
        return nasState;
    }

    public void setNasState(String nasState) {
        this.nasState = nasState;
    }

    @XmlElementWrapper(name = "storage_domains")
    @XmlElement(name = "storage_domain")
    public Set<String> getStorageDomain() {
        return storageDomain;
    }

    public void setStorageDomain(Set<String> storageDomain) {
        this.storageDomain = storageDomain;
    }

    @XmlElement(name = "registration_status")
    public String getRegistrationStatus() {
        return registrationStatus;
    }

    public void setRegistrationStatus(String registrationStatus) {
        this.registrationStatus = registrationStatus;
    }

    @XmlElement(name = "compatibility_status")
    public String getCompatibilityStatus() {
        return compatibilityStatus;
    }

    public void setCompatibilityStatus(String compatibilityStatus) {
        this.compatibilityStatus = compatibilityStatus;
    }

    @XmlElement(name = "discovery_status")
    public String getDiscoveryStatus() {
        return discoveryStatus;
    }

    public void setDiscoveryStatus(String discoveryStatus) {
        this.discoveryStatus = discoveryStatus;
    }

    @XmlElementWrapper(name = "nas_tags")
    @XmlElement(name = "nas_tag")
    public Set<String> getNasTag() {
        return nasTag;
    }

    public void setNasTag(Set<String> nasTag) {
        this.nasTag = nasTag;
    }

    @XmlElement(name = "project")
    public RelatedResourceRep getProject() {
        return project;
    }

    public void setProject(RelatedResourceRep project) {
        this.project = project;
    }

    @XmlElement(name = "base_dir_path")
    public String getBaseDirPath() {
        return baseDirPath;
    }

    public void setBaseDirPath(String baseDirPath) {
        this.baseDirPath = baseDirPath;
    }

    @XmlElement(name = "parent_nas")
    public NamedRelatedResourceRep getParentNASURI() {
        return parentNASURI;
    }

    public void setParentNASURI(NamedRelatedResourceRep parentNASURI) {
        this.parentNASURI = parentNASURI;
    }

    @XmlElement(name = "max_storage_objects")
    public String getMaxStorageObjects() {
        return maxStorageObjects;
    }

    public void setMaxStorageObjects(String maxStorageObjects) {
        this.maxStorageObjects = maxStorageObjects;
    }

    @XmlElement(name = "max_storage_capacity")
    public String getMaxStorageCapacity() {
        return maxStorageCapacity;
    }

    public void setMaxStorageCapacity(String maxStorageCapacity) {
        this.maxStorageCapacity = maxStorageCapacity;
    }

    @XmlElement(name = "storage_objects")
    public String getStorageObjects() {
        return storageObjects;
    }

    public void setStorageObjects(String storageObjects) {
        this.storageObjects = storageObjects;
    }

    @XmlElement(name = "storage_capacity")
    public String getUsedStorageCapacity() {
        return usedStorageCapacity;
    }

    public void setUsedStorageCapacity(String storageCapacity) {
        this.usedStorageCapacity = storageCapacity;
    }

    @XmlElement(name = "avg_percentage_busy")
    public String getAvgPercentagebusy() {
        return avgPercentagebusy;
    }

    public void setAvgPercentagebusy(String avgPercentagebusy) {
        this.avgPercentagebusy = avgPercentagebusy;
    }

    @XmlElement(name = "avg_ema_percentage_busy")
    public String getAvgEmaPercentagebusy() {
        return avgEmaPercentagebusy;
    }

    public void setAvgEmaPercentagebusy(String avgEmaPercentagebusy) {
        this.avgEmaPercentagebusy = avgEmaPercentagebusy;
    }

    @XmlElement(name = "over_loaded")
    public Boolean getIsOverloaded() {
        return isOverloaded;
    }

    public void setIsOverloaded(Boolean isOverloaded) {
        this.isOverloaded = isOverloaded;
    }

    @XmlElement(name = "percent_load")
    public String getPercentLoad() {
        return percentLoad;
    }

    public void setPercentLoad(String percentLoad) {
        this.percentLoad = percentLoad;
    }

}
