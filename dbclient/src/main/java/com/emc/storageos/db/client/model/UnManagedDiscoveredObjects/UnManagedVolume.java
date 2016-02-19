/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.UnManagedDiscoveredObjects;

import java.net.URI;
import java.util.Map;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.IndexByKey;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObject;

@Cf("UnManagedVolume")
public class UnManagedVolume extends UnManagedDiscoveredObject {

    private StringSetMap _volumeInformation;

    private StringMap _volumeCharacterstics;

    private URI _storageSystemUri;

    private URI storagePoolUri;

    private StringSet _unmanagedExportMasks;

    private StringSet _initiatorUris;

    private StringSet _initiatorNetworkIds;

    private StringSet storagePortUris;

    private String _wwn;

    public enum SupportedVolumeCharacterstics {

        IS_MAPPED("EMCSVIsMapped", "EMCIsMapped"),
        IS_RECOVERPOINT_ENABLED("EMCSVRecoverPointEnabled", "EMCRecoverPointEnabled"),
        IS_METAVOLUME("EMCSVIsComposite", "EMCIsComposite"),
        IS_AUTO_TIERING_ENABLED("PolicyRuleName", "PolicyRuleName"),
        IS_FULL_COPY("FullCopy", "FullCopy"),
        IS_LOCAL_MIRROR("LocalMirror", "LocalMirror"),
        IS_VPLEX_NATIVE_MIRROR("VplexNativeMirror", "VplexNativeMirror"),
        IS_SNAP_SHOT("Snapshot", "Snapshot"),
        IS_THINLY_PROVISIONED("EMCSVThinlyProvisioned", "ThinlyProvisioned"),
        IS_BOUND("EMCSVIsBound", "EMCIsBound"),
        // Is this volume exported to anything? (including RP and VPLEX)
        IS_VOLUME_EXPORTED("isVolumeExported", "isVolumeExported"),
        // Is this volume export to hosts/clusters? (excluding RP)
        IS_NONRP_EXPORTED("isNonRPExported", "isNonRPExported"),
        HAS_REPLICAS("hasReplicas", "hasReplicas"),
        IS_VOLUME_ADDED_TO_CONSISTENCYGROUP("isVolumeAddedToCG", "isVolumeAddedToCG"),
        IS_INGESTABLE("IsIngestable", "IsIngestable"),
        REMOTE_MIRRORING("remoteMirror", "remoteMirror"),
        IS_VPLEX_VOLUME("isVplexVolume", "isVplexVolume"),
        IS_VPLEX_BACKEND_VOLUME("isVplexBackendVolume", "isVplexBackendVolume"),
        EXPORTGROUP_TYPE("exportGroupType", "exportGroupType");

        private final String _charactersticsKey;
        private final String _charactersticAlternateKey;

        SupportedVolumeCharacterstics(String charactersticsKey, String charactersticAlternateKey) {
            _charactersticsKey = charactersticsKey;
            _charactersticAlternateKey = charactersticAlternateKey;
        }

        public String getCharacterstic() {
            return _charactersticsKey;
        }

        public String getAlterCharacterstic() {
            return _charactersticAlternateKey;
        }

        public static String getVolumeCharacterstic(String charactersticsKey) {
            for (SupportedVolumeCharacterstics characterstic : values()) {
                if (characterstic.getCharacterstic().equalsIgnoreCase(charactersticsKey)
                        || characterstic.getAlterCharacterstic().equalsIgnoreCase(charactersticsKey)) {
                    return characterstic.toString();
                }
            }
            return null;
        }
    }

