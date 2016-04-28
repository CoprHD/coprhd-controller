/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.model.valid.EnumType;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Cf("StoragePool")
public class StoragePool extends VirtualArrayTaggedResource {
    private static final long KB = 1024;
    // device native ID
    private String _nativeId;
    // pool name
    private String _poolName;
    // storage controller where this pool is located
    private URI _storageDevice;
    // storage protocols supported by pool
    private StringSet _protocols;
    // set controller specific parameter extensions for the pool
    private StringMap _controllerParams;
    // Current total storage capacity held by the pool (KBytes)
    private Long _totalCapacity;
    // Total free capacity available for allocating volumes from the pool (KBytes)
    private Long _freeCapacity;
    // In case of ThinPools, this would indicate how much real storage is being used by
    // the allocated devices in the pool (KBytes)
    private Long _subscribedCapacity;

    // Whether limit on number of Resources has been set
    private Boolean isResourceLimitSet = false;
    // Max Resources limit
    private Integer maxResources;

    // DeviceStoragePools in VNXBlock & VMAX considered as THICK Pools
    // UnifiedStoragePool in VNXBlock & VirtualProvisioningPool in VMAX considered as THIN Pools
    private String _poolClassName;

    // Supported Raid Levels in Pool
    private StringSet _supportedRaidLevels;
    // Setting Id to be used if Tier policy set to Auto
    private String _autoTierSettingId;
    // Setting Id to be used if Tier policy set to No_DataMovement
    private String _noDataMovementId;
    // Setting Id to be used if Tier policy set to Highest_Available_Tier
    private String _highAvailableTierId;
    // Setting Id to be used if Tier policy set to Lowest_Available_Tier
    private String _lowAvailableTierId;
    // Setting Id to be used if Tier policy set to Start_High_Then_Auto
    private String _startHighThenAutoTierId;
    // Max Retention days
    private Integer maxRetention;
    // Number of Data Centers on which this pool is spread
    //This is required only for object storage pools
    private Integer dataCenters;

    // Storage Tier Information for each Pool
    // VNX Pools have multiple tiers and VMAX has single Tier always
    private StringSet _tiers;

    private StringSet _supportedDriveTypes;

    private StringMap _tierUtilizationPercentage;

    private StringSet _supportedCopyTypes;

    private Integer _maxPoolUtilizationPercentage;

    private Integer _maxThinPoolSubscriptionPercentage;

    // limit set on array
    private Integer _maxThinPoolSubscriptionPercentageFromArray;

    private String _registrationStatus = RegistrationStatus.REGISTERED.toString();

    private Boolean thinVolumePreAllocationSupported = false;

    private Boolean autoTieringEnabled;

    // used in finding out whether or not the pool is Compatible
    private String _compatibilityStatus = CompatibilityStatus.UNKNOWN.name();

    private Double _avgStorageDevicePortMetrics;
    private String _discoveryStatus = DiscoveryStatus.VISIBLE.name();

    public static enum PoolClassNames {
        Symm_VirtualProvisioningPool,
        Clar_UnifiedStoragePool,
        Clar_DeviceStoragePool,
        Symm_DeviceStoragePool,
        VNXe_Pool,
        IBMTSDS_VirtualPool,
        Symm_SRPStoragePool;

        public static boolean isThinPool(String poolClassName) {
            return (Symm_VirtualProvisioningPool.name().equals(poolClassName)
                    || Symm_SRPStoragePool.name().equals(poolClassName)
                    || Clar_UnifiedStoragePool.name().equals(poolClassName)
                    || VNXe_Pool.name().equals(poolClassName));
        }

        public static boolean isThickPool(String poolClassName) {
            return (Clar_DeviceStoragePool.name().equals(poolClassName)
            || Symm_DeviceStoragePool.name().equals(poolClassName));
        }

    }

    public static enum RaidLevels {
        RAID0, RAID1, RAID2, RAID3, RAID4, RAID5, RAID6, RAID10
    }

    public static enum CopyTypes {
        ASYNC, SYNC, UNSYNC_ASSOC, UNSYNC_UNASSOC
    }

