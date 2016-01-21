/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlEnumValue;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.valid.EnumType;

/**
 * VirtualPool data object (like gold, silver, etc.)
 */
/**
 * @author belliott
 */
@Cf("VirtualPool")
public class VirtualPool extends DataObjectWithACLs implements GeoVisibleResource {
    // service type
    private String _type;
    // brief description for this VirtualPool
    private String _description;
    // storage protocols supported by this VirtualPool
    private StringSet _protocols;
    // number of multiple paths to access storage, block only
    private Integer _numPaths;
    // minimum number of paths to access storage, block only
    private Integer _minPaths;
    // number of paths to access storage per Initiator, block only
    private Integer _pathsPerInitiator;
    // VirtualArrays where this VirtualPool is available
    private StringSet _virtualArrays;
    // optional VirtualPool reference used for mirror placement
    private String _mirrorVirtualPool;
    // reference to additional VirtualPool values used to overlay object on file
    private String _refVirtualPool;
    // fast Policy Name
    private String _autoTierPolicyName;
    // Indicates the high availability type for the VirtualPool.
    private String _highAvailability;
    // Thin or Thick or ThinandThick
    // combination of provisioningType & fast indicates FAST_VP or FAST_DP
    // Thin & Fast_ON --> FAST_VP
    private String _provisioningType;
    // contains Device Type Information (VMAX,VNX) & Raid Levels
    private StringSetMap _arrayInfo;
    // desired Initial Tier, where the volume needs to be created.
    private String _driveType;
    // VPlex high availability varray vpool Map.
    private StringMap _haVarrayVpoolMap;
    // RP source journal size policy
    private String _journalSize;
    // RP source journal Virtual Array (Primary/Active site in case of MetroPoint)
    private String _journalVarray;
    // RP source journal Virtual Pool (Primary/Active in case of MetroPoint)
    private String _journalVpool;
    // Stand-by journal varray (applies only to MetroPoint configurations)
    private String _standbyJournalVarray;
    // Stand-by journal virtual pool (applies only to MetroPoint configurations)
    private String _standbyJournalVpool;
    // RP CG RPO value
    private Long _rpRpoValue;
    // RP CG RPO type
    private String _rpRpoType;
    // RP Copy Mode
    private String _rpCopyMode;
    // Protection policy information
    private StringMap _protectionVarraySettings;
    // SRDF Protection
    private StringMap _protectionRemoteCopySettings;
    // percentage to specify thinVolumePreAllocateSize during provisioning.
    private Integer _thinVolumePreAllocationPercentage;
    private Long _quotaGB;
    private Boolean _quotaEnabled;
    // flag to specify whether the volume should be added to a BlockConsistencyGroup
    private Boolean _multivolumeconsistency = false;
    // Standard values to use max integer fields as specifiers whether it's enabled, unlimited.
    public final static int MAX_UNLIMITED = -1;
    public final static int MAX_DISABLED = 0;
    // Maximum number of native snapshots allowed (0 == disabled, -1 == unlimited)
    private Integer _maxNativeSnapshots;
    // Maximum number of native continuous copies allowed (0 == disabled, -1 == unlimited)
    private Integer _maxNativeContinuousCopies;
    // This attribute is applicable only to Block Systems.
    // If uniquePolicyNames is true, then only unique Auto Tiering Policy Names will be returned.
    // else all policies will be returned.
    private Boolean uniquePolicyNames = false;
    // Long term retention indicates if this vPool is to be used for archiving and other
    // long term retention activities
    private Boolean _longTermRetention = false;
    // This attribute is applicable only to VPlex Distributed
    // vpools that also have RP protection specified.
    // This is optionally set if the HA varray has connectivity to RP
    // and the Source varray does not.
    private String haVarrayConnectedToRp;
    // Flag that specifies whether or not to enable MetroPoint configuration.
    private Boolean metroPoint = false;
    // Flag that enables VPlex automatic cross-connected export, default disabled
    private Boolean autoCrossConnectExport = false;
    // Max retention for a Virtual Pool
    private Integer maxRetention;

    public static enum MetroPointType {
        @XmlEnumValue("singleRemote")
        SINGLE_REMOTE,
        @XmlEnumValue("localOnly")
        LOCAL_ONLY,
        @XmlEnumValue("localRemote")
        ONE_LOCAL_REMOTE,
        @XmlEnumValue("twoLocalRemote")
        TWO_LOCAL_REMOTE,
        @XmlEnumValue("invalid")
        INVALID
    }

    // VMAX Host IO Limits attributes
    private Integer _hostIOLimitBandwidth; // Host Front End limit bandwidth. If not specfied or 0, inidicated unlimited
    private Integer _hostIOLimitIOPs; // Host Front End limit I/O. If not specified or 0, indicated unlimited

    /**
     * RPOType enum, modeled after RP's QuantityType object. That allows us to be able to pass the values
     * down to the RP appliance without having to jump through any hoops, like translations, etc.
     */
    public static enum RPOType {
        @XmlEnumValue("microseconds")
        MICROSECONDS("microseconds"),
        @XmlEnumValue("milliseconds")
        MILLISECONDS("milliseconds"),
        @XmlEnumValue("seconds")
        SECONDS("seconds"),
        @XmlEnumValue("minutes")
        MINUTES("minutes"),
        @XmlEnumValue("hours")
        HOURS("hours"),
        @XmlEnumValue("days")
        DAYS("days"),
        @XmlEnumValue("bytes")
        BYTES("bytes"),
        KB("KB"),
        MB("MB"),
        GB("GB"),
        TB("TB"),
        @XmlEnumValue("writes")
        WRITES("writes"),
        @XmlEnumValue("Unknown")
        UNKNOWN("Unknown");
        private final String _value;

