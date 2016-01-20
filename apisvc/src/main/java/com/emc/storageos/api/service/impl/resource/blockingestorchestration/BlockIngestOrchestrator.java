/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.resource.StoragePoolService;
import com.emc.storageos.api.service.impl.resource.StorageSystemService;
import com.emc.storageos.api.service.impl.resource.UnManagedVolumeService;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery.RemoteMirrorObject;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.vplexcontroller.VplexBackendIngestionContext;
import com.google.common.base.Joiner;

/**
 * 
 * Block ingest orchestration responsible for ingesting block objects (snap,clone,volume,mirror).
 * All subclasses should override ingestBlockObjects().
 * 
 */
public abstract class BlockIngestOrchestrator {

    public static final DataObject.Flag[] INTERNAL_VOLUME_FLAGS = new DataObject.Flag[] { Flag.INTERNAL_OBJECT,
            Flag.NO_PUBLIC_ACCESS, Flag.NO_METERING };

    private static final Logger _logger = LoggerFactory.getLogger(BlockIngestOrchestrator.class);

    private static final String POLICY_NOT_MATCH = "POLICY_NOT_MATCH";

    protected DbClient _dbClient;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    /**
     * Ingesta BlockObjects Volume, Snapshot, or Clone. All Replica subclasses should extend this.
     *
     * @param requestContext the IngestionRequestContext for this ingestion process
     * @param clazz the type Class of the current UnManagedVolume being ingested
     * @return BlockObject the ingestd BlockObject
     */
    protected abstract <T extends BlockObject> T ingestBlockObjects(IngestionRequestContext requestContext, Class<T> clazz)
            throws IngestionException;

    /**
     * Validate that the Unmanaged Volume can be ingested.
     * 
     * @param unManagedVolume the unmanaged volume in question.
     * @param vPool the VirtualPool into which it would be ingested.
     */
    protected void validateUnManagedVolume(UnManagedVolume unManagedVolume, VirtualPool vPool) throws IngestionException {
        VolumeIngestionUtil.checkUnmanagedVolumeInactive(unManagedVolume);
        VolumeIngestionUtil.checkVPoolValidForUnManagedVolumeInProtectedMode(vPool, unManagedVolume, _dbClient);
        VolumeIngestionUtil.checkUnManagedResourceIngestable(unManagedVolume);
        VolumeIngestionUtil.checkUnManagedResourceExportWwnPresent(unManagedVolume);
        VolumeIngestionUtil.checkUnManagedResourceIsRecoverPointEnabled(unManagedVolume);
    }

    /**
     * Verify whether volume already ingested or not.
     * 
     * @param unManagedVolume
     * @param volumeType
     * @return
     */
    protected boolean isVolumeAlreadyIngested(UnManagedVolume unManagedVolume) {
        boolean isValid = true;
        String volumeNativeGuid = unManagedVolume.getNativeGuid().replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                VolumeIngestionUtil.VOLUME);

