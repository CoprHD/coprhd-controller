/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
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

@XmlRootElement(name = "unmanaged_cg")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class UnManagedCGRestRep extends DataObjectRestRep {
    /**
     * The native GUID of a discovered unmanaged volume which
     * has not yet been ingested into ViPR.
     * 
     * @valid none
     */
    private String nativeGuid;

    /**
     * Information about the unmanaged CG. 
     * 
     * @valid none
     */
    private List<StringSetMapAdapter.Entry> cgInformation;

    /**
     * Characteristics of the unmanaged CG.
     * 
     * @valid none
     */
    private List<StringHashMapEntry> cgCharacteristics;

    /**
     * Volumess that have been ingested.
     * 
     * @valid none
     */
    private List<String> managedVolumeIds;
    
    /**
     * Volumes that have not been ingested.
     * 
     * @valid none
     */
    private List<String> unManagedVolumeIds;
    
    /**
     * Volume WWNs, regardless of system management
     */
    private List<String> volumeWwns;
    
    /**
     * Name of RecoverPoint consistency group
     */
    private String cgName;
    
    /**
     * The protection system to which this CG belongs.
     * 
     * @valid none
     */
    private RelatedResourceRep protectionSystem;

    @XmlElement(name = "native_guid")
    public String getNativeGuid() {
        return nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        this.nativeGuid = nativeGuid;
    }

    @XmlElement(name = "protection_system")
    public RelatedResourceRep getProtectionSystem() {
        return protectionSystem;
    }

    public void setProtectionSystem(RelatedResourceRep protectionSystem) {
        this.protectionSystem = protectionSystem;
    }

    @XmlElementWrapper(name = "unmanaged_cgs_characterstics")
    @XmlElement(name = "unmanaged_cg_characterstic")
    public List<StringHashMapEntry> getCGCharacteristics() {
        if (cgCharacteristics == null) {
            cgCharacteristics = new ArrayList<StringHashMapEntry>();
        }
        return cgCharacteristics;
    }

    public void setCGCharacteristics(List<StringHashMapEntry> cgCharacteristics) {
        this.cgCharacteristics = cgCharacteristics;
    }

    @XmlElementWrapper(name = "unmanaged_cgs_info")
    @XmlElement(name = "unmanaged_cg_info")
    public List<StringSetMapAdapter.Entry> getCGInformation() {
        if (cgInformation == null) {
            cgInformation = new ArrayList<StringSetMapAdapter.Entry>();
        }
        return cgInformation;
    }

    public void setCGInformation(List<StringSetMapAdapter.Entry> cgInformation) {
        this.cgInformation = cgInformation;
    }

    @XmlElementWrapper(name = "unmanaged_volumes")
    @XmlElement(name = "unmanaged_volume")
    public List<String> getUnManagedVolumeUris() {
        if (unManagedVolumeIds == null) {
            unManagedVolumeIds = new ArrayList<String>();
        }
        return unManagedVolumeIds;
    }

    public void setUnManagedVolumeUris(List<String> unManagedVolumeUris) {
        this.unManagedVolumeIds = unManagedVolumeUris;
    }

    @XmlElementWrapper(name = "managed_volumes")
    @XmlElement(name = "managed_volume")
    public List<String> getManagedVolumeUris() {
        if (managedVolumeIds == null) {
            managedVolumeIds = new ArrayList<String>();
        }
        return managedVolumeIds;
    }

    public void setManagedVolumeUris(List<String> managedVolumeUris) {
        this.managedVolumeIds = managedVolumeUris;
    }

    @XmlElementWrapper(name = "volume_wwns")
    @XmlElement(name = "volume_wwn")
    public List<String> getVolumeWwns() {
        if (volumeWwns == null) {
            volumeWwns = new ArrayList<String>();
        }
        return volumeWwns;
    }

    public void setVolumeWwns(List<String> volumeWwns) {
        this.volumeWwns = volumeWwns;
    }
    
    public void setCGName(String cgName) {
        this.cgName = cgName;
    }

    @XmlElement(name = "cg_name")
    public String getCGName() {
        return cgName;
    }
}
