/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import java.io.Serializable;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Wrapper for COS base profile HashMap
 * 
 */
public class CosBaseProfileWrapper implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String AUTO_TIER__POLICY_NAME = "auto_tier_policy";
    public static final String RAID_LEVEL = "raid_level";
    public static final String SYSTEM_TYPE = "system_type";
    public static final String VARRAYS = "varrays";
    public static final String PROTOCOLS = "protocols";
    public static final String SIZE = "size";
    public static final String THIN_VOLUME_PRE_ALLOCATE_SIZE = "thin_volume_pre_allocatesize";
    public static final String RESOURCE_COUNT = "resource_count";
    public static final String THIN_PROVISIONING = "thin_provisioning";
    public static final String BLOCK_CONSISTENCY_GROUP = "block_consistency_group";
    
    public static final String PROVISIONING_TYPE = "provisioning_type";
    public static final String MAX_PATHS = "max_paths";
    public static final String MIN_PATHS = "min_paths";
    public static final String PATHS_PER_INITIATOR = "paths_per_initiator";
    public static final String DRIVE_TYPE = "drive_type";
    public static final String MULTI_VOLUME_CONSISTENCY = "multi_volume_consistancy";
    public static final String EXPANDABLE = "expandable";
    public static final String HOST_IO_BANDWIDTH = "host_io_limit_bandwidth";
    public static final String HOST_IO_IOPS = "host_io_limit_iops";
    
    

    private final Map<String, Object> _baseCapabilities = new HashMap<String, Object>();

    /**
     * Default constructor
     */
    public CosBaseProfileWrapper() {
    }

    /**
     * Copy the passed capabilities to a new instance.
     * 
     * @param capabilities A reference to a VirtualPoolCapabilityValuesWrapper
     */
    public CosBaseProfileWrapper(Map<String, Object> capabilities) {
        // Copy the value set in the passed reference capabilities.
        if (capabilities.containsKey(AUTO_TIER__POLICY_NAME)) {
        	_baseCapabilities.put(AUTO_TIER__POLICY_NAME, capabilities.get(AUTO_TIER__POLICY_NAME));
        }

        if (capabilities.containsKey(RAID_LEVEL)) {
        	_baseCapabilities.put(RAID_LEVEL, capabilities.get(RAID_LEVEL));
        }

        if (capabilities.containsKey(SYSTEM_TYPE)) {
        	_baseCapabilities.put(SYSTEM_TYPE, capabilities.get(SYSTEM_TYPE));
        }

        if (capabilities.containsKey(VARRAYS)) {
            _baseCapabilities.put(VARRAYS, capabilities.get(VARRAYS));
        }

        if (capabilities.containsKey(PROTOCOLS)) {
            _baseCapabilities.put(PROTOCOLS, capabilities.get(PROTOCOLS));
        }

        if (capabilities.containsKey(SIZE)) {
            _baseCapabilities.put(SIZE, capabilities.get(SIZE));
        }

        if (capabilities.containsKey(THIN_VOLUME_PRE_ALLOCATE_SIZE)) {
            _baseCapabilities.put(THIN_VOLUME_PRE_ALLOCATE_SIZE, capabilities.get(THIN_VOLUME_PRE_ALLOCATE_SIZE));
        }

        if (capabilities.containsKey(RESOURCE_COUNT)) {
            _baseCapabilities.put(RESOURCE_COUNT, capabilities.get(RESOURCE_COUNT));
        }

        if (capabilities.containsKey(THIN_PROVISIONING)) {
            _baseCapabilities.put(THIN_PROVISIONING, capabilities.get(THIN_PROVISIONING));
        }

        if (capabilities.containsKey(BLOCK_CONSISTENCY_GROUP)) {
            _baseCapabilities.put(BLOCK_CONSISTENCY_GROUP, capabilities.get(BLOCK_CONSISTENCY_GROUP));
        }
        
        if (capabilities.containsKey(MIN_PATHS)) {
            _baseCapabilities.put(MIN_PATHS, capabilities.get(MIN_PATHS));
        }
        
        if (capabilities.containsKey(PATHS_PER_INITIATOR)) {
            _baseCapabilities.put(PATHS_PER_INITIATOR, capabilities.get(PATHS_PER_INITIATOR));
        }
        
        if (capabilities.containsKey(DRIVE_TYPE)) {
            _baseCapabilities.put(DRIVE_TYPE, capabilities.get(DRIVE_TYPE));
        }
        
        if (capabilities.containsKey(PROVISIONING_TYPE)) {
            _baseCapabilities.put(PROVISIONING_TYPE, capabilities.get(PROVISIONING_TYPE));
        }
        
        
        if (capabilities.containsKey(MULTI_VOLUME_CONSISTENCY)) {
            _baseCapabilities.put(MULTI_VOLUME_CONSISTENCY, capabilities.get(MULTI_VOLUME_CONSISTENCY));
        }
        
        if (capabilities.containsKey(EXPANDABLE)) {
            _baseCapabilities.put(EXPANDABLE, capabilities.get(EXPANDABLE));
        }
        
        if (capabilities.containsKey(HOST_IO_BANDWIDTH)) {
            _baseCapabilities.put(HOST_IO_BANDWIDTH, capabilities.get(HOST_IO_BANDWIDTH));
        }
        
        if (capabilities.containsKey(HOST_IO_IOPS)) {
            _baseCapabilities.put(HOST_IO_IOPS, capabilities.get(HOST_IO_IOPS));
        }
    }

    public String getVirtualArrays() {
        Object value = _baseCapabilities.get(VARRAYS);
        return value != null ? (String) value : null;
    }

    public Set<String> getProtocols() {
        Object value = _baseCapabilities.get(PROTOCOLS);
        return value != null ? (Set<String>) value : null;
    }

    public String getAutoTierPolicyName() {
        Object value = _baseCapabilities.get(AUTO_TIER__POLICY_NAME);
        return value != null ? (String) value : null;
    }

    public String getRaidLevel() {
        Object value = _baseCapabilities.get(RAID_LEVEL);
        return value != null ? (String) value : null;
    }

    public String getDeviceType() {
        Object value = _baseCapabilities.get(SYSTEM_TYPE);
        return value != null ? (String) value : null;
    }

    public void put(String key, Object value) {
        _baseCapabilities.put(key, value);
    }

    public boolean contains(String key) {
        return _baseCapabilities.containsKey(key);
    }

    public long getSize() {
        Object value = _baseCapabilities.get(SIZE);
        return value != null ? (Long) value : 0L;
    }

    public long getThinVolumePreAllocateSize() {
        Object value = _baseCapabilities.get(THIN_VOLUME_PRE_ALLOCATE_SIZE);
        return value != null ? (Long) value : 0L;
    }

    public int getResourceCount() {
        Object value = _baseCapabilities.get(RESOURCE_COUNT);
        return value != null ? (Integer) value : 1;
    }

    public boolean getThinProvisioning() {
        Object value = _baseCapabilities.get(THIN_PROVISIONING);
        return value != null ? (Boolean) value : false;
    }

    public URI getBlockConsistencyGroup() {
        Object value = _baseCapabilities.get(BLOCK_CONSISTENCY_GROUP);
        return value != null ? (URI) value : null;
    }
    
    public long getMinPaths() {
        Object value = _baseCapabilities.get(MIN_PATHS);
        return value != null ? (Long) value : 0L;
    }
    
    public long getMaxPaths() {
        Object value = _baseCapabilities.get(MAX_PATHS);
        return value != null ? (Long) value : 0L;
    }
    
    public long getPathsPerInitiator() {
        Object value = _baseCapabilities.get(PATHS_PER_INITIATOR);
        return value != null ? (Long) value : 0L;
    }
    
    public String getDriveType() {
        Object value = _baseCapabilities.get(DRIVE_TYPE);
        return value != null ? (String) value : null;
    }
    
    public boolean getMultiVolumeConsistency() {
        Object value = _baseCapabilities.get(MULTI_VOLUME_CONSISTENCY);
        return value != null ? (Boolean) value : false;
    }
    
    public boolean getExpandable() {
        Object value = _baseCapabilities.get(EXPANDABLE);
        return value != null ? (Boolean) value : false;
    }
    
    public String getIOBandwidth() {
        Object value = _baseCapabilities.get(HOST_IO_BANDWIDTH);
        return value != null ? (String) value : null;
    }
    
    public String getIOIops() {
        Object value = _baseCapabilities.get(HOST_IO_IOPS);
        return value != null ? (String) value : null;
    }
    
    public String getProvisioningType() {
        Object value = _baseCapabilities.get(PROVISIONING_TYPE);
        return value != null ? (String) value : null;
    }


}