        Volume volume = VolumeIngestionUtil.checkIfVolumeExistsInDB(volumeNativeGuid, _dbClient);
        if (volume != null) {
            _logger.warn("UnManaged Volume {} is already ingested.  Skipping Ingestion", unManagedVolume.getId());
            isValid = false;
        }
        return isValid;
    }

    /**
     * Checks that the Virtual Pool has a protocols setting that is compatible
     * with the UnManagedVolume. Does not apply to UnManagedVolumes that are not
     * exported.
     * 
     * @param vpool
     *            the virtual pool requested
     * @param unManagedVolume
     *            the unmanaged volume being ingested
     * @param dbClient
     *            database client
     * @return true if the virtual pool is compatible with the unmaanged
     *         volume's export's initiators' protocols
     */
    protected void checkVPoolValidForExportInitiatorProtocols(VirtualPool vpool, UnManagedVolume unManagedVolume) {
        if (unManagedVolume.getInitiatorUris().isEmpty()) {
            _logger.info("unmanaged volume {} has no initiators, so no need to verify vpool protocols",
                    unManagedVolume.getNativeGuid());
            return;
        }

        _logger.info("checking validity of virtual pool {} protocols for unmanaged volume {}", vpool.getLabel(),
                unManagedVolume.getNativeGuid());
        Set<String> initiatorProtocols = new HashSet<String>();

        for (String initUri : unManagedVolume.getInitiatorUris()) {
            Initiator init = _dbClient.queryObject(Initiator.class, URI.create(initUri));
            if (init != null) {
                initiatorProtocols.add(init.getProtocol());
            }
        }

        _logger.info("this unmanaged volume's export's initiators' protocols are {}", Joiner.on(",").join(initiatorProtocols));
        _logger.info("the requested virtual pool's protocols are {}", Joiner.on(",").join(vpool.getProtocols()));

        boolean atLeastOneProtocolIsSatisfied = false;
        for (String protocol : initiatorProtocols) {
            if (vpool.getProtocols().contains(protocol)) {
                _logger.info("at least one protocol matches between the volume and virtual pool");
                atLeastOneProtocolIsSatisfied = true;
                break;
            }
        }

        if (!atLeastOneProtocolIsSatisfied) {
            _logger.warn("no protocol overlap found between unmanaged volume and virtual pool. Skipping ingestion.");
            throw IngestionException.exceptions.unmanagedVolumeAndVpoolProtocolMismatch(
                    unManagedVolume.getLabel(), vpool.getLabel(), Joiner.on(",").join(initiatorProtocols));
        }
    }

    /**
     * validate Host IO limits
     * 
     * @param vpool
     * @param unManagedVolume
     * @return
     */
    protected void checkHostIOLimits(VirtualPool vpool, UnManagedVolume unManagedVolume, boolean isExportedVolumeIngest) {

        // Skip validation for unExportedVolumes and VPLEX virtual volumes
        if (!isExportedVolumeIngest || VolumeIngestionUtil.isVplexVolume(unManagedVolume)) {
            return;
        }

        Set<String> hostIoBws = PropertySetterUtil.extractValuesFromStringSet(
                SupportedVolumeInformation.EMC_MAXIMUM_IO_BANDWIDTH.name(), unManagedVolume.getVolumeInformation());
        Set<String> hostIoPs = PropertySetterUtil.extractValuesFromStringSet(SupportedVolumeInformation.EMC_MAXIMUM_IOPS.name(),
                unManagedVolume.getVolumeInformation());
        String vPoolBw = "0";
        if (vpool.getHostIOLimitBandwidth() != null) {
            vPoolBw = String.valueOf(vpool.getHostIOLimitBandwidth());
        }

        String vPoolIoPs = "0";
        if (vpool.getHostIOLimitIOPs() != null) {
            vPoolIoPs = String.valueOf(vpool.getHostIOLimitIOPs());
        }
        _logger.info("Volume's bw {} and iops {} --> Virtual Pool's bw {} and iops {}",
                new Object[] { Joiner.on(",").join(hostIoBws), Joiner.on(",").join(hostIoPs), vPoolBw, vPoolIoPs });
        // values [0,2000] hence if size is > 1 then we need to explicitly
        // return false, if vpool value is 0.
        if (hostIoBws.size() > 1) {
            if ("0".equalsIgnoreCase(vPoolBw)) {
                throw IngestionException.exceptions.unmanagedVolumeHasInvalidHostIoLimits(unManagedVolume.getLabel());
            }
        }

        if (hostIoPs.size() > 1) {
            if ("0".equalsIgnoreCase(vPoolIoPs)) {
                throw IngestionException.exceptions.unmanagedVolumeHasInvalidHostIoLimits(unManagedVolume.getLabel());
            }
        }

        if (!hostIoBws.contains(vPoolBw) || !hostIoPs.contains(vPoolIoPs)) {
            throw IngestionException.exceptions.unmanagedVolumeHasInvalidHostIoLimits(unManagedVolume.getLabel());
        }
    }

    /**
     * validate Storage Pool in VArray
     * 
     * @param unManagedVolume
     * @param virtualArray
     * @return
     */
    protected StoragePool validateAndReturnStoragePoolInVAarray(UnManagedVolume unManagedVolume, VirtualArray virtualArray) {
        URI storagePoolUri = unManagedVolume.getStoragePoolUri();
        StoragePool pool = null;
        if (null != storagePoolUri) {
            pool = _dbClient.queryObject(StoragePool.class, storagePoolUri);
            if (null == pool.getTaggedVirtualArrays() || !pool.getTaggedVirtualArrays().contains(virtualArray.getId().toString())) {
                _logger.warn(String.format(UnManagedVolumeService.UNMATCHED_VARRAYS, new Object[] { unManagedVolume.getId() }));
            }
        } else if (!VolumeIngestionUtil.isVplexVolume(unManagedVolume)) {
            _logger.warn("UnManaged Volume {} does not have a Storage Pool set. Skipping Ingestion.", unManagedVolume.getLabel());
            throw IngestionException.exceptions.unmanagedVolumeHasNoStoragePool(unManagedVolume.getLabel());
        }
        return pool;
    }

    /**
     * is Volume already exported to Host.
     * This method validates whether exported unmanaged volumes are ingesting
     * through unmanaged unexported volume service catalog.
     * 
     * @param unManagedVolume
     * @return
     */
    protected void checkVolumeExportState(UnManagedVolume unManagedVolume, boolean doIngestExported) {
        boolean isExported = VolumeIngestionUtil.checkUnManagedResourceAlreadyExported(unManagedVolume);

        if (isExported && !doIngestExported) {
            _logger.warn(
                    "UnManaged Volume {} is exported to Host but ingesting through unexported volume catalog service. Skipping Ingestion",
                    unManagedVolume.getId());
            throw IngestionException.exceptions.unmanagedVolumeIsExported(unManagedVolume.getLabel());
        }
    }

    /**
     * is UnManaged Volume has Replicas
     * 
     * @param unManagedVolume
     * @return
     */
    protected void checkUnmanagedVolumeReplicas(UnManagedVolume unManagedVolume) {
        if (VolumeIngestionUtil.checkUnManagedVolumeHasReplicas(unManagedVolume)) {
            _logger.warn("UnManaged Volume {} has replicas associated. Skipping Ingestion", unManagedVolume.getId());
            throw IngestionException.exceptions.unmanagedVolumeHasReplicas(unManagedVolume.getLabel());
        }
    }

    /**
     * Create ViPR managed volume in DB
     * 
     * @param system
     * @param volumeNativeGuid
     * @param pool
     * @param virtualArray
     * @param vPool
     * @param unManagedVolume
     * @param project
     * @param tenant
     * @return
     * @throws Exception
     */
    protected Volume createVolume(StorageSystem system, String volumeNativeGuid, StoragePool pool, VirtualArray virtualArray,
            VirtualPool vPool, UnManagedVolume unManagedVolume, Project project, TenantOrg tenant, String autoTierPolicyId)
            throws IngestionException {
        _logger.info("creating new Volume for native volume id " + volumeNativeGuid);

        Volume volume = new Volume();
        volume.setId(URIUtil.createId(Volume.class));
        updateVolume(volume, system, volumeNativeGuid, pool, virtualArray, vPool, unManagedVolume, project);
        updateMetaVolumeProperties(volume, unManagedVolume);
        volume.setThinlyProvisioned(Boolean.parseBoolean(unManagedVolume.getVolumeCharacterstics().get(
                SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString())));
        volume.setAccessState(String.valueOf(unManagedVolume
                .getVolumeInformation().get(
                        SupportedVolumeInformation.ACCESS.toString())));

        URI cgUri = getConsistencyGroupUri(unManagedVolume, vPool, project.getId(), tenant.getId(), virtualArray.getId(), _dbClient);
        if (null != cgUri) {
            updateCGPropertiesInVolume(cgUri, volume, system, unManagedVolume);
        }
        if (null != autoTierPolicyId) {
            updateTierPolicyProperties(autoTierPolicyId, volume);
        }

        return volume;
    }

    /*
     * Update volume properties
     */
    protected void updateVolume(Volume volume, StorageSystem system, String volumeNativeGuid, StoragePool pool, VirtualArray virtualArray,
            VirtualPool vPool, UnManagedVolume unManagedVolume, Project project) throws IngestionException {
        _logger.debug("Updating Volume for native volume id " + volumeNativeGuid);
        volume.setNativeGuid(volumeNativeGuid);
        volume.setVirtualPool(vPool.getId());
        volume.setVirtualArray(virtualArray.getId());
        volume.setStorageController(system.getId());
        volume.setCreationTime(Calendar.getInstance());
        volume.setPool(unManagedVolume.getStoragePoolUri());
        // adding capacity
        String allocatedCapacity = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.ALLOCATED_CAPACITY.toString(), unManagedVolume.getVolumeInformation());
        String provisionedCapacity = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.PROVISIONED_CAPACITY.toString(), unManagedVolume.getVolumeInformation());

        volume.setAllocatedCapacity(Long.parseLong(allocatedCapacity));
        volume.setProvisionedCapacity(Long.parseLong(provisionedCapacity));
        volume.setCapacity(Long.parseLong(provisionedCapacity));

        volume.setWWN(unManagedVolume.getWwn());
        updateBlockObjectNativeIds(volume, unManagedVolume);
        setProtocol(pool, volume, vPool);

        volume.setTenant(new NamedURI(project.getTenantOrg().getURI(), volume.getLabel()));
        volume.setProject(new NamedURI(project.getId(), volume.getLabel()));
        try {
            PropertySetterUtil.addVolumeDetails(unManagedVolume.getVolumeInformation(), volume);
        } catch (Exception e) {
            _logger.error("UnManaged Volume {} ingestion ran into an exception: ", unManagedVolume.getLabel(), e);
            throw IngestionException.exceptions.couldNotCreateVolume(e.getLocalizedMessage());
        }
    }

    /**
     * set Protocol on volume
     * 
     * @param pool
     * @param volume
     * @param vPool
     */
    protected void setProtocol(StoragePool pool, Volume volume, VirtualPool vPool) {
        if (null == volume.getProtocol()) {
            volume.setProtocol(new StringSet());
        }
        volume.getProtocol().addAll(VirtualPoolUtil.getMatchingProtocols(vPool.getProtocols(), pool.getProtocols()));
    }

    /**
     * get CG , applicable only for VPlex.
     * 
     * @param unManagedVolume
     * @param vPool
     * @param project
     * @param tenant
     * @param virtualArray
     * @param dbClient
     * @return
     */
    protected URI getConsistencyGroupUri(UnManagedVolume unManagedVolume, VirtualPool vPool, URI project, URI tenant,
            URI virtualArray, DbClient dbClient) {
        return null;
    }

    /**
     * update CG, applicable only for VPlex.
     * 
     * @param consistencyGroupUri
     * @param volume
     * @param system
     * @param unManagedVolume
     */
    protected void updateCGPropertiesInVolume(URI consistencyGroupUri, Volume volume, StorageSystem system,
            UnManagedVolume unManagedVolume) {
        // Update with default code, when CG ingestion is supported
    }

    /**
     * update Native Ids on volumes.
     * 
     * @param blockObject
     * @param unManagedVolume
     */
    protected void updateBlockObjectNativeIds(BlockObject blockObject, UnManagedVolume unManagedVolume) {
        String nativeId = PropertySetterUtil.extractValueFromStringSet(SupportedVolumeInformation.NATIVE_ID.toString(),
                unManagedVolume.getVolumeInformation());
        String deviceLabel = PropertySetterUtil.extractValueFromStringSet(SupportedVolumeInformation.DEVICE_LABEL.toString(),
                unManagedVolume.getVolumeInformation());
        if (null == deviceLabel || deviceLabel.trim().isEmpty()) {
            deviceLabel = blockObject.getNativeGuid();
        }

        blockObject.setDeviceLabel(deviceLabel);
        blockObject.setLabel(deviceLabel);
        blockObject.setNativeId(nativeId);
        blockObject.setAlternateName(nativeId);
    }

    /**
     * update Tier Policy properties
     * 
     * @param autoTierPolicyId
     * @param volume
     */
    protected void updateTierPolicyProperties(String autoTierPolicyId, Volume volume) {
        // set auto tier policy Id
        if (null != autoTierPolicyId) {
            List<URI> autoTierPolicyURIs = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getAutoTieringPolicyByNativeGuidConstraint(autoTierPolicyId));
            if (!autoTierPolicyURIs.isEmpty()) {
                volume.setAutoTieringPolicyUri(autoTierPolicyURIs.get(0));
            }
        }
    }

    /**
     * update Meta volume properties
     * 
     * @param volume
     * @param unManagedVolume
     */
    protected void updateMetaVolumeProperties(Volume volume, UnManagedVolume unManagedVolume) {
        Boolean isMetaVolume = Boolean.parseBoolean(unManagedVolume.getVolumeCharacterstics().get(
                SupportedVolumeCharacterstics.IS_METAVOLUME.toString()));
        volume.setIsComposite(isMetaVolume);
        if (isMetaVolume) {
            String metaVolumeType = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.META_VOLUME_TYPE.toString(), unManagedVolume.getVolumeInformation());
            // If unmanaged meta volume was discovered prior to 2.2 upgrade, it
            // won't have any meta volume characteristics set.
            // In this case we skip meta volume characteristics processing here.
            // These characteristics will be added to vipr volume by
            // regular rediscovery of its storage system after the volume is
            // ingested.
            if (metaVolumeType != null && !metaVolumeType.isEmpty()) {
                volume.setCompositionType(metaVolumeType);

                String metaVolumeMemberSize = PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.META_MEMBER_SIZE.toString(), unManagedVolume.getVolumeInformation());
                volume.setMetaMemberSize(Long.parseLong(metaVolumeMemberSize));

                String metaVolumeMemberCount = PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.META_MEMBER_COUNT.toString(), unManagedVolume.getVolumeInformation());
                volume.setMetaMemberCount(Integer.parseInt(metaVolumeMemberCount));
                Long totalMetaMemberCapacity = volume.getMetaMemberCount() * volume.getMetaMemberSize();
                volume.setTotalMetaMemberCapacity(totalMetaMemberCapacity);
                _logger.info(String
                        .format("Set meta volume data for meta volume: %s . Type: %s, member count: %s, member size: %s, total capacity: %s",
                                volume.getLabel(), metaVolumeType, metaVolumeMemberCount, metaVolumeMemberSize,
                                totalMetaMemberCapacity));
            } else {
                _logger.info("Unmanaged meta volume {} does not have meta volume properties set due to pre-upgrade discovery",
                        volume.getNativeGuid());
            }
        }
    }

    /**
     * Compare unmanaged volume and given virtual pool's Auto Tier Policy are
     * same.
     * 
     * @param unManagedVolume
     * @param system
     * @param vPool
     * @return
     */
    protected String getAutoTierPolicy(UnManagedVolume unManagedVolume, StorageSystem system, VirtualPool vPool) {
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();

        String autoTierPolicyId = null;
        // get Policy name
        if (null != unManagedVolumeInformation.get(SupportedVolumeInformation.AUTO_TIERING_POLICIES.toString())) {
            for (String policyName : unManagedVolumeInformation.get(SupportedVolumeInformation.AUTO_TIERING_POLICIES.toString())) {
                autoTierPolicyId = NativeGUIDGenerator.generateAutoTierPolicyNativeGuid(system.getNativeGuid(), policyName,
                        NativeGUIDGenerator.getTieringPolicyKeyForSystem(system));
                break;
            }
        }

        if (!DiscoveryUtils.checkVPoolValidForUnManagedVolumeAutoTieringPolicy(vPool, autoTierPolicyId, system)) {
            _logger.warn(String.format(UnManagedVolumeService.AUTO_TIERING_NOT_CONFIGURED, new Object[] {
                    unManagedVolume.getId(), autoTierPolicyId, vPool.getAutoTierPolicyName(), vPool.getLabel() }));
            return "POLICY_NOT_MATCH";
        }
        return autoTierPolicyId;
    }

    /**
     * validate whether storage system resource limits are exceeded.
     * 
     * @param system
     * @param unManagedVolume
     * @param systemCache
     * @return
     */
    protected void checkSystemResourceLimitsExceeded(StorageSystem system, UnManagedVolume unManagedVolume, List<URI> systemCache) {

        if (systemCache.contains(system.getId())) {
            // skip this volume
            throw IngestionException.exceptions.systemResourceLimitsExceeded(unManagedVolume.getLabel());
        }
        if (system.getIsResourceLimitSet()) {
            if (system.getMaxResources() <= StorageSystemService.getNumResources(system, _dbClient)) {
                // reached limit for this system
                systemCache.add(system.getId());
                throw IngestionException.exceptions.systemResourceLimitsExceeded(unManagedVolume.getLabel());
            }
        }
    }

    /**
     * validate Storage Pool resource limits are exceeded.
     * 
     * @param system
     * @param pool
     * @param unManagedVolume
     * @param poolCache
     * @return
     */
    protected void checkPoolResourceLimitsExceeded(StorageSystem system, StoragePool pool, UnManagedVolume unManagedVolume,
            List<URI> poolCache) {
        if (poolCache.contains(pool.getId())) {
            // skip this volume
            throw IngestionException.exceptions.poolResourceLimitsExceeded(unManagedVolume.getLabel());
        }
        if (pool.getIsResourceLimitSet()) {
            if (pool.getMaxResources() <= StoragePoolService.getNumResources(pool, _dbClient)) {
                // reached limit for this pool
                poolCache.add(pool.getId());
                throw IngestionException.exceptions.poolResourceLimitsExceeded(unManagedVolume.getLabel());
            }
        }
    }

    /**
     * check if unmanaged volume added to CG
     * 
     * @param unManagedVolume
     * @param virtualArray
     * @param tenant
     * @param project
     * @param vPool
     * @return
     */
    protected void checkUnManagedVolumeAddedToCG(UnManagedVolume unManagedVolume, VirtualArray virtualArray, TenantOrg tenant,
            Project project, VirtualPool vPool) {
        if (VolumeIngestionUtil.checkUnManagedResourceAddedToConsistencyGroup(unManagedVolume)) {
            _logger.warn("UnManaged Volume {} is added to consistency group.  Skipping Ingestion", unManagedVolume.getId());
            throw IngestionException.exceptions.unmanagedVolumeAddedToConsistencyGroup(unManagedVolume.getLabel());
        }
    }

    /**
     * validate tieringpolicy of a given UnManagedVolume.
     * 
     * @param autoTierPolicyId
     * @param unManagedVolume
     * @param vPool
     */
    protected void validateAutoTierPolicy(String autoTierPolicyId, UnManagedVolume unManagedVolume, VirtualPool vPool) {
        if (POLICY_NOT_MATCH.equalsIgnoreCase(autoTierPolicyId)) {
            _logger.error(
                    "Unmanaged Volume {} is not a match for the virtual pool {} auto tiering policy.",
                    unManagedVolume.getLabel(), vPool.getLabel());
            throw IngestionException.exceptions
                    .unmanagedVolumeVpoolTieringPolicyMismatch(
                            unManagedVolume.getLabel(), vPool.getLabel());
        }
    }

    /**
     * Algorithm:
     * 
     * 1. Get the parent of the Current UMV
     * 2. If parent is null AND current UMV doesn't have any replicas, its actually a UMV with no replicas.
     * 3. Else run while loop to find out the ROOT of the current's Parent.
     * 4. At the end of this while loop, we get the ROOT UMV, ROOT Block Object.
     * 5. Invoke RunReplicasIngestedCheck
     * a. Get the replicas of the ROOT UMV
     * b. Find if all replicas Ingested
     * c. If Yes, then update parent Replica Map [Parent --> Child]
     * 1. For each Replica unmanaged volume, RUN STEP 5.
     * d. Else Clear Parent Replica and come out.
     * 6. If parent Replica map is not empty (parent-child ingested successfully)
     * a. Set Parent Child relations
     * b. Hold the data in Memory and don't persist to DB.
     * 
     * 
     * @param unmanagedVolume current Processed UnManaged Volume
     * @param blockObject current Processed Block Object
     * @param unManagedVolumes
     * @param createdObjects Already processed Block Objects in Memory
     * @param taskStatusMap
     * @param vplexIngestionMethod the VPLEX backend ingestion method
     * @return
     */
    @SuppressWarnings("deprecation")
    protected boolean markUnManagedVolumeInactive(
            IngestionRequestContext requestContext, BlockObject currentBlockObject) {
        UnManagedVolume currentUnmanagedVolume = requestContext.getCurrentUnmanagedVolume();
        
        _logger.info("Running unmanagedvolume {} replica ingestion status", currentUnmanagedVolume.getNativeGuid());
        boolean markUnManagedVolumeInactive = false;

        // if the vplex ingestion method is vvol-only, we don't need to check replicas
        if (VolumeIngestionUtil.isVplexVolume(currentUnmanagedVolume) &&
                VplexBackendIngestionContext.INGESTION_METHOD_VVOL_ONLY.equals(
                        requestContext.getVplexIngestionMethod())) {
            _logger.info("This is a VPLEX virtual volume and the ingestion method is "
                    + "virtual volume only. Skipping replica ingestion algorithm.");
            return true;
        }

        StringSetMap unManagedVolumeInformation = currentUnmanagedVolume.getVolumeInformation();
        List<String> parentVolumeNativeGUIDs = getParentVolumeNativeGUIDByRepType(unManagedVolumeInformation);

        // If no source volume set and no replicas, then it is a simple case where the unmanaged volume can be marked as inactive
        // TODO - may be move this to a single utility method
        if (parentVolumeNativeGUIDs.isEmpty() && !VolumeIngestionUtil.checkUnManagedVolumeHasReplicas(currentUnmanagedVolume)) {
            _logger.info("Simple unmanagedvolume without any replicas. Skipping replica ingestion algorithm.");
            return true;
        }
        _logger.info("Running algorithm to find the root source volume for {}", currentUnmanagedVolume.getNativeGuid());
        // Get the topmost parent object
        if (!parentVolumeNativeGUIDs.isEmpty()) {
            markUnManagedVolumeInactive = true;
            for (String parentVolumeNativeGUID : parentVolumeNativeGUIDs) {
                boolean allGood = false;
                Map<BlockObject, List<BlockObject>> parentReplicaMap = new HashMap<BlockObject, List<BlockObject>>();
                StringSet processedUnManagedGUIDS = new StringSet();
                UnManagedVolume rootUnManagedVolume = currentUnmanagedVolume;
                BlockObject rootBlockObject = currentBlockObject;

                while (parentVolumeNativeGUID != null) {
                    _logger.info("Finding unmanagedvolume {} in vipr db", parentVolumeNativeGUID);
                    List<URI> parentUnmanagedUris = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                            .getVolumeInfoNativeIdConstraint(parentVolumeNativeGUID));
                    if (!parentUnmanagedUris.isEmpty()) {
                        _logger.info("Found unmanagedvolume {} in vipr db", parentVolumeNativeGUID);
                        rootUnManagedVolume = _dbClient.queryObject(UnManagedVolume.class, parentUnmanagedUris.get(0));
                        unManagedVolumeInformation = rootUnManagedVolume.getVolumeInformation();
                        String blockObjectNativeGUID = rootUnManagedVolume.getNativeGuid().replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                                VolumeIngestionUtil.VOLUME);
                        rootBlockObject = VolumeIngestionUtil.getBlockObject(blockObjectNativeGUID, _dbClient);
                        // If the volumeobject is not found in DB. check in locally createdObjects.
                        if (rootBlockObject == null) {
                            rootBlockObject = requestContext.getObjectsToBeCreatedMap().get(blockObjectNativeGUID);
                        }
                        // Get the parent unmanagedvolume for the current unmanagedvolume.
                        List<String> parents = getParentVolumeNativeGUIDByRepType(unManagedVolumeInformation);
                        if (parents.isEmpty()) {
                            parentVolumeNativeGUID = null;
                            _logger.info("No parent for current unmanagedvolume {}", rootUnManagedVolume.getNativeGuid());
                        } else {
                            parentVolumeNativeGUID = parents.get(0);
                            _logger.info("Found the parent {} for current unmanagedvolume {}", parentVolumeNativeGUID,
                                    rootUnManagedVolume.getNativeGuid());
                        }

                        // if the parent is null and this is a VPLEX backend volume, then it
                        // would seem the backend array has been discovered for
                        // UnManaged Volumes, but the VPLEX device has not.
                        if ((null == parentVolumeNativeGUID)
                                && VolumeIngestionUtil.isVplexBackendVolume(rootUnManagedVolume)) {
                            throw IngestionException.exceptions.vplexBackendVolumeHasNoParent(rootUnManagedVolume.getLabel());
                        }
                    } else {
                        _logger.info("unmanagedvolume not found looking for ingested volume {} in vipr db", parentVolumeNativeGUID);
                        // parent might be already ingested
                        // Native guid might correspond to ViPR object, find if there is still a unmanaged volume corresponding to the
                        // parent
                        parentUnmanagedUris = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                                .getVolumeInfoNativeIdConstraint(parentVolumeNativeGUID.replace(VolumeIngestionUtil.VOLUME,
                                        VolumeIngestionUtil.UNMANAGEDVOLUME)));
                        if (!parentUnmanagedUris.isEmpty()) {
                            _logger.info("Found ingested volume {} in vipr db", parentVolumeNativeGUID);
                            rootUnManagedVolume = _dbClient.queryObject(UnManagedVolume.class, parentUnmanagedUris.get(0));
                            unManagedVolumeInformation = rootUnManagedVolume.getVolumeInformation();
                            rootBlockObject = VolumeIngestionUtil.getBlockObject(parentVolumeNativeGUID, _dbClient);
                            List<String> parents = getParentVolumeNativeGUIDByRepType(unManagedVolumeInformation);
                            if (parents.isEmpty()) {
                                parentVolumeNativeGUID = null;
                            } else {
                                parentVolumeNativeGUID = parents.get(0);
                            }
                            _logger.info("Found the parent {} for current unmanagedvolume {}", parentVolumeNativeGUID,
                                    rootUnManagedVolume.getNativeGuid());

                            // if the parent is null and this is a VPLEX backend volume, then it
                            // would seem the backend array has been discovered for
                            // UnManaged Volumes, but the VPLEX device has not.
                            if ((null == parentVolumeNativeGUID)
                                    && VolumeIngestionUtil.isVplexBackendVolume(rootUnManagedVolume)) {
                                throw IngestionException.exceptions.vplexBackendVolumeHasNoParent(rootUnManagedVolume.getLabel());
                            }
                        } else {
                            _logger.info("Found a replica {} whose parent is already ingested with PUBLIC_ACCESS=true",
                                    parentVolumeNativeGUID);
                            // Find the ViPR object and put the block object and the parent in the map and break
                            List<BlockObject> replicas = new ArrayList<BlockObject>();
                            replicas.add(rootBlockObject);
                            parentReplicaMap
                                    .put(
                                            VolumeIngestionUtil.getBlockObject(
                                                    parentVolumeNativeGUID.replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                                                            VolumeIngestionUtil.VOLUME),
                                                    _dbClient), replicas);
                            break;
                        }
                    }
                }

                if (null != rootBlockObject) {
                    _logger.info("Found root source volume {}", rootBlockObject.getNativeGuid());
                }
                if (null != rootUnManagedVolume) {
                    _logger.info("Found root unmanagedvolume {}", rootUnManagedVolume.getNativeGuid());
                }

                _logger.info("Running algorithm to check all replicas ingested for parent");
                runReplicasIngestedCheck(rootUnManagedVolume, rootBlockObject, currentUnmanagedVolume, currentBlockObject,
                        processedUnManagedGUIDS,
                        requestContext.getObjectsToBeCreatedMap(), parentReplicaMap, requestContext.getTaskStatusMap());
                _logger.info("Ended algorithm to check all replicas ingested for parent");
                List<UnManagedVolume> processedUnManagedVolumes = _dbClient.queryObject(UnManagedVolume.class,
                        VolumeIngestionUtil.getUnManagedVolumeUris(processedUnManagedGUIDS, _dbClient));

                if (!parentReplicaMap.isEmpty()) {
                    setupParentReplicaRelationships(currentUnmanagedVolume, parentReplicaMap, 
                            requestContext.getUnManagedVolumesToBeDeleted(), requestContext.getObjectsToBeCreatedMap(),
                            requestContext.getObjectsToBeUpdatedMap(),
                            processedUnManagedVolumes);
                    allGood = true;
                }

                if (!allGood) {
                    markUnManagedVolumeInactive = false;
                }
            }
        } else {
            Map<BlockObject, List<BlockObject>> parentReplicaMap = new HashMap<BlockObject, List<BlockObject>>();
            StringSet processedUnManagedGUIDS = new StringSet();
            UnManagedVolume rootUnManagedVolume = currentUnmanagedVolume;
            BlockObject rootBlockObject = currentBlockObject;

            if (null != rootBlockObject) {
                _logger.info("Found root source volume {}", rootBlockObject.getNativeGuid());
            }
            if (null != rootUnManagedVolume) {
                _logger.info("Found root unmanagedvolume {}", rootUnManagedVolume.getNativeGuid());
            }

            _logger.info("Running algorithm to check all replicas ingested for parent");
            runReplicasIngestedCheck(rootUnManagedVolume, rootBlockObject, currentUnmanagedVolume, currentBlockObject,
                    processedUnManagedGUIDS,
                    requestContext.getObjectsToBeCreatedMap(), parentReplicaMap, requestContext.getTaskStatusMap());
            _logger.info("Ended algorithm to check all replicas ingested for parent");
            List<UnManagedVolume> processedUnManagedVolumes = _dbClient.queryObject(UnManagedVolume.class,
                    VolumeIngestionUtil.getUnManagedVolumeUris(processedUnManagedGUIDS, _dbClient));

            if (!parentReplicaMap.isEmpty()) {
                setupParentReplicaRelationships(currentUnmanagedVolume, parentReplicaMap, 
                        requestContext.getUnManagedVolumesToBeDeleted(), requestContext.getObjectsToBeCreatedMap(), 
                        requestContext.getObjectsToBeUpdatedMap(), processedUnManagedVolumes);
                return true;
            }
        }

        return markUnManagedVolumeInactive;
    }

    private void setupParentReplicaRelationships(UnManagedVolume currentUnmanagedVolume,
            Map<BlockObject, List<BlockObject>> parentReplicaMap, List<UnManagedVolume> unManagedVolumes,
            Map<String, BlockObject> createdObjects, Map<String, List<DataObject>> updatedObjects,
            List<UnManagedVolume> processedUnManagedVolumes) {
        List<DataObject> updateObjects = updatedObjects.get(currentUnmanagedVolume.getNativeGuid());
        if (updateObjects == null) {
            updateObjects = new ArrayList<DataObject>();
            updatedObjects.put(currentUnmanagedVolume.getNativeGuid(), updateObjects);
        }
        for (BlockObject parent : parentReplicaMap.keySet()) {
            for (BlockObject replica : parentReplicaMap.get(parent)) {
                if (replica instanceof BlockMirror) {
                    VolumeIngestionUtil.setupMirrorParentRelations(replica, parent, _dbClient);
                } else if (replica instanceof Volume) {
                    if (isSRDFTargetVolume(replica, processedUnManagedVolumes)) {
                    VolumeIngestionUtil.setupSRDFParentRelations(replica, parent, _dbClient);
                    } else if (VolumeIngestionUtil.isVplexVolume(parent, _dbClient)
                            && VolumeIngestionUtil.isVplexBackendVolume(replica, _dbClient)) {
                        VolumeIngestionUtil.setupVplexParentRelations(replica, parent, _dbClient);
                    } else {
                    VolumeIngestionUtil.setupCloneParentRelations(replica, parent, _dbClient);
                    }
                } else if (replica instanceof BlockSnapshot) {
                    VolumeIngestionUtil.setupSnapParentRelations(replica, parent, _dbClient);
                }
                VolumeIngestionUtil.clearInternalFlags(replica, updateObjects, _dbClient);
                if (!createdObjects.containsKey(replica.getNativeGuid())) {
                    updateObjects.add(replica);
                }
            }
            // clear the parent internal flags after all the replica relationships have been set
            VolumeIngestionUtil.clearInternalFlags(parent, updateObjects, _dbClient);
            if (!createdObjects.containsKey(parent.getNativeGuid())) {
                updateObjects.add(parent);
            }
        }
    }

    /**
     * Verifies whether volume is SRDF target volume or not.
     * 
     * @param replica
     * @param processedUnManagedVolumes
     * @return
     */
    private boolean isSRDFTargetVolume(BlockObject replica, List<UnManagedVolume> processedUnManagedVolumes) {
        boolean srdfTargetVolFound = false;
        String unmanagedVolumeNativeGuid = replica.getNativeGuid().replace(VolumeIngestionUtil.VOLUME, VolumeIngestionUtil.UNMANAGEDVOLUME);
        if (null != processedUnManagedVolumes && !processedUnManagedVolumes.isEmpty()) {
            for (UnManagedVolume umv : processedUnManagedVolumes) {
                String type = PropertySetterUtil.extractValueFromStringSet(SupportedVolumeInformation.REMOTE_VOLUME_TYPE.toString(),
                        umv.getVolumeInformation());
                if (unmanagedVolumeNativeGuid.equalsIgnoreCase(umv.getNativeGuid())
                        && RemoteMirrorObject.Types.TARGET.toString().equalsIgnoreCase(type)) {
                    srdfTargetVolFound = true;
                    break;
                }
            }
        }
        return srdfTargetVolFound;
    }

    /**
     * Return a list specifying the native guids of the parent(s) of the unmanaged volume
     * associated with the passed unmanaged volume info. An unmanaged volume typically
     * has a single source volume based on its replica type or whether or not it is a
     * VPLEX backend volume. However, there is at least one case where the unmanaged
     * volume can have multiple parents and this is when the unmanaged volume in not
     * only a snapshot target volume, but is also the backend volume of a VPLEX volume.
     * 
     * @param unManagedVolumeInformation
     * @return A list specifying the native guids of the parent(s) of the unmanaged volume
     *         associated with the passed unmanaged volume info.
     */
    private List<String> getParentVolumeNativeGUIDByRepType(StringSetMap unManagedVolumeInformation) {
        List<String> parentVolumeNativeGuids = new ArrayList<String>();
        if (unManagedVolumeInformation.containsKey(SupportedVolumeInformation.LOCAL_REPLICA_SOURCE_VOLUME.toString())) {
            parentVolumeNativeGuids.add(PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.LOCAL_REPLICA_SOURCE_VOLUME.toString(),
                    unManagedVolumeInformation));
            if (unManagedVolumeInformation.containsKey(SupportedVolumeInformation.VPLEX_PARENT_VOLUME.toString())) {
                parentVolumeNativeGuids.add(PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.VPLEX_PARENT_VOLUME.toString(),
                        unManagedVolumeInformation));
            }
        } else if (unManagedVolumeInformation.containsKey(SupportedVolumeInformation.REMOTE_MIRROR_SOURCE_VOLUME.toString())) {
            parentVolumeNativeGuids.add(PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.REMOTE_MIRROR_SOURCE_VOLUME.toString(),
                    unManagedVolumeInformation));
        } else if (unManagedVolumeInformation.containsKey(SupportedVolumeInformation.VPLEX_PARENT_VOLUME.toString())) {
            parentVolumeNativeGuids.add(PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.VPLEX_PARENT_VOLUME.toString(),
                    unManagedVolumeInformation));
        }
        return parentVolumeNativeGuids;
    }

    /**
     * Verifies whether unManagedVolume is already ingested & if there are any exportMasks pending.
     * It also validates whether unexported volume/replica is already ingested or not.
     * 
     * @param volume
     * @param unManagedVolumeUri
     * @param unManagedVolumeExported
     * @return
     */
    protected boolean isExportIngestionPending(BlockObject blockObj, URI unManagedVolumeUri, boolean unManagedVolumeExported) {
        if (null != blockObj && !blockObj.checkInternalFlags(Flag.NO_PUBLIC_ACCESS)) {
            if (!unManagedVolumeExported) {
                _logger.warn("UnManaged Volume {} is already ingested. Skipping Ingestion", unManagedVolumeUri);
                throw IngestionException.exceptions.unmanagedVolumeAlreadyIngested(blockObj.getLabel());
            } else {
                _logger.warn("UnManaged Volume {} already ingested. But since it is exported, try ingesting the export masks",
                        unManagedVolumeUri);
                return true;
            }
        }
        return false;
    }

    /**
     * Invoke RunReplicasIngestedCheck
     * a. Get the replicas of the ROOT UMV
     * b. Find if all replicas Ingested
     * c. If Yes, then update parent Replica Map [Parent --> Child]
     * 1. For each Replica unmanaged volume, RUN STEP 5.
     * d. Else Clear Parent Replica and come out.
     * 
     * @param unmanagedVolume
     * @param blockObject
     * @param processingUnManagedVolume
     * @param processingBlockObject
     * @param unManagedVolumeGUIDs
     * @param createdObjectMap
     * @param parentReplicaMap
     * @param taskStatusMap
     */
    protected void runReplicasIngestedCheck(UnManagedVolume rootUnmanagedVolume, BlockObject rootBlockObject,
            UnManagedVolume currentUnManagedVolume,
            BlockObject currentBlockObject, StringSet unManagedVolumeGUIDs, Map<String, BlockObject> createdObjectMap, Map<BlockObject,
            List<BlockObject>> parentReplicaMap, Map<String, StringBuffer> taskStatusMap) {
        if (rootBlockObject == null) {
            _logger.warn("parent object {} not ingested yet.", rootUnmanagedVolume.getNativeGuid());
            parentReplicaMap.clear();
            StringBuffer taskStatus = taskStatusMap.get(currentUnManagedVolume.getNativeGuid());
            if (taskStatus == null) {
                taskStatus = new StringBuffer();
                taskStatusMap.put(currentUnManagedVolume.getNativeGuid(), taskStatus);
            }
            taskStatus.append(String.format("Parent object %s not ingested yet for unmanaged volume %s.", rootUnmanagedVolume.getLabel(),
                    currentUnManagedVolume.getLabel()));
            return;
        }
        boolean traverseTree = true;

        String unManagedVolumeNativeGUID = rootUnmanagedVolume.getNativeGuid();
        StringSetMap unManagedVolumeInformation = rootUnmanagedVolume.getVolumeInformation();

        StringSet unmanagedReplicaGUIDs = new StringSet();
        StringSet expectedIngestedReplicas = new StringSet();
        List<BlockObject> foundIngestedReplicas = new ArrayList<BlockObject>();
        List<String> foundIngestedReplicaNativeGuids = new ArrayList<String>();

        StringSet mirrors = PropertySetterUtil.extractValuesFromStringSet(SupportedVolumeInformation.MIRRORS.toString(),
                unManagedVolumeInformation);
        if (mirrors != null && !mirrors.isEmpty()) {
            unmanagedReplicaGUIDs.addAll(mirrors);
            StringSet mirrorGUIDs = VolumeIngestionUtil.getListofVolumeIds(mirrors);
            expectedIngestedReplicas.addAll(mirrorGUIDs);
            foundIngestedReplicas.addAll(VolumeIngestionUtil.getMirrorObjects(mirrorGUIDs, createdObjectMap, _dbClient));
        }

        StringSet clones = PropertySetterUtil.extractValuesFromStringSet(SupportedVolumeInformation.FULL_COPIES.toString(),
                unManagedVolumeInformation);
        if (clones != null && !clones.isEmpty()) {
            unmanagedReplicaGUIDs.addAll(clones);
            StringSet cloneGUIDs = VolumeIngestionUtil.getListofVolumeIds(clones);
            expectedIngestedReplicas.addAll(cloneGUIDs);
            foundIngestedReplicas.addAll(VolumeIngestionUtil.getVolumeObjects(cloneGUIDs, createdObjectMap, _dbClient));
        }

        StringSet snaps = PropertySetterUtil.extractValuesFromStringSet(SupportedVolumeInformation.SNAPSHOTS.toString(),
                unManagedVolumeInformation);
        if (snaps != null && !snaps.isEmpty()) {
            unmanagedReplicaGUIDs.addAll(snaps);
            StringSet snapGUIDs = VolumeIngestionUtil.getListofVolumeIds(snaps);
            expectedIngestedReplicas.addAll(snapGUIDs);
            foundIngestedReplicas.addAll(VolumeIngestionUtil.getSnapObjects(snapGUIDs, createdObjectMap, _dbClient));
        }

        StringSet remoteMirrors = PropertySetterUtil.extractValuesFromStringSet(SupportedVolumeInformation.REMOTE_MIRRORS.toString(),
                unManagedVolumeInformation);
        if (remoteMirrors != null && !remoteMirrors.isEmpty()) {
            unmanagedReplicaGUIDs.addAll(remoteMirrors);
            StringSet remoteMirrorGUIDs = VolumeIngestionUtil.getListofVolumeIds(remoteMirrors);
            expectedIngestedReplicas.addAll(remoteMirrorGUIDs);
            foundIngestedReplicas.addAll(VolumeIngestionUtil.getVolumeObjects(remoteMirrorGUIDs, createdObjectMap, _dbClient));
        }

        StringSet vplexBackendVolumes = PropertySetterUtil.extractValuesFromStringSet(
                SupportedVolumeInformation.VPLEX_BACKEND_VOLUMES.toString(),
                unManagedVolumeInformation);
        StringSet vplexBackendVolumeGUIDs = null;
        if (vplexBackendVolumes != null && !vplexBackendVolumes.isEmpty()) {
            unmanagedReplicaGUIDs.addAll(vplexBackendVolumes);
            vplexBackendVolumeGUIDs = VolumeIngestionUtil.getListofVolumeIds(vplexBackendVolumes);
            expectedIngestedReplicas.addAll(vplexBackendVolumeGUIDs);
            foundIngestedReplicas.addAll(VolumeIngestionUtil.getVolumeObjects(vplexBackendVolumeGUIDs, createdObjectMap, _dbClient));
        }

        if (unmanagedReplicaGUIDs.contains(currentUnManagedVolume.getNativeGuid())) {
            foundIngestedReplicas.add(currentBlockObject);
        }
        getFoundIngestedReplicaURIs(foundIngestedReplicas, foundIngestedReplicaNativeGuids);

        _logger.info("Expected replicas : {} -->Found replica URIs : {}", expectedIngestedReplicas.size(),
                foundIngestedReplicaNativeGuids.size());
        _logger.info("Expected replicas {} : Found {} : ", Joiner.on(", ").join(expectedIngestedReplicas),
                Joiner.on(", ").join(foundIngestedReplicaNativeGuids));

        if (foundIngestedReplicas.size() == expectedIngestedReplicas.size()) {
            if (null != rootBlockObject && !foundIngestedReplicas.isEmpty()) {
                parentReplicaMap.put(rootBlockObject, foundIngestedReplicas);
                unManagedVolumeGUIDs.add(unManagedVolumeNativeGUID);
                unManagedVolumeGUIDs.addAll(unmanagedReplicaGUIDs);
                traverseTree = true;
            }
        } else {
            Set<String> unIngestedReplicas = VolumeIngestionUtil.getUnIngestedReplicas(expectedIngestedReplicas, foundIngestedReplicas);
            _logger.info("The replicas {} not ingested for volume {}", Joiner.on(", ").join(unIngestedReplicas), unManagedVolumeNativeGUID);
            StringBuffer taskStatus = taskStatusMap.get(currentUnManagedVolume.getNativeGuid());
            if (taskStatus == null) {
                taskStatus = new StringBuffer();
                taskStatusMap.put(currentUnManagedVolume.getNativeGuid(), taskStatus);
            }
            // we don't need to include vplex backend
            // volume guids in the list returned to the user
            if (vplexBackendVolumeGUIDs != null) {
                _logger.info("removing the subset of vplex backend volume GUIDs from the error message: "
                        + vplexBackendVolumeGUIDs);
                // have to convert this because getUningestedReplicas returns an immutable set
                Set<String> mutableSet = new HashSet<String>();
                mutableSet.addAll(unIngestedReplicas);
                mutableSet.removeAll(vplexBackendVolumeGUIDs);
                unIngestedReplicas = mutableSet;
            }
            taskStatus.append(String.format("The umanaged volume %s has been partially ingested, but not all replicas "
                    + "have been ingested. Uningested replicas: %s.", currentUnManagedVolume.getLabel(),
                    Joiner.on(", ").join(unIngestedReplicas)));
            // clear the map and stop traversing
            parentReplicaMap.clear();
            traverseTree = false;
        }

        if (traverseTree && !unmanagedReplicaGUIDs.isEmpty()) {
            // get all the unmanaged volume replicas and traverse through them
            List<UnManagedVolume> replicaUnManagedVolumes = _dbClient.queryObject(UnManagedVolume.class,
                    VolumeIngestionUtil.getUnManagedVolumeUris(unmanagedReplicaGUIDs, _dbClient));
            for (UnManagedVolume replica : replicaUnManagedVolumes) {
                BlockObject replicaBlockObject = null;
                if (replica.getNativeGuid().equals(currentUnManagedVolume.getNativeGuid())) {
                    replicaBlockObject = currentBlockObject;
                } else {
                    _logger.info("Checking for replica object in created object map");
                    String replicaGUID = replica.getNativeGuid().replace(VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME);
                    replicaBlockObject = createdObjectMap.get(replicaGUID);
                    if (replicaBlockObject == null) {
                        _logger.info("Checking if the replica is ingested");
                        replicaBlockObject = VolumeIngestionUtil.getBlockObject(replicaGUID, _dbClient);
                    }
                }

                runReplicasIngestedCheck(replica, replicaBlockObject, currentUnManagedVolume, currentBlockObject, unManagedVolumeGUIDs,
                        createdObjectMap, parentReplicaMap, taskStatusMap);
                // TODO- break out if the parent-replica map is empty
            }
        }
    }

    /**
     * Return the foundingestedreplica nativeguids.
     * 
     * @param foundIngestedReplicas
     * @param foundIngestedReplicaNativeGuids
     */
    private void getFoundIngestedReplicaURIs(List<BlockObject> foundIngestedReplicas, List<String> foundIngestedReplicaNativeGuids) {
        if (null != foundIngestedReplicas && !foundIngestedReplicas.isEmpty()) {
            for (BlockObject blockObj : foundIngestedReplicas) {
                _logger.info("getFoundIngestedReplicaURIs blockObj: " + blockObj);
                foundIngestedReplicaNativeGuids.add(blockObj.getNativeGuid());
            }
        }
    }

}
