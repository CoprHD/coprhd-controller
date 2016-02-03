/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery.RemoteMirrorObject;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;
import com.google.common.base.Joiner;

/**
 * Remote Replication Ingestion
 * S1 - Source Volume ,T1 - Target Volume
 * S1 under ViPR control & T1 not yet
 * ****************************************
 * If user tries to ingest volumes of S1, S1 will be ingested with usable flag bit set to false.
 * usable bit set to false indicates, that this volume cannot be used for any provisioning operations
 * If we find S1 as source for multiple protected volumes, check whether ALL expected target volumes are already ingested.
 * If found true, then create ViPR SRDF links between source and targets, by making them as if these source and targets are created via ViPR
 * using SRDF protected VirtualPool, and set usable bit ot TRUE.
 * If not, usable bit remains in false state.
 * T1 under ViPR control and S1 not yet
 * ****************************************
 * If user tries to ingest volumes of T1, T1 will be ingested with usable flag bit set to false.
 * usable bit set to false indicates, that this volume cannot be used for any provisioning operations
 * If we find T1 as target for a source volume, check whether source volume and ALL its expected target volumes are already ingested,
 * exclusing the target which we work on.
 * If found true, then create ViPR SRDF links between source and targets, by making them as if these source and targets are created via ViPR
 * using SRDF protected VirtualPool, and set usable bit ot TRUE.
 * If not, usable bit remains in false state.
 */

public class BlockRemoteReplicationIngestOrchestrator extends BlockVolumeIngestOrchestrator {

    private static final Logger _logger = LoggerFactory.getLogger(BlockRemoteReplicationIngestOrchestrator.class);

    @Override
    protected void checkUnmanagedVolumeReplicas(UnManagedVolume unmanagedVolume) {
        return;
    }

    @Override
    public <T extends BlockObject> T ingestBlockObjects(IngestionRequestContext requestContext, Class<T> clazz)
            throws IngestionException {
        
        UnManagedVolume unManagedVolume = requestContext.getCurrentUnmanagedVolume();
        
        String volumeNativeGuid = unManagedVolume.getNativeGuid().replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                VolumeIngestionUtil.VOLUME);
        BlockObject blockObject = VolumeIngestionUtil.checkIfVolumeExistsInDB(volumeNativeGuid, _dbClient);

        // validate srdf blockObjects.
        validateUnManagedVolumeProperties(unManagedVolume, requestContext.getVarray(unManagedVolume), 
                requestContext.getVpool(unManagedVolume), requestContext.getProject());
        // Check if ingested volume has exportmasks pending for ingestion.
        if (isExportIngestionPending(blockObject, unManagedVolume.getId(), 
                requestContext.getVolumeContext().isVolumeExported())) {
            return clazz.cast(blockObject);
        }

        if (null == blockObject) {
            blockObject = super.ingestBlockObjects(requestContext, clazz);

            if (null == blockObject) {
                _logger.warn("SRDF Volume ingestion failed for unmanagedVolume {}", unManagedVolume.getNativeGuid());
                throw IngestionException.exceptions.unmanagedVolumeMasksNotIngested(unManagedVolume.getNativeGuid(), "none.");
            }
        } else {
            // blockObject already ingested, now just update internalflags &
            // srdf relationships. Run this logic always when volume NO_PUBLIC_ACCESS
            if (markUnManagedVolumeInactive(requestContext, blockObject)) {
                _logger.info("All the related replicas and parent of unManagedVolume {} has been ingested ",
                        unManagedVolume.getNativeGuid());
                unManagedVolume.setInactive(true);
                requestContext.getUnManagedVolumesToBeDeleted().add(unManagedVolume);
            } else {
                _logger.info(
                        "Not all the parent/replicas of unManagedVolume {} have been ingested , hence marking as internal",
                        unManagedVolume.getNativeGuid());
                blockObject.addInternalFlags(INTERNAL_VOLUME_FLAGS);
            }

        }
        // Decorate blockobjects with SRDF Properties.
        decorateBlockObjectWithSRDFProperties(blockObject, unManagedVolume);