        RPOType(String v) {
            _value = v;
        }

        public String value() {
            return _value;
        }

        public static RPOType fromValue(final String v) {
            RPOType returnVal = lookup(v);
            if (returnVal == null) {
                throw new IllegalArgumentException(v);
            }
            return returnVal;
        }

        public static final RPOType[] copyOfValues = values();

        public static RPOType lookup(final String name) {
            for (RPOType value : copyOfValues) {
                if (value.name().equals(name)) {
                    return value;
                }
                if (value.value().equals(name)) {
                    return value;
                }
            }
            return null;
        }
    };

    /**
     * RPCopyMode enum, modeled after RP's ProtectionMode object. That allows us to be able to pass the values
     * down to the RP appliance without having to jump through any hoops, like translations, etc.
     */
    public static enum RPCopyMode {
        @XmlEnumValue("Asynchronous")
        ASYNCHRONOUS("Asynchronous"),
        @XmlEnumValue("Synchronous")
        SYNCHRONOUS("Synchronous"),
        @XmlEnumValue("Unknown")
        UNKNOWN("Unknown");
        private final String _value;

        RPCopyMode(String v) {
            _value = v;
        }

        public String value() {
            return _value;
        }

        public static RPCopyMode fromValue(String v) {
            RPCopyMode returnVal = lookup(v);
            if (returnVal == null) {
                throw new IllegalArgumentException(v);
            }
            return returnVal;
        }

        public static final RPCopyMode[] copyOfValues = values();

        public static RPCopyMode lookup(final String name) {
            for (RPCopyMode value : copyOfValues) {
                if (value.name().equals(name)) {
                    return value;
                }
                if (value.value().equals(name)) {
                    return value;
                }
            }
            return null;
        }
    };

    public static enum SupportedDriveTypes {
        NONE, SSD, FC, SAS, SATA, NL_SAS, UNKNOWN;
        private static final SupportedDriveTypes[] copyOfValues = values();

        public static SupportedDriveTypes lookup(final String name) {
            for (SupportedDriveTypes value : copyOfValues) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return null;
        }
    }

    public static enum ProvisioningType {
        NONE, Thin, Thick;
        public static ProvisioningType lookup(final String name) {
            for (ProvisioningType value : values()) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return null;
        }
    }

    public static enum RaidLevel {
        RAID0, RAID1, RAID2, RAID3, RAID4, RAID5, RAID6, RAID10;
        private static final RaidLevel[] copyOfValues = values();

        public static RaidLevel lookup(final String name) {
            for (RaidLevel value : copyOfValues) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return null;
        }
    }

    public static enum SystemType {
        NONE, isilon, vnxblock, vnxfile, vmax, netapp, netappc, hds, openstack, vnxe, scaleio, datadomain, xtremio, ibmxiv, ecs;
        private static final SystemType[] copyOfValues = values();

        public static SystemType lookup(final String name) {
            for (SystemType value : copyOfValues) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return null;
        }

        public static boolean isFileTypeSystem(final String name) {
            return isilon.name().equalsIgnoreCase(name)
                    || vnxfile.name().equalsIgnoreCase(name)
                    || netapp.name().equalsIgnoreCase(name)
                    || netappc.name().equalsIgnoreCase(name)
                    || vnxe.name().equalsIgnoreCase(name)
                    || datadomain.name().equalsIgnoreCase(name);
        }
        
        public static boolean isBlockTypeSystem(final String name) {
            return vnxblock.name().equalsIgnoreCase(name) || vmax.name().equalsIgnoreCase(name)
                    || hds.name().equalsIgnoreCase(name) || openstack.name().equalsIgnoreCase(name)
                    || scaleio.name().equalsIgnoreCase(name) || xtremio.name().equalsIgnoreCase(name)
                    || ibmxiv.name().equalsIgnoreCase(name) || vnxe.name().equalsIgnoreCase(name);
        }
        
        public static boolean isObjectTypeSystem(final String name) {
            return ecs.name().equalsIgnoreCase(name);
        }

    }

    // flag tells whether to use recommended pools or not.
    private Boolean _useMatchedPools;
    // reference to hold the implicit pools recommended for this VirtualPool.
    private StringSet _matchedStoragePools;
    // reference to hold user assigned pools.
    private StringSet _assignedStoragePools;
    // reference to hold invalid matched pools.
    private StringSet _invalidMatchedPools;
    // defines if volume expansion should be supported
    // default to false
    private Boolean _expandable = false;
    private Boolean _nonDisruptiveExpansion = true;
    // By default fast expansion is false --- we create meta volume as striped by default (for example when vpool is not 'expandable')
    private Boolean _fastExpansion = false;

    // names to be used in type field
    public static enum Type {
        block, file, object;
        private static final Type[] vpoolTypeValues = values();

        public static Type lookup(final String name) {
            for (Type value : vpoolTypeValues) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return null;
        }
    }

    // VirtualPool parameter names
    public static enum Param {
        numPaths, pathsPerInitiator, minPaths, auto_tier_policy, protection, raid_level, refVirtualPool
    }

    // Supported types for high availability
    public static enum HighAvailabilityType {
        vplex_distributed, vplex_local
    };

    // VirtualPool protection enums
    public static enum Protection {
        rp, local
    }

    @AlternateId("AltIdIndex")
    @Name("type")
    public String getType() {
        return _type;
    }