    public enum SupportedVolumeInformation {
        ALLOCATED_CAPACITY("AFSPSpaceConsumed", "EMCSpaceConsumed"),
        PROVISIONED_CAPACITY("ProvisionedCapacity", "ProvisionedCapacity"),
        TOTAL_CAPACITY("TotalCapacity", "TotalCapacity"),
        DISK_TECHNOLOGY("DiskTechnology", "DiskTechnology"),
        SYSTEM_TYPE("SystemType", "SystemType"),
        RAID_LEVEL("SSElementName", "EMCRaidLevel"),
        STORAGE_POOL("PoolUri", "PoolUri"),
        NATIVE_GUID("NativeGuid", "NativeGuid"),
        AUTO_TIERING_POLICIES("PolicyRuleName", "PolicyRuleName"),
        IS_THINLY_PROVISIONED("EMCSVThinlyProvisioned", "ThinlyProvisioned"),
        NATIVE_ID("SVDeviceID", "DeviceID"),
        SUPPORTED_VPOOL_LIST("vpoolUriList", "vpoolUriList"),
        DATA_FORMAT("EMCSVDataFormat", "EMCDataFormat"),
        DEVICE_LABEL("SVElementName", "ElementName"),
        NAME("SVName", "Name"), REMOTE_COPY_MODE("remoteCopyMode", "remoteCopyMode"), REMOTE_MIRRORS("remoteMirrors", "remoteMirrors"),
        REMOTE_MIRROR_SOURCE_VOLUME("sourceVolume", "sourceVolume"), REMOTE_MIRROR_RDF_GROUP("remoteRAGroup", "remoteRAGroup"),
        REMOTE_VOLUME_TYPE("volumeType", "volumeType"),
        ACCESS("Access", "Access"),
        STATUS_DESCRIPTIONS("StatusDescriptions", "StatusDescriptions"),
        UNMANAGED_CONSISTENCY_GROUP_URI("UnManagedConsistencyGroupURI", "UnManagedConsistencyGroupURI"),
        VPLEX_LOCALITY("vplexLocality", "vplexLocality"),
        VPLEX_SUPPORTING_DEVICE_NAME("vplexSupportingDeviceName", "vplexSupportingDeviceName"),
        VPLEX_CONSISTENCY_GROUP_NAME("vplexConsistencyGroup", "vplexConsistencyGroup"),
        VPLEX_CLUSTER_IDS("vplexClusters", "vplexClusters"),
        // unmanaged volume native GUIDs for the vplex backend volumes
        VPLEX_BACKEND_VOLUMES("vplexBackendVolumes", "vplexBackendVolumes"),
        // vplex cluster id for a vplex backend volume
        VPLEX_BACKEND_CLUSTER_ID("vplexBackendClusterId", "vplexBackendClusterId"),
        // native GUID of the VPLEX virtual volume containing this volume
        VPLEX_PARENT_VOLUME("vplexParentVolume", "vplexParentVolume"),
        // map of backend clone volume GUID to virtual volume GUID
        VPLEX_FULL_CLONE_MAP("vplexFullCloneMap", "vplexFullCloneMap"),
        // map of unmanaged volume GUID mirror to vplex device info context path
        VPLEX_MIRROR_MAP("vplexMirrorMap", "vplexMirrorMap"),
        VPLEX_NATIVE_MIRROR_TARGET_VOLUME("vplexNativeMirrorTargetVolume", "vplexNativeMirrorTargetVolume"),
        VPLEX_NATIVE_MIRROR_SOURCE_VOLUME("vplexNativeMirrorSourceVolume", "vplexNativeMirrorSourceVolume"),
        HLU_TO_EXPORT_MASK_NAME_MAP("hluToExportLabelMap", "hluToExportLabelMap"),
        META_MEMBER_SIZE("metaMemberSize", "metaMemberSize"),
        META_MEMBER_COUNT("metaMemberCount", "metaMemberCount"),
        META_VOLUME_TYPE("compositeType", "compositeType"),
        EMC_MAXIMUM_IO_BANDWIDTH("emcMaximumBandwidth", "emcMaximumBandwidth"),
        EMC_MAXIMUM_IOPS("emcMaximumIops", "emcMaximumIops"),
        // for clone
        FULL_COPIES("fullCopies", "fullCopies"), // volume prop
        REPLICA_STATE("replicaState", "replicaState"),
        // for clone and snapshot
        IS_SYNC_ACTIVE("isSyncActive", "isSyncActive"),
        // for clone, block mirror and block snapshot
        LOCAL_REPLICA_SOURCE_VOLUME("localReplicaSourceVolume", "localReplicaSourceVolume"),
        // for block mirror
        MIRRORS("mirrors", "mirrors"), // volume prop
        SYNC_STATE("syncState", "syncState"),
        SYNC_TYPE("syncType", "syncType"),
        SYNCHRONIZED_INSTANCE("synchronizedInstance", "synchronizedInstance"),
        // for block snapshot
        SNAPSHOTS("snapshots", "snapshots"), // snapshots of a source volume, for internal ingestion use only
        SNAPSHOT_SESSIONS("snapshotSessions", "snapshotSessions"), // snapshot session for a source volume
        NEEDS_COPY_TO_TARGET("needsCopyToTarget", "needsCopyToTarget"),
        TECHNOLOGY_TYPE("technologyType", "technologyType"),
        SETTINGS_INSTANCE("settingsInstance", "settingsInstance"),
        IS_READ_ONLY("isReadOnly", "isReadOnly"),
        RP_PERSONALITY("personality", "personality"),
        RP_COPY_NAME("rpCopyName", "rpCopyName"),
        RP_STANDBY_COPY_NAME("rpStandbyCopyName", "rpStandbyCopyName"),
        RP_COPY_ROLE("rpCopyRole", "rpCopyRole"),
        RP_RSET_NAME("rpRSetName", "rpRSetName"),
        RP_INTERNAL_SITENAME("rpInternalSiteName", "rpInternalSiteName"),
        RP_STANDBY_INTERNAL_SITENAME("rpStandbyInternalSiteName", "rpStandbyInternalSiteName"),
        RP_PROTECTIONSYSTEM("protectionSystem", "protectionSystem"),
        RP_UNMANAGED_TARGET_VOLUMES("rpUnManagedTargetVolumes", "rpUnManagedTargetVolumes"),
        RP_MANAGED_TARGET_VOLUMES("rpManagedTargetVolumes", "rpManagedTargetVolumes"),
        RP_UNMANAGED_SOURCE_VOLUME("rpUnManagedSourceVolume", "rpUnManagedSourceVolume"),
        RP_MANAGED_SOURCE_VOLUME("rpManagedSourceVolume", "rpManagedSourceVolume"),
        RP_ACCESS_STATE("rpAccessState", "rpAccessState"),
        SNAPSHOT_CONSISTENCY_GROUP_NAME("snapshotConsistencyGroupName", "snapshotConsistencyGroupName");