        return clazz.cast(blockObject);
    }

    @Override
    protected void validateAutoTierPolicy(String autoTierPolicyId, UnManagedVolume unManagedVolume, VirtualPool vPool) {
        super.validateAutoTierPolicy(autoTierPolicyId, unManagedVolume, vPool);
    }

    /**
     * Decorates the block objects with srdf properties.
     * 
     * @param blockObject
     * @param unManagedVolume
     */
    private void decorateBlockObjectWithSRDFProperties(BlockObject blockObject, UnManagedVolume unManagedVolume) {
        Volume volume = (Volume) blockObject;
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        String type = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.REMOTE_VOLUME_TYPE.toString(), unManagedVolumeInformation);
        if (RemoteMirrorObject.Types.SOURCE.toString().equalsIgnoreCase(type)) {
            volume.setPersonality(PersonalityTypes.SOURCE.toString());
        } else if (RemoteMirrorObject.Types.TARGET.toString().equalsIgnoreCase(type)) {
            volume.setPersonality(PersonalityTypes.TARGET.toString());
            String copyMode = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.REMOTE_COPY_MODE.toString(), unManagedVolumeInformation);
            String raGroup = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.REMOTE_MIRROR_RDF_GROUP.toString(), unManagedVolumeInformation);
            volume.setSrdfCopyMode(copyMode);
            volume.setSrdfGroup(URI.create(raGroup));
        }
    }

    /**
     * Validates the UnManagedVolume SRDF Properties.
     * 
     * @param unManagedVolume
     * @param virtualArray
     * @param virtualPool
     */
    private void validateUnManagedVolumeProperties(UnManagedVolume unManagedVolume, VirtualArray virtualArray,
            VirtualPool virtualPool, Project project) {
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        URI rdfGroupId = getRDFGroupBasedOnPersonality(unManagedVolumeInformation);
        // To make sure rdfGroup is populated for both R1 & R2 volumes.
        if (null == rdfGroupId) {
            _logger.warn("SRDF Volume ingestion failed for unmanagedVolume {} as not able to find RDFGroup.",
                    unManagedVolume.getNativeGuid());
            throw IngestionException.exceptions.unmanagedVolumeRDFGroupMissing(unManagedVolume.getNativeGuid());
        }
        RemoteDirectorGroup rdfGroup = _dbClient.queryObject(RemoteDirectorGroup.class, rdfGroupId);
        // name check, "V-<projectname>" or "<projectname>"
        StringSet grpNames = SRDFUtils.getQualifyingRDFGroupNames(project);
        // Validate the project Name with the unmanaged volume rdfGroup name.
        if (null == rdfGroup.getLabel() || !SRDFUtils.containsRaGroupName(grpNames, rdfGroup.getLabel())) {
            _logger.warn("SRDF Volume ingestion failed for unmanagedVolume {} due to mismatch in RDF group name",
                    unManagedVolume.getNativeGuid());
            throw IngestionException.exceptions.unmanagedVolumeRDFGroupMismatch(unManagedVolume.getNativeGuid(),
                    rdfGroup.getLabel(), project.getLabel(), StringUtils.join(grpNames, ","));
        }

        String type = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.REMOTE_VOLUME_TYPE.toString(), unManagedVolumeInformation);
        if (null == type) {
            _logger.warn("SRDF Volume ingestion failed for unmanagedVolume {}", unManagedVolume.getNativeGuid());
            throw IngestionException.exceptions.unmanagedVolumeVolumeTypeNotSet(unManagedVolume.getNativeGuid());
        }
        _logger.info("Type {} Source Native Guid {}", type, unManagedVolume.getNativeGuid());

        if (RemoteMirrorObject.Types.SOURCE.toString().equalsIgnoreCase(type)) {
            validateSourceVolumeVarrayWithTargetVPool(unManagedVolume, virtualPool);
        } else if (RemoteMirrorObject.Types.TARGET.toString().equalsIgnoreCase(type)) {
            validateTargetVolumeVpoolWithSourceVolume(unManagedVolume, virtualArray);
        }

    }

    /**
     * Return the rdfGroupId based on the personality.
     * For source volume, we will not have RDFGroup hence we should get it from its targets.
     * 
     * @param unManagedVolumeInformation
     * @return
     */
    private URI getRDFGroupBasedOnPersonality(StringSetMap unManagedVolumeInformation) {
        String type = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.REMOTE_VOLUME_TYPE.toString(), unManagedVolumeInformation);
        URI rdfGroupId = null;
        if (RemoteMirrorObject.Types.SOURCE.toString().equalsIgnoreCase(type)) {
            StringSet targetUnManagedVolumeGuids = unManagedVolumeInformation.get(SupportedVolumeInformation.REMOTE_MIRRORS
                    .toString());
            if (null != targetUnManagedVolumeGuids && !targetUnManagedVolumeGuids.isEmpty()) {
                StringSet targetVolumeNativeGuids = VolumeIngestionUtil.getListofVolumeIds(targetUnManagedVolumeGuids);
                List<URI> targetUris = VolumeIngestionUtil.getVolumeUris(targetVolumeNativeGuids, _dbClient);
                if (null == targetUris || targetUris.isEmpty()) {
                    List<URI> unmanagedTargetVolumes = VolumeIngestionUtil.getUnManagedVolumeUris(targetUnManagedVolumeGuids, _dbClient);
                    for (URI targetUmv : unmanagedTargetVolumes) {
                        _logger.info("RDFGroup Found using unmanaged Target volume {}", targetUmv);
                        UnManagedVolume umv = _dbClient.queryObject(UnManagedVolume.class, targetUmv);
                        rdfGroupId = URI.create(PropertySetterUtil.extractValueFromStringSet(
                                SupportedVolumeInformation.REMOTE_MIRROR_RDF_GROUP.toString(), umv.getVolumeInformation()));
                        break;
                    }
                } else {
                    // If targets are already ingested.
                    List<Volume> targetVolumes = _dbClient.queryObject(Volume.class, targetUris);
                    if (null != targetVolumes && !targetVolumes.isEmpty()) {
                        for (Volume targetVolume : targetVolumes) {
                            _logger.info("RDFGroup Found for using ingested Target volumes {}.", targetVolume.getNativeGuid());
                            rdfGroupId = targetVolume.getSrdfGroup();
                            break;
                        }
                    }
                }
            }

        } else if (RemoteMirrorObject.Types.TARGET.toString().equalsIgnoreCase(type)) {
            rdfGroupId = URI.create(PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.REMOTE_MIRROR_RDF_GROUP.toString(), unManagedVolumeInformation));
        }

        return rdfGroupId;
    }

    /**
     * Validate the SourceVolume VArray details with ingested target volumes
     * VArray.
     * 
     * @param unManagedVolume
     * @param VirtualPool
     * @return
     */
    private void validateSourceVolumeVarrayWithTargetVPool(UnManagedVolume unManagedVolume, VirtualPool sourceVPool) {
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        // find whether all targets are ingested
        StringSet targetUnManagedVolumeGuids = unManagedVolumeInformation.get(SupportedVolumeInformation.REMOTE_MIRRORS
                .toString());
        if (null != targetUnManagedVolumeGuids && !targetUnManagedVolumeGuids.isEmpty()) {
            StringSet targetVolumeNativeGuids = VolumeIngestionUtil.getListofVolumeIds(targetUnManagedVolumeGuids);
            // check whether target exists
            List<URI> targetUris = VolumeIngestionUtil.getVolumeUris(targetVolumeNativeGuids, _dbClient);
            if (null == targetUris || targetUris.isEmpty()) {
                _logger.info("None of the targets ingested for source volume: {}", unManagedVolume.getNativeGuid());
            } else {
                List<Volume> targetVolumes = _dbClient.queryObject(Volume.class, targetUris);
                for (Volume targetVolume : targetVolumes) {
                    Map<URI, VpoolRemoteCopyProtectionSettings> settings = sourceVPool.getRemoteProtectionSettings(
                            sourceVPool, _dbClient);
                    if (null == settings || settings.size() == 0
                            || !settings.containsKey(targetVolume.getVirtualArray())) {
                        _logger.info(
                                "Target Volume's VArray {} is not matching already ingested source volume virtual pool's remote VArray {}",
                                targetVolume.getVirtualArray(), Joiner.on(",").join(settings.keySet()));
                        throw IngestionException.exceptions.unmanagedSRDFSourceVolumeVArrayMismatch(
                                unManagedVolume.getLabel(), targetVolume.getVirtualArray().toString());
                    }
                }
            }
        }
    }

    /**
     * Validate the Target Volume VirtualArray with the Source Volume VPool
     * VirtualArray.
     * 
     * @param type
     * @param unManagedVolume
     * @param virtualArray
     */
    private void validateTargetVolumeVpoolWithSourceVolume(UnManagedVolume unManagedVolume, VirtualArray virtualArray) {
        String sourceUnManagedVolumeId = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.REMOTE_MIRROR_SOURCE_VOLUME.toString(),
                unManagedVolume.getVolumeInformation());
        String sourceVolumeId = sourceUnManagedVolumeId.replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                VolumeIngestionUtil.VOLUME);
        List<URI> sourceUris = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVolumeNativeGuidConstraint(sourceVolumeId));
        if (sourceUris.isEmpty()) {
            _logger.info("Source {} Not found for target {}", sourceVolumeId, unManagedVolume.getNativeGuid());
        } else {
            // if source volume is ingested, then
            Volume sourceVolume = _dbClient.queryObject(Volume.class, sourceUris.get(0));
            // check whether the source Volume's VPool is actually having this
            // target Volume's varray
            // specified as remote
            VirtualPool sourceVPool = _dbClient.queryObject(VirtualPool.class, sourceVolume.getVirtualPool());
            Map<URI, VpoolRemoteCopyProtectionSettings> settings = sourceVPool.getRemoteProtectionSettings(sourceVPool,
                    _dbClient);
            if (null == settings || settings.isEmpty() || !settings.containsKey(virtualArray.getId())) {
                _logger.info(
                        "Target Volume's VArray {} is not matching already ingested source volume virtual pool's remote VArray ",
                        virtualArray.getId());
                throw IngestionException.exceptions.unmanagedSRDFTargetVolumeVArrayMismatch(
                        unManagedVolume.getLabel(), sourceVolume.getVirtualArray().toString());
            }
        }
    }
}