    public void setType(final String type) {
        _type = type;
        setChanged("type");
    }

    @Name("description")
    public String getDescription() {
        return _description;
    }

    public void setDescription(final String description) {
        _description = description;
        setChanged("description");
    }

    /**
     * Get supported protocols
     * 
     * @return StringSet of protocols
     */
    @Name("protocols")
    public StringSet getProtocols() {
        return _protocols;
    }

    /**
     * Set storage protocols supported by this VirtualPool
     * 
     * @param protocols
     *            Set of storage protocols for this VirtualPool
     */
    public void setProtocols(final StringSet protocols) {
        _protocols = protocols;
        setChanged("protocols");
    }

    /**
     * Add a protocols to list of protocols in VirtualPool.
     * 
     * @param protocols
     */
    public void addProtocols(final Set<String> protocols) {
        if (null == _protocols) {
            setProtocols(new StringSet());
        }
        if (!protocols.isEmpty()) {
            _protocols.addAll(protocols);
        }
    }

    /**
     * remove the protocols from set of protocols in VirtualPool.
     * 
     * @param protocols
     */
    public void removeProtocols(final Set<String> protocols) {
        if (protocols != null && _protocols != null) {
            HashSet<String> removeProtocols = new HashSet<String>();
            removeProtocols.addAll(protocols);
            _protocols.removeAll(removeProtocols);
        }
    }

    @Name("numPaths")
    public Integer getNumPaths() {
        return _numPaths;
    }

    public void setNumPaths(final Integer numPaths) {
        _numPaths = numPaths;
        setChanged("numPaths");
    }

    @Name("pathsPerInitiator")
    public Integer getPathsPerInitiator() {
        return _pathsPerInitiator;
    }

    public void setPathsPerInitiator(final Integer pathsPerInitiator) {
        this._pathsPerInitiator = pathsPerInitiator;
        setChanged("pathsPerInitiator");
    }

    @Name("minPaths")
    public Integer getMinPaths() {
        return _minPaths;
    }

    public void setMinPaths(final Integer minPaths) {
        this._minPaths = minPaths;
        setChanged("minPaths");
    }

    @RelationIndex(cf = "RelationIndex", type = VirtualArray.class)
    @IndexByKey
    @Name("virtualArrays")
    public StringSet getVirtualArrays() {
        return _virtualArrays;
    }

    public void setVirtualArrays(final StringSet virtualArrays) {
        _virtualArrays = virtualArrays;
        setChanged("virtualArrays");
    }

    @RelationIndex(cf = "VpoolProtRelationIndex", type = VpoolProtectionVarraySettings.class)
    @IndexByKey
    @Name("protectionVarraySettings")
    public StringMap getProtectionVarraySettings() {
        return _protectionVarraySettings;
    }

    public void setProtectionVarraySettings(final StringMap protectionVarraySettings) {
        _protectionVarraySettings = protectionVarraySettings;
        setChanged("protectionVarraySettings");
    }

    public void addVirtualArrays(final Set<String> vArrayURIs) {
        if (vArrayURIs != null && !vArrayURIs.isEmpty()) {
            // Must be a HashSet to ensure AbstractChangeTrackingSet
            // addAll method is invoked, else base class method
            // is invoked.
            HashSet<String> addVarrays = new HashSet<String>();
            addVarrays.addAll(vArrayURIs);
            if (_virtualArrays == null) {
                setVirtualArrays(new StringSet());
                _virtualArrays.addAll(addVarrays);
            } else {
                _virtualArrays.addAll(addVarrays);
            }
        }
    }

    public void removeVirtualArrays(final Set<String> varrayURIs) {
        if (varrayURIs != null && !varrayURIs.isEmpty() && _virtualArrays != null) {
            // Must be a HashSet to ensure AbstractChangeTrackingSet
            // removeAll method is invoked, else base class method
            // is invoked.
            HashSet<String> removeVarrays = new HashSet<String>();
            removeVarrays.addAll(varrayURIs);
            _virtualArrays.removeAll(removeVarrays);
        }
    }

    @Name("refVirtualPool")
    public String getRefVirtualPool() {
        return _refVirtualPool;
    }

    public void setRefVirtualPool(final String refVirtualPool) {
        _refVirtualPool = refVirtualPool;
        setChanged("refVirtualPool");
    }

    @Name("highAvailability")
    public String getHighAvailability() {
        return _highAvailability;
    }

    public void setHighAvailability(final String highAvailability) {
        _highAvailability = highAvailability;
        setChanged("highAvailability");
    }

    public void setSupportedProvisioningType(final String provisioningType) {
        _provisioningType = provisioningType;
        setChanged("provisioningType");
    }

    @Name("provisioningType")
    public String getSupportedProvisioningType() {
        return _provisioningType;
    }

    @Name("useMatchedPools")
    public Boolean getUseMatchedPools() {
        return _useMatchedPools;
    }

    public void setUseMatchedPools(final Boolean useMatchedPools) {
        _useMatchedPools = useMatchedPools;
        setChanged("useMatchedPools");
    }

    @Name("matchedPools")
    @RelationIndex(cf = "MatchedPoolsToVpool", type = StoragePool.class)
    @IndexByKey
    public StringSet getMatchedStoragePools() {
        return _matchedStoragePools;
    }

    public void setMatchedStoragePools(final StringSet matchedStoragePools) {
        _matchedStoragePools = matchedStoragePools;
    }

