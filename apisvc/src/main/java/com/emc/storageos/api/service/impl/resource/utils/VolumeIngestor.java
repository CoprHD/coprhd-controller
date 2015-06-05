/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2008-2014 EMC Corporation All Rights Reserved This software contains the
 * intellectual property of EMC Corporation or is licensed to EMC Corporation from third parties.
 * Use of this software and the intellectual property contained therein is expressly limited to the
 * terms and conditions of the License Agreement under which it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.resource.StoragePoolService;
import com.emc.storageos.api.service.impl.resource.StorageSystemService;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestionException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.DataObject;
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
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.ResourceAndUUIDNameGenerator;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeExportIngestParam;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.smis.SRDFOperations;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;

@Deprecated
public class VolumeIngestor {
    private static Logger _logger = LoggerFactory.getLogger(VolumeIngestionUtil.class);
    private static final DataObject.Flag[] INTERNAL_VOLUME_FLAGS = new DataObject.Flag[] {
            Flag.INTERNAL_OBJECT, Flag.NO_PUBLIC_ACCESS, Flag.NO_METERING };
    private static final String UNMATCHED_VARRAYS = "UnManaged Volume %s cannot be ingested as given VArray is not "
            + "matching with storage pool's connected VArrays. Skipping Ingestion";
    private static final String AUTO_TIERING_NOT_CONFIGURED = "UnManaged Volume %s Auto Tiering Policy %s does not match"
            + " with the Policy %s in given vPool %s. Skipping Ingestion";
    
    /**
     * Validate the unmanaged volume and create a volume object
     * @param param
     * @param project
     * @param tenant
     * @param varray
     * @param vpool
     * @param unManagedVolumes
     * @param volumeList
     * @param unManagedVolume
     * @param volumes
     * @param full_pools
     * @param full_systems
     * @param volume
     * @param dbClient
     * @param nameGenerator
     * @return
     * @throws Exception
     */
    public Volume createVolume(VolumeExportIngestParam param, Project project, TenantOrg tenant,
            VirtualArray varray, VirtualPool vpool, List<UnManagedVolume> unManagedVolumes,
            NamedVolumesList volumeList, UnManagedVolume unManagedVolume, List<Volume> volumes,
            List<URI> full_pools, List<URI> full_systems, Volume volume, DbClient dbClient,
            ResourceAndUUIDNameGenerator nameGenerator) throws Exception {

        String volumeNativeGuid = unManagedVolume.getNativeGuid().replace(
                VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME);
        boolean removeUnmanagedVolume = false;
        boolean isVplexVolume = VolumeIngestionUtil.isVplexVolume(unManagedVolume);
        URI consistencyGroupUri = null;
        // in case of srdf environments, we would end up in partial volumes
        // being ingested
        // and this block handles it, by not creating a new volume if already
        // exists,
        // instead update the srdf links if any.
      

            // TODO: is this check redundant???
            if (!canIngestVolume(unManagedVolume, volumeNativeGuid, dbClient, isVplexVolume, varray, project,
                    vpool, tenant)) {
                return volume;
            }

            URI storagePoolUri = unManagedVolume.getStoragePoolUri();
            StoragePool pool = null;

            if (null != storagePoolUri) {
                pool = dbClient.queryObject(StoragePool.class, storagePoolUri);
            }

            if (!isStoragePoolValid(unManagedVolume, dbClient, isVplexVolume, varray.getId(), pool)) {
                return volume;
            }

            StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
            URI storageSystemUri = unManagedVolume.getStorageSystemUri();
            StorageSystem system = dbClient.queryObject(StorageSystem.class, storageSystemUri);

            String autoTierPolicyId = null;
            if (null != unManagedVolumeInformation.get(SupportedVolumeInformation.AUTO_TIERING_POLICIES
                    .toString())) {
                for (String policyName : unManagedVolumeInformation
                        .get(SupportedVolumeInformation.AUTO_TIERING_POLICIES.toString())) {
                    autoTierPolicyId = NativeGUIDGenerator.generateAutoTierPolicyNativeGuid(
                            system.getNativeGuid(), policyName, NativeGUIDGenerator.getTieringPolicyKeyForSystem(system));
                    _logger.info("Auto Tier Policy Id {}", autoTierPolicyId);
                    break;
                }
            }
            if (!DiscoveryUtils
                .checkVPoolValidForUnManagedVolumeAutoTieringPolicy(vpool, autoTierPolicyId, system)) {
                _logger.warn(String.format(AUTO_TIERING_NOT_CONFIGURED, new Object[] { unManagedVolume.getId(),
                        autoTierPolicyId, vpool.getAutoTierPolicyName(), vpool.getLabel() }));
                return volume;
            }

            if (!verifyResourceLimits(isVplexVolume, pool, system, dbClient, full_pools, full_systems)) {
                return volume;
            }
            volume = new Volume();

            addVolumeProperties(volume, volumeNativeGuid, vpool, varray, storageSystemUri, unManagedVolume,
                    isVplexVolume, pool, project, nameGenerator, dbClient, autoTierPolicyId);

            updateVplexVolumeCGs(consistencyGroupUri, isVplexVolume, unManagedVolume, dbClient,
                    storageSystemUri, volume);
            PropertySetterUtil.addVolumeDetails(unManagedVolumeInformation, volume);
            return volume;

    }
   
