/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.file;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.VirtualArrayRelatedResourceRep;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "filesystem")
public class FileShareRestRep extends FileObjectRestRep {
    private RelatedResourceRep project;
    private RelatedResourceRep tenant;
    private String capacity;
    private String usedCapacity;
    private Long softLimit;
    private Integer softGrace;
    private Long notificationLimit;
    private Boolean softLimitExceeded;
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
    private FileProtectionRestRep protection;

    /**
     * File system's actual path on the array.
     * 
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
     */
    @XmlElement(name = "used_capacity_gb")
    public String getUsedCapacity() {
        return usedCapacity;
    }

    public void setUsedCapacity(String usedCapacity) {
        this.usedCapacity = usedCapacity;
    }

    @XmlElement(name = "soft_limit", required = false)
    public Long getSoftLimit() {
        return softLimit;
    }

    public void setSoftLimit(Long softLimit) {
        this.softLimit = softLimit;
    }

    @XmlElement(name = "soft_grace", required = false)
    public Integer getSoftGrace() {
        return softGrace;
    }

    public void setSoftGrace(Integer softGrace) {
        this.softGrace = softGrace;
    }

    @XmlElement(name = "notification_limit", required = false)
    public Long getNotificationLimit() {
        return notificationLimit;
    }

    public void setNotificationLimit(Long notificationLimit) {
        this.notificationLimit = notificationLimit;
    }

    @XmlElement(name = "soft_limit_exceeded", required = false)
    public Boolean getSoftLimitExceeded() {
        return softLimitExceeded;
    }

    public void setSoftLimitExceeded(Boolean softLimitExceeded) {
        this.softLimitExceeded = softLimitExceeded;
    }

    /**
     * URI for the virtual pool the file share resides on.
     * 
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
     * Valid values:
     *   CIFS = Common Interface File System 
     *   NFS = Network File System
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

    /**
     * File system replication info
     * 
     */
    @XmlElement(name = "file_replication")
    public FileProtectionRestRep getProtection() {
        return protection;
    }

    public void setProtection(FileProtectionRestRep protection) {
        this.protection = protection;
    }

    // Fields specific to protection characteristics of the file system!!
    public static class FileProtectionRestRep {
        private String personality;
        private String mirrorStatus;
        private String accessState;
        private RelatedResourceRep parentFileSystem;
        private List<VirtualArrayRelatedResourceRep> targetFileSystems;

        /**
         * SOURCE
         * TARGET
         * 
         */
        @XmlElement(name = "personality")
        public String getPersonality() {
            return personality;
        }

        public void setPersonality(String personality) {
            this.personality = personality;
        }

        @XmlElement(name = "access_state")
        public String getAccessState() {
            return accessState;
        }

        public void setAccessState(String accessState) {
            this.accessState = accessState;
        }

        @XmlElement(name = "mirror_status")
        public String getMirrorStatus() {
            return mirrorStatus;
        }

        public void setMirrorStatus(String mirrorStatus) {
            this.mirrorStatus = mirrorStatus;
        }

        @XmlElement(name = "parent_file_system")
        public RelatedResourceRep getParentFileSystem() {
            return parentFileSystem;
        }

        public void setParentFileSystem(RelatedResourceRep parentFileSystem) {
            this.parentFileSystem = parentFileSystem;
        }

        @XmlElementWrapper(name = "target_file_systems")
        @XmlElement(name = "file_system")
        public List<VirtualArrayRelatedResourceRep> getTargetFileSystems() {
            if (targetFileSystems == null) {
                targetFileSystems = new ArrayList<VirtualArrayRelatedResourceRep>();
            }
            return targetFileSystems;
        }

        public void setTargetFileSystems(List<VirtualArrayRelatedResourceRep> targetFileSystems) {
            this.targetFileSystems = targetFileSystems;
        }
    }

}
