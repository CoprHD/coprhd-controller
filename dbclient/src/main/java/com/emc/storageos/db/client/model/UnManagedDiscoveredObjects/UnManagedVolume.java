/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.UnManagedDiscoveredObjects;


import java.net.URI;
import java.util.Map;

import com.emc.storageos.db.client.model.AlternateId;
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
public class UnManagedVolume extends UnManagedDiscoveredObject{
    
    
    private StringSetMap _volumeInformation;
    
    private StringMap _volumeCharacterstics;
    
    private URI _storageSystemUri;
    
    private URI storagePoolUri;
    
    private StringSet _unmanagedExportMasks;
    
    private StringSet _initiatorUris;
    
    private StringSet _initiatorNetworkIds;
    
    private StringSet storagePortUris;
    
    public enum SupportedVolumeCharacterstics {
        
        IS_MAPPED("EMCSVIsMapped", "EMCIsMapped"),
        IS_RECOVERPOINT_ENABLED("EMCSVRecoverPointEnabled", "EMCRecoverPointEnabled"),
        IS_METAVOLUME("EMCSVIsComposite", "EMCIsComposite"),
        IS_AUTO_TIERING_ENABLED("PolicyRuleName", "PolicyRuleName"),
        IS_FULL_COPY("FullCopy", "FullCopy"),
        IS_LOCAL_MIRROR("LocalMirror", "LocalMirror"),
        IS_SNAP_SHOT("Snapshot", "Snapshot"),
        IS_THINLY_PROVISIONED("EMCSVThinlyProvisioned", "ThinlyProvisioned"),
        IS_BOUND("EMCSVIsBound", "EMCIsBound"),
        IS_VOLUME_EXPORTED("isVolumeExported", "isVolumeExported"), 
        HAS_REPLICAS("hasReplicas", "hasReplicas"),
        IS_VOLUME_ADDED_TO_CONSISTENCYGROUP("isVolumeAddedToCG", "isVolumeAddedToCG"), 
        IS_INGESTABLE("IsIngestable", "IsIngestable"), 
        REMOTE_MIRRORING("remoteMirror", "remoteMirror"),
        IS_VPLEX_VOLUME("isVplexVolume", "isVplexVolume"),
		IS_VPLEX_BACKEND_VOLUME("isVplexBackendVolume", "isVplexBackendVolume"),
        EXPORTGROUP_TYPE("exportGroupType", "exportGroupType");
        
        
        private String _charactersticsKey;
        private String _charactersticAlternateKey;
        
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
            for(SupportedVolumeCharacterstics characterstic : values()) {
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
        WWN("EMCSVWWN", "EMCWWN"),
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
        VPLEX_LOCALITY("vplexLocality", "vplexLocality"),
        VPLEX_SUPPORTING_DEVICE_NAME("vplexSupportingDeviceName", "vplexSupportingDeviceName"),
        VPLEX_CONSISTENCY_GROUP_NAME("vplexConsistencyGroup", "vplexConsistencyGroup"),
        VPLEX_CLUSTER_IDS("vplexClusters", "vplexClusters"),
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
        NEEDS_COPY_TO_TARGET("needsCopyToTarget", "needsCopyToTarget"),
        TECHNOLOGY_TYPE("technologyType", "technologyType"),
        SETTINGS_INSTANCE("settingsInstance", "settingsInstance");

        private String _infoKey;
        private String _alternateKey;
        
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
            for(SupportedVolumeInformation info : values()) {
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

        StringSet oldValues  = _volumeInformation.get(key);
        if (oldValues != null) {
            oldValues.replace(values);
        } else {
            _volumeInformation.put(key, values);
        }
    }
    
    public void addVolumeInformation(Map<String,StringSet> volumeInfo) {
        if (null == _volumeInformation)
            setVolumeInformation(new StringSetMap());
        else
            _volumeInformation.clear();
        
        if(volumeInfo.size() > 0)
            _volumeInformation.putAll(volumeInfo);
    }
    
    public void setVolumeInformation(StringSetMap volumeInfo) {
        _volumeInformation = volumeInfo;
    }

    @Name("volumeInformation")
    public StringSetMap getVolumeInformation() {
        return _volumeInformation;
    }

 
    
    public void putVolumeCharacterstics(String key, String value) {
        if (null == _volumeCharacterstics)
            setVolumeCharacterstics(new StringMap());
        else
            _volumeCharacterstics.put(key, value);
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
    @AlternateId("InitiatorNetwordIdIndex")
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

    public enum Types{
		SOURCE,
		TARGET
	}
}