    /**
     * Ingest Volume Validation
     * @param unManagedVolume
     * @param volumeNativeGuid
     * @param dbClient
     * @param isVplexVolume
     * @param varray
     * @param project
     * @param vpool
     * @param tenant
     * @return
     */
    private boolean canIngestVolume(UnManagedVolume unManagedVolume, String volumeNativeGuid,
            DbClient dbClient, boolean isVplexVolume, VirtualArray varray, Project project,
            VirtualPool vpool, TenantOrg tenant) throws IngestionException {

        String remoteProtected = unManagedVolume.getVolumeCharacterstics().get(
                SupportedVolumeCharacterstics.REMOTE_MIRRORING.toString());
        URI unManagedVolumeUri = unManagedVolume.getId();
        
        VolumeIngestionUtil.checkVPoolValidForUnManagedVolumeInProtectedMode(vpool, unManagedVolume,dbClient);
        
        if (null != VirtualPoolUtil.checkIfVolumeExistsInDB(volumeNativeGuid, dbClient)) {
            _logger.warn("UnManagedVolume {} already ingested.  Skipping ingestion", unManagedVolumeUri);
            return false;
        }
        if (unManagedVolume.getInactive()) {
            _logger.warn("UnManaged Volume {} is in inactive state.  Skipping Ingestion", unManagedVolumeUri);
            return false;
        }
        if (VolumeIngestionUtil.checkUnManagedResourceAddedToConsistencyGroup(unManagedVolume)) {
            if (isVplexVolume) {
                URI consistencyGroupUri = VolumeIngestionUtil.getVplexConsistencyGroup(unManagedVolume,
                        vpool, project.getId(), tenant.getId(), varray.getId(), dbClient);
                if (null == consistencyGroupUri) {
                    _logger.warn("A Consistency Group for the VPLEX volume could not be determined. Skipping Ingestion.");
                    return false;
                }
            } else {
                _logger.warn("UnManaged Volume {} is added to consistency group.  Skipping Ingestion",
                        unManagedVolumeUri);
                return false;
            }
        }

        // check for Replica validations only if unmanaged volume is not SRDF
        // protected
        if (!Boolean.parseBoolean(remoteProtected)) {
            if (VolumeIngestionUtil.checkUnManagedVolumeHasReplicas(unManagedVolume)) {
                _logger.warn("UnManaged Volume {} has replicas associated.Skipping Ingestion",
                        unManagedVolumeUri);
                return false;
            }
        } else {
            String copyMode = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.REMOTE_COPY_MODE.toString(),
                    unManagedVolume.getVolumeInformation());
            if (SRDFOperations.Mode.ASYNCHRONOUS.toString().equalsIgnoreCase(copyMode)) {
                _logger.warn("UnManaged Volume {} with SRDF Asynchronous mode.Skipping Ingestion",
                        unManagedVolumeUri);
                return false;
            }
        }
        
        VolumeIngestionUtil.checkUnManagedResourceIngestable(unManagedVolume);

        VolumeIngestionUtil.checkUnManagedResourceIsRecoverPointEnabled(unManagedVolume);