    @Name("assignedStoragePools")
    @RelationIndex(cf = "AssignedPoolsToVpool", type = StoragePool.class)
    @IndexByKey
    public StringSet getAssignedStoragePools() {
        return _assignedStoragePools;
    }

    public void setAssignedStoragePools(final StringSet assignedStoragePools) {
        _assignedStoragePools = assignedStoragePools;
    }

    /**
     * Add all passed assigned pool URI to VirtualPool
     * 
     * @param assignedPools
     */
    public void addAssignedStoragePools(final Set<String> assignedPools) {
        if (null != _assignedStoragePools) {
            _assignedStoragePools.replace(assignedPools);
        } else {
            if (null != assignedPools && !assignedPools.isEmpty()) {
                setAssignedStoragePools(new StringSet());
                _assignedStoragePools.addAll(assignedPools);
            }
        }
    }

    /**
     * This method is used to update the assignedPools.
     * 
     * @param poolsToUpdate
     */
    public void updateAssignedStoragePools(final Set<String> poolsToUpdate) {
        if (null == _assignedStoragePools) {
            setAssignedStoragePools(new StringSet());
        }
        _assignedStoragePools.addAll(poolsToUpdate);
    }

    /**
     * Add all passed matched pool URI to VirtualPool. Clear if there are any existing pools in db
     * before updating.
     * 
     * @param matchedPools
     */
    public void addMatchedStoragePools(final Set<String> matchedPools) {
        if (null != _matchedStoragePools) {
            _matchedStoragePools.replace(matchedPools);
        } else {
            if (null != matchedPools && !matchedPools.isEmpty()) {
                setMatchedStoragePools(new StringSet());
                _matchedStoragePools.addAll(matchedPools);
            }
        }
    }

    /**
     * Removes the passed pool URIs from the set of storage pools assigned to the storage pool by
     * the user.
     * 
     * @param storagePoolURIs
     *            The URIs of the storage pools to be removed from the storage pool.
     */
    public void removeAssignedStoragePools(final Set<String> storagePoolURIs) {
        if (storagePoolURIs != null && !storagePoolURIs.isEmpty() && _assignedStoragePools != null) {
            // Must be a HashSet to ensure AbstractChangeTrackingSet
            // removeAll method is invoked, else base class method
            // is invoked.
            HashSet<String> removeStoragePools = new HashSet<String>();
            removeStoragePools.addAll(storagePoolURIs);
            _assignedStoragePools.removeAll(removeStoragePools);
        }
    }

    @Name("invalidMatchedPools")
    @RelationIndex(cf = "InvalidPoolsToVpool", type = StoragePool.class)
    @IndexByKey
    public StringSet getInvalidMatchedPools() {
        return _invalidMatchedPools;
    }

    public void setInvalidMatchedPools(final StringSet invalidMatchedPools) {
        _invalidMatchedPools = invalidMatchedPools;
    }

    /**
     * Add all passed invalid pool URI to VirtualPOol. Clear if there are any existing pools in db
     * before updating.
     * 
     * @param invalidPools
     */
    public void addInvalidMatchedPools(final Set<String> invalidPools) {
        if (null != _invalidMatchedPools) {
            _invalidMatchedPools.replace(invalidPools);
        } else {
            if (null != invalidPools && !invalidPools.isEmpty()) {
                setInvalidMatchedPools(new StringSet());
                _invalidMatchedPools.addAll(invalidPools);
            }
        }
    }

    public void addArrayInfoDetails(final StringSetMap arrayInfo) {
        if (null != _arrayInfo) {
            _arrayInfo.clear();
        } else {
            setArrayInfo(new StringSetMap());
        }
        if (null != arrayInfo && arrayInfo.size() > 0) {
            _arrayInfo.putAll(arrayInfo);
        }
    }

    public void setArrayInfo(final StringSetMap arrayInfo) {
        _arrayInfo = arrayInfo;
    }

    @Name("arrayInfo")
    public StringSetMap getArrayInfo() {
        return _arrayInfo;
    }

    public void setDriveType(final String driveType) {
        _driveType = driveType;
        setChanged("driveType");
    }

    @EnumType(SupportedDriveTypes.class)
    @Name("driveType")
    public String getDriveType() {
        return _driveType;
    }

    public void setJournalSize(final String journalSize) {
        _journalSize = journalSize;
        setChanged("journalSize");
    }

    @Name("journalSize")
    public String getJournalSize() {
        return _journalSize;
    }

    @Name("journalVarray")
    public String getJournalVarray() {
        return _journalVarray;
    }

    public void setJournalVarray(String _journalVarray) {
        this._journalVarray = _journalVarray;
        setChanged("journalVarray");
    }

    @Name("journalVpool")
    public String getJournalVpool() {
        return _journalVpool;
    }

    public void setJournalVpool(String _journalVpool) {
        this._journalVpool = _journalVpool;
        setChanged("journalVpool");
    }

    @Name("standbyJournalVarray")
    public String getStandbyJournalVarray() {
        return _standbyJournalVarray;
    }

    public void setStandbyJournalVarray(String _standbyJournalVarray) {
        this._standbyJournalVarray = _standbyJournalVarray;
        setChanged("standbyJournalVarray");
    }

    @Name("standbyJournalVpool")
    public String getStandbyJournalVpool() {
        return _standbyJournalVpool;
    }

    public void setStandbyJournalVpool(String _standbyJournalVpool) {
        this._standbyJournalVpool = _standbyJournalVpool;
        setChanged("standbyJournalVpool");
    }

