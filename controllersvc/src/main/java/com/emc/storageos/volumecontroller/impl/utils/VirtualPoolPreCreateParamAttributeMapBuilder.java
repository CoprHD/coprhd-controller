/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.utils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.volumecontroller.AttributeMatcher.Attributes;

/**
 * AttributeMapBuilder to construct map using VirtualPool attributes.
 * This builder is only used prior to VirtualPool creation.
 * 
 */
public class VirtualPoolPreCreateParamAttributeMapBuilder extends AttributeMapBuilder {

    private String _autoTieringPolicyName;
    private String _driveType;
    private String _haType;
    private String _haVarray;
    private String _haVpool;
    private Boolean _haUseAsRecoverPointSource;
    private Boolean metroPoint;
    private Set<String> _varrays;
    private Integer _maxPaths;
    private Integer _pathsPerInitiator;
    private Set<String> _protocols;
    private String _provisionType;
    private Set<String> _raidLevels;
    private String _systemType;
    private String _type;
    private Boolean _multiVolumeConsistency;
    private Integer _maxNativeSnapshots;
    private Integer _maxNativeContinuousCopies;
    private StringMap _rpVaVpMap;
    private Integer _thinVolumePreAllocation;
    private Map<String, List<String>> remoteProtectionSettings;
    private Boolean _long_term_retention;
    private boolean uniquePolicyNames;
    private Integer _minDataCenters;

    public VirtualPoolPreCreateParamAttributeMapBuilder(String autoTieringPolicyName,
            String driveType, String haType, String haVarray, String haVpool,
            Boolean haUseAsRecoverPointSource,
            Boolean metroPoint,
            Set<String> varrays, Integer maxPaths, Set<String> protocols,
            String provisionType, Set<String> raidLevels,
            String systemType, String type, Boolean multiVolumeConsistency,
            Integer maxNativeSnapshots, Integer maxNativeContinuousCopies,
            StringMap rpVaVpMap, Integer thinVolumePreAllocation,
            Integer pathsPerInitiator,
            Map<String, List<String>> remoteProtectionSettings,
            Boolean long_term_retention,
            boolean uniquePolicyNames,
            Integer minDataCenters) {
        this(varrays, protocols, provisionType, systemType, type, (long_term_retention == null ? Boolean.FALSE : long_term_retention));
        _autoTieringPolicyName = autoTieringPolicyName;
        _driveType = driveType;
        _haType = haType;
        _haVarray = haVarray;
        _haVpool = haVpool;
        _haUseAsRecoverPointSource = haUseAsRecoverPointSource;
        this.metroPoint = metroPoint;
        _maxPaths = maxPaths;
        _raidLevels = raidLevels;
        _multiVolumeConsistency = multiVolumeConsistency;
        _maxNativeSnapshots = maxNativeSnapshots;
        _maxNativeContinuousCopies = maxNativeContinuousCopies;
        _rpVaVpMap = rpVaVpMap;
        _thinVolumePreAllocation = thinVolumePreAllocation;
        _pathsPerInitiator = pathsPerInitiator;
        this.remoteProtectionSettings = remoteProtectionSettings;
        this.uniquePolicyNames = uniquePolicyNames;
        _minDataCenters = minDataCenters;
    }

    public VirtualPoolPreCreateParamAttributeMapBuilder(Set<String> varrays, Set<String> protocols,
            String provisionType, String systemType, String type, Boolean long_term_retention) {
        _varrays = varrays;
        _protocols = protocols;
        _provisionType = provisionType;
        _systemType = systemType;
        _type = type;
        _long_term_retention = long_term_retention;
    }

    @Override
    public Map<String, Object> buildMap() {
        putAttributeInMap(Attributes.protocols.toString(), _protocols);
        putAttributeInMap(Attributes.auto_tiering_policy_name.toString(), _autoTieringPolicyName);
        putAttributeInMap(Attributes.unique_policy_names.toString(), uniquePolicyNames);
        putAttributeInMap(Attributes.drive_type.toString(), _driveType);
        if (null != _systemType) {
            StringSet systemTypeSet = new StringSet();
            systemTypeSet.add(_systemType);
            putAttributeInMap(Attributes.system_type.toString(), systemTypeSet);
            // raid levels valid only for vnx and vmax system type.
            if (!VirtualPool.SystemType.NONE.name().equalsIgnoreCase(_systemType)) {
                putAttributeInMap(Attributes.raid_levels.toString(), _raidLevels);
            }
        }
        putAttributeInMap(Attributes.high_availability_type.toString(), _haType);
        putAttributeInMap(Attributes.high_availability_varray.toString(), _haVarray);
        putAttributeInMap(Attributes.high_availability_vpool.toString(), _haVpool);
        putAttributeInMap(Attributes.high_availability_rp.toString(), _haUseAsRecoverPointSource);
        putAttributeInMap(Attributes.metropoint.toString(), metroPoint);
        putAttributeInMap(Attributes.provisioning_type.toString(), _provisionType);
        putAttributeInMap(Attributes.vpool_type.toString(), _type);
        putAttributeInMap(Attributes.max_paths.toString(), _maxPaths);
        putAttributeInMap(Attributes.paths_per_initiator.toString(), _pathsPerInitiator);
        putAttributeInMap(Attributes.varrays.toString(), _varrays);

        // Only check pools for consistency group compatibility if RecoverPoint protection is
        // not selected. We are creating a RecoverPoint consistency group if RP is selected,
        // not an array-based consistency group.
        putAttributeInMap(Attributes.multi_volume_consistency.toString(),
                (_rpVaVpMap == null || _rpVaVpMap.size() == 0) ? _multiVolumeConsistency : Boolean.FALSE);

        putAttributeInMap(Attributes.max_native_snapshots.toString(), _maxNativeSnapshots);
        putAttributeInMap(Attributes.max_native_continuous_copies.toString(), _maxNativeContinuousCopies);
        putAttributeInMap(Attributes.recoverpoint_map.toString(), _rpVaVpMap);
        if (null != remoteProtectionSettings) {
            putAttributeInMap(Attributes.remote_copy.toString(), remoteProtectionSettings);
        }
        // Ignore preallocation percent if it is null or zero
        if ((_thinVolumePreAllocation != null) && (_thinVolumePreAllocation > 0)) {
            putAttributeInMap(Attributes.thin_volume_preallocation_percentage.toString(), _thinVolumePreAllocation);
        }
        putAttributeInMap(Attributes.long_term_retention_policy.toString(), _long_term_retention);
        putAttributeInMap(Attributes.min_datacenters.toString(), _minDataCenters);
        return _attributeMap;
    }

}
