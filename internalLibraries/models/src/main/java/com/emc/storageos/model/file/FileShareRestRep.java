/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.file;

import javax.xml.bind.annotation.*;

import com.emc.storageos.model.RelatedResourceRep;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.LinkedHashSet;
import java.util.Set;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "filesystem")
public class FileShareRestRep extends FileObjectRestRep {
    private RelatedResourceRep project;
    private RelatedResourceRep tenant;
    private String capacity;
    private String usedCapacity;
    private RelatedResourceRep vpool;
    private RelatedResourceRep varray;
    private Set<String> protocols;
    private String dataProtection;
    private RelatedResourceRep storageSystem;
    private RelatedResourceRep pool;
    private RelatedResourceRep storagePort;
    private Boolean thinlyProvisioned;
    private String nativeId;
    private RelatedResourceRep virtualNAS;

    /**
     * File system's actual path on the array.
     * 
     * @valid none
     */
    @XmlElement(name = "native_id")
    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    /**
     * Total capacity of the file system in GB
     * 
     * @valid none
     */
    @XmlElement(name = "capacity_gb")
    public String getCapacity() {
        return capacity;
    }

    public void setCapacity(String capacity) {
        this.capacity = capacity;
    }

    /**
     * Used capacity of the file system in GB
     * 
     * @valid none
     */
    @XmlElement(name = "used_capacity_gb")
    public String getUsedCapacity() {
        return usedCapacity;
    }

    public void setUsedCapacity(String usedCapacity) {
        this.usedCapacity = usedCapacity;
    }

    /**
     * URI for the virtual pool the file share resides on.
     * 
     * @valid none
     */
    @XmlElement(name = "vpool")
    @JsonProperty("vpool")
    public RelatedResourceRep getVirtualPool() {
        return vpool;
    }

    public void setVirtualPool(RelatedResourceRep vpool) {
        this.vpool = vpool;
    }

    /**
     * Not currently used
     * 
     * @valid none
     */
    @XmlElement(name = "data_protection")
    public String getDataProtection() {
        return dataProtection;
    }

    public void setDataProtection(String dataProtection) {
        this.dataProtection = dataProtection;
    }

    /**
     * URI for the virtual array containing the virtual pool and the file share.
     * 
     * @valid none
     */
    @XmlElement(name = "varray")
    @JsonProperty("varray")
    public RelatedResourceRep getVirtualArray() {
        return varray;
    }

    public void setVirtualArray(RelatedResourceRep varray) {
        this.varray = varray;
    }

    /**
     * URI for the storage pool containing storage allocated for the file system.
     * 
     * @valid none
     */
    @XmlElement(name = "storage_pool")
    public RelatedResourceRep getPool() {
        return pool;
    }

    public void setPool(RelatedResourceRep pool) {
        this.pool = pool;
    }

    /**
     * URI for the project containing the file system.
     * 
     * @valid none
     */
    @XmlElement
    public RelatedResourceRep getProject() {
        return project;
    }

    public void setProject(RelatedResourceRep project) {
        this.project = project;
    }

    @XmlElementWrapper(name = "protocols")
    /**
     * Set of valid protocols.
     * @valid CIFS = Common Interface File System 
     * @valid NFS = Network File System
     */
    @XmlElement(name = "protocol")
    public Set<String> getProtocols() {
        if (protocols == null) {
            protocols = new LinkedHashSet<String>();
        }
        return protocols;
    }

    public void setProtocols(Set<String> protocols) {
        this.protocols = protocols;
    }

    /**
     * URI representing the storage system supporting the file system.
     * 
     * @valid none
     */
    @XmlElement(name = "storage_system")
    public RelatedResourceRep getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(RelatedResourceRep storageSystem) {
        this.storageSystem = storageSystem;
    }

    /**
     * URI representing the storage port.
     * 
     * @valid 1 - 65535
     */
    @XmlElement(name = "storage_port")
    public RelatedResourceRep getStoragePort() {
        return storagePort;
    }

    public void setStoragePort(RelatedResourceRep storagePort) {
        this.storagePort = storagePort;
    }

    /**
     * The URI of the tenant to which the file system belongs.
     * 
     * @valid none
     */
    @XmlElement
    public RelatedResourceRep getTenant() {
        return tenant;
    }

    public void setTenant(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }

    /**
     * Is the storage for the file system thinly provisioned?
     * If thinly provisioned, only a portion of the total capacity
     * is initially allocated. Additional storage is allocated
     * later as needed.
     * 
     * @valid true
     * @valid false
     */
    @XmlElement(name = "thinly_provisioned")
    public Boolean getThinlyProvisioned() {
        return thinlyProvisioned;
    }

    public void setThinlyProvisioned(Boolean thinlyProvisioned) {
        this.thinlyProvisioned = thinlyProvisioned;
    }

    @XmlElement(name = "virtual_nas")
	public RelatedResourceRep getVirtualNAS() {
		return virtualNAS;
	}

	public void setVirtualNAS(RelatedResourceRep virtualNAS) {
		this.virtualNAS = virtualNAS;
	}
    
    
}
