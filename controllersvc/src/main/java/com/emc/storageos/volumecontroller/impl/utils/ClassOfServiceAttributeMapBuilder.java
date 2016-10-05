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

import com.emc.storageos.db.client.model.ClassOfService;
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
public class ClassOfServiceAttributeMapBuilder extends AttributeMapBuilder {
    private ClassOfService _cos = null;
    private CosBaseProfileWrapper _baseProfile = null;
    private static final Logger _logger = LoggerFactory
            .getLogger(VirtualPoolAttributeMapBuilder.class);

    /**
     * Constructor to initialize with VirtualPool.
     * 
     * @param vpool
     * @param map
     */
    public ClassOfServiceAttributeMapBuilder(ClassOfService cos) {
    	_cos = cos;
    	if(_cos.getBasicProfile() != null) {
    		_baseProfile = new CosBaseProfileWrapper(cos.getBasicProfile());
    	}
    }

    @Override
    public Map<String, Object> buildMap() {
    	if (null != _baseProfile.getProtocols() && !_baseProfile.getProtocols().isEmpty()) {
    		putAttributeInMap(Attributes.protocols.toString(), _baseProfile.getProtocols());
    	}
    	putAttributeInMap(Attributes.auto_tiering_policy_name.toString(), _baseProfile.getAutoTierPolicyName());

    	putAttributeInMap(Attributes.drive_type.toString(), _baseProfile.getDriveType());

    	putAttributeInMap(Attributes.system_type.toString(), _baseProfile.getDeviceType());
    	putAttributeInMap(Attributes.raid_levels.toString(), _baseProfile.getRaidLevel());

    	putAttributeInMap(Attributes.provisioning_type.toString(), _baseProfile.getProvisioningType());

    	putAttributeInMap(Attributes.max_paths.toString(), _baseProfile.getMaxPaths());
    	putAttributeInMap(Attributes.paths_per_initiator.toString(), _baseProfile.getPathsPerInitiator());

    	long preAllocationInt = _baseProfile.getThinVolumePreAllocateSize();
    	if (preAllocationInt > 0) {
    		putAttributeInMap(Attributes.thin_volume_preallocation_percentage.toString(), preAllocationInt);
    	}
    	putAttributeInMap(Attributes.multi_volume_consistency.toString(), _baseProfile.getMultiVolumeConsistency());

    	// putAttributeInMap(Attributes.vpool_type.toString(), _vpool.getType());
    	// putAttributeInMap(Attributes.unique_policy_names.toString(), _vpool.getUniquePolicyNames());
    	// putAttributeInMap(Attributes.varrays.toString(), _vpool.getVirtualArrays());
    	// putAttributeInMap(Attributes.max_native_snapshots.toString(), _vpool.getMaxNativeSnapshots());
    	// putAttributeInMap(Attributes.max_native_continuous_copies.toString(), _vpool.getMaxNativeContinuousCopies());
    	return _attributeMap;
    }
}