    @AlternateId("AltIdIndex")
    @Name("mirrorVirtualPool")
    public String getMirrorVirtualPool() {
        return _mirrorVirtualPool;
    }

    public void setMirrorVirtualPool(final String mirrorVirtualPool) {
        _mirrorVirtualPool = mirrorVirtualPool;
        setChanged("mirrorVirtualPool");
    }

    @Name("maxNativeSnapshots")
    public Integer getMaxNativeSnapshots() {
        return _maxNativeSnapshots;
    }

    public void setMaxNativeSnapshots(final Integer maxNativeSnapshots) {
        _maxNativeSnapshots = maxNativeSnapshots > 0 ? maxNativeSnapshots : 0;
        setChanged("maxNativeSnapshots");
    }

    @Name("maxNativeContinuousCopies")
    public Integer getMaxNativeContinuousCopies() {
        return _maxNativeContinuousCopies;
    }

    public void setMaxNativeContinuousCopies(final Integer maxNativeContinuousCopies) {
        _maxNativeContinuousCopies = maxNativeContinuousCopies > 0 ? maxNativeContinuousCopies : 0;
        setChanged("maxNativeContinuousCopies");
    }

    public void setAutoTierPolicyName(final String autoTierPolicyName) {
        _autoTierPolicyName = autoTierPolicyName;
        setChanged("autoTierPolicyName");
    }

    @Name("autoTierPolicyName")
    public String getAutoTierPolicyName() {
        return _autoTierPolicyName;
    }

    public boolean checkRpRpoValueSet() {
        return _rpRpoValue != null && _rpRpoValue > 0;
    }

    @Name("rpRpoValue")
    public Long getRpRpoValue() {
        return _rpRpoValue;
    }

    public void setRpRpoValue(Long rpRpoValue) {
        this._rpRpoValue = rpRpoValue;
        setChanged("rpRpoValue");
    }

    @Name("rpRpoType")
    public String getRpRpoType() {
        return _rpRpoType;
    }

    public void setRpRpoType(String rpRpoType) {
        this._rpRpoType = rpRpoType;
        setChanged("rpRpoType");
    }

    @Name("rpCopyMode")
    public String getRpCopyMode() {
        return _rpCopyMode;
    }

    public void setRpCopyMode(String rpCopyMode) {
        this._rpCopyMode = rpCopyMode;
        setChanged("rpCopyMode");
    }

    @Name("haVarrayVpoolMap")
    public StringMap getHaVarrayVpoolMap() {
        return _haVarrayVpoolMap;
    }

    public void setHaVarrayVpoolMap(final StringMap haVarrayVpoolMap) {
        _haVarrayVpoolMap = haVarrayVpoolMap;
        setChanged("haVarrayVpoolMap");
    }

    @Name("expandable")
    public Boolean getExpandable() {
        return _expandable;
    }

    public void setExpandable(final Boolean expandable) {
        this._expandable = expandable;
        setChanged("expandable");
    }

    @Name("fastExpansion")
    public Boolean getFastExpansion() {
        return _fastExpansion;
    }

    public void setFastExpansion(final Boolean fastExpansion) {
        _fastExpansion = fastExpansion;
        setChanged("fastExpansion");
    }

    /**
     * Returns whether or not the passed VirtualPool specifies VPlex high availability.
     * 
     * @param virtualPool
     *            A reference to the VirtualPool.
     * @return true if the VirtualPool specifies VPlex high availability, false otherwise.
     */
    public static boolean vPoolSpecifiesHighAvailability(final VirtualPool virtualPool) {
        String highAvailability = virtualPool.getHighAvailability();
        return highAvailability != null
                && (VirtualPool.HighAvailabilityType.vplex_local.name().equals(highAvailability) || VirtualPool.HighAvailabilityType.vplex_distributed
                        .name().equals(highAvailability));
    }

    /**
     * Returns whether or not the passed VirtualPool specifies VPlex high availability.
     * 
     * @param virtualPool
     *            A reference to the VirtualPool.
     * @return true if the VirtualPool specifies VPlex high availability, false otherwise.
     */
    public static boolean vPoolSpecifiesHighAvailabilityDistributed(final VirtualPool virtualPool) {
        String highAvailability = virtualPool.getHighAvailability();
        return highAvailability != null
                && (VirtualPool.HighAvailabilityType.vplex_distributed.name().equals(highAvailability));
    }
    
    /**
     * Returns whether or not the passed VirtualPool specifies MetroPoint.  This requires
     * the MetroPoint flag to be enabled along with RP protection and VPLex distributed.
     * 
     * @param virtualPool A reference to the VirtualPool
     * @return true if the VirtualPool specifies MetroPoint, false otherwise.
     */
    public static boolean vPoolSpecifiesMetroPoint(final VirtualPool virtualPool) {
        Boolean metroPoint = virtualPool.getMetroPoint();
        String highAvailability = virtualPool.getHighAvailability();
        return metroPoint != null && metroPoint
                && vPoolSpecifiesProtection(virtualPool)
                && highAvailability != null && VirtualPool.HighAvailabilityType.vplex_distributed.name().equals(highAvailability);
    }

    /**
     * Returns whether or not the passed VirtualPool specifies Protection
     * 
     * @param virtualPool
     *            A reference to the VirtualPool.
     * @return true if the VirtualPool specifies RP protection, false otherwise.
     */
    public static boolean vPoolSpecifiesProtection(final VirtualPool virtualPool) {
        return virtualPool.getProtectionVarraySettings() != null
                && !virtualPool.getProtectionVarraySettings().isEmpty();
    }