        private final String _infoKey;
        private final String _alternateKey;

        SupportedVolumeInformation(String infoKey, String alterateKey) {
            _infoKey = infoKey;
            _alternateKey = alterateKey;
        }

        public String getInfoKey() {
            return _infoKey;
        }

        public String getAlternateKey() {
            return _alternateKey;
        }

        public static String getVolumeInformation(String infoKey) {
            for (SupportedVolumeInformation info : values()) {
                if (info.getInfoKey().equalsIgnoreCase(infoKey)
                        || info.getAlternateKey().equalsIgnoreCase(infoKey)) {
                    return info.toString();
                }
            }
            return null;
        }
    }

    // Replaces key entry in the volumeInformation map with the new set.
    public void putVolumeInfo(String key, StringSet values) {
        if (null == _volumeInformation) {
            setVolumeInformation(new StringSetMap());
        }

        StringSet oldValues = _volumeInformation.get(key);
        if (oldValues != null) {
            oldValues.replace(values);
        } else {
            _volumeInformation.put(key, values);
        }
    }

    public void addVolumeInformation(Map<String, StringSet> volumeInfo) {
        if (null == _volumeInformation) {
            setVolumeInformation(new StringSetMap());
        } else {
            _volumeInformation.clear();
        }

        if (volumeInfo.size() > 0) {
            _volumeInformation.putAll(volumeInfo);
        }
    }

