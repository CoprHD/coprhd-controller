package com.emc.storageos.storagedriver.model;


import com.emc.storageos.storagedriver.CapabilityInstance;

import java.util.List;

public class StoragePool extends StorageObject {

    // All attributes are Output attributes --- should be set by driver at discovery time.

    // pool name
    private String poolName;
    // storage system where this pool is located
    private String storageSystemId;
    // storage protocols supported by pool
    private List<String> protocols;
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
    private List<RaidLevels> supportedRaidLevels;

    public static enum RaidLevels {
        RAID0, RAID1, RAID2, RAID3, RAID4, RAID5, RAID6, RAID10
    }

    private List<SupportedDriveTypes> supportedDriveTypes;

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

    public List<String> getProtocols() {
        return protocols;
    }

    public void setProtocols(List<String> protocols) {
        this.protocols = protocols;
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

    public void setOperationalStatus(String operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public List<RaidLevels> getSupportedRaidLevels() {
        return supportedRaidLevels;
    }

    public void setSupportedRaidLevels(List<RaidLevels> supportedRaidLevels) {
        this.supportedRaidLevels = supportedRaidLevels;
    }

    public List<SupportedDriveTypes> getSupportedDriveTypes() {
        return supportedDriveTypes;
    }

    public void setSupportedDriveTypes(List<SupportedDriveTypes> supportedDriveTypes) {
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

    public SupportedResourceType getSupportedResourceType() {
        return supportedResourceType;
    }

    public void setSupportedResourceType(SupportedResourceType supportedResourceType) {
        this.supportedResourceType = supportedResourceType;
    }

    public String getPoolServiceType() {
        return poolServiceType;
    }

    public void setPoolServiceType(String poolServiceType) {
        this.poolServiceType = poolServiceType;
    }

    public List<CapabilityInstance> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<CapabilityInstance> capabilities) {
        this.capabilities = capabilities;
    }
}
