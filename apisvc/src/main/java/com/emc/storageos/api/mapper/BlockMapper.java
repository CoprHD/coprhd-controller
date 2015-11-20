/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.mapDiscoveredDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toLink;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageTier;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.SynchronizationState;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.VirtualArrayRelatedResourceRep;
import com.emc.storageos.model.adapters.StringMapAdapter;
import com.emc.storageos.model.adapters.StringSetMapAdapter;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.BlockMirrorRestRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.MigrationRestRep;
import com.emc.storageos.model.block.UnManagedExportMaskRestRep;
import com.emc.storageos.model.block.UnManagedVolumeRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.VolumeRestRep.FullCopyRestRep;
import com.emc.storageos.model.block.VolumeRestRep.MirrorRestRep;
import com.emc.storageos.model.block.VolumeRestRep.ProtectionRestRep;
import com.emc.storageos.model.block.VolumeRestRep.RecoverPointRestRep;
import com.emc.storageos.model.block.VolumeRestRep.SRDFRestRep;
import com.emc.storageos.model.block.VplexMirrorRestRep;
import com.emc.storageos.model.block.tier.AutoTierPolicyList;
import com.emc.storageos.model.block.tier.AutoTieringPolicyRestRep;
import com.emc.storageos.model.block.tier.StorageTierRestRep;
import com.emc.storageos.model.vpool.NamedRelatedVirtualPoolRep;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.storageos.model.vpool.VirtualPoolChangeRep;
import com.emc.storageos.volumecontroller.impl.xtremio.prov.utils.XtremIOProvUtils;

public class BlockMapper {

    private static final Logger logger = LoggerFactory.getLogger(BlockMapper.class);

    public static void mapBlockObjectFields(BlockObject from, BlockObjectRestRep to) {
        mapDataObjectFields(from, to);
        to.setWwn(from.getWWN());
        to.setStorageController(from.getStorageController());
        to.setProtocols(from.getProtocol());
        to.setVirtualArray(toRelatedResource(ResourceTypeEnum.VARRAY, from.getVirtualArray()));
        to.setDeviceLabel(from.getDeviceLabel() != null ? from.getDeviceLabel() : "");
        to.setNativeId(from.getNativeId() != null ? from.getNativeId() : "");
        to.setConsistencyGroup(toRelatedResource(ResourceTypeEnum.BLOCK_CONSISTENCY_GROUP, from.getConsistencyGroup()));
    }

    public static VolumeRestRep map(Volume from) {
        return map(null, from);
    }