    public void setVolumeInformation(StringSetMap volumeInfo) {
        _volumeInformation = volumeInfo;
    }

    @Name("volumeInformation")
    public StringSetMap getVolumeInformation() {
        return _volumeInformation;
    }

    public void putVolumeCharacterstics(String key, String value) {
        if (null == _volumeCharacterstics) {
            setVolumeCharacterstics(new StringMap());
        } else {
            _volumeCharacterstics.put(key, value);
        }
    }

    public void setVolumeCharacterstics(StringMap volumeCharacterstics) {
        _volumeCharacterstics = volumeCharacterstics;
    }

    @Name("volumeCharacterstics")
    public StringMap getVolumeCharacterstics() {
        return _volumeCharacterstics;
    }

    public void setStorageSystemUri(URI storageSystemUri) {
        _storageSystemUri = storageSystemUri;
        setChanged("storageDevice");
    }

    @RelationIndex(cf = "UnManagedVolumeRelationIndex", type = StorageSystem.class)
    @Name("storageDevice")
    public URI getStorageSystemUri() {
        return _storageSystemUri;
    }

    @RelationIndex(cf = "UnManagedVolumeRelationIndex", type = StoragePool.class)
    @Name("storagePool")
    public URI getStoragePoolUri() {
        return storagePoolUri;
    }

    public void setStoragePoolUri(URI storagePoolUri) {
        this.storagePoolUri = storagePoolUri;
        setChanged("storagePool");
    }

    @Name("unmanagedExportMasks")
    public StringSet getUnmanagedExportMasks() {
        if (_unmanagedExportMasks == null) {
            setUnmanagedExportMasks(new StringSet());
        }
        return _unmanagedExportMasks;
    }

    public void setUnmanagedExportMasks(StringSet unmanagedExportMasks) {
        this._unmanagedExportMasks = unmanagedExportMasks;
    }

    @Name("initiatorUris")
    public StringSet getInitiatorUris() {
        if (_initiatorUris == null) {
            setInitiatorUris(new StringSet());
        }
        return _initiatorUris;
    }

    public void setInitiatorUris(StringSet initiatorUris) {
        this._initiatorUris = initiatorUris;
    }

    @IndexByKey
    @AlternateId("InitiatorNetworkIdIndex")
    @Name("initiatorNetworkIds")
    public StringSet getInitiatorNetworkIds() {
        if (null == _initiatorNetworkIds) {
            this.setInitiatorNetworkIds(new StringSet());
        }
        return _initiatorNetworkIds;
    }

    public void setInitiatorNetworkIds(StringSet initiatorNetworkIds) {
        this._initiatorNetworkIds = initiatorNetworkIds;
    }

    @Name("storagePortUris")
    public StringSet getStoragePortUris() {
        if (storagePortUris == null) {
            setStoragePortUris(new StringSet());
        }
        return storagePortUris;
    }

    public void setStoragePortUris(StringSet storagePortUris) {
        this.storagePortUris = storagePortUris;
    }

    @AlternateId("UnManagedVolumeWwnIndex")
    @Name("wwn")
    public String getWwn() {
        return _wwn;
    }

    public void setWwn(String wwn) {
        _wwn = BlockObject.normalizeWWN(wwn);
        setChanged("wwn");
    }

    public enum Types {
        SOURCE,
        TARGET,
        REGULAR;

        public static boolean isSourceVolume(Types types) {
            return SOURCE == types;
        }

        public static boolean isTargetVolume(Types types) {
            return TARGET == types;
        }

        public static boolean isRegularVolume(Types types) {
            return REGULAR == types;
        }
    }

    @Override
    public String toString() {
        return this.getLabel() + " (" + this.getId() + ")";
    }
}
