/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.FileReplicationType;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.volumecontroller.AttributeMatcher.Attributes;
import com.google.common.base.Joiner;

/**
 * Constructs the AttributeMap using VirtualPool and finally returns a map with all VirtualPool
 * attributes specified.
 */
public class VirtualPoolAttributeMapBuilder extends AttributeMapBuilder {
    private VirtualPool _vpool = null;
    private Map<URI, VpoolProtectionVarraySettings> protectionSettings = null;
    private Map<String, List<String>> remoteProtectionSettings = null;
    private Map<String, List<String>> fileRemoteProtectionSettings = null;
    private static final Logger _logger = LoggerFactory
            .getLogger(VirtualPoolAttributeMapBuilder.class);

    /**
     * Constructor to initialize with VirtualPool.
     * 
     * @param vpool
     * @param map
     */
    public VirtualPoolAttributeMapBuilder(VirtualPool vpool, Map<URI, VpoolProtectionVarraySettings> map,
            Map<String, List<String>> remoteProtectionSettings) {
        _vpool = vpool;
        protectionSettings = map;
        this.remoteProtectionSettings = remoteProtectionSettings;
    }
    
    public VirtualPoolAttributeMapBuilder(VirtualPool vpool, Map<URI, VpoolProtectionVarraySettings> map,
            Map<String, List<String>> remoteProtectionSettings, Map<String, List<String>> fileRemoteProtectionSettings) {
        _vpool = vpool;
        protectionSettings = map;
        this.remoteProtectionSettings = remoteProtectionSettings;
        this.fileRemoteProtectionSettings = fileRemoteProtectionSettings;
    }

    @Override
    public Map<String, Object> buildMap() {
        if (null != _vpool.getProtocols() && !_vpool.getProtocols().isEmpty()) {
            putAttributeInMap(Attributes.protocols.toString(), _vpool.getProtocols());
        }
        putAttributeInMap(Attributes.auto_tiering_policy_name.toString(), _vpool.getAutoTierPolicyName());
        putAttributeInMap(Attributes.unique_policy_names.toString(), _vpool.getUniquePolicyNames());
        putAttributeInMap(Attributes.drive_type.toString(), _vpool.getDriveType());
        if (null != _vpool.getArrayInfo() && !_vpool.getArrayInfo().isEmpty()) {
            putAttributeInMap(Attributes.system_type.toString(),
                    _vpool.getArrayInfo().get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE));
            putAttributeInMap(Attributes.raid_levels.toString(),
                    _vpool.getArrayInfo().get(VirtualPoolCapabilityValuesWrapper.RAID_LEVEL));
        }
        putAttributeInMap(Attributes.high_availability_type.toString(), _vpool.getHighAvailability());
        String haNh = null;
        String haCos = null;
        StringMap haNhCosMap = _vpool.getHaVarrayVpoolMap();
        if (haNhCosMap != null && !haNhCosMap.isEmpty()) {
            haNh = haNhCosMap.keySet().iterator().next();
            haCos = haNhCosMap.get(haNh);
            if (haNh.equals(NullColumnValueGetter.getNullURI().toString())) {
                haNh = null;
            }
            if (haCos.equals(NullColumnValueGetter.getNullURI().toString())) {
                haCos = null;
            }
        }
        putAttributeInMap(Attributes.high_availability_varray.toString(), haNh);
        putAttributeInMap(Attributes.high_availability_vpool.toString(), haCos);
        putAttributeInMap(Attributes.high_availability_rp.toString(), _vpool.getHaVarrayConnectedToRp());
        putAttributeInMap(Attributes.metropoint.toString(), _vpool.getMetroPoint());
        putAttributeInMap(Attributes.provisioning_type.toString(), _vpool.getSupportedProvisioningType());
        putAttributeInMap(Attributes.vpool_type.toString(), _vpool.getType());
        putAttributeInMap(Attributes.max_paths.toString(), _vpool.getNumPaths());
        putAttributeInMap(Attributes.paths_per_initiator.toString(), _vpool.getPathsPerInitiator());
        putAttributeInMap(Attributes.varrays.toString(), _vpool.getVirtualArrays());
        putAttributeInMap(Attributes.max_native_snapshots.toString(), _vpool.getMaxNativeSnapshots());
        putAttributeInMap(Attributes.max_native_continuous_copies.toString(), _vpool.getMaxNativeContinuousCopies());
        Integer preAllocationInt = _vpool.getThinVolumePreAllocationPercentage();
        if (preAllocationInt != null && preAllocationInt > 0) {
            putAttributeInMap(Attributes.thin_volume_preallocation_percentage.toString(), preAllocationInt);
        }
        StringMap rpVaVpMap = new StringMap();
        if (null != protectionSettings) {
            for (URI vArray : protectionSettings.keySet()) {
                VpoolProtectionVarraySettings vaVp = protectionSettings.get(vArray);
                if (vaVp != null) {
                    rpVaVpMap.put(vArray.toString(), vaVp.getVirtualPool() != null ? vaVp.getVirtualPool().toString() : null);
                }
            }
        }
        putAttributeInMap(Attributes.recoverpoint_map.toString(), rpVaVpMap);

        // Only check pools for consistency group compatibility if RecoverPoint protection is
        // not selected. We are creating a RecoverPoint consistency group if RP is selected,
        // not an array-based consistency group.
        putAttributeInMap(Attributes.multi_volume_consistency.toString(),
                (rpVaVpMap.size() == 0) ? _vpool.getMultivolumeConsistency() : Boolean.FALSE);

        // Remote Mirror Protection
        if (null != remoteProtectionSettings && !remoteProtectionSettings.isEmpty()) {
            _logger.info("Remote Settings : {}", Joiner.on("\t").join(remoteProtectionSettings.keySet()));
            putAttributeInMap(Attributes.remote_copy.toString(), remoteProtectionSettings);
        }
        putAttributeInMap(Attributes.long_term_retention_policy.toString(), _vpool.getLongTermRetention());
        
        if (_vpool.getFileReplicationType() != null &&
        		!FileReplicationType.NONE.name().equalsIgnoreCase(_vpool.getFileReplicationType())) {
        	
        	putAttributeInMap(Attributes.file_replication_type.toString(), _vpool.getFileReplicationType());
        	if (_vpool.getFileReplicationCopyMode() != null) {
        		putAttributeInMap(Attributes.file_replication_copy_mode.toString(), _vpool.getFileReplicationCopyMode());
        	}
        	if (null != fileRemoteProtectionSettings && !fileRemoteProtectionSettings.isEmpty()) {
                _logger.info("File Replication Remote Settings : {}", Joiner.on("\t").join(fileRemoteProtectionSettings.keySet()));
                putAttributeInMap(Attributes.file_replication.toString(), fileRemoteProtectionSettings);
            }
        }
        putAttributeInMap(Attributes.min_datacenters.toString(), _vpool.getMinDataCenters());
        return _attributeMap;
    }
}