    public static enum SupportedDriveTypeValues {
        FC("FC"),
        SAS("SAS"),
        SATA("SATA SATA2 ATA"),
        NL_SAS("NL_SAS"),
        SSD("FC_SSD SATA2_SSD SAS_SSD EFD SSD SAS_SSD_VP"),
        UNKNOWN("UNKNOWN");

        private String _diskDriveValues;

        SupportedDriveTypeValues(String diskDriveValues) {
            _diskDriveValues = diskDriveValues;
        }

        public String getDiskDriveValues() {
            return _diskDriveValues;
        }

        public static String getDiskDriveDisplayName(String diskDrive) {
            for (SupportedDriveTypeValues driveType : copyOfValues) {
                if (driveType.getDiskDriveValues().contains(diskDrive)) {
                    return driveType.toString();
                }
            }
            return null;
        }

        private static final SupportedDriveTypeValues[] copyOfValues = values();

        public static SupportedDriveTypeValues lookup(String name) {
            for (SupportedDriveTypeValues value : copyOfValues) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return null;
        }
    }

    // Operational Status of Pool
    private String _operationalStatus;

    public static enum PoolOperationalStatus {
        READY, NOTREADY
    }

    // Maximum size of Thin Volume which can be carved out of this Storage Pool in KiloBytes
    private Long _maximumThinVolumeSize;

    // Minimum size of Thin Volume which can be carved out of this Storage Pool in KiloBytes.
    private Long _minimumThinVolumeSize;

    // Maximum size of Thick Volume which can be carved out of this Storage Pool in KiloBytes
    private Long _maximumThickVolumeSize;

    // Minimum size of Thick Volume which can be carved out of this Storage Pool in KiloBytes.
    private Long _minimumThickVolumeSize;

    public static enum ControllerParam {
        PoolType, NativeId,
    }

    public static enum SupportedResourceTypes {
        THICK_ONLY,
        THIN_ONLY,
        THIN_AND_THICK
    }

    private String _supportedResourceTypes;

    public static enum PoolServiceType {
        block,
        file,
        object,
        block_file;
    }

    // tells the type of pool in which it belongs Ex. block, file, object.
    private String _poolServiceType;

    // tells
    public Boolean _longTermRetention;

    // Map of reserved capacity in this storage pool.
    // Key: volume URI, value: capacity
    private StringMap _reservedCapacityMap;

    // Custom properties that are array specific
    private StringMap _customProperties;

    /**********************************************
     * AlternateIDIndex - nativeID [Clariion+APM12345+C+00000] *
     * RelationIndex - StorageDevice *
     * *
     **********************************************/

    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("storageDevice")
    public URI getStorageDevice() {
        return _storageDevice;
    }

    public void setStorageDevice(URI storageDevice) {
        this._storageDevice = storageDevice;
        setChanged("storageDevice");
    }

    @Name("protocols")
    public StringSet getProtocols() {
        return _protocols;
    }

    public void setProtocols(StringSet protocols) {
        _protocols = protocols;
        setChanged("protocols");
    }

    @Name("nativeId")
    public String getNativeId() {
        return _nativeId;
    }

    public void setNativeId(String nativeId) {
        this._nativeId = nativeId;
        setChanged("nativeId");
    }

    @Name("poolName")
    public String getPoolName() {
        return _poolName;
    }

    public void setPoolName(String poolName) {
        _poolName = poolName;
        setChanged("poolName");
    }

    @Name("controllerParams")
    public StringMap getControllerParams() {
        return _controllerParams;
    }

    /**
     * Set extensions map - overwrites existing one
     * 
     * @param controllerParams
     *            StringMap of extensions to set
     */
    public void setControllerParams(StringMap controllerParams) {
        _controllerParams = controllerParams;
        setChanged("controllerParams");
    }

    @Name("operationalStatus")
    public void setOperationalStatus(String operationalStatus) {
        _operationalStatus = operationalStatus;
        setChanged("operationalStatus");
    }

    @Name("operationalStatus")
    public String getOperationalStatus() {
        return _operationalStatus;
    }