        // For VPLEX volumes, verify that it is OK to ingest the unmanaged
        // volume into the requested virtual array.
        if ((isVplexVolume)
                && (!VolumeIngestionUtil.isValidVarrayForUnmanagedVolume(unManagedVolume, varray.getId(),
                        dbClient))) {
            _logger.warn(
                    "UnManaged Volume {} cannot be ingested into the requested varray. Skipping Ingestion.",
                    unManagedVolume.getLabel());
            return false;
        }
        return true;

    }
    
    /**
     * Check if Storage Pool valid
     * @param unManagedVolume
     * @param dbClient
     * @param isVplexVolume
     * @param vArray
     * @param pool
     * @return
     */
    private boolean isStoragePoolValid(UnManagedVolume unManagedVolume, DbClient dbClient,
            boolean isVplexVolume, URI vArray, StoragePool pool) {
        if (null != pool) {
            if (null == pool.getTaggedVirtualArrays()
                    || !pool.getTaggedVirtualArrays().contains(vArray.toString())) {
                _logger.warn(String.format(UNMATCHED_VARRAYS, new Object[] { unManagedVolume.getId() }));
                return false;
            }
        }
        if (!isVplexVolume && null == pool) {
            _logger.warn("UnManaged Volume {} does not have a Storage Pool set.  Skipping Ingestion.",
                    unManagedVolume.getLabel());
            return false;
        }
        return true;
    }

    /**
     * Verify resource limits
     * @param isVplexVolume
     * @param pool
     * @param system
     * @param dbClient
     * @param full_pools
     * @param full_systems
     * @return
     */
    private boolean verifyResourceLimits(boolean isVplexVolume, StoragePool pool, StorageSystem system,
            DbClient dbClient, List<URI> full_pools, List<URI> full_systems) {
        if (!isVplexVolume) {
            if (full_pools.contains(pool.getId())) {
                return false;
            }
            if (pool.getIsResourceLimitSet()) {
                if (pool.getMaxResources() <= StoragePoolService.getNumResources(pool, dbClient)) {
                    // reached limit for this pool
                    full_pools.add(pool.getId());
                    return false;
                }
            }
        }

        if (full_systems.contains(system.getId())) {
            return false;
        }
        if (system.getIsResourceLimitSet()) {
            if (system.getMaxResources() <= StorageSystemService.getNumResources(system, dbClient)) {
                // reached limit for this system
                full_systems.add(system.getId());
                return false;
            }
        }
        return true;
    }
    
    /**
     * update VPlex Volumes in CGs
     * @param consistencyGroupUri
     * @param isVplexVolume
     * @param unManagedVolume
     * @param dbClient
     * @param storageSystemUri
     * @param volume
     */
    private void updateVplexVolumeCGs(URI consistencyGroupUri, boolean isVplexVolume,
            UnManagedVolume unManagedVolume, DbClient dbClient, URI storageSystemUri, Volume volume) {
        if (consistencyGroupUri != null) {
            if (isVplexVolume) {
                String cgName = PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.VPLEX_CONSISTENCY_GROUP_NAME.toString(),
                        unManagedVolume.getVolumeInformation());

                BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class,
                        consistencyGroupUri);

                StringSet unmanagedVolumeClusters = unManagedVolume.getVolumeInformation().get(
                        SupportedVolumeInformation.VPLEX_CLUSTER_IDS.toString());
                // Add a ViPR CG mapping for each of the VPlex clusters the
                // VPlex CG belongs to.
                if (unmanagedVolumeClusters != null && !unmanagedVolumeClusters.isEmpty()) {
                    Iterator<String> unmanagedVolumeClustersItr = unmanagedVolumeClusters.iterator();
                    while (unmanagedVolumeClustersItr.hasNext()) {
                        cg.addSystemConsistencyGroup(
                                storageSystemUri.toString(),
                                BlockConsistencyGroupUtils.buildClusterCgName(
                                        unmanagedVolumeClustersItr.next(), cgName));
                    }

                    dbClient.updateAndReindexObject(cg);
                }
            }

            volume.setConsistencyGroup(consistencyGroupUri);
        }

    }
    
    /**
     * Add Volume Properties
     * @param volume
     * @param volumeNativeGuid
     * @param vPool
     * @param vArray
     * @param storageSystemUri
     * @param unManagedVolume
     * @param isVplexVolume
     * @param pool
     * @param project
     * @param nameGenerator
     * @param dbClient
     * @param autoTierPolicyId
     */
    private void addVolumeProperties(Volume volume, String volumeNativeGuid, VirtualPool vPool,
            VirtualArray vArray, URI storageSystemUri, UnManagedVolume unManagedVolume,
            boolean isVplexVolume, StoragePool pool, Project project,
            ResourceAndUUIDNameGenerator nameGenerator, DbClient dbClient, String autoTierPolicyId) {

        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        Calendar timeNow = Calendar.getInstance();
        volume.setId(URIUtil.createId(Volume.class));
        volume.setNativeGuid(volumeNativeGuid);
        volume.setVirtualPool(vPool.getId());
        volume.setVirtualArray(vArray.getId());
        volume.setStorageController(storageSystemUri);
        volume.setCreationTime(timeNow);
        volume.setPool(unManagedVolume.getStoragePoolUri());
        volume.setProtocol(new StringSet());
        if (isVplexVolume) {
            volume.getProtocol().addAll(vPool.getProtocols());
        } else {
            volume.getProtocol().addAll(
                    VirtualPoolUtil.getMatchingProtocols(vPool.getProtocols(), pool.getProtocols()));
        }
        // adding capacity
        String allocatedCapacity = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.ALLOCATED_CAPACITY.toString(), unManagedVolumeInformation);
        String provisionedCapacity = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.PROVISIONED_CAPACITY.toString(), unManagedVolumeInformation);

        volume.setAllocatedCapacity(Long.parseLong(allocatedCapacity));
        volume.setProvisionedCapacity(Long.parseLong(provisionedCapacity));
        volume.setCapacity(Long.parseLong(provisionedCapacity));

        String wwn = PropertySetterUtil.extractValueFromStringSet(SupportedVolumeInformation.WWN.toString(),
                unManagedVolumeInformation);
        volume.setWWN(wwn);

        volume.setThinlyProvisioned(Boolean.parseBoolean(unManagedVolume.getVolumeCharacterstics().get(
                SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString())));

        String nativeId = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.NATIVE_ID.toString(), unManagedVolumeInformation);

        // Set volume access state
        String accessState = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.ACCESS.toString(), unManagedVolumeInformation);
        StringSet statusDescriptions = PropertySetterUtil.extractValuesFromStringSet(
                SupportedVolumeInformation.STATUS_DESCRIPTIONS.toString(), unManagedVolumeInformation);
        volume.setAccessState(SmisUtils.generateAccessState(accessState, statusDescriptions));

        String deviceLabel = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.DEVICE_LABEL.toString(), unManagedVolumeInformation);
        if (null == deviceLabel || deviceLabel.trim().isEmpty()) {
            deviceLabel = volumeNativeGuid;
        }

        // set auto tier policy Id
        if (null != autoTierPolicyId) {
        	_logger.info("Adding Tier Policy URI");
            List<URI> autoTierPolicyURIs = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getAutoTieringPolicyByNativeGuidConstraint(autoTierPolicyId));
            if (autoTierPolicyURIs.size() > 0) {
            	_logger.info("Setting Tier Policy URI");
                volume.setAutoTieringPolicyUri(autoTierPolicyURIs.get(0));
            }
        }

        // TODO Need to think about adding option to user to provide label
        // TODO Use nameGenerator to generate BourneCreatedLabelFormat, and
        // update
        // volume label in Array
        if (isVplexVolume) {
            String label = unManagedVolume.getLabel();
            volume.setDeviceLabel(label);
            volume.setLabel(label);
            volume.setNativeId(volumeNativeGuid);
            // unsetting the native GUID to be consistent with ViPR-provisioned
            // volumes
            volume.setNativeGuid("");
        } else {
            volume.setDeviceLabel(deviceLabel);
            volume.setLabel(deviceLabel);
            volume.setNativeId(nativeId);
        }
        volume.setWWN(PropertySetterUtil.extractValueFromStringSet(SupportedVolumeInformation.WWN.toString(),
                unManagedVolumeInformation));

        // set meta volume specific information
        Boolean isMetaVolume = Boolean.parseBoolean(unManagedVolume.getVolumeCharacterstics().
                get(SupportedVolumeCharacterstics.IS_METAVOLUME.toString()));
        volume.setIsComposite(isMetaVolume);
        if (isMetaVolume) {
            String metaVolumeType = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.META_VOLUME_TYPE.toString(),
                    unManagedVolumeInformation);
            // If unmanaged meta volume was discovered prior to 2.2 upgrade, it won't have any meta volume characteristics set.
            // In this case we skip meta volume characteristics processing here. These characteristics will be added to vipr volume by
            // regular rediscovery of its storage system after the volume is ingested.
            if (metaVolumeType != null && !metaVolumeType.isEmpty()) {
                volume.setCompositionType(metaVolumeType);

                String metaVolumeMemberSize = PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.META_MEMBER_SIZE.toString(),
                        unManagedVolumeInformation);
                volume.setMetaMemberSize(Long.parseLong(metaVolumeMemberSize));

                String metaVolumeMemberCount = PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.META_MEMBER_COUNT.toString(),
                        unManagedVolumeInformation);
                volume.setMetaMemberCount(Integer.parseInt(metaVolumeMemberCount));
                Long totalMetaMemberCapacity = volume.getMetaMemberCount() * volume.getMetaMemberSize();
                volume.setTotalMetaMemberCapacity(totalMetaMemberCapacity);
                _logger.info(String.format(
                        "Set meta volume data for meta volume: %s . Type: %s, member count: %s, member size: %s, total capacity: %s",
                        volume.getLabel(), metaVolumeType, metaVolumeMemberCount, metaVolumeMemberSize, totalMetaMemberCapacity));
            } else {
                _logger.info("Unmanaged meta volume {} does not have meta volume properties set due to pre-upgrade discovery", volumeNativeGuid);
            }
        }
        
        volume.setTenant(new NamedURI(project.getTenantOrg().getURI(), volume.getLabel()));
        volume.setProject(new NamedURI(project.getId(), volume.getLabel()));
       
    }

}
