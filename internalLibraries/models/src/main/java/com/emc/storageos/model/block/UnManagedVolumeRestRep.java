/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.block;

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

@XmlRootElement(name = "unmanaged_volume")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class UnManagedVolumeRestRep extends DataObjectRestRep {
    /**
     * The native GUID of a discovered unmanaged volume which 
     * has not yet been ingested into ViPR.  
     * @valid none
     */ 
    private String nativeGuid;
    
    /**
     * Information about the unmanaged volume. For example, allocated capacity, 
     * provisioned capacity, disk technology, and whether or not the volume is 
     * thinly provisioned. 
     * @valid none
     */ 
    private List<StringSetMapAdapter.Entry> volumeInformation;
    
    /**
     * Characteristics of the unmanaged volume, such as is it mapped, 
     * is it a composite, and is it a snapshot. 
     * @valid none
     */ 
    private List<StringHashMapEntry> volumeCharacteristics;

    /**
     * List of UnManagedExportMasks associated with this UnManagedVolume.
     */
    private List<String> unManagedExportMasks;

    /**
     * List of initiator URIs associated with this UnManagedVolume.
     */
    private List<String> initiatorUris;

    /**
     * List of initiator network IDs associated with this UnManagedVolume.
     */
    private List<String> initiatorNetworkIds;

    /**
     * List of storage port URIs associated with this UnManagedVolume.
     */
    private List<String> storagePortUris;
    
    /**
     * The storage system to which this volume belongs. 
     * @valid none
     */ 
    private RelatedResourceRep storageSystem;

    /**
     * The storage pool to which this volume belongs. 
     * @valid none
     */ 
    private RelatedResourceRep storagePool;

    @XmlElement(name = "native_guid")
    public String getNativeGuid() {
        return nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        this.nativeGuid = nativeGuid;
    }

    @XmlElement(name = "storage_system")
    public RelatedResourceRep getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(RelatedResourceRep storageSystem) {
        this.storageSystem = storageSystem;
    }
    
    @XmlElement(name = "storage_pool")
    public RelatedResourceRep getStoragePool() {
        return storagePool;
    }

    public void setStoragePool(RelatedResourceRep storagePool) {
        this.storagePool = storagePool;
    }

    @XmlElementWrapper(name = "unmanaged_volumes_characterstics")
    @XmlElement(name = "unmanaged_volume_characterstic")
    public List<StringHashMapEntry> getVolumeCharacteristics() {
        if (volumeCharacteristics == null) {
            volumeCharacteristics = new ArrayList<StringHashMapEntry>();
        }
        return volumeCharacteristics;
    }

    public void setVolumeCharacteristics(List<StringHashMapEntry> volumeCharacteristics) {
        this.volumeCharacteristics = volumeCharacteristics;
    }

    @XmlElementWrapper(name = "unmanaged_volumes_info")
    @XmlElement(name = "unmanaged_volume_info")
    public List<StringSetMapAdapter.Entry> getVolumeInformation() {
        if (volumeInformation == null) {
            volumeInformation = new ArrayList<StringSetMapAdapter.Entry>();
        }
        return volumeInformation;
    }

    public void setVolumeInformation(List<StringSetMapAdapter.Entry> volumeInformation) {
        this.volumeInformation = volumeInformation;
    }

    @XmlElementWrapper(name = "unmanaged_export_masks")
    @XmlElement(name = "unmanaged_export_mask")
    public List<String> getUnManagedExportMasks() {
        if (unManagedExportMasks == null) {
            unManagedExportMasks = new ArrayList<String>();
        }
        return unManagedExportMasks;
    }

    public void setUnManagedExportMasks(List<String> unManagedExportMasks) {
        this.unManagedExportMasks = unManagedExportMasks;
    }

    @XmlElementWrapper(name = "initiators")
    @XmlElement(name = "initiator")
    public List<String> getInitiatorUris() {
        if (initiatorUris == null) {
            initiatorUris = new ArrayList<String>();
        }
        return initiatorUris;
    }

    public void setInitiatorUris(List<String> initiatorUris) {
        this.initiatorUris = initiatorUris;
    }

    @XmlElementWrapper(name = "initiator_network_ids")
    @XmlElement(name = "initiator_network_id")
    public List<String> getInitiatorNetworkIds() {
        if (initiatorNetworkIds == null) {
            initiatorNetworkIds = new ArrayList<String>();
        }
        return initiatorNetworkIds;
    }

    public void setInitiatorNetworkIds(List<String> initiatorNetworkIds) {
        this.initiatorNetworkIds = initiatorNetworkIds;
    }

    @XmlElementWrapper(name = "storage_ports")
    @XmlElement(name = "storage_port")
    public List<String> getStoragePortUris() {
        if (storagePortUris == null) {
            storagePortUris = new ArrayList<String>();
        }
        return storagePortUris;
    }

    public void setStoragePortUris(List<String> storagePortUris) {
        this.storagePortUris = storagePortUris;
    }

}