    public void setPoolClassName(String poolClassName) {
        _poolClassName = poolClassName;
        setChanged("poolClassName");
    }

    @Name("poolClassName")
    public String getPoolClassName() {
        return _poolClassName;
    }

    @Name("totalCapacity")
    @AggregatedIndex(cf = "AggregatedIndex", classGlobal = true)
    public Long getTotalCapacity() {
        return (_totalCapacity != null) ? _totalCapacity : -1;
    }

    public void setTotalCapacity(Long totalCapacity) {
        if (_totalCapacity == null || !_totalCapacity.equals(totalCapacity)) {
            _totalCapacity = totalCapacity;
            setChanged("totalCapacity");
        }
    }

    @Name("freeCapacity")
    @AggregatedIndex(cf = "AggregatedIndex", classGlobal = true)
    public Long getFreeCapacity() {
        return (_freeCapacity != null) ? _freeCapacity - calculateReservedCapacity() : -1;
    }

    public void setFreeCapacity(Long freeCapacity) {
        if (_freeCapacity == null || !_freeCapacity.equals(freeCapacity)) {
            _freeCapacity = freeCapacity;
            setChanged("freeCapacity");
        }
    }

    public Long calculateFreeCapacityWithoutReservations() {
        return (_freeCapacity != null) ? _freeCapacity : -1;
    }

    @Name("subscribedCapacity")
    @AggregatedIndex(cf = "AggregatedIndex", classGlobal = true)
    public Long getSubscribedCapacity() {
        return ((_subscribedCapacity != null) ? _subscribedCapacity : -1);
    }

    public void setSubscribedCapacity(Long subscribedCapacity) {
        if (_subscribedCapacity == null || !_subscribedCapacity.equals(subscribedCapacity)) {
            _subscribedCapacity = subscribedCapacity;
            setChanged("subscribedCapacity");
        }
    }

    @Name("isResourceLimitSet")
    public Boolean getIsResourceLimitSet() {
        return isResourceLimitSet;
    }

    public void setIsResourceLimitSet(Boolean isResourceLimitSet) {
        this.isResourceLimitSet = isResourceLimitSet;
        setChanged("isResourceLimitSet");
    }

    @Name("maxResources")
    public Integer getMaxResources() {
        return ((isResourceLimitSet) ? maxResources : -1);
    }

    public void setMaxResources(Integer maxResources) {
        this.maxResources = (maxResources > 0) ? maxResources : 0;
        setChanged("maxResources");
    }

    @Name("maxRetention")
    public Integer getMaxRetention() {
        return (null != maxRetention) ? maxRetention : 0;
    }

    public void setMaxRetention(Integer maxRetention) {
        this.maxRetention = (maxRetention > 0) ? maxRetention : 0;
        setChanged("maxRetention");
    }

    @Name("dataCenters")
    public Integer getDataCenters() {
        return (null != dataCenters) ? dataCenters : 0;
    }

    public void setDataCenters(Integer dataCenters) {
        this.dataCenters = (dataCenters > 0) ? dataCenters : 0;
        setChanged("dataCenters");
    }

    public boolean supportsProtocols(Set<String> protocols) {
        if (_protocols == null) {
            return false;
        }
        return _protocols.containsAll(protocols);
    }

    public void addProtocols(Set<String> protocolSet) {
        if (_protocols == null) {
            setProtocols(new StringSet());
        } else {
            _protocols.clear();
        }
        _protocols.addAll(protocolSet);
    }

    public void addControllerParams(Map<String, String> controllerParams) {
        if (controllerParams == null) {
            return;
        }
        setControllerParams(new StringMap());
        _controllerParams.putAll(controllerParams);
    }

    public void setMaximumThinVolumeSize(Long maximumThinVolumeSize) {
        _maximumThinVolumeSize = maximumThinVolumeSize;
        setChanged("maximumThinVolumeSize");
    }

    @Name("maximumThinVolumeSize")
    public Long getMaximumThinVolumeSize() {
        return _maximumThinVolumeSize == null ? 0L : _maximumThinVolumeSize;
    }

