/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;


/**
 * This class represents Storage Tiers of VMAX and VNX
 * 
 */
@Cf("StorageTier")
public class StorageTier extends DiscoveredDataObject{
    private String _percentage;
    private String _enabledState;
    private long _totalCapacity;
    private String _diskDriveTechnology;
    // same tier can exist in multiple policies.
    private StringSet _autoTieringPolicies;
   // storage device this storage tier belongs to
    private URI _storageDevice;
    
    public static enum SupportedTiers {
        SSD("3"),
        FC("4"),
        SATA("5"),
        SAS("6"),
        MIXED("7");
        
        private String _tierTechnology;
        
        SupportedTiers(String tierTechnology) {
            _tierTechnology = tierTechnology;
        }
        
        public String getTierTechnology() {
            return _tierTechnology;
        }
        
        public static String getTier(String tierTechnology) {
            for(SupportedTiers tierType : values()) {
                if(tierType.getTierTechnology().equalsIgnoreCase(tierTechnology))
                    return tierType.toString();
            }
            return null;
        }
        
        public static SupportedTiers getTierName(String tier) {
            for(SupportedTiers tierType : values()) {
                if (tierType.name().equalsIgnoreCase(tier)) {
                    return tierType;
                }
            }
            return null;
        }
         
     }

    
    @RelationIndex(cf = "TierToStorageDevice", type = StorageSystem.class)
    @Name("storageDevice")
    public URI getStorageDevice() {
        return _storageDevice;
    }

    public void setStorageDevice(URI storageDevice) {
        _storageDevice = storageDevice;
        setChanged("storageDevice");
    }
    
    public void setPercentage(String percentage) {
        _percentage = percentage;
        setChanged("percentage");
    }
    @Name("percentage")
    public String getPercentage() {
        return _percentage;
    }
    public void setEnabledState(String enabledState) {
        _enabledState = enabledState;
        setChanged("enabledState");
    }
    @Name("enabledState")
    public String getEnabledState() {
        return _enabledState;
    }
    public void setTotalCapacity(Long totalCapacity) {
        _totalCapacity = totalCapacity;
        setChanged("totalCapacity");
    }
    @Name("totalCapacity")
    public Long getTotalCapacity() {
        return _totalCapacity;
    }
   
    public void setDiskDriveTechnology(String diskDriveTechnology) {
        _diskDriveTechnology = diskDriveTechnology;
        setChanged("diskDriveTechnology");
    }
    @Name("diskDriveTechnology")
    public String getDiskDriveTechnology() {
        return _diskDriveTechnology;
    }
    
    @AlternateId("PolicyToTierIndex")
    @Name("autoTieringPolicies")
    public StringSet getAutoTieringPolicies() {
        return _autoTieringPolicies;
    }

    public void setAutoTieringPolicies(StringSet autoTieringPolicies) {
        this._autoTieringPolicies = autoTieringPolicies;
    }
    
}