    public static VolumeRestRep map(DbClient dbClient, Volume from) {
        if (from == null) {
            return null;
        }
        VolumeRestRep to = new VolumeRestRep();
        mapBlockObjectFields(from, to);

        if (from.getProject() != null) {
            to.setProject(toRelatedResource(ResourceTypeEnum.PROJECT, from.getProject().getURI()));
        }
        if (from.getTenant() != null) {
            to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, from.getTenant().getURI()));
        }
        to.setProvisionedCapacity(CapacityUtils.convertBytesToGBInStr(from.getProvisionedCapacity()));
        to.setAllocatedCapacity(CapacityUtils.convertBytesToGBInStr(from.getAllocatedCapacity()));
        to.setCapacity(CapacityUtils.convertBytesToGBInStr(from.getCapacity()));
        if (from.getThinlyProvisioned()) {
            to.setPreAllocationSize(CapacityUtils.convertBytesToGBInStr(from.getThinVolumePreAllocationSize()));
        }
        to.setVirtualPool(toRelatedResource(ResourceTypeEnum.BLOCK_VPOOL, from.getVirtualPool()));
        to.setIsComposite(from.getIsComposite());
        to.setAutoTierPolicyUri(toRelatedResource(ResourceTypeEnum.AUTO_TIERING_POLICY, from.getAutoTieringPolicyUri(), from.getId()));
        to.setThinlyProvisioned(from.getThinlyProvisioned());
        to.setAccessState(from.getAccessState());
        to.setLinkStatus(from.getLinkStatus());
        // Set xio3xvolume in virtualvolume only if it's backend volume belongs to xtremio & version is 3.x
        if (null != dbClient && null != from.getAssociatedVolumes() && !from.getAssociatedVolumes().isEmpty()) {
            for (String backendVolumeuri : from.getAssociatedVolumes()) {
                Volume backendVol = dbClient.queryObject(Volume.class, URIUtil.uri(backendVolumeuri));
                if (null != backendVol) {
                    StorageSystem system = dbClient.queryObject(StorageSystem.class, backendVol.getStorageController());

                    if (null != system && StorageSystem.Type.xtremio.name().equalsIgnoreCase(system.getSystemType())
                            && !XtremIOProvUtils.is4xXtremIOModel(system.getModel())) {
                        to.setHasXIO3XVolumes(Boolean.TRUE);
                        break;
                    }
                }
            }
        }

        if (from.getPool() != null) {
            to.setPool(toRelatedResource(ResourceTypeEnum.STORAGE_POOL, from.getPool()));
        }

        // RecoverPoint specific section
        RecoverPointRestRep toRp = null;

        if (from.checkForRp()) {
            toRp = new RecoverPointRestRep();
            toRp.setProtectionSystem(toRelatedResource(ResourceTypeEnum.PROTECTION_SYSTEM, from.getProtectionController()));
            toRp.setPersonality(from.getPersonality());
            toRp.setInternalSiteName(from.getInternalSiteName());
            toRp.setCopyName(from.getRpCopyName());
            toRp.setRsetName(from.getRSetName());
            if ((from.getRpTargets() != null) && (!from.getRpTargets().isEmpty())) {
                List<VirtualArrayRelatedResourceRep> rpTargets = new ArrayList<VirtualArrayRelatedResourceRep>();
                for (String target : from.getRpTargets()) {
                    rpTargets.add(toTargetVolumeRelatedResource(ResourceTypeEnum.VOLUME, URI.create(target), getVarray(dbClient, target)));
                }
                toRp.setRpTargets(rpTargets);
            }

            if (from.getProtectionSet() != null) {
                toRp.setProtectionSet(toRelatedResource(ResourceTypeEnum.PROTECTION_SET, from.getProtectionSet().getURI(), from.getId()));
            }
        }

        // Mirror specific section
        MirrorRestRep toMirror = null;
        if ((from.getMirrors() != null) && (!from.getMirrors().isEmpty())) {
            toMirror = new MirrorRestRep();
            List<VirtualArrayRelatedResourceRep> mirrors = new ArrayList<VirtualArrayRelatedResourceRep>();
            for (String mirror : from.getMirrors()) {
                mirrors.add(toTargetVolumeRelatedResource(ResourceTypeEnum.BLOCK_MIRROR, URI.create(mirror), from.getId(),
                        getVarray(dbClient, mirror)));
            }
            toMirror.setMirrors(mirrors);
        }

        // Full copy specific section
        FullCopyRestRep toFullCopy = null;
        URI fullCopySourceVolumeURI = from.getAssociatedSourceVolume();
        StringSet fromFullCopies = from.getFullCopies();
        if (fullCopySourceVolumeURI != null || (fromFullCopies != null && !fromFullCopies.isEmpty())) {
            toFullCopy = new FullCopyRestRep();
            if (fullCopySourceVolumeURI != null) {
                toFullCopy.setAssociatedSourceVolume(toRelatedResource(ResourceTypeEnum.VOLUME, fullCopySourceVolumeURI));
            }
            if (fromFullCopies != null) {
                List<VirtualArrayRelatedResourceRep> fullCopies = new ArrayList<VirtualArrayRelatedResourceRep>();
                for (String fullCopy : fromFullCopies) {
                    fullCopies.add(toTargetVolumeRelatedResource(ResourceTypeEnum.VOLUME, URI.create(fullCopy),
                            getVarray(dbClient, fullCopy)));
                }
                toFullCopy.setFullCopyVolumes(fullCopies);
            }
            if (from.getSyncActive() != null) {
                toFullCopy.setSyncActive(from.getSyncActive());
            }
            if (from.getReplicaState() != null) {
                toFullCopy.setReplicaState(from.getReplicaState());
            }
        }

        // SRDF specific section
        SRDFRestRep toSRDF = null;
        if ((from.getSrdfTargets() != null) && (!from.getSrdfTargets().isEmpty())) {
            toSRDF = new SRDFRestRep();
            List<VirtualArrayRelatedResourceRep> targets = new ArrayList<VirtualArrayRelatedResourceRep>();
            for (String target : from.getSrdfTargets()) {
                targets.add(toTargetVolumeRelatedResource(ResourceTypeEnum.VOLUME, URI.create(target), getVarray(dbClient, target)));
            }
            toSRDF.setPersonality(from.getPersonality());
            toSRDF.setSRDFTargetVolumes(targets);
        } else if (!NullColumnValueGetter.isNullNamedURI(from.getSrdfParent())) {
            toSRDF = new SRDFRestRep();
            toSRDF.setPersonality(from.getPersonality());
            toSRDF.setAssociatedSourceVolume(toRelatedResource(ResourceTypeEnum.VOLUME, from.getSrdfParent().getURI()));
            toSRDF.setSrdfCopyMode(from.getSrdfCopyMode());
            toSRDF.setSrdfGroup(from.getSrdfGroup());
        }

        // Protection object encapsulates mirrors and RP
        if (toMirror != null || toRp != null || toFullCopy != null || toSRDF != null) {
            ProtectionRestRep toProtection = new ProtectionRestRep();
            toProtection.setMirrorRep(toMirror);
            toProtection.setRpRep(toRp);
            toProtection.setFullCopyRep(toFullCopy);
            toProtection.setSrdfRep(toSRDF);
            to.setProtection(toProtection);
        }

        if ((from.getAssociatedVolumes() != null) && (!from.getAssociatedVolumes().isEmpty())) {
            List<RelatedResourceRep> backingVolumes = new ArrayList<RelatedResourceRep>();
            for (String backingVolume : from.getAssociatedVolumes()) {
                backingVolumes.add(toRelatedResource(ResourceTypeEnum.VOLUME, URI.create(backingVolume)));
            }
            to.setHaVolumes(backingVolumes);
        }

        return to;
    }

    private static URI getVarray(DbClient dbClient, String target) {
        if (dbClient != null) {
            if (URIUtil.isType(URI.create(target), VplexMirror.class)) {
                VplexMirror mirror = dbClient.queryObject(VplexMirror.class, URI.create(target));
                return mirror.getVirtualArray();
            }
            BlockObject volume = BlockObject.fetch(dbClient, URI.create(target));
            return volume == null ? null : volume.getVirtualArray();
        }
        return null;
    }

    private static VirtualArrayRelatedResourceRep toTargetVolumeRelatedResource(
            ResourceTypeEnum type, URI id, URI varray) {
        VirtualArrayRelatedResourceRep resourceRep = new VirtualArrayRelatedResourceRep();
        if (NullColumnValueGetter.isNullURI(id)) {
            return null;
        }
        resourceRep.setId(id);
        resourceRep.setLink(toLink(type, id));
        resourceRep.setVirtualArray(toRelatedResource(ResourceTypeEnum.VARRAY, varray));
        return resourceRep;
    }

    private static VirtualArrayRelatedResourceRep toTargetVolumeRelatedResource(
            ResourceTypeEnum type, URI id, URI parentId, URI varray) {
        VirtualArrayRelatedResourceRep resourceRep = new VirtualArrayRelatedResourceRep();
        if (NullColumnValueGetter.isNullURI(id)) {
            return null;
        }
        resourceRep.setId(id);
        resourceRep.setLink(toLink(type, id, parentId));
        resourceRep.setVirtualArray(toRelatedResource(ResourceTypeEnum.VARRAY, varray));
        return resourceRep;
    }

    public static BlockSnapshotRestRep map(DbClient dbClient, BlockSnapshot from) {
        if (from == null) {
            return null;
        }
        BlockSnapshotRestRep to = new BlockSnapshotRestRep();
        mapBlockObjectFields(from, to);

        // Map the consistency group
        to.setConsistencyGroup(toRelatedResource(ResourceTypeEnum.BLOCK_CONSISTENCY_GROUP, from.getConsistencyGroup()));

        if (from.getParent() != null) {
            URI parentURI = from.getParent().getURI();
            URIQueryResultList results = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVolumeByAssociatedVolumesConstraint(parentURI.toString()), results);
            Iterator<URI> resultsIter = results.iterator();
            if (resultsIter.hasNext()) {
                parentURI = resultsIter.next();
            }
            to.setParent(toRelatedResource(ResourceTypeEnum.VOLUME, parentURI));
        }
        if (from.getProject() != null) {
            to.setProject(toRelatedResource(ResourceTypeEnum.PROJECT, from.getProject().getURI()));
        }
        to.setNewVolumeNativeId(from.getNewVolumeNativeId());
        to.setSourceNativeId(from.getSourceNativeId());
        to.setSyncActive(from.getIsSyncActive());
        to.setReplicaState(getReplicaState(from));
        to.setReadOnly(from.getIsReadOnly());
        return to;
    }

    public static String getReplicaState(BlockSnapshot snapshot) {
        if (snapshot.getIsSyncActive()) {
            return SynchronizationState.SYNCHRONIZED.name();
        } else {
            return SynchronizationState.PREPARED.name();
        }
    }

    public static BlockMirrorRestRep map(DbClient dbClient, BlockMirror from) {
        if (from == null) {
            return null;
        }
        BlockMirrorRestRep to = new BlockMirrorRestRep();
        mapBlockObjectFields(from, to);

        if (from.getSource() != null) {
            to.setSource(toNamedRelatedResource(ResourceTypeEnum.VOLUME, from.getSource().getURI(), from.getSource().getName()));
        }
        to.setSyncState(from.getSyncState());
        to.setSyncType(from.getSyncType());
        to.setReplicaState(SynchronizationState.fromState(from.getSyncState()).name());
        to.setVirtualPool(toRelatedResource(ResourceTypeEnum.BLOCK_VPOOL, from.getVirtualPool()));
        if (from.getPool() != null) {
            to.setPool(toRelatedResource(ResourceTypeEnum.STORAGE_POOL, from.getPool()));
        }
        return to;
    }

    public static VplexMirrorRestRep map(VplexMirror from) {
        if (from == null) {
            return null;
        }
        VplexMirrorRestRep to = new VplexMirrorRestRep();
        mapDataObjectFields(from, to);

        if (from.getSource() != null) {
            to.setSource(toNamedRelatedResource(ResourceTypeEnum.VOLUME, from.getSource().getURI(), from.getSource().getName()));
        }
        to.setStorageController(from.getStorageController());
        to.setVirtualArray(toRelatedResource(ResourceTypeEnum.VARRAY, from.getVirtualArray()));
        to.setDeviceLabel(from.getDeviceLabel() != null ? from.getDeviceLabel() : "");
        to.setNativeId(from.getNativeId() != null ? from.getNativeId() : "");
        to.setVirtualPool(toRelatedResource(ResourceTypeEnum.BLOCK_VPOOL, from.getVirtualPool()));
        return to;
    }

    public static BlockConsistencyGroupRestRep map(BlockConsistencyGroup from, Set<URI> volumes, DbClient dbClient) {
        if (from == null) {
            return null;
        }

        BlockConsistencyGroupRestRep to = new BlockConsistencyGroupRestRep();
        mapDataObjectFields(from, to);

        to.setVirtualArray(toRelatedResource(ResourceTypeEnum.VARRAY, from.getVirtualArray()));
        to.setProject(toRelatedResource(ResourceTypeEnum.PROJECT, from.getProject().getURI()));
        to.setStorageController(toRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM, from.getStorageController()));

        try {
            if (from.getSystemConsistencyGroups() != null) {
                to.setSystemConsistencyGroups(new StringSetMapAdapter().marshal(from.getSystemConsistencyGroups()));
            }
        } catch (Exception e) {
            // internally ignored
            logger.debug(e.getMessage(), e);
        }

        if (from.getTypes() != null) {
            to.setTypes(from.getTypes());

            if (dbClient != null && volumes != null && volumes.iterator().hasNext()) {
                // Get the first volume in the list. From this we are able to obtain the
                // link status and protection set (RP) information for all volumes in the
                // CG.
                Volume volume = dbClient.queryObject(Volume.class, volumes.iterator().next());

                if (from.getTypes().contains(BlockConsistencyGroup.Types.RP.toString())
                        && !NullColumnValueGetter.isNullNamedURI(volume.getProtectionSet())) {
                    // Get the protection set from the first volume and set the appropriate fields
                    ProtectionSet protectionSet = dbClient.queryObject(ProtectionSet.class, volume.getProtectionSet());
                    to.setRpConsistenyGroupId(protectionSet.getProtectionId());
                    to.setLinkStatus(protectionSet.getProtectionStatus());
                    to.setRpProtectionSystem(protectionSet.getProtectionSystem());
                } else if (from.getTypes().contains(BlockConsistencyGroup.Types.SRDF.toString())) {
                    // Operations cannot be performed individually on volumes within an SRDF CG, hence
                    // we can take any one of the volume's link status and update the CG link status.
                    to.setLinkStatus(volume.getLinkStatus());
                }
            }
        }

        if (volumes != null) {
            List<RelatedResourceRep> volumesResourceRep = new ArrayList<RelatedResourceRep>();
            for (URI volumeUri : volumes) {
                Volume volume = dbClient.queryObject(Volume.class, volumeUri);
                // Only display CG volumes that are non-RP or RP source volumes.
                if (!volume.checkForRp() || (volume.checkForRp() && volume.getPersonality() != null
                        && volume.getPersonality().equals(PersonalityTypes.SOURCE.name()))) {
                    volumesResourceRep.add(toRelatedResource(ResourceTypeEnum.VOLUME, volumeUri));
                }
            }
            to.setVolumes(volumesResourceRep);
        }

        return to;
    }

    public static MigrationRestRep map(Migration from) {
        if (from == null) {
            return null;
        }
        MigrationRestRep to = new MigrationRestRep();
        to.setVolume(toRelatedResource(ResourceTypeEnum.VOLUME, from.getVolume()));
        to.setSource(toRelatedResource(ResourceTypeEnum.VOLUME, from.getSource()));
        to.setTarget(toRelatedResource(ResourceTypeEnum.VOLUME, from.getTarget()));
        to.setStartTime(from.getStartTime());
        to.setStatus(from.getMigrationStatus());
        to.setPercentageDone(from.getPercentDone());
        return to;
    }

    public static AutoTieringPolicyRestRep map(AutoTieringPolicy from) {
        if (from == null) {
            return null;
        }
        AutoTieringPolicyRestRep to = new AutoTieringPolicyRestRep();
        mapDiscoveredDataObjectFields(from, to);
        to.setSystemType(from.getSystemType());
        to.setStorageGroupName(from.getStorageGroupName());
        to.setStorageDevice(toRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM, from.getStorageSystem()));
        to.setStoragePools(from.getPools());
        to.setPolicyName(from.getPolicyName());
        to.setPolicyEnabled(from.getPolicyEnabled());
        to.setProvisioningType(from.getProvisioningType());
        return to;
    }

    public static void addAutoTierPolicy(AutoTieringPolicy policy, AutoTierPolicyList list, boolean uniquePolicyNames) {
        if (DiscoveredDataObject.Type.vnxblock.toString().equalsIgnoreCase(policy.getSystemType())
                || uniquePolicyNames) {
            if (!list.containsPolicy(policy.getPolicyName())) {
                list.getAutoTierPolicies().add(
                        toNamedRelatedResource(policy, policy.getPolicyName()));
            }
        } else if (!uniquePolicyNames) {
            list.getAutoTierPolicies().add(
                    toNamedRelatedResource(policy, policy.getNativeGuid()));
        }
    }

    public static StorageTierRestRep map(StorageTier from) {
        if (from == null) {
            return null;
        }
        StorageTierRestRep to = new StorageTierRestRep();
        mapDiscoveredDataObjectFields(from, to);
        to.setEnabledState(from.getEnabledState());
        to.setPercentage(from.getPercentage());
        to.setTotalCapacity(from.getTotalCapacity());
        to.setDiskDriveTechnology(from.getDiskDriveTechnology());
        to.setAutoTieringPolicies(from.getAutoTieringPolicies());
        return to;
    }

    public static UnManagedVolumeRestRep map(UnManagedVolume from) {
        if (from == null) {
            return null;
        }
        UnManagedVolumeRestRep to = new UnManagedVolumeRestRep();
        mapDataObjectFields(from, to);
        to.setNativeGuid(from.getNativeGuid());
        try {
            to.setVolumeInformation(new StringSetMapAdapter().marshal(from.getVolumeInformation()));
        } catch (Exception e) {
            // Intentionally ignored
        }
        to.setVolumeCharacteristics(new StringMapAdapter().marshal(from.getVolumeCharacterstics()));
        to.setStorageSystem(toRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM, from.getStorageSystemUri()));
        to.setStoragePool(toRelatedResource(ResourceTypeEnum.STORAGE_POOL, from.getStoragePoolUri()));

        List<String> uems = new ArrayList<String>();
        for (String uem : from.getUnmanagedExportMasks()) {
            uems.add(uem);
        }
        to.setUnManagedExportMasks(uems);

        List<String> initiatorUris = new ArrayList<String>();
        for (String uri : from.getInitiatorUris()) {
            initiatorUris.add(uri);
        }
        to.setInitiatorUris(initiatorUris);

        List<String> initiatorNetworkIds = new ArrayList<String>();
        for (String id : from.getInitiatorNetworkIds()) {
            initiatorNetworkIds.add(id);
        }
        to.setInitiatorNetworkIds(initiatorNetworkIds);

        List<String> storagePortUris = new ArrayList<String>();
        for (String uri : from.getStoragePortUris()) {
            storagePortUris.add(uri);
        }
        to.setStoragePortUris(storagePortUris);

        List<String> supportedVPoolUris = new ArrayList<String>();
        for (String uri : from.getSupportedVpoolUris()) {
            supportedVPoolUris.add(uri);
        }
        to.setSupportedVPoolUris(supportedVPoolUris);

        to.setWWN(from.getWwn());

        return to;
    }

    public static UnManagedExportMaskRestRep map(UnManagedExportMask from) {
        if (from == null) {
            return null;
        }
        UnManagedExportMaskRestRep to = new UnManagedExportMaskRestRep();
        mapDataObjectFields(from, to);
        to.setNativeId(from.getNativeId());
        to.setMaskName(from.getMaskName());
        to.setStorageSystem(toRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM, from.getStorageSystemUri()));

        if ((from.getKnownInitiatorUris() != null) && (!from.getKnownInitiatorUris().isEmpty())) {
            List<RelatedResourceRep> reps = new ArrayList<RelatedResourceRep>();
            for (String uri : from.getKnownInitiatorUris()) {
                reps.add(toRelatedResource(ResourceTypeEnum.INITIATOR, URI.create(uri)));
            }
            to.setKnownInitiatorUris(reps);
        }

        if ((from.getKnownStoragePortUris() != null) && (!from.getKnownStoragePortUris().isEmpty())) {
            List<RelatedResourceRep> reps = new ArrayList<RelatedResourceRep>();
            for (String uri : from.getKnownStoragePortUris()) {
                reps.add(toRelatedResource(ResourceTypeEnum.STORAGE_PORT, URI.create(uri)));
            }
            to.setKnownStoragePortUris(reps);
        }

        if ((from.getKnownVolumeUris() != null) && (!from.getKnownVolumeUris().isEmpty())) {
            List<RelatedResourceRep> reps = new ArrayList<RelatedResourceRep>();
            for (String uri : from.getKnownVolumeUris()) {
                reps.add(toRelatedResource(ResourceTypeEnum.VOLUME, URI.create(uri)));
            }
            to.setKnownStorageVolumeUris(reps);
        }

        if ((from.getUnmanagedVolumeUris() != null) && (!from.getUnmanagedVolumeUris().isEmpty())) {
            List<RelatedResourceRep> reps = new ArrayList<RelatedResourceRep>();
            for (String uri : from.getUnmanagedVolumeUris()) {
                reps.add(toRelatedResource(ResourceTypeEnum.UNMANAGED_VOLUMES, URI.create(uri)));
            }
            to.setUnmanagedVolumeUris(reps);
        }

        to.setUnmanagedInitiatorNetworkIds(from.getUnmanagedInitiatorNetworkIds());
        to.setUnmanagedStoragePortNetworkIds(from.getUnmanagedStoragePortNetworkIds());

        return to;
    }

    public static NamedRelatedVirtualPoolRep toVirtualPoolResource(VirtualPool vpool) {
        ResourceTypeEnum type = BlockMapper.getResourceType(VirtualPool.Type.valueOf(vpool.getType()));
        return new NamedRelatedVirtualPoolRep(vpool.getId(), toLink(type, vpool.getId()), vpool.getLabel(), vpool.getType());
    }

    public static VirtualPoolChangeRep toVirtualPoolChangeRep(VirtualPool vpool, List<VirtualPoolChangeOperationEnum> allowedOpertions,
            String notAllowedReason) {
        ResourceTypeEnum type = BlockMapper.getResourceType(VirtualPool.Type.valueOf(vpool.getType()));
        return new VirtualPoolChangeRep(vpool.getId(), toLink(type, vpool.getId()), vpool.getLabel(), vpool.getType(), notAllowedReason,
                allowedOpertions);
    }

    public static ResourceTypeEnum getResourceType(VirtualPool.Type cosType) {
        if (VirtualPool.Type.block == cosType) {
            return ResourceTypeEnum.BLOCK_VPOOL;
        } else if (VirtualPool.Type.file == cosType) {
            return ResourceTypeEnum.FILE_VPOOL;
        } else if (VirtualPool.Type.object == cosType) {
            return ResourceTypeEnum.OBJECT_VPOOL;
        } else {
            // impossible;
            return ResourceTypeEnum.BLOCK_VPOOL;
        }
    }
}