    /**
     * @param virtualPool
     *            A reference to the VirtualPool.
     * @return true if the VirtualPool specifies RP + VPLEX, false otherwise.
     */
    public static boolean vPoolSpecifiesRPVPlex(final VirtualPool virtualPool) {
        return (vPoolSpecifiesProtection(virtualPool)
        && vPoolSpecifiesHighAvailability(virtualPool));
    }

    /**
     * Convenience method to determine if the Virtual Pool supports mirrors
     * 
     * @param virtualPool
     *            Virtual Pool
     * @return true if supports mirrors
     */
    public static boolean vPoolSpecifiesMirrors(final VirtualPool virtualPool, DbClient dbClient) {
        if (virtualPool.getHighAvailability() != null
                && virtualPool.getHighAvailability().equals(VirtualPool.HighAvailabilityType.vplex_distributed.name())) {
            boolean supportsMirror = false;
            if (virtualPool.getMaxNativeContinuousCopies() == null) {
                supportsMirror = false;
            } else {
                supportsMirror = virtualPool.getMaxNativeContinuousCopies() != VirtualPool.MAX_DISABLED
                        && virtualPool.getMirrorVirtualPool() != null;
            }
            if (supportsMirror) {
                // If source side supports mirror then just return
                return supportsMirror;
            } else {
                // If we are here means source side do not support mirrors
                // hence we need to check HA side as well
                VirtualPool haVpool = getHAVPool(virtualPool, dbClient);
                if (haVpool == null) {
                    supportsMirror = false;
                } else {
                    supportsMirror = haVpool.getMaxNativeContinuousCopies() != VirtualPool.MAX_DISABLED
                            && haVpool.getMirrorVirtualPool() != null;
                }
            }
            return supportsMirror;
        }

        if (virtualPool.getMaxNativeContinuousCopies() == null) {
            return false;
        }
        return virtualPool.getMaxNativeContinuousCopies() != MAX_DISABLED;
    }

    /**
     * Convenience method to get HA VPool if it's set.
     * 
     * @param sourceVirtualPool A Reference to VPLEX Volume source virtual pool
     * @param dbClient an instance of {@link DbClient}
     * 
     * @return returns HA VPool if its set else returns null
     */
    public static VirtualPool getHAVPool(VirtualPool sourceVirtualPool, DbClient dbClient) {
        VirtualPool haVPool = null;
        StringMap haVarrayVpoolMap = sourceVirtualPool.getHaVarrayVpoolMap();
        if (haVarrayVpoolMap != null
                && !haVarrayVpoolMap.isEmpty()) {
            String haVarrayStr = haVarrayVpoolMap.keySet().iterator().next();
            String haVpoolStr = haVarrayVpoolMap.get(haVarrayStr);
            if (haVpoolStr != null && !(haVpoolStr.equals(NullColumnValueGetter.getNullURI().toString()))) {
                haVPool = dbClient.queryObject(VirtualPool.class, URI.create(haVpoolStr));
            }
        }
        return haVPool;
    }

    /**
     * Convenience method to determine if the Virtual Pool supports snapshots
     * 
     * @param virtualPool
     *            Virtual Pool
     * @return true if supports snapshots
     */
    public static boolean vPoolSpecifiesSnapshots(final VirtualPool virtualPool) {
        if (virtualPool.getMaxNativeSnapshots() == null) {
            return false;
        }
        return virtualPool.getMaxNativeSnapshots() != MAX_DISABLED;
    }

    /**
     * Convenience method to determine if the Virtual Pool supports SRDF
     * 
     * @param virtualPool
     *            virtual pool
     * @return true if supports srdf
     */
    public static boolean vPoolSpecifiesSRDF(final VirtualPool virtualPool) {
        if (virtualPool.getProtectionRemoteCopySettings() == null
                || virtualPool.getProtectionRemoteCopySettings().size() == 0) {
            return false;
        }
        return true;
    }

    /**
     * Convenience method to determine if the Virtual Pool supports expansion.
     * 
     * @param virtualPool
     * @return
     */
    public static boolean vPoolAllowsExpansion(final VirtualPool virtualPool) {
        return virtualPool.getExpandable() != null
                && virtualPool.getExpandable();
    }

    @Name("thinVolumePreAllocationPercentage")
    public Integer getThinVolumePreAllocationPercentage() {
        return _thinVolumePreAllocationPercentage;
    }

    public void setThinVolumePreAllocationPercentage(final Integer thinVolumePreAllocationPercentage) {
        _thinVolumePreAllocationPercentage = thinVolumePreAllocationPercentage;
        setChanged("thinVolumePreAllocationPercentage");
    }

    @Name("quota")
    public Long getQuota() {
        return null == _quotaGB ? 0L : _quotaGB;
    }

    public void setQuota(final Long quota) {
        _quotaGB = quota;
        setChanged("quota");
    }

    @Name("quotaEnabled")
    public Boolean getQuotaEnabled() {
        return _quotaEnabled == null ? false : _quotaEnabled;
    }

    public void setQuotaEnabled(final Boolean enable) {
        _quotaEnabled = enable;
        setChanged("quotaEnabled");
    }

    @Name("multivolumeconsistency")
    public Boolean getMultivolumeConsistency() {
        return _multivolumeconsistency;
    }

    public void setMultivolumeConsistency(final Boolean _multivolumeconsistency) {
        this._multivolumeconsistency = _multivolumeconsistency;
        setChanged("multivolumeconsistency");
    }