    public void setMinimumThinVolumeSize(Long minimumThinVolumeSize) {
        _minimumThinVolumeSize = minimumThinVolumeSize;
        setChanged("minimumThinVolumeSize");
    }

    @Name("minimumThinVolumeSize")
    public Long getMinimumThinVolumeSize() {
        return _minimumThinVolumeSize == null ? 0L : _minimumThinVolumeSize;
    }

    public void setMaximumThickVolumeSize(Long maximumThickVolumeSize) {
        _maximumThickVolumeSize = maximumThickVolumeSize;
        setChanged("maximumThickVolumeSize");
    }

    @Name("maximumThickVolumeSize")
    public Long getMaximumThickVolumeSize() {
        return _maximumThickVolumeSize == null ? 0L : _maximumThickVolumeSize;
    }

    public void setMinimumThickVolumeSize(Long minimumThickVolumeSize) {
        _minimumThickVolumeSize = minimumThickVolumeSize;
        setChanged("minimumThickVolumeSize");
    }

    @Name("minimumThickVolumeSize")
    public Long getMinimumThickVolumeSize() {
        return _minimumThickVolumeSize == null ? 0L : _minimumThickVolumeSize;
    }

    public void setSupportedResourceTypes(String supportedResourceTypes) {
        _supportedResourceTypes = supportedResourceTypes;
        setChanged("supportedResourceTypes");
    }

    /**
     * Get support volume types by the pool.
     * 
     * @return supported volume types
     */
    @EnumType(SupportedResourceTypes.class)
    @Name("supportedResourceTypes")
    public String getSupportedResourceTypes() {
        return _supportedResourceTypes;
    }

    public void setAutoTierSettingId(String autoTierSettingId) {
        _autoTierSettingId = autoTierSettingId;
        setChanged("autoTierSettingId");
    }

    @Name("autoTierSettingId")
    public String getAutoTierSettingId() {
        return _autoTierSettingId;
    }

    public void setNoDataMovementId(String noDataMovementId) {
        _noDataMovementId = noDataMovementId;
        setChanged("noDataMovementTierSettingId");
    }

    @Name("noDataMovementTierSettingId")
    public String getNoDataMovementId() {
        return _noDataMovementId;
    }

    public void setHighAvailableTierId(String highAvailableTierId) {
        _highAvailableTierId = highAvailableTierId;
        setChanged("highAvailabilityTierSettingId");
    }

    @Name("highAvailabilityTierSettingId")
    public String getHighAvailableTierId() {
        return _highAvailableTierId;
    }

    public void setLowAvailableTierId(String lowAvailableTierId) {
        _lowAvailableTierId = lowAvailableTierId;
        setChanged("lowAvailabilityTierSettingId");
    }

    @Name("lowAvailabilityTierSettingId")
    public String getLowAvailableTierId() {
        return _lowAvailableTierId;
    }

    public void setStartHighThenAutoTierId(String startHighThenAutoTierId) {
        _startHighThenAutoTierId = startHighThenAutoTierId;
        setChanged("startHighThenAutoTierSettingId");
    }

    @Name("startHighThenAutoTierSettingId")
    public String getStartHighThenAutoTierId() {
        return _startHighThenAutoTierId;
    }

    public void addSupportedRaidLevels(Set<String> raidLevels) {
        if (null == _supportedRaidLevels) {
            _supportedRaidLevels = new StringSet();
        } else {
            _supportedRaidLevels.clear();
        }

        if (!raidLevels.isEmpty()) {
            _supportedRaidLevels.addAll(raidLevels);
        }

    }

    public void setSupportedRaidLevels(StringSet raidLevels) {
        _supportedRaidLevels = raidLevels;
        setChanged("supportedRaidLevels");
    }

    @Name("supportedRaidLevels")
    public StringSet getSupportedRaidLevels() {
        return _supportedRaidLevels;
    }

    @EnumType(PoolServiceType.class)
    @Name("poolServiceType")
    public String getPoolServiceType() {
        return _poolServiceType;
    }

