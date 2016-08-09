/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model;


import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;

public class StoragePool extends StorageObject {

    // All attributes are Output attributes --- should be set by driver at discovery time.

    // pool name
    private String poolName;
    // storage system where this pool is located
    private String storageSystemId;

    // storage protocols supported by pool
    private Set<Protocols> supportedProtocols;

    public static enum Protocols {
        // storage block protocols
        iSCSI,              // block
        FC,                 // block
        FCoE,               // FC block protocol with Ethernet transport
        ScaleIO,            // ScaleIO Data Clients
        Ceph,               // Ceph Data Clients
        // storage file protocols
        NFS,                // file, NFSv2 & NFSv3
        NFSv4,              // file, authenticated NFS
        CIFS,               // file
        NFS_OR_CIFS,        // NFS or CIFS
    }

    // Total storage capacity held by the pool (KBytes)
    private Long totalCapacity;
    // Total free capacity available for allocating volumes from the pool (KBytes)
    private Long freeCapacity;
    // In case of ThinPools, this would indicate how much real storage is being used by
    // the allocated devices in the pool (KBytes)
    private Long subscribedCapacity;

    // Operational Status of Pool
    private String operationalStatus;

    public static enum PoolOperationalStatus {
        READY, NOTREADY
    }

    // Supported Raid Levels in Pool
    private Set<RaidLevels> supportedRaidLevels;

    public static enum RaidLevels {
        RAID0, RAID1, RAID2, RAID3, RAID4, RAID5, RAID6, RAID10
    }

    private Set<SupportedDriveTypes> supportedDriveTypes;

    public static enum SupportedDriveTypes {
        FC("FC"),
        SAS("SAS"),
        SATA("SATA SATA2 ATA"),
        NL_SAS("NL_SAS"),
        SSD("FC_SSD SATA2_SSD SAS_SSD EFD SSD SAS_SSD_VP"),
        UNKNOWN("UNKNOWN");

        private String _diskDriveValues;

        SupportedDriveTypes(String diskDriveValues) {
            _diskDriveValues = diskDriveValues;
        }

        public String getDiskDriveValues() {
            return _diskDriveValues;
        }

        private static final SupportedDriveTypes[] copyOfValues = values();

        public static String getDiskDriveDisplayName(String diskDrive) {
            for (SupportedDriveTypes driveType : copyOfValues) {
                if (driveType.getDiskDriveValues().contains(diskDrive)) {
                    return driveType.toString();
                }
            }
            return null;
        }



        public static SupportedDriveTypes lookup(String name) {
            for (SupportedDriveTypes value : copyOfValues) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return null;
        }
    }


    // Maximum size of Thin Volume which can be carved out of this Storage Pool in KiloBytes
    private Long maximumThinVolumeSize;

    // Minimum size of Thin Volume which can be carved out of this Storage Pool in KiloBytes.
    private Long minimumThinVolumeSize;

    // Maximum size of Thick Volume which can be carved out of this Storage Pool in KiloBytes
    private Long maximumThickVolumeSize;

    // Minimum size of Thick Volume which can be carved out of this Storage Pool in KiloBytes.
    private Long minimumThickVolumeSize;

    private SupportedResourceType supportedResourceType;
    
	public static enum SupportedResourceType {
        THICK_ONLY,
        THIN_ONLY,
        THIN_AND_THICK
    }
    
    public static enum AutoTieringPolicyProvisioningType {
        ThinlyProvisioned,
        ThicklyProvisioned,
        All
    }

    public static enum PoolServiceType {
        block,
        file,
        object,
        block_file;
    }

    // pool service type
    private String poolServiceType;

    private List<CapabilityInstance> capabilities;

    public String getPoolName() {
        return poolName;
    }

    public void setPoolName(String poolName) {
        this.poolName = poolName;
    }

    public String getStorageSystemId() {
        return storageSystemId;
    }

    public void setStorageSystemId(String storageSystemId) {
        this.storageSystemId = storageSystemId;
    }

    public Set<String> getProtocols() {
        Set<String> protocols = new HashSet();
        if (supportedProtocols != null) {
            for (Protocols protocol : supportedProtocols) {
                protocols.add(protocol.name());
            }
        }
        return protocols;
    }

    public void setProtocols(Set<Protocols> protocols) {
        this.supportedProtocols = protocols;
    }

    public Long getTotalCapacity() {
        return totalCapacity;
    }

    public void setTotalCapacity(Long totalCapacity) {
        this.totalCapacity = totalCapacity;
    }

    public Long getFreeCapacity() {
        return freeCapacity;
    }

    public void setFreeCapacity(Long freeCapacity) {
        this.freeCapacity = freeCapacity;
    }

    public Long getSubscribedCapacity() {
        return subscribedCapacity;
    }

    public void setSubscribedCapacity(Long subscribedCapacity) {
        this.subscribedCapacity = subscribedCapacity;
    }

    public String getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(PoolOperationalStatus operationalStatus) {
        this.operationalStatus = operationalStatus.name();
    }

    public Set<String> getSupportedRaidLevels() {
        Set<String> raidLevels = new HashSet();
        if (supportedRaidLevels != null) {
            for (RaidLevels raid : supportedRaidLevels) {
                raidLevels.add(raid.name());
            }
        }
        return raidLevels;
    }

    public void setSupportedRaidLevels(Set<RaidLevels> supportedRaidLevels) {
        this.supportedRaidLevels = supportedRaidLevels;
    }

    public Set<String> getSupportedDriveTypes() {
        Set<String> driveTypes = new HashSet();
        if (supportedDriveTypes != null) {
            for (SupportedDriveTypes drive : supportedDriveTypes) {
                driveTypes.add(drive.name());
            }
        }
        return driveTypes;
    }

    public void setSupportedDriveTypes(Set<SupportedDriveTypes> supportedDriveTypes) {
        this.supportedDriveTypes = supportedDriveTypes;
    }

    public Long getMaximumThinVolumeSize() {
        return maximumThinVolumeSize;
    }

    public void setMaximumThinVolumeSize(Long maximumThinVolumeSize) {
        this.maximumThinVolumeSize = maximumThinVolumeSize;
    }

    public Long getMinimumThinVolumeSize() {
        return minimumThinVolumeSize;
    }

    public void setMinimumThinVolumeSize(Long minimumThinVolumeSize) {
        this.minimumThinVolumeSize = minimumThinVolumeSize;
    }

    public Long getMaximumThickVolumeSize() {
        return maximumThickVolumeSize;
    }

    public void setMaximumThickVolumeSize(Long maximumThickVolumeSize) {
        this.maximumThickVolumeSize = maximumThickVolumeSize;
    }

    public Long getMinimumThickVolumeSize() {
        return minimumThickVolumeSize;
    }

    public void setMinimumThickVolumeSize(Long minimumThickVolumeSize) {
        this.minimumThickVolumeSize = minimumThickVolumeSize;
    }

    public String getSupportedResourceType() {
        return supportedResourceType.name();
    }

    public void setSupportedResourceType(SupportedResourceType supportedResourceType) {
        this.supportedResourceType = supportedResourceType;
    }

    public String getPoolServiceType() {
        return poolServiceType;
    }

    public void setPoolServiceType(PoolServiceType poolServiceType) {
        this.poolServiceType = poolServiceType.name();
    }
	
    public List<CapabilityInstance> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<CapabilityInstance> capabilities) {
        this.capabilities = capabilities;
    }
}