    @Name("uniquePolicyNames")
    public Boolean getUniquePolicyNames() {
        return uniquePolicyNames;
    }

    public void setUniquePolicyNames(final Boolean uniquePolicyNames) {
        this.uniquePolicyNames = uniquePolicyNames;
        setChanged("uniquePolicyNames");
    }

    @Name("longTermRetention")
    public Boolean getLongTermRetention() {
        return (_longTermRetention != null) ?
                _longTermRetention : false;
    }

    public void setLongTermRetention(final Boolean longTermRetention) {
        _longTermRetention = longTermRetention;
        setChanged("longTermRetention");
    }

    /**
     * return pools based on the useMatchedPools flag set. remove if there are any invalid pools.
     * 
     * @param virtualPool
     *            : VirtualPool to find valid pools.
     * @return
     */
    public static List<StoragePool> getValidStoragePools(final VirtualPool virtualPool,
            final DbClient dbClient, final boolean excludeUnreachable) {
        List<StoragePool> validPools = new ArrayList<StoragePool>();
        StringSet storagePools = virtualPool.getUseMatchedPools() ? virtualPool
                .getMatchedStoragePools() : virtualPool.getAssignedStoragePools();
        // Remove the invalid pools as they are no longer valid to do
        // provisioning.
        if (null != storagePools) {
            if (null != virtualPool.getInvalidMatchedPools()) {
                storagePools.removeAll(virtualPool.getInvalidMatchedPools());
            }
            for (String poolStr : storagePools) {
                StoragePool pool = dbClient.queryObject(StoragePool.class, URI.create(poolStr));
                if (pool.getInactive()) {
                    continue;
                }
                StorageSystem storage = dbClient.queryObject(StorageSystem.class,
                        pool.getStorageDevice());
                if (excludeUnreachable && !storage.getReachableStatus()) {
                    continue;
                }
                validPools.add(pool);
            }
        }
        return validPools;
    }

    /**
     * return pools based on the useMatchedPools flag set. remove if there are
     * any invalid pools.
     * 
     * @param virtualPool : VirtualPool to find valid pools.
     * @return
     */
    public static List<StoragePool> getInvalidStoragePools(VirtualPool virtualPool, DbClient dbClient) {
        List<StoragePool> invalidPools = new ArrayList<StoragePool>();
        StringSet storagePools = virtualPool.getInvalidMatchedPools();
        if (null != storagePools) {
            for (String poolStr : storagePools) {
                StoragePool pool = dbClient.queryObject(StoragePool.class,
                        URI.create(poolStr));
                if (!pool.getInactive()) {
                    invalidPools.add(pool);
                }
            }
        }
        return invalidPools;
    }

    /**
     * Return the protection setting objects associated with this virtual pool.
     * 
     * @param vpool
     *            the virtual pool
     * @return a mapping of virtual arrays to the protection settings for that copy
     */
    public static Map<URI, VpoolProtectionVarraySettings> getProtectionSettings(
            final VirtualPool vpool, final DbClient dbClient) {
        Map<URI, VpoolProtectionVarraySettings> settings = null;
        if (vpool.getProtectionVarraySettings() != null) {
            settings = new HashMap<URI, VpoolProtectionVarraySettings>();
            for (String protectionVarray : vpool.getProtectionVarraySettings().keySet()) {
                settings.put(URI.create(protectionVarray), dbClient.queryObject(
                        VpoolProtectionVarraySettings.class,
                        URI.create(vpool.getProtectionVarraySettings().get(protectionVarray))));
            }
        }
        return settings;
    }

    /**
     * Return the remote protection setting objects associated with this virtual pool.
     * 
     * @param vpool
     *            the virtual pool
     * @return a mapping of virtual arrays to the protection settings for that copy
     */
    public static Map<URI, VpoolRemoteCopyProtectionSettings> getRemoteProtectionSettings(
            final VirtualPool vpool, final DbClient dbClient) {
        Map<URI, VpoolRemoteCopyProtectionSettings> settings = new HashMap<URI, VpoolRemoteCopyProtectionSettings>();
        if (vpool.getProtectionRemoteCopySettings() != null) {
            for (String protectionVarray : vpool.getProtectionRemoteCopySettings().keySet()) {
                settings.put(
                        URI.create(protectionVarray),
                        dbClient.queryObject(
                                VpoolRemoteCopyProtectionSettings.class,
                                URI.create(vpool.getProtectionRemoteCopySettings().get(
                                        protectionVarray))));
            }
        }
        return settings;
    }

    public static Map<String, List<String>> groupRemoteCopyModesByVPool(final VirtualPool vpool,
            final DbClient dbClient) {
        Map<URI, VpoolRemoteCopyProtectionSettings> remoteSettingsMap =
                getRemoteProtectionSettings(vpool, dbClient);
        return groupRemoteCopyModesByVPool(vpool.getId(), remoteSettingsMap);
    }

    public static Map<String, List<String>> groupRemoteCopyModesByVPool(
            URI defaultVpool,
            Map<URI, VpoolRemoteCopyProtectionSettings> remoteSettingsMap) {
        Map<String, List<String>> settings = new HashMap<String, List<String>>();
        if (remoteSettingsMap != null && !remoteSettingsMap.isEmpty()) {
            for (VpoolRemoteCopyProtectionSettings remoteSettings : remoteSettingsMap.values()) {
                String vPoolUri = null;
                if (remoteSettings.getVirtualPool() == null
                        || remoteSettings.getVirtualPool().toString().isEmpty()) {
                    vPoolUri = defaultVpool.toString();
                } else {
                    vPoolUri = remoteSettings.getVirtualPool().toString();
                }
                if (null == settings.get(vPoolUri)) {
                    settings.put(vPoolUri, new ArrayList<String>());
                }
                settings.get(vPoolUri).add(remoteSettings.getCopyMode());
            }
        }
        return settings;
    }

