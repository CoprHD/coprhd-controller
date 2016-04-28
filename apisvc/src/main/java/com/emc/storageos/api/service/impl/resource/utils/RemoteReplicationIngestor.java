/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery.RemoteMirrorObject;
import com.google.common.base.Joiner;

public class RemoteReplicationIngestor {
    private static final Logger _logger = LoggerFactory
            .getLogger(RemoteReplicationIngestor.class);

    private static final DataObject.Flag[] INTERNAL_VOLUME_FLAGS = new DataObject.Flag[] {
            Flag.INTERNAL_OBJECT, Flag.PARTIALLY_INGESTED, Flag.NO_METERING };

    /**
     * If unmanaged volume is a Target Volume, then 1. Find if source is ingested 2. If yes, then
     * find whether expected targets of this source had been ingested already excluding the current
     * target. 3. If yes, establish links between source and targets. 4. If not,then make sure
     * unmanaged volume hasn't been deleted.
     *
     * @param unManagedVolume
     * @param volume
     * @param unManagedVolumes
     * @param type
     * @return
     */
    @SuppressWarnings("deprecation")
    private static boolean runRemoteReplicationStepsOnTarget(UnManagedVolume unManagedVolume, Volume volume,
            List<UnManagedVolume> unManagedVolumes, String type, DbClient dbClient) {
        boolean removeUnManagedVolume = false;
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        String sourceUnManagedVolumeId = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.REMOTE_MIRROR_SOURCE_VOLUME.toString(), unManagedVolumeInformation);
        _logger.info("Type {} Source Native Guid {}", type, sourceUnManagedVolumeId);
        String sourceVolumeId = sourceUnManagedVolumeId.replace(VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME);
        List<URI> sourceUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVolumeNativeGuidConstraint(sourceVolumeId));
        String copyMode = PropertySetterUtil.extractValueFromStringSet(SupportedVolumeInformation.REMOTE_COPY_MODE.toString(),
                unManagedVolumeInformation);
        String raGroup = PropertySetterUtil.extractValueFromStringSet(SupportedVolumeInformation.REMOTE_MIRROR_RDF_GROUP.toString(),
                unManagedVolumeInformation);
        volume.setSrdfCopyMode(copyMode);
        volume.setSrdfGroup(URI.create(raGroup));
        if (sourceUris.isEmpty()) {
            _logger.info("Source {} Not found for target {}", sourceVolumeId, volume.getId());
        } else {
            // check whether all targets of the source are ingested
            List<URI> sourceUnmanagedUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getVolumeInfoNativeIdConstraint(sourceUnManagedVolumeId));
            if (!sourceUnmanagedUris.isEmpty()) {
                UnManagedVolume sourceUnManagedVolume = dbClient.queryObject(UnManagedVolume.class, sourceUnmanagedUris.get(0));
                if (null != sourceUnManagedVolume) {
                    StringSet targetUnManagedVolumeGuids = sourceUnManagedVolume.getVolumeInformation().get(
                            SupportedVolumeInformation.REMOTE_MIRRORS.toString());
                    if (null != targetUnManagedVolumeGuids && !targetUnManagedVolumeGuids.isEmpty()) {
                        StringSet targetVolumeNativeGuids = VolumeIngestionUtil.getListofVolumeIds(targetUnManagedVolumeGuids);
                        List<URI> targetUris = VolumeIngestionUtil.getVolumeUris(targetVolumeNativeGuids, dbClient);
                        targetUris.add(volume.getId());
                        _logger.info("Expected targets Size {} , found {} ", targetUnManagedVolumeGuids.size(), targetUris.size());
                        _logger.debug("Expected Targets {} : Found {}", Joiner.on("\t").join(targetVolumeNativeGuids), Joiner.on("\t")
                                .join(targetUris));
                        List<Volume> modifiedVolumes = new ArrayList<Volume>();
                        if (targetUris.size() == targetUnManagedVolumeGuids.size()) {
                            // if all other targets are ingested, then
                            Volume sourceVolume = dbClient.queryObject(Volume.class, sourceUris.get(0));
                            // check whether the source Volume's VPool is actually having this target Volume's varray
                            // specified as remote
                            VirtualPool sourceVPool = dbClient.queryObject(VirtualPool.class, sourceVolume.getVirtualPool());
                            Map<URI, VpoolRemoteCopyProtectionSettings> settings = sourceVPool.getRemoteProtectionSettings(sourceVPool,
                                    dbClient);
                            if (null == settings || settings.size() == 0 || !settings.containsKey(volume.getVirtualArray())) {
                                _logger.info(
                                        "Target Volume's VArray {} is not matching already ingested source volume virtual pool's remote VArray ",
                                        volume.getVirtualArray());
                                return false;
                            }
                            sourceVolume.setSrdfTargets(VolumeIngestionUtil.convertUrisToStrings(targetUris));
                            _logger.info("Clearing internal flag for source volume {} found", sourceVolume.getNativeGuid());
                            sourceVolume.clearInternalFlags(INTERNAL_VOLUME_FLAGS);
                            _logger.debug("Set srdf target for source volume {} found", sourceVolume.getId());
                            modifiedVolumes.add(sourceVolume);
                            // source unmanagedVolume
                            sourceUnManagedVolume.setInactive(true);
                            unManagedVolumes.add(sourceUnManagedVolume);
                            // this target unmanaged volume
                            volume.setSrdfParent(new NamedURI(sourceVolume.getId(), sourceVolume.getLabel()));
                            _logger.debug("target volume  set parent", volume.getId());
                            removeUnManagedVolume = true;
                            // handle other target volumes
                            List<Volume> targetVolumes = dbClient.queryObject(Volume.class, targetUris);
                            for (Volume targetVolume : targetVolumes) {
                                _logger.debug("Set parent for remaining target volume {}", targetVolume.getId());
                                targetVolume.setSrdfParent(new NamedURI(sourceVolume.getId(), sourceVolume.getLabel()));
                                targetVolume.clearInternalFlags(INTERNAL_VOLUME_FLAGS);
                            }
                            modifiedVolumes.addAll(targetVolumes);
                            // target unmanaged volumes
                            List<UnManagedVolume> targetUnManagedVolumes = dbClient.queryObject(UnManagedVolume.class,
                                    VolumeIngestionUtil.getUnManagedVolumeUris(targetUnManagedVolumeGuids, dbClient));
                            for (UnManagedVolume targetUnManagedVol : targetUnManagedVolumes) {
                                _logger.debug("Set Target unmanaged volume inactive {}", targetUnManagedVol.getId());
                                targetUnManagedVol.setInactive(true);
                                unManagedVolumes.add(targetUnManagedVol);
                            }
                            dbClient.persistObject(modifiedVolumes);
                            _logger.info("Target Volume successfully ingested with remote replication links", volume.getNativeGuid());
                        } else {
                            // set volume flag to false
                            _logger.info("Expected Targets not found for source Volume {}", sourceUnManagedVolumeId);
                        }
                    } else {
                        _logger.info(
                                "Targets information not found on source volume {}."
                                        + "This could happen when parallel ingests are tried or the actual volume got deleted on array.",
                                sourceUnManagedVolumeId);
                    }
                }
            }
        }
        return removeUnManagedVolume;
    }

    /**
     * If unmanaged volume is of type Source, then check if all its target volumes are already
     * ingested. if yes, establish links.
     *
     * @param unManagedVolume
     * @param srcVolume
     * @param unManagedVolumes
     * @param type
     * @return
     */
    private static boolean runRemoteReplicationStepsOnSource(UnManagedVolume unManagedVolume, Volume srcVolume,
            List<UnManagedVolume> unManagedVolumes, String type, DbClient dbClient) {
        boolean removeUnManagedVolume = false;
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        // find whether all targets are ingested
        StringSet targetUnManagedVolumeGuids = unManagedVolumeInformation.get(SupportedVolumeInformation.REMOTE_MIRRORS.toString());
        _logger.info("Type : {} --> Source Volume {}", type, srcVolume.getNativeGuid());
        if (null != targetUnManagedVolumeGuids && !targetUnManagedVolumeGuids.isEmpty()) {
            StringSet targetVolumeNativeGuids = VolumeIngestionUtil.getListofVolumeIds(targetUnManagedVolumeGuids);
            // check whether target exists
            List<URI> targetUris = VolumeIngestionUtil.getVolumeUris(targetVolumeNativeGuids, dbClient);
            _logger.info("Expected targets : {} -->Found Target URIs : {}", targetUnManagedVolumeGuids.size(), targetUris.size());
            _logger.debug("Expected Targets {} : Found {}", Joiner.on("\t").join(targetVolumeNativeGuids),
                    Joiner.on("\t").join(targetUris));
            if (targetUris.size() != targetUnManagedVolumeGuids.size()) {
                _logger.info("Found Target Volumes still not ingested.Skipping Remote Replication Link establishment.");
            } else {
                List<Volume> targetVolumes = dbClient.queryObject(Volume.class, targetUris);
                for (Volume targetVolume : targetVolumes) {
                    // Get the Source Volume's remote VArray and compare the same with target's Virtual Array.
                    VirtualPool sourceVPool = dbClient.queryObject(VirtualPool.class, srcVolume.getVirtualPool());
                    Map<URI, VpoolRemoteCopyProtectionSettings> settings = sourceVPool.getRemoteProtectionSettings(sourceVPool,
                            dbClient);
                    if (null == settings || settings.size() == 0 || !settings.containsKey(targetVolume.getVirtualArray())) {
                        _logger.info(
                                "Target Volume's VArray {} is not matching already ingested source volume virtual pool's remote VArray {}",
                                targetVolume.getVirtualArray(), Joiner.on(",").join(settings.keySet()));
                        // remove the target from processing .so that links will never get established
                        targetUris.remove(targetVolume.getId());
                    } else {
                        // for each target set its srdf parent ; copyMode and
                        // raGroup should be updated as part of target ingestion.
                        _logger.info("Set parent for volume {}", targetVolume.getId());
                        targetVolume.setSrdfParent(new NamedURI(srcVolume.getId(), srcVolume.getLabel()));
                        targetVolume.clearInternalFlags(INTERNAL_VOLUME_FLAGS);
                    }

                }

                if (!targetUris.isEmpty()) {
                    // set targets from source
                    srcVolume.setSrdfTargets(VolumeIngestionUtil.convertUrisToStrings(targetUris));

                    dbClient.persistObject(targetVolumes);
                    // can remove unmanaged volume
                    removeUnManagedVolume = true;
                    // setting target unmanaged volumes inactive
                    List<UnManagedVolume> targetUnManagedVolumes = dbClient.queryObject(UnManagedVolume.class,
                            VolumeIngestionUtil.getUnManagedVolumeUris(targetUnManagedVolumeGuids, dbClient));
                    for (UnManagedVolume targetUnManagedVol : targetUnManagedVolumes) {
                        if (!targetUris.contains(targetUnManagedVol.getId())) {
                            _logger.info("Setting unmanaged target inactive {}", targetUnManagedVol.getId());
                            targetUnManagedVol.setInactive(true);
                            unManagedVolumes.add(targetUnManagedVol);
                        } else {
                            _logger.info("Skipping deletion of unmanaged volume {} as remote  links are not established",
                                    targetUnManagedVol.getId());
                        }
                    }
                    _logger.info("Source Volume successfully ingested with remote replication links", srcVolume.getNativeGuid());
                } else {
                    _logger.info("Source Volume failed to ingest with remote replication links", srcVolume.getNativeGuid());
                }
            }
        }
        return removeUnManagedVolume;
    }

    public static boolean runRemoteReplicationStepsOnPartiallyIngestedVolume(UnManagedVolume unManagedVolume, BlockObject bo,
            List<UnManagedVolume> unManagedVolumes, DbClient dbClient) {
        Volume volume = (Volume) bo;
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        boolean remoteLinksEstablished = false;
        String type = PropertySetterUtil.extractValueFromStringSet(SupportedVolumeInformation.REMOTE_VOLUME_TYPE.toString(),
                unManagedVolumeInformation);
        if (null == type) {
            return true;
        }
        if (RemoteMirrorObject.Types.SOURCE.toString().equalsIgnoreCase(type)) {
            volume.setPersonality(PersonalityTypes.SOURCE.toString());
            remoteLinksEstablished = runRemoteReplicationStepsOnSource(unManagedVolume, volume, unManagedVolumes, type, dbClient);
        } else if (RemoteMirrorObject.Types.TARGET.toString().equalsIgnoreCase(type)) {
            volume.setPersonality(PersonalityTypes.TARGET.toString());
            remoteLinksEstablished = runRemoteReplicationStepsOnTarget(unManagedVolume, volume, unManagedVolumes, type, dbClient);
        }
        return remoteLinksEstablished;
    }

}
