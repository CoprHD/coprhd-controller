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
 * Wrapper for VirtualPoolParams HashMap
 * 
 */
public class VirtualPoolCapabilityValuesWrapper implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String AUTO_TIER__POLICY_NAME = "auto_tier_policy";
    public static final String RAID_LEVEL = "raid_level";
    public static final String SYSTEM_TYPE = "system_type";
    public static final String VARRAYS = "varrays";
    public static final String PROTOCOLS = "protocols";
    public static final String SIZE = "size";
    public static final String THIN_VOLUME_PRE_ALLOCATE_SIZE = "thinVolumePreAllocateSize";
    public static final String RESOURCE_COUNT = "resource_count";
    public static final String THIN_PROVISIONING = "thin_provisioning";
    public static final String BLOCK_CONSISTENCY_GROUP = "block_consistency_group";
    public static final String SRDF_SOURCE = "srdf_source";
    public static final String SRDF_TARGET = "srdf_target";
    public static final String PERSONALITY = "personality";
    public static final String RP_RPO_VALUE = "rpRpoValue";
    public static final String RP_RPO_TYPE = "rpRpoType";
    public static final String RP_COPY_MODE = "rpCopyMode";
    public static final String ADD_JOURNAL_CAPACITY = "add_journal_capacity";
    public static final String RP_COPY_TYPE = "rp_copy_type";
    public static final String RP_MAX_SNAPS = "rp_max_snaps";
    public static final String QUOTA = "quota";
    
    public static final String FILE_RP_RPO_VALUE = "fileRpRpoValue";
    public static final String FILE_RP_RPO_TYPE  = "fileRpRpoType";
    public static final String FILE_RP_COPY_MODE = "fileRpCopyMode";


    public static final String FILE_REPLICATION_SOURCE = "file_replication_source";
    public static final String FILE_REPLICATION_TARGET = "file_replication_target";

    // meta volume capabilities
    public static final String IS_META_VOLUME = "isMetaVolume";
    public static final String META_VOLUME_MEMBER_SIZE = "metaVolumeMemberSize";
    public static final String META_VOLUME_MEMBER_COUNT = "metaVolumeMemberCount";
    public static final String META_VOLUME_TYPE = "metaVolumeType";

    private final Map<String, Object> _vpoolCapabilities = new HashMap<String, Object>();

    /**
     * Default constructor
     */
    public VirtualPoolCapabilityValuesWrapper() {
    }

    /**
     * Copy the passed capabilities to a new instance.
     * 
     * @param capabilities A reference to a VirtualPoolCapabilityValuesWrapper
     */
    public VirtualPoolCapabilityValuesWrapper(VirtualPoolCapabilityValuesWrapper capabilities) {
        // Copy the value set in the passed reference capabilities.
        if (capabilities.contains(AUTO_TIER__POLICY_NAME)) {
            _vpoolCapabilities.put(AUTO_TIER__POLICY_NAME, capabilities.getAutoTierPolicyName());
        }

        if (capabilities.contains(RAID_LEVEL)) {
            _vpoolCapabilities.put(RAID_LEVEL, capabilities.getRaidLevel());
        }

        if (capabilities.contains(SYSTEM_TYPE)) {
            _vpoolCapabilities.put(SYSTEM_TYPE, capabilities.getDeviceType());
        }

        if (capabilities.contains(VARRAYS)) {
            _vpoolCapabilities.put(VARRAYS, capabilities.getVirtualArrays());
        }

        if (capabilities.contains(PROTOCOLS)) {
            _vpoolCapabilities.put(PROTOCOLS, capabilities.getProtocols());
        }

        if (capabilities.contains(SIZE)) {
            _vpoolCapabilities.put(SIZE, capabilities.getSize());
        }

        if (capabilities.contains(THIN_VOLUME_PRE_ALLOCATE_SIZE)) {
            _vpoolCapabilities.put(THIN_VOLUME_PRE_ALLOCATE_SIZE, capabilities.getThinVolumePreAllocateSize());
        }

        if (capabilities.contains(RESOURCE_COUNT)) {
            _vpoolCapabilities.put(RESOURCE_COUNT, capabilities.getResourceCount());
        }

        if (capabilities.contains(THIN_PROVISIONING)) {
            _vpoolCapabilities.put(THIN_PROVISIONING, capabilities.getThinProvisioning());
        }

        if (capabilities.contains(BLOCK_CONSISTENCY_GROUP)) {
            _vpoolCapabilities.put(BLOCK_CONSISTENCY_GROUP, capabilities.getBlockConsistencyGroup());
        }

        if (capabilities.contains(SRDF_SOURCE)) {
            _vpoolCapabilities.put(SRDF_SOURCE, capabilities.getSrdfSource());
        }

        if (capabilities.contains(SRDF_TARGET)) {
            _vpoolCapabilities.put(SRDF_TARGET, capabilities.getSrdfTarget());
        }

        if (capabilities.contains(PERSONALITY)) {
            _vpoolCapabilities.put(PERSONALITY, capabilities.getPersonality());
        }

        if (capabilities.contains(RP_RPO_VALUE)) {
            _vpoolCapabilities.put(RP_RPO_VALUE, capabilities.getRpRpoValue());
        }

        if (capabilities.contains(RP_RPO_TYPE)) {
            _vpoolCapabilities.put(RP_RPO_TYPE, capabilities.getRpRpoType());
        }

        if (capabilities.contains(RP_COPY_MODE)) {
            _vpoolCapabilities.put(RP_COPY_MODE, capabilities.getRpCopyMode());
        }

        if (capabilities.contains(ADD_JOURNAL_CAPACITY)) {
            _vpoolCapabilities.put(ADD_JOURNAL_CAPACITY, capabilities.getAddJournalCapacity());
        }

        if (capabilities.contains(RP_COPY_TYPE)) {
            _vpoolCapabilities.put(RP_COPY_TYPE, capabilities.getRPCopyType());
        }

        if (capabilities.contains(RP_MAX_SNAPS)) {
            _vpoolCapabilities.put(RP_MAX_SNAPS, capabilities.getRPMaxSnaps());
        }

        if (capabilities.contains(IS_META_VOLUME)) {
            _vpoolCapabilities.put(IS_META_VOLUME, capabilities.getIsMetaVolume());
        }

        if (capabilities.contains(META_VOLUME_MEMBER_SIZE)) {
            _vpoolCapabilities.put(META_VOLUME_MEMBER_SIZE, capabilities.getMetaVolumeMemberSize());
        }

        if (capabilities.contains(META_VOLUME_MEMBER_COUNT)) {
            _vpoolCapabilities.put(META_VOLUME_MEMBER_COUNT, capabilities.getMetaVolumeMemberCount());
        }

        if (capabilities.contains(META_VOLUME_TYPE)) {
            _vpoolCapabilities.put(META_VOLUME_TYPE, capabilities.getMetaVolumeType());
        }

        if (capabilities.contains(QUOTA)) {
            _vpoolCapabilities.put(QUOTA, capabilities.getQuota());
        }
        
        if (capabilities.contains(FILE_RP_RPO_TYPE)) {
            _vpoolCapabilities.put(FILE_RP_RPO_TYPE, capabilities.getRpRpoType());
        }

        if (capabilities.contains(FILE_RP_RPO_TYPE)) {
            _vpoolCapabilities.put(FILE_RP_RPO_TYPE, capabilities.getRpCopyMode());
        }
        
        if (capabilities.contains(FILE_RP_COPY_MODE)) {
            _vpoolCapabilities.put(FILE_RP_COPY_MODE, capabilities.getRpCopyMode());
        }


    }

    public String getVirtualArrays() {
        Object value = _vpoolCapabilities.get(VARRAYS);
        return value != null ? (String) value : null;
    }

    public Set<String> getProtocols() {
        Object value = _vpoolCapabilities.get(PROTOCOLS);
        return value != null ? (Set<String>) value : null;
    }

    public String getAutoTierPolicyName() {
        Object value = _vpoolCapabilities.get(AUTO_TIER__POLICY_NAME);
        return value != null ? (String) value : null;
    }

    public String getRaidLevel() {
        Object value = _vpoolCapabilities.get(RAID_LEVEL);
        return value != null ? (String) value : null;
    }

    public String getDeviceType() {
        Object value = _vpoolCapabilities.get(SYSTEM_TYPE);
        return value != null ? (String) value : null;
    }

    public void put(String key, Object value) {
        _vpoolCapabilities.put(key, value);
    }

    public boolean contains(String key) {
        return _vpoolCapabilities.containsKey(key);
    }

    public long getSize() {
        Object value = _vpoolCapabilities.get(SIZE);
        return value != null ? (Long) value : 0L;
    }

    public long getThinVolumePreAllocateSize() {
        Object value = _vpoolCapabilities.get(THIN_VOLUME_PRE_ALLOCATE_SIZE);
        return value != null ? (Long) value : 0L;
    }

    public int getResourceCount() {
        Object value = _vpoolCapabilities.get(RESOURCE_COUNT);
        return value != null ? (Integer) value : 1;
    }

    public boolean getThinProvisioning() {
        Object value = _vpoolCapabilities.get(THIN_PROVISIONING);
        return value != null ? (Boolean) value : false;
    }

    public URI getBlockConsistencyGroup() {
        Object value = _vpoolCapabilities.get(BLOCK_CONSISTENCY_GROUP);
        return value != null ? (URI) value : null;
    }

    public String getPersonality() {
        Object value = _vpoolCapabilities.get(PERSONALITY);
        return value != null ? (String) value : null;
    }

    public Long getRpRpoValue() {
        Object value = _vpoolCapabilities.get(RP_RPO_VALUE);
        return value != null ? (Long) value : 0L;
    }

    public String getRpRpoType() {
        Object value = _vpoolCapabilities.get(RP_RPO_TYPE);
        return value != null ? (String) value : null;
    }

    public String getRpCopyMode() {
        Object value = _vpoolCapabilities.get(RP_COPY_MODE);
        return value != null ? (String) value : null;
    }

    public boolean getAddJournalCapacity() {
        Object value = _vpoolCapabilities.get(ADD_JOURNAL_CAPACITY);
        return value != null ? (Boolean) value : false;
    }

    public int getRPCopyType() {
        Object value = _vpoolCapabilities.get(RP_COPY_TYPE);
        return value != null ? (int) value : 0;
    }

    public int getRPMaxSnaps() {
        Object value = _vpoolCapabilities.get(RP_MAX_SNAPS);
        return value != null ? (int) value : 0;
    }

    public String getSrdfSource() {
        Object value = _vpoolCapabilities.get(SRDF_SOURCE);
        return value != null ? (String) value : null;
    }

    public String getSrdfTarget() {
        Object value = _vpoolCapabilities.get(SRDF_TARGET);
        return value != null ? (String) value : null;
    }

    public boolean getIsMetaVolume() {
        Object value = _vpoolCapabilities.get(IS_META_VOLUME);
        return value != null ? (Boolean) value : false;
    }

    public long getMetaVolumeMemberSize() {
        Object value = _vpoolCapabilities.get(META_VOLUME_MEMBER_SIZE);
        return value != null ? (Long) value : 0L;
    }

    public int getMetaVolumeMemberCount() {
        Object value = _vpoolCapabilities.get(META_VOLUME_MEMBER_COUNT);
        return value != null ? (Integer) value : 0;
    }

    public String getMetaVolumeType() {
        Object value = _vpoolCapabilities.get(META_VOLUME_TYPE);
        return value != null ? (String) value : null;
    }

    public String getQuota() {
        Object value = _vpoolCapabilities.get(QUOTA);
        return value != null ? (String) value : null;
    }
    
    public Long getFileRpRpoValue() {
        Object value = _vpoolCapabilities.get(FILE_RP_RPO_VALUE);
        return value != null ? (Long) value : 0L;
    }

    public String getFileRpRpoType() {
        Object value = _vpoolCapabilities.get(FILE_RP_RPO_TYPE);
        return value != null ? (String) value : null;
    }
    
    public String getFileRpCopyMode() {
        Object value = _vpoolCapabilities.get(FILE_RP_COPY_MODE);
        return value != null ? (String) value : null;
    }

}
