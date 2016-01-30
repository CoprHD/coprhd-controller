/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.StringHashMapEntry;
import com.emc.storageos.model.adapters.StringSetMapAdapter;

/**
 * An unmanaged file system. UnManaged FileSystem are FileSystems, which
 * are present within ViPR Storage Systems, but are not under ViPR management.
 * 
 */
@XmlRootElement(name = "unmanaged_filesystem")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class UnManagedFileSystemRestRep extends DataObjectRestRep {
    private String nativeGuid;
    private List<StringSetMapAdapter.Entry> filesystemInformation;
    private List<StringHashMapEntry> filesystemCharacteristics;
    private RelatedResourceRep storageSystem;
    private RelatedResourceRep storagePool;
    
    /**
     * List of supported VPool URIs associated with this UnManagedVolume.
     */
    private List<String> supportedVPoolUris;

    /**
     * GUID associated with the unmanaged file system.
     * 
     */
    @XmlElement(name = "native_guid")
    public String getNativeGuid() {
        return nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        this.nativeGuid = nativeGuid;
    }

    /**
     * URI for the storage system supporting the unmanaged
     * file system.
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
     * URI representing the storage pool supporting the unmanaged file
     * system.
     * 
     */
    @XmlElement(name = "storage_pool")
    public RelatedResourceRep getStoragePool() {
        return storagePool;
    }

    public void setStoragePool(RelatedResourceRep storagePool) {
        this.storagePool = storagePool;
    }
    
    @XmlElementWrapper(name = "supported_virtual_pools")
    @XmlElement(name = "virtual_pool")
    public List<String> getSupportedVPoolUris() {
        if (supportedVPoolUris == null) {
            supportedVPoolUris = new ArrayList<String>();
        }
        return supportedVPoolUris;
    }

    public void setSupportedVPoolUris(List<String> supportedVPoolUris) {
        this.supportedVPoolUris = supportedVPoolUris;
    }

    /**
     * A list of name-value pairs representing the characteristics of
     * the unmanaged file system.
     * 
     */
    @XmlElementWrapper(name = "unmanaged_filesystems_characterstics")
    @XmlElement(name = "unmanaged_filesystem_characterstic")
    public List<StringHashMapEntry> getFileSystemCharacteristics() {
        if (filesystemCharacteristics == null) {
            filesystemCharacteristics = new ArrayList<StringHashMapEntry>();
        }
        return filesystemCharacteristics;
    }

    public void setFileSystemCharacteristics(List<StringHashMapEntry> filesystemCharacteristics) {
        this.filesystemCharacteristics = filesystemCharacteristics;
    }

    /**
     * A list of name-value pairs containing information relevant to the
     * unmanaged file system.
     * 
     */
    @XmlElementWrapper(name = "unmanaged_filesystems_info")
    @XmlElement(name = "unmanaged_filesystem_info")
    public List<StringSetMapAdapter.Entry> getFileSystemInformation() {
        if (filesystemInformation == null) {
            filesystemInformation = new ArrayList<StringSetMapAdapter.Entry>();
        }
        return filesystemInformation;
    }

    public void setFileSystemInformation(List<StringSetMapAdapter.Entry> filesystemInformation) {
        this.filesystemInformation = filesystemInformation;
    }
}
