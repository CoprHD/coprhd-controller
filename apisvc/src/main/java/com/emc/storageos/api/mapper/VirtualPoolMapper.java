/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFieldsNoLink;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;
import static com.emc.storageos.db.client.util.NullColumnValueGetter.isNotNullValue;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolProtectionParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileReplicationPolicy;
import com.emc.storageos.model.vpool.FileVirtualPoolProtectionParam;
import com.emc.storageos.model.vpool.FileVirtualPoolReplicationParam;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.ObjectVirtualPoolRestRep;
import com.emc.storageos.model.vpool.ProtectionCopyPolicy;
import com.emc.storageos.model.vpool.ProtectionSourcePolicy;
import com.emc.storageos.model.vpool.VirtualPoolCommonRestRep;
import com.emc.storageos.model.vpool.VirtualPoolHighAvailabilityParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionMirrorParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionRPParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionSnapshotsParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionVirtualArraySettingsParam;
import com.emc.storageos.model.vpool.VirtualPoolRemoteMirrorProtectionParam;
import com.emc.storageos.model.vpool.VirtualPoolRemoteProtectionVirtualArraySettingsParam;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class VirtualPoolMapper {
    public static BlockVirtualPoolRestRep toBlockVirtualPool(DbClient dbclient, VirtualPool from) {
        return toBlockVirtualPool(dbclient, from, null, null);
    }

    public static BlockVirtualPoolRestRep toBlockVirtualPool(DbClient dbclient, VirtualPool from, Map<URI,
            VpoolProtectionVarraySettings> protectionSettings,
            Map<URI, VpoolRemoteCopyProtectionSettings> remoteProtectionSettings) {
        if (from == null) {
            return null;
        }
        BlockVirtualPoolRestRep to = new BlockVirtualPoolRestRep();
        to.setDriveType(from.getDriveType());
        to.setAutoTieringPolicyName(from.getAutoTierPolicyName());
        to.setThinVolumePreAllocationPercentage(from.getThinVolumePreAllocationPercentage());
        to.setExpandable(from.getExpandable());
        to.setFastExpansion(from.getFastExpansion());
        to.setMultiVolumeConsistent(from.getMultivolumeConsistency());
        to.setUniquePolicyNames(from.getUniquePolicyNames());
        to.setMaxPaths(from.getNumPaths());
        to.setMinPaths(from.getMinPaths());
        to.setPathsPerInitiator(from.getPathsPerInitiator());
        to.setHostIOLimitBandwidth(from.getHostIOLimitBandwidth());
        to.setHostIOLimitIOPs(from.getHostIOLimitIOPs());

        if (from.getArrayInfo() != null) {
            StringSetMap arrayInfo = from.getArrayInfo();

            // Raid Levels
            StringSet raidLevels = arrayInfo.get(VirtualPoolCapabilityValuesWrapper.RAID_LEVEL);
            if (raidLevels != null) {
                to.setRaidLevels(raidLevels);
            }
        }

        BlockVirtualPoolProtectionParam protection = new BlockVirtualPoolProtectionParam();

        // mirror logic
        protection.setContinuousCopies(new VirtualPoolProtectionMirrorParam());
        if (NullColumnValueGetter.isNotNullValue(from.getMirrorVirtualPool())) {
            protection.getContinuousCopies().setVpool(URI.create(from.getMirrorVirtualPool()));
        }
        protection.getContinuousCopies().setMaxMirrors(from.getMaxNativeContinuousCopies());

        // SRDF logic
        if (null != remoteProtectionSettings && !remoteProtectionSettings.isEmpty()) {
            protection.setRemoteCopies(new VirtualPoolRemoteMirrorProtectionParam());

            protection.getRemoteCopies().setRemoteCopySettings(new ArrayList<VirtualPoolRemoteProtectionVirtualArraySettingsParam>());
            for (Map.Entry<URI, VpoolRemoteCopyProtectionSettings> remoteSetting : remoteProtectionSettings.entrySet()) {
                VirtualPoolRemoteProtectionVirtualArraySettingsParam remoteCopy = new VirtualPoolRemoteProtectionVirtualArraySettingsParam();
                remoteCopy.setRemoteCopyMode(remoteSetting.getValue().getCopyMode());
                remoteCopy.setVarray(remoteSetting.getValue().getVirtualArray());
                remoteCopy.setVpool(remoteSetting.getValue().getVirtualPool());
                protection.getRemoteCopies().getRemoteCopySettings().add(remoteCopy);
            }

        }

        // RP logic
        if (protectionSettings != null && !protectionSettings.isEmpty()) {
            protection.setRecoverPoint(new VirtualPoolProtectionRPParam());

            if (isNotNullValue(from.getJournalSize())) {
                if (protection.getRecoverPoint().getSourcePolicy() == null) {
                    protection.getRecoverPoint().setSourcePolicy(new ProtectionSourcePolicy());
                }
                protection.getRecoverPoint().getSourcePolicy().setJournalSize(from.getJournalSize());
            }

            if (isNotNullValue(from.getJournalVarray())) {
                if (protection.getRecoverPoint().getSourcePolicy() == null) {
                    protection.getRecoverPoint().setSourcePolicy(new ProtectionSourcePolicy());
                }
                protection.getRecoverPoint().getSourcePolicy().setJournalVarray(URI.create(from.getJournalVarray()));
            }

            if (isNotNullValue(from.getJournalVpool())) {
                if (protection.getRecoverPoint().getSourcePolicy() == null) {
                    protection.getRecoverPoint().setSourcePolicy(new ProtectionSourcePolicy());
                }
                protection.getRecoverPoint().getSourcePolicy().setJournalVpool(URI.create(from.getJournalVpool()));
            }

            if (isNotNullValue(from.getStandbyJournalVarray())) {
                if (protection.getRecoverPoint().getSourcePolicy() == null) {
                    protection.getRecoverPoint().setSourcePolicy(new ProtectionSourcePolicy());
                }
                protection.getRecoverPoint().getSourcePolicy().setStandbyJournalVarray(URI.create(from.getStandbyJournalVarray()));
            }

            if (isNotNullValue(from.getStandbyJournalVpool())) {
                if (protection.getRecoverPoint().getSourcePolicy() == null) {
                    protection.getRecoverPoint().setSourcePolicy(new ProtectionSourcePolicy());
                }
                protection.getRecoverPoint().getSourcePolicy().setStandbyJournalVpool(URI.create(from.getStandbyJournalVpool()));
            }

            if (isNotNullValue(from.getRpCopyMode())) {
                if (protection.getRecoverPoint().getSourcePolicy() == null) {
                    protection.getRecoverPoint().setSourcePolicy(new ProtectionSourcePolicy());
                }
                protection.getRecoverPoint().getSourcePolicy().setRemoteCopyMode(from.getRpCopyMode());
            }

            if (isNotNullValue(from.getRpRpoType())) {
                if (protection.getRecoverPoint().getSourcePolicy() == null) {
                    protection.getRecoverPoint().setSourcePolicy(new ProtectionSourcePolicy());
                }
                protection.getRecoverPoint().getSourcePolicy().setRpoType(from.getRpRpoType());
            }

            if (from.checkRpRpoValueSet()) {
                if (protection.getRecoverPoint().getSourcePolicy() == null) {
                    protection.getRecoverPoint().setSourcePolicy(new ProtectionSourcePolicy());
                }
                protection.getRecoverPoint().getSourcePolicy().setRpoValue(from.getRpRpoValue());
            }

            protection.getRecoverPoint().setCopies(new HashSet<VirtualPoolProtectionVirtualArraySettingsParam>());
            for (Map.Entry<URI, VpoolProtectionVarraySettings> setting : protectionSettings.entrySet()) {
                VirtualPoolProtectionVirtualArraySettingsParam copy = new VirtualPoolProtectionVirtualArraySettingsParam();
                copy.setVpool(setting.getValue().getVirtualPool());
                copy.setVarray(setting.getKey());
                copy.setCopyPolicy(new ProtectionCopyPolicy());
                copy.getCopyPolicy().setJournalSize(setting.getValue().getJournalSize());
                copy.getCopyPolicy().setJournalVarray(setting.getValue().getJournalVarray());
                copy.getCopyPolicy().setJournalVpool(setting.getValue().getJournalVpool());
                protection.getRecoverPoint().getCopies().add(copy);
            }
        }

        // Show the feature even if it's disabled
        protection.setSnapshots(new VirtualPoolProtectionSnapshotsParam());
        protection.getSnapshots().setMaxSnapshots(from.getMaxNativeSnapshots());

        if (protection.hasAnyProtection()) {
            to.setProtection(protection);
        }

        // VPLEX logic
        if (isNotNullValue(from.getHighAvailability())) {
            VirtualPoolHighAvailabilityParam haParam = new VirtualPoolHighAvailabilityParam(
                    from.getHighAvailability());

            // Set the MetroPoint field.
            haParam.setMetroPoint(from.getMetroPoint());
            haParam.setAutoCrossConnectExport(from.getAutoCrossConnectExport());

            StringMap haVarrayVpoolMap = from.getHaVarrayVpoolMap();
            if ((haVarrayVpoolMap != null) && (haVarrayVpoolMap.size() != 0)) {
                VirtualPoolHighAvailabilityParam.VirtualArrayVirtualPoolMapEntry varrayVpoolMapEntry = null;
                for (Map.Entry<String, String> entry : haVarrayVpoolMap.entrySet()) {

                    URI vpoolUri = null;
                    // Logic to ensure "null" doesn't get displayed as the HA vpool.
                    if (isNotNullValue(entry.getValue())) {
                        vpoolUri = URI.create(entry.getValue());
                    }

                    if (entry.getKey() != null && !entry.getKey().isEmpty()) {
                        varrayVpoolMapEntry = new VirtualPoolHighAvailabilityParam.VirtualArrayVirtualPoolMapEntry(
                                URI.create(entry.getKey()), vpoolUri);
                    }

                    if (isNotNullValue(from.getHaVarrayConnectedToRp())
                            && !from.getHaVarrayConnectedToRp().isEmpty()) {
                        varrayVpoolMapEntry.setActiveProtectionAtHASite(true);
                    } else {
                        varrayVpoolMapEntry.setActiveProtectionAtHASite(false);
                    }

                    haParam.setHaVirtualArrayVirtualPool(varrayVpoolMapEntry);

                    if (vpoolUri != null) {
                        VirtualPool haVpool = dbclient.queryObject(VirtualPool.class, vpoolUri);
                        if (haVpool != null) {
                            if (haVpool.getMirrorVirtualPool() != null) {
                                protection.getContinuousCopies().setHaVpool(URI.create(haVpool.getMirrorVirtualPool()));
                            }
                            protection.getContinuousCopies().setHaMaxMirrors(haVpool.getMaxNativeContinuousCopies());
                        }
                    }
                }
            }
            to.setHighAvailability(haParam);
        }

        return mapVirtualPoolFields(from, to, protectionSettings);
    }

    public static FileVirtualPoolRestRep toFileVirtualPool(VirtualPool from,
            Map<URI, VpoolRemoteCopyProtectionSettings> fileRemoteCopySettings) {
        if (from == null) {
            return null;
        }
        FileVirtualPoolRestRep to = new FileVirtualPoolRestRep();

        // Show the feature even if it's disabled
        to.setProtection(new FileVirtualPoolProtectionParam());
        to.getProtection().setSnapshots(new VirtualPoolProtectionSnapshotsParam());
        to.getProtection().getSnapshots().setMaxSnapshots(from.getMaxNativeSnapshots());
        to.getProtection().setScheduleSnapshots(from.getScheduleSnapshots());
        to.setLongTermRetention(from.getLongTermRetention());
        // Set File replication parameters!!
        to.setFileReplicationType(from.getFileReplicationType());
        to.getProtection().setReplicationParam(new FileVirtualPoolReplicationParam());
        FileVirtualPoolReplicationParam fileReplicationParams = to.getProtection().getReplicationParam();
        fileReplicationParams.setSourcePolicy(new FileReplicationPolicy());
        fileReplicationParams.getSourcePolicy().setCopyMode(from.getFileReplicationCopyMode());
        fileReplicationParams.getSourcePolicy().setRpoValue(from.getFrRpoValue());
        fileReplicationParams.getSourcePolicy().setRpoType(from.getFrRpoType());

        // Remote copies!!
        if (null != fileRemoteCopySettings && !fileRemoteCopySettings.isEmpty()) {
            fileReplicationParams.setCopies(new HashSet<VirtualPoolRemoteProtectionVirtualArraySettingsParam>());
            for (Map.Entry<URI, VpoolRemoteCopyProtectionSettings> remoteSetting : fileRemoteCopySettings.entrySet()) {
                VirtualPoolRemoteProtectionVirtualArraySettingsParam remoteCopy = new VirtualPoolRemoteProtectionVirtualArraySettingsParam();
                remoteCopy.setRemoteCopyMode(remoteSetting.getValue().getCopyMode());
                remoteCopy.setVarray(remoteSetting.getValue().getVirtualArray());
                remoteCopy.setVpool(remoteSetting.getValue().getVirtualPool());
                fileReplicationParams.getCopies().add(remoteCopy);
            }
        }
        return mapVirtualPoolFields(from, to, null);
    }

    public static ObjectVirtualPoolRestRep toObjectVirtualPool(VirtualPool from) {
        if (from == null) {
            return null;
        }
        ObjectVirtualPoolRestRep to = new ObjectVirtualPoolRestRep();
        return mapVirtualPoolFields(from, to, null);
    }

    private static <T extends VirtualPoolCommonRestRep> T mapVirtualPoolFields(VirtualPool from, T to,
            Map<URI, VpoolProtectionVarraySettings> protectionSettings) {
        mapDataObjectFieldsNoLink(from, to);
        ResourceTypeEnum type = ResourceTypeEnum.BLOCK_VPOOL;
        switch (VirtualPool.Type.valueOf(from.getType())) {
            case block:
                type = ResourceTypeEnum.BLOCK_VPOOL;
                break;
            case file:
                type = ResourceTypeEnum.FILE_VPOOL;
                break;
            case object:
                type = ResourceTypeEnum.OBJECT_VPOOL;
        }
        to.setLink(new RestLinkRep("self", RestLinkFactory.newLink(type, from.getId())));
        to.setType(from.getType());
        to.setDescription(from.getDescription());
        to.setProtocols(from.getProtocols());
        to.setProvisioningType(from.getSupportedProvisioningType());
        to.setNumPaths(from.getNumPaths());
        if (from.getArrayInfo() != null) {
            StringSetMap arrayInfo = from.getArrayInfo();
            // System Type
            StringSet systemTypes = arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE);
            if (systemTypes != null) {
                // TODO: CD - This does not seem right. Do we support many?
                for (String systemType : systemTypes) {
                    to.setSystemType(systemType);
                }
            }
        }

        if (!VdcUtil.isRemoteObject(from)) {
            mapVirtualPoolCommonLocalMappings(from, to);
        }

        return to;
    }

    private static <T extends VirtualPoolCommonRestRep> T mapVirtualPoolCommonLocalMappings(VirtualPool from, T to) {
        // don't include local mappings on non-local objects
        if (VdcUtil.isRemoteObject(from)) {
            return to;
        }

        if (from.getVirtualArrays() != null) {
            for (String neighborhood : from.getVirtualArrays()) {
                to.getVirtualArrays().add(toRelatedResource(ResourceTypeEnum.VARRAY, URI.create(neighborhood)));
            }
        }

        to.setUseMatchedPools(from.getUseMatchedPools());
        // CTRL-5086: remove the invalid pools from assigned pools list in RestRep
        if (from.getAssignedStoragePools() != null) {
            StringSet validAssignedPools = from.getAssignedStoragePools();
            if (from.getInvalidMatchedPools() != null) {
                validAssignedPools.removeAll(from.getInvalidMatchedPools());
            }
            for (String pool : validAssignedPools) {
                to.getAssignedStoragePools().add(toRelatedResource(
                        ResourceTypeEnum.STORAGE_POOL, URI.create(pool)));
            }
        }
        if (from.getMatchedStoragePools() != null) {
            for (String pool : from.getMatchedStoragePools()) {
                to.getMatchedStoragePools().add(toRelatedResource(
                        ResourceTypeEnum.STORAGE_POOL, URI.create(pool)));
            }
        }
        if (from.getInvalidMatchedPools() != null) {
            for (String pool : from.getInvalidMatchedPools()) {
                to.getInvalidMatchedStoragePools().add(toRelatedResource(
                        ResourceTypeEnum.STORAGE_POOL, URI.create(pool)));
            }
        }

        return to;
    }
}