    public void setPoolServiceType(String poolServiceType) {
        _poolServiceType = poolServiceType;
        setChanged("poolServiceType");
    }

    @Name("longTermRetention")
    public Boolean getLongTermRetention() {
        return (_longTermRetention != null) ?
                _longTermRetention : false;
    }

    public void setLongTermRetention(Boolean longTermRetention) {
        _longTermRetention = longTermRetention;
        setChanged("longTermRetention");
    }

    public void addTiers(Set<String> tiers) {
        if (null != _tiers) {
            _tiers.clear();
        } else {
            setTiers(new StringSet());
        }
        if (!tiers.isEmpty()) {
            _tiers.addAll(tiers);
        }
    }

    public void setTiers(StringSet tiers) {
        _tiers = tiers;
        setChanged("tiers");
    }

    @Name("tiers")
    public StringSet getTiers() {
        return _tiers;
    }

    public void addDriveTypes(Set<String> driveTypes) {
        if (null != _supportedDriveTypes) {
            _supportedDriveTypes.clear();
        } else {
            setSupportedDriveTypes(new StringSet());
        }
        if (!driveTypes.isEmpty()) {
            _supportedDriveTypes.addAll(driveTypes);
        }
    }

    public void setSupportedDriveTypes(StringSet supportedDriveTypes) {
        _supportedDriveTypes = supportedDriveTypes;
        setChanged("supportedDriveTypes");
    }

    @AlternateId("DriveTypeToPools")
    @EnumType(SupportedDriveTypeValues.class)
    @Name("supportedDriveTypes")
    public StringSet getSupportedDriveTypes() {
        return _supportedDriveTypes;
    }

    public void setSupportedCopyTypes(StringSet supportedCopyTypes) {
        _supportedCopyTypes = supportedCopyTypes;
        setChanged("supportedCopyTypes");
    }

    @EnumType(CopyTypes.class)
    @Name("supportedCopyTypes")
    public StringSet getSupportedCopyTypes() {
        return _supportedCopyTypes;
    }

    public void addTierUtilizationPercentage(Map<String, String> tierUtilizationPercentage) {
        if (null == _tierUtilizationPercentage) {
            setTierUtilizationPercentage(new StringMap());
        } else {
            _tierUtilizationPercentage.clear();
        }
        if (tierUtilizationPercentage.size() > 0) {
            _tierUtilizationPercentage.putAll(tierUtilizationPercentage);
        }

    }

    public void setTierUtilizationPercentage(StringMap tierUtilizationPercentage) {
        this._tierUtilizationPercentage = tierUtilizationPercentage;
        setChanged("tierUtilizationPercentage");
    }

    @Name("tierUtilizationPercentage")
    public StringMap getTierUtilizationPercentage() {
        return _tierUtilizationPercentage;
    }

    @Name("maxPoolUtilizationPercentage")
    public Integer getMaxPoolUtilizationPercentage() {
        return _maxPoolUtilizationPercentage;
    }

    public void setMaxPoolUtilizationPercentage(Integer maxPoolUtilizationPercentage) {
        _maxPoolUtilizationPercentage = maxPoolUtilizationPercentage;
        setChanged("maxPoolUtilizationPercentage");
    }

    @Name("maxThinPoolSubscriptionPercentage")
    public Integer getMaxThinPoolSubscriptionPercentage() {
        return _maxThinPoolSubscriptionPercentage;
    }

    public void setMaxThinPoolSubscriptionPercentage(Integer maxThinPoolSubscriptionPercentage) {
        _maxThinPoolSubscriptionPercentage = maxThinPoolSubscriptionPercentage;
        setChanged("maxThinPoolSubscriptionPercentage");
    }

    @Name("maxThinPoolSubscriptionPercentageFromArray")
    public Integer getMaxThinPoolSubscriptionPercentageFromArray() {
        return _maxThinPoolSubscriptionPercentageFromArray;
    }

    public void setMaxThinPoolSubscriptionPercentageFromArray(Integer maxThinPoolSubscriptionPercentageFromArray) {
        _maxThinPoolSubscriptionPercentageFromArray = maxThinPoolSubscriptionPercentageFromArray;
        setChanged("maxThinPoolSubscriptionPercentageFromArray");
    }