    @Name("remoteProtectionSettings")
    public StringMap getProtectionRemoteCopySettings() {
        return _protectionRemoteCopySettings;
    }

    public void setProtectionRemoteCopySettings(final StringMap _protectionRemoteCopySettings) {
        this._protectionRemoteCopySettings = _protectionRemoteCopySettings;
    }

    // this field is not used in 2.0
    @Name("nonDisruptiveExpansion")
    public Boolean getNonDisruptiveExpansion() {
        return _nonDisruptiveExpansion;
    }

    public void setNonDisruptiveExpansion(Boolean nonDisruptiveExpansion) {
        this._nonDisruptiveExpansion = nonDisruptiveExpansion;
        setChanged("nonDisruptiveExpansion");
    }

    @Name("haVarrayConnectedToRp")
    public String getHaVarrayConnectedToRp() {
        return haVarrayConnectedToRp;
    }

    public void setHaVarrayConnectedToRp(String haVarrayConnectedToRp) {
        this.haVarrayConnectedToRp = haVarrayConnectedToRp;
        setChanged("haVarrayConnectedToRp");
    }

    @Name("metroPoint")
    public Boolean getMetroPoint() {
        return metroPoint;
    }

    public void setMetroPoint(Boolean metroPoint) {
        this.metroPoint = metroPoint;
        setChanged("metroPoint");
    }

    /**
     * This method checks if the passed vpool is set as the continuous copies vpool for any of the vpool.
     * If yes returns virtual pool names where it is used as continuous copies vpool.
     * 
     * @param vpool
     * @param dbClient dbClient an instance of {@link DbClient}
     * 
     * @return comma separated names of the virtual pool in which this vpool is
     *         in use as a continuous copies vpool else empty string
     */
    public static String isContinuousCopiesVpool(VirtualPool vpool, DbClient dbClient) {
        StringBuilder virtualPoolNameBuilder = new StringBuilder();
        URIQueryResultList virtualPoolURIs = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVirtualPoolByMirrorVpool(vpool.getId().toString()), virtualPoolURIs);
        for (URI uri : virtualPoolURIs) {
            VirtualPool virtualPool = dbClient.queryObject(VirtualPool.class, uri);
            if (virtualPool != null && !virtualPool.getInactive()) {
                if (virtualPoolNameBuilder.length() == 0) {
                    virtualPoolNameBuilder.append(virtualPool.getLabel());
                } else {
                    virtualPoolNameBuilder.append(", ").append(virtualPool.getLabel());
                }
            }
        }
        return virtualPoolNameBuilder.toString();
    }

    /**
     * Determine whether or not this vpool is set to protect the HA side of
     * an RP+VPLEX setup instead of the Source side.
     * 
     * @param vpool The vpool to check
     * @return true if the vpool indicates to protect the HA side for RP+VPLEX, false otherwise.
     */
    public static boolean isRPVPlexProtectHASide(VirtualPool vpool) {
        return (NullColumnValueGetter.isNotNullValue(vpool.getHaVarrayConnectedToRp())
                && VirtualPool.HighAvailabilityType.vplex_distributed.name().equals(vpool.getHighAvailability())
                && VirtualPool.vPoolSpecifiesRPVPlex(vpool)
                && !VirtualPool.vPoolSpecifiesMetroPoint(vpool));
    }

    @Name("hostIOLimitBandwidth")
    public Integer getHostIOLimitBandwidth() {
        return _hostIOLimitBandwidth;
    }

    public void setHostIOLimitBandwidth(Integer limitHostBandwidth) {
        // ensure number is 0 or above
        this._hostIOLimitBandwidth = limitHostBandwidth == null ? null : Math.max(0, limitHostBandwidth);
        setChanged("hostIOLimitBandwidth");
    }

    @Name("hostIOLimitIOPs")
    public Integer getHostIOLimitIOPs() {
        return _hostIOLimitIOPs;
    }

    public void setHostIOLimitIOPs(Integer limitHostIOPs) {
        // ensure number is 0 or above
        this._hostIOLimitIOPs = limitHostIOPs == null ? null : Math.max(0, limitHostIOPs);
        setChanged("hostIOLimitIOPs");
    }

    public boolean isHostIOLimitIOPsSet() {
        return _hostIOLimitIOPs != null && _hostIOLimitIOPs > 0;
    }

    public boolean isHostIOLimitBandwidthSet() {
        return _hostIOLimitBandwidth != null && _hostIOLimitBandwidth > 0;
    }

    @Name("autoCrossConnectExport")
    public Boolean getAutoCrossConnectExport() {
        if (autoCrossConnectExport == null) {
            return false;
        }
        return autoCrossConnectExport;
    }

    public void setAutoCrossConnectExport(Boolean autoCrossConnectExport) {
        this.autoCrossConnectExport = autoCrossConnectExport;
        setChanged("autoCrossConnectExport");
    }
    
    @Name("maxRetention")
    public Integer getMaxRetention() {
        return (maxRetention==null) ? 0 : maxRetention;
    }

    public void setMaxRetention(Integer maxRetention) {
        this.maxRetention = (null==maxRetention || maxRetention == 0) ? 0 : maxRetention;
        setChanged("maxRetention");
    }
}
