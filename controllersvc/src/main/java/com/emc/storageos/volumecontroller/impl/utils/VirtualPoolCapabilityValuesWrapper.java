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

import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageSystem;

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
    public static final String SUPPORT_SOFT_LIMIT = "soft_limit";
    public static final String SUPPORT_NOTIFICATION_LIMIT = "notification_limit";
    public static final String QUOTA = "quota";
    public static final String DEDUP = "dedup";
    public static final String RDF_GROUP = "replication_group";

    public static final String FILE_REPLICATION_RPO_VALUE = "fileReplicationRpoValue";
    public static final String FILE_REPLICATION_RPO_TYPE = "fileReplicationRpoType";
    public static final String FILE_REPLICATION_COPY_MODE = "fileReplicationCopyMode";
    public static final String FILE_REPLICATION_TARGET_VARRAYS = "fileReplicationTargetVarrays";
    public static final String FILE_REPLICATION_TARGET_VPOOL = "fileReplicationTargetVPool";
    public static final String FILE_REPLICATION_TYPE = "fileReplicationType";
    public static final String FILE_REPLICATION_APPLIED_AT = "fileReplicationAppliedAt";

    // Not vpool params, but hints for volume descriptor creation
    // TODO: Move to ControllerOperationValuesWrapper
    public static final String FILE_REPLICATION_SOURCE = "file_replication_source";
    public static final String FILE_REPLICATION_TARGET = "file_replication_target";
    public static final String FILE_SYSTEM_CREATE_MIRROR_COPY = "file_system_create_mirror_copy";
    public static final String EXISTING_SOURCE_FILE_SYSTEM = "existing_source_file_system";
    public static final String SOURCE_STORAGE_SYSTEM = "source_storage_system";
    public static final String FILE_PROTECTION_SOURCE_STORAGE_SYSTEM = "file_protection_source_storage_system";
    public static final String EXCLUDED_STORAGE_SYSTEM = "excluded_storage_system";
    public static final String FILE_TARGET_COPY_NAME = "file_target_copy_name";
    public static final String CHANGE_VPOOL_VOLUME = "changeVpoolVolume";
    public static final String SOURCE_VIRTUAL_NAS_SERVER = "source_virtual_nas_server";
    public static final String TARGET_NAS_SERVER = "target_nas_server";
    public static final String TARGET_STORAGE_SYSTEM = "target_storage_system";

    // meta volume capabilities
    public static final String IS_META_VOLUME = "isMetaVolume";
    public static final String META_VOLUME_MEMBER_SIZE = "metaVolumeMemberSize";
    public static final String META_VOLUME_MEMBER_COUNT = "metaVolumeMemberCount";
    public static final String META_VOLUME_TYPE = "metaVolumeType";

    // compute resource capabilities
    public static final String COMPUTE = "compute";
    public static final String ARRAY_AFFINITY = "array_affinity";

    // replica options
    public static final String REPLICA_CREATE_INACTIVE = "replicaActiveInactiveMode";
    public static final String SNAPSHOT_SESSION_COPY_MODE = "snapshotSessionCopyMode";

    public static final String VPOOL_PROJECT_POLICY_ASSIGN = "vpoolProjectPolicyAssign";
    public static final String GET_ALL_SOURCE_RECOMMENDATIONS = "getallsourcerecommendations";

    private final Map<String, Object> _vpoolCapabilities = new HashMap<String, Object>();

    /**
     * Default constructor
     */
    public VirtualPoolCapabilityValuesWrapper() {
    }

    /**
     * Copy the passed capabilities to a new instance.
     * 
     * @param capabilities
     *            A reference to a VirtualPoolCapabilityValuesWrapper
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

        if (capabilities.contains(SUPPORT_SOFT_LIMIT)) {
            _vpoolCapabilities.put(SUPPORT_SOFT_LIMIT, capabilities.getSupportsSoftLimit());
        }

        if (capabilities.contains(SUPPORT_NOTIFICATION_LIMIT)) {
            _vpoolCapabilities.put(SUPPORT_NOTIFICATION_LIMIT, capabilities.getSupportsNotificationLimit());
        }

        if (capabilities.contains(QUOTA)) {
            _vpoolCapabilities.put(QUOTA, capabilities.getQuota());
        }

        if (capabilities.contains(DEDUP)) {
            _vpoolCapabilities.put(DEDUP, capabilities.getDedupCapable());
        }

        if (capabilities.contains(RDF_GROUP)) {
            _vpoolCapabilities.put(RDF_GROUP, capabilities.getRDFGroup());
        }

        if (capabilities.contains(FILE_REPLICATION_RPO_TYPE)) {
            _vpoolCapabilities.put(FILE_REPLICATION_RPO_TYPE, capabilities.getFileRpRpoType());
        }

        if (capabilities.contains(FILE_REPLICATION_RPO_VALUE)) {
            _vpoolCapabilities.put(FILE_REPLICATION_RPO_VALUE, capabilities.getFileRpRpoValue());
        }

        if (capabilities.contains(FILE_REPLICATION_COPY_MODE)) {
            _vpoolCapabilities.put(FILE_REPLICATION_COPY_MODE, capabilities.getFileRpCopyMode());
        }

        if (capabilities.contains(FILE_REPLICATION_TARGET_VARRAYS)) {
            _vpoolCapabilities.put(FILE_REPLICATION_TARGET_VARRAYS, capabilities.getFileReplicationTargetVArrays());
        }

        if (capabilities.contains(FILE_REPLICATION_TARGET_VPOOL)) {
            _vpoolCapabilities.put(FILE_REPLICATION_TARGET_VPOOL, capabilities.getFileReplicationTargetVPool());
        }

        if (capabilities.contains(FILE_REPLICATION_TYPE)) {
            _vpoolCapabilities.put(FILE_REPLICATION_TYPE, capabilities.getFileReplicationType());
        }

        if (capabilities.contains(FILE_REPLICATION_APPLIED_AT)) {
            _vpoolCapabilities.put(FILE_REPLICATION_APPLIED_AT, capabilities.getFileReplicationAppliedAt());
        }

        if (capabilities.contains(COMPUTE)) {
            _vpoolCapabilities.put(COMPUTE, capabilities.getCompute());
        }

        if (capabilities.contains(ARRAY_AFFINITY)) {
            _vpoolCapabilities.put(ARRAY_AFFINITY, capabilities.getArrayAffinity());
        }

        if (capabilities.contains(CHANGE_VPOOL_VOLUME)) {
            _vpoolCapabilities.put(CHANGE_VPOOL_VOLUME, capabilities.getChangeVpoolVolume());
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

    public Set<String> getFileReplicationTargetVArrays() {
        Object value = _vpoolCapabilities.get(FILE_REPLICATION_TARGET_VARRAYS);
        return value != null ? (Set<String>) value : null;
    }

    public URI getFileReplicationTargetVPool() {
        Object value = _vpoolCapabilities.get(FILE_REPLICATION_TARGET_VPOOL);
        return value != null ? (URI) value : null;
    }

    public String getFileReplicationType() {
        Object value = _vpoolCapabilities.get(FILE_REPLICATION_TYPE);
        return value != null ? (String) value : null;
    }

    public String getFileReplicationAppliedAt() {
        Object value = _vpoolCapabilities.get(FILE_REPLICATION_APPLIED_AT);
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

    public Boolean getSupportsSoftLimit() {
        Object value = _vpoolCapabilities.get(SUPPORT_SOFT_LIMIT);
        return value != null ? (boolean) value : false;
    }

    public Boolean getSupportsNotificationLimit() {
        Object value = _vpoolCapabilities.get(SUPPORT_NOTIFICATION_LIMIT);
        return value != null ? (boolean) value : false;
    }

    public String getQuota() {
        Object value = _vpoolCapabilities.get(QUOTA);
        return value != null ? (String) value : null;
    }

    public boolean getDedupCapable() {
        Object value = _vpoolCapabilities.get(DEDUP);
        return value != null ? (Boolean) value : false;
    }

    public String getRDFGroup() {
        Object value = _vpoolCapabilities.get(RDF_GROUP);
        return value != null ? (String) value : null;
    }

    public Long getFileRpRpoValue() {
        Object value = _vpoolCapabilities.get(FILE_REPLICATION_RPO_VALUE);
        return value != null ? (Long) value : 0L;
    }

    public String getFileRpRpoType() {
        Object value = _vpoolCapabilities.get(FILE_REPLICATION_RPO_TYPE);
        return value != null ? (String) value : null;
    }

    public String getFileRpCopyMode() {
        Object value = _vpoolCapabilities.get(FILE_REPLICATION_COPY_MODE);
        return value != null ? (String) value : null;
    }

    public boolean createMirrorExistingFileSystem() {
        Object value = _vpoolCapabilities.get(FILE_SYSTEM_CREATE_MIRROR_COPY);
        return value != null ? (Boolean) value : false;
    }

    public FileShare getSourceFileSystem() {
        Object value = _vpoolCapabilities.get(EXISTING_SOURCE_FILE_SYSTEM);
        return value != null ? (FileShare) value : null;
    }

    public URI getSourceVirtualNasServer() {
        Object value = _vpoolCapabilities.get(SOURCE_VIRTUAL_NAS_SERVER);
        return value != null ? (URI) value : null;
    }

    public URI getTargetNasServer() {
        Object value = _vpoolCapabilities.get(TARGET_NAS_SERVER);
        return value != null ? (URI) value : null;
    }

    public URI getTargetStorageSystem() {
        Object value = _vpoolCapabilities.get(TARGET_STORAGE_SYSTEM);
        return value != null ? (URI) value : null;
    }

    public StorageSystem getSourceStorageDevice() {
        Object value = _vpoolCapabilities.get(SOURCE_STORAGE_SYSTEM);
        return value != null ? (StorageSystem) value : null;
    }

    public URI getFileProtectionSourceStorageDevice() {
        Object value = _vpoolCapabilities.get(FILE_PROTECTION_SOURCE_STORAGE_SYSTEM);
        return value != null ? (URI) value : null;
    }

    public StorageSystem getExcludedStorageDevice() {
        Object value = _vpoolCapabilities.get(EXCLUDED_STORAGE_SYSTEM);
        return value != null ? (StorageSystem) value : null;
    }

    public String getFileTargetCopyName() {
        Object value = _vpoolCapabilities.get(FILE_TARGET_COPY_NAME);
        return value != null ? (String) value : null;
    }

    public String getChangeVpoolVolume() {
        Object value = _vpoolCapabilities.get(CHANGE_VPOOL_VOLUME);
        return value != null ? (String) value : null;
    }

    public String getReplicaCreateInactive() {
        Object value = _vpoolCapabilities.get(REPLICA_CREATE_INACTIVE);
        return value != null ? (String) value : null;
    }

    public String getSnapshotSessionCopyMode() {
        Object value = _vpoolCapabilities.get(SNAPSHOT_SESSION_COPY_MODE);
        return value != null ? (String) value : null;
    }

    public String getCompute() {
        Object value = _vpoolCapabilities.get(COMPUTE);
        return value != null ? (String) value : null;
    }

    public boolean getArrayAffinity() {
        Object value = _vpoolCapabilities.get(ARRAY_AFFINITY);
        return value != null ? (Boolean) value : false;
    }

    public void removeCapabilityEntry(String keyEntry) {
        if (_vpoolCapabilities.get(keyEntry) != null) {
            _vpoolCapabilities.remove(keyEntry);
        }
    }

    public boolean isVpoolProjectPolicyAssign() {
        Object value = _vpoolCapabilities.get(VPOOL_PROJECT_POLICY_ASSIGN);
        return value != null ? (Boolean) value : false;
    }

    public boolean getAllSourceRecommnedations() {
        Object value = _vpoolCapabilities.get(GET_ALL_SOURCE_RECOMMENDATIONS);
        return value != null ? (Boolean) value : false;
    }
}