    public void setRegistrationStatus(String registrationStatus) {
        _registrationStatus = registrationStatus;
        setChanged("registrationStatus");
    }

    @EnumType(RegistrationStatus.class)
    @Name("registrationStatus")
    public String getRegistrationStatus() {
        return _registrationStatus;
    }

    @Name("thinVolumePreAllocationSupported")
    public Boolean getThinVolumePreAllocationSupported() {
        return thinVolumePreAllocationSupported;
    }

    public void setThinVolumePreAllocationSupported(Boolean thinVolumePreAllocationSupported) {
        this.thinVolumePreAllocationSupported = thinVolumePreAllocationSupported;
        setChanged("thinVolumePreAllocationSupported");
    }

    @Ttl(60 * 10)
    // set to 10 minutes
            @Name("reservedCapacityMap")
            public
            StringMap getReservedCapacityMap() {
        if (_reservedCapacityMap == null) {
            _reservedCapacityMap = new StringMap();
        }
        return _reservedCapacityMap;
    }

    public void setReservedCapacityMap(StringMap reservedCapacityMap) {
        this._reservedCapacityMap = reservedCapacityMap;
        setChanged("reservedCapacityMap");
    }

    @Name("customProperties")
    public StringMap getCustomProperties() {
        if (_customProperties == null) {
            _customProperties = new StringMap();
        }
        return _customProperties;
    }

    public void setCustomProperties(StringMap customProperties) {
        this._customProperties = customProperties;
    }

    @EnumType(CompatibilityStatus.class)
    @Name("compatibilityStatus")
    public String getCompatibilityStatus() {
        return _compatibilityStatus;
    }

    public void setCompatibilityStatus(String compatibilityStatus) {
        _compatibilityStatus = compatibilityStatus;
        setChanged("compatibilityStatus");
    }

    public void setAutoTieringEnabled(final Boolean autoTieringEnabled) {
        this.autoTieringEnabled = autoTieringEnabled;
        setChanged("autoTieringEnabled");
    }

    @Name("autoTieringEnabled")
    public Boolean getAutoTieringEnabled() {
        return this.autoTieringEnabled == null ? false : autoTieringEnabled;
    }

    @Name("avgStorageDevicePortMetrics")
    public Double getAvgStorageDevicePortMetrics() {
        return _avgStorageDevicePortMetrics;
    }

    public void setAvgStorageDevicePortMetrics(Double avgStorageDevicePortMetrics) {
        this._avgStorageDevicePortMetrics = avgStorageDevicePortMetrics;
        setChanged("avgStorageDevicePortMetrics");
    }

    @EnumType(DiscoveryStatus.class)
    @Name("discoveryStatus")
    public String getDiscoveryStatus() {
        return _discoveryStatus;
    }

    public void setDiscoveryStatus(String discoveryStatus) {
        this._discoveryStatus = discoveryStatus;
        setChanged("discoveryStatus");
    }

    /**
     * Helper method to get total reserved capacity of the pool.
     * 
     * @return reserved capacity in KBytes
     */
    private Long calculateReservedCapacity() {
        Long reservedCapacity = 0L;
        if (_reservedCapacityMap != null) {
            Collection<String> capacityCollection = _reservedCapacityMap.values();
            for (String capacity : capacityCollection) {
                reservedCapacity = reservedCapacity + Long.valueOf(capacity);
            }
        }

        Long reservedCapacityKB = (reservedCapacity % KB == 0) ? reservedCapacity / KB : reservedCapacity / KB + 1;
        return reservedCapacityKB; // in KB
    }

    /**
     * Removes each mapping from the reservedCapacityMap
     * 
     * @param objectIdStrings - list of Volume/BlockObject ids
     */
    public void removeReservedCapacityForVolumes(Collection<String> objectIdStrings) {
        if (objectIdStrings != null && _reservedCapacityMap != null) {
            for (String idString : objectIdStrings) {
                _reservedCapacityMap.remove(idString);
            }
        }
    }
}
