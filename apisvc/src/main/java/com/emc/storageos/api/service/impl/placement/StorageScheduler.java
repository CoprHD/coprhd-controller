/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.placement;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.AbstractBlockServiceApiImpl;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.PoolClassNames;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.SynchronizationState;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.FileReplicationType;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.SizeUtil;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.emc.storageos.volumecontroller.AttributeMatcher.Attributes;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.PortMetricsProcessor;
import com.emc.storageos.volumecontroller.impl.utils.AttributeMapBuilder;
import com.emc.storageos.volumecontroller.impl.utils.AttributeMatcherFramework;
import com.emc.storageos.volumecontroller.impl.utils.ObjectLocalCache;
import com.emc.storageos.volumecontroller.impl.utils.ProvisioningAttributeMapBuilder;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.volumecontroller.impl.utils.attrmatchers.CapacityMatcher;
import com.emc.storageos.volumecontroller.impl.utils.attrmatchers.MaxResourcesMatcher;

/**
 * Basic storage scheduling functions of block and file storage. StorageScheduler is done based on desired
 * virtual pool parameters for the provisioned storage.
 */
public class StorageScheduler implements Scheduler {
    public static final Logger _log = LoggerFactory.getLogger(StorageScheduler.class);
    private DbClient _dbClient;

    private CoordinatorClient _coordinator;
    private PortMetricsProcessor _portMetricsProcessor;

    private final Comparator<StoragePool> _storagePoolComparator = new StoragePoolFreeCapacityComparator();
    private AttributeMatcherFramework _matcherFramework;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    public void setPortMetricsProcessor(PortMetricsProcessor portMetricsProcessor) {
        if (_portMetricsProcessor == null) {
            _portMetricsProcessor = portMetricsProcessor;
        }
    }

    public AttributeMatcherFramework getMatcherFramework() {
        return _matcherFramework;
    }

    public void setMatcherFramework(AttributeMatcherFramework matcherFramework) {
        _matcherFramework = matcherFramework;
    }

    public CoordinatorClient getCoordinator() {
        return _coordinator;
    }

    /**
     * Returns list of recommendations for block volumes.
     *
     * Select and return one or more storage pools where the volume(s)/fileshare(s)
     * should be created. The placement logic is based on:
     * - VirtualArray, only storage devices in the given varray are candidates
     * - VirtualPool, specifies must-meet & best-meet service specifications
     * - access-protocols: storage pools must support all protocols specified in VirtualPool
     * - snapshots: if yes, only select storage pools with this capability
     * - snapshot-consistency: if yes, only select storage pools with this capability
     * - performance: best-match, select storage pools which meets desired performance
     * - provision-mode: thick/thin
     * - numPaths: select storage pools with required number of paths to the volume
     * - size: Place the resources in the minimum number of storage pools that can
     * accommodate the size and number of resource requested.
     *
     * @param neighborhood
     * @param cos
     * @param capabilities
     * @return list of VolumeRecommendation instances
     */
    @Override
    public List<VolumeRecommendation> getRecommendationsForResources(VirtualArray neighborhood, Project project, VirtualPool cos,
            VirtualPoolCapabilityValuesWrapper capabilities) {

        _log.debug("Schedule storage for {} resource(s) of size {}.", capabilities.getResourceCount(), capabilities.getSize());

        List<VolumeRecommendation> volumeRecommendations = new ArrayList<VolumeRecommendation>();

        // Initialize a list of recommendations to be returned.
        List<Recommendation> recommendations = new ArrayList<Recommendation>();

        // Get all storage pools that match the passed CoS params and
        // protocols. In addition, the pool must have enough capacity
        // to hold at least one resource of the requested size.
        List<StoragePool> candidatePools = getMatchingPools(neighborhood, cos, capabilities);

        // Get the recommendations for the candidate pools.
        recommendations = getRecommendationsForPools(neighborhood.getId().toString(), candidatePools, capabilities);

        // We need to place all the resources. If we can't then
        // log an error and clear the list of recommendations.
        if (recommendations.isEmpty()) {
            // TODO reevaluate
            _log.error(
                    "Could not find matching pools for VArray {} & VPool {}",
                    neighborhood.getId(), cos.getId());
            return volumeRecommendations;
        }

        // create list of VolumeRecommendation(s) for volumes
        for (Recommendation recommendation : recommendations) {
            int count = recommendation.getResourceCount();
            while (count > 0) {
                VolumeRecommendation volumeRecommendation = new VolumeRecommendation(VolumeRecommendation.VolumeType.BLOCK_VOLUME,
                        capabilities.getSize(), cos, neighborhood.getId());
                volumeRecommendation.addStoragePool(recommendation.getSourceStoragePool());
                volumeRecommendation.addStorageSystem(recommendation.getSourceStorageSystem());
                volumeRecommendations.add(volumeRecommendation);
                if (capabilities.getBlockConsistencyGroup() != null) {
                    volumeRecommendation.setParameter(VolumeRecommendation.ARRAY_CG, capabilities.getBlockConsistencyGroup());
                }
                count--;
            }
        }

        return volumeRecommendations;
    }

    public void getRecommendationsForMirrors(VirtualArray vArray, VirtualPool vPool,
            VirtualPoolCapabilityValuesWrapper capabilities, List<Recommendation> volumeRecommendations) {

        List<VolumeRecommendation> mirrorRecommendations = new ArrayList<VolumeRecommendation>();
        // separate volumes by different devices
        Map<URI, List<VolumeRecommendation>> deviceMap = VolumeRecommendation.getDeviceMap(volumeRecommendations, _dbClient);

        // get matching pools for recommendations on each device
        Set<URI> storageSystems = deviceMap.keySet();
        for (URI storageSystem : storageSystems) {
            Map<String, Object> attributeMap = new HashMap<String, Object>();
            Set<String> storageSystemSet = new HashSet<String>();
            storageSystemSet.add(storageSystem.toString());
            attributeMap.put(AttributeMatcher.Attributes.storage_system.name(), storageSystemSet);

            Set<String> virtualArraySet = new HashSet<String>();
            virtualArraySet.add(vArray.getId().toString());
            attributeMap.put(AttributeMatcher.Attributes.varrays.name(), virtualArraySet);

            _log.info("Matching pools for storage system {} ", storageSystem);
            List<StoragePool> matchedPools = getMatchingPools(vArray, vPool, capabilities, attributeMap);
            if (matchedPools == null || matchedPools.isEmpty()) {
                // TODO fix message and throw service code exception
                _log.warn("VArray {} does not have storage pools which match VPool {}.", vArray.getId(), vPool.getId());
                throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(vPool.getId(), vArray.getId());
            }

            // place all mirrors for this storage system in the matched pools
            List<VolumeRecommendation> sourceVolumeRecommendations = deviceMap.get(storageSystem);
            for (VolumeRecommendation sourceRecommendation : sourceVolumeRecommendations) {
                StoragePool poolForMirror = placeLocalMirror(matchedPools, sourceRecommendation);
                // create mirror volume recommendation
                VolumeRecommendation mirrorRecommendation = new VolumeRecommendation(VolumeRecommendation.VolumeType.BLOCK_LOCAL_MIRROR,
                        capabilities.getSize(), vPool, vArray.getId());
                mirrorRecommendation.addStoragePool(poolForMirror.getId());
                mirrorRecommendation.addStorageSystem(poolForMirror.getStorageDevice());
                mirrorRecommendation.setParameter(VolumeRecommendation.BLOCK_VOLUME, sourceRecommendation);
                sourceRecommendation.setParameter(VolumeRecommendation.LOCAL_MIRROR, mirrorRecommendation);
                mirrorRecommendations.add(mirrorRecommendation);
            }
        }

        volumeRecommendations.addAll(mirrorRecommendations);
    }

    private StoragePool placeLocalMirror(List<StoragePool> candidatePools, VolumeRecommendation sourceVolumeRecommendation) {

        URI sourcePoolId = sourceVolumeRecommendation.getCandidatePools().get(0);
        _log.info("START blockmirror placement");

        _log.debug("We have {} candidate pool(s)", candidatePools.size());
        _log.debug("Source pool is: {}", sourcePoolId);
        sortPools(candidatePools);
        StoragePool poolForMirror = candidatePools.get(0);
        if (candidatePools.size() > 1) {
            // do not use storage pool which was used for source volume
            if (poolForMirror.getId().equals(sourcePoolId)) {
                poolForMirror = candidatePools.get(1);
            }
        }

        return poolForMirror;
    }

    public List<VolumeRecommendation> getRecommendationsForVolumeClones(VirtualArray vArray, VirtualPool vPool,
            BlockObject blockObject, VirtualPoolCapabilityValuesWrapper capabilities) {

        _log.debug("Schedule storage for {} block volume copies {} of size {}.", capabilities.getResourceCount(), capabilities.getSize());

        List<VolumeRecommendation> volumeRecommendations = new ArrayList<VolumeRecommendation>();

        // Initialize a list of recommendations to be returned.
        List<Recommendation> recommendations = new ArrayList<Recommendation>();

        // For volume clones, candidate pools should be on the same storage system as the source volume
        URI storageSystemId = blockObject.getStorageController();
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        Set<String> storageSystemSet = new HashSet<String>();
        storageSystemSet.add(storageSystemId.toString());
        attributeMap.put(AttributeMatcher.Attributes.storage_system.name(), storageSystemSet);

        Set<String> virtualArraySet = new HashSet<String>();
        virtualArraySet.add(vArray.getId().toString());
        attributeMap.put(AttributeMatcher.Attributes.varrays.name(), virtualArraySet);

        // Get all storage pools that match the passed CoS params and
        // protocols. In addition, the pool must have enough capacity
        // to hold at least one resource of the requested size.
        // In addition, we need to only select pools from the
        // StorageSystem that the source volume was created against.
        List<StoragePool> matchedPools = getMatchingPools(vArray, vPool, capabilities, attributeMap);
        if (matchedPools == null || matchedPools.isEmpty()) {
            _log.warn("VArray {} does not have storage pools which match VPool {} to clone volume {}.", new Object[] { vArray.getId(),
                    vPool.getId(), blockObject.getId() });
            throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarrayForClones(vPool.getId(), vArray.getId(),
                    blockObject.getId());
        }

        _log.info(String.format("Found %s candidate pools for placement of %s clone(s) of volume %s .",
                String.valueOf(matchedPools.size()), String.valueOf(capabilities.getResourceCount()), blockObject.getId()));

        // Get the recommendations for the candidate pools.
        recommendations = getRecommendationsForPools(vArray.getId().toString(), matchedPools, capabilities);

        // We need to place all the resources. If we can't then
        // log an error and clear the list of recommendations.
        if (recommendations.isEmpty()) {
            String msg = String.format("Could not find placement for %s clones of volume %s with capacity %s",
                    capabilities.getResourceCount(), blockObject.getId(), capabilities.getSize());
            _log.error(msg);
            throw APIException.badRequests.invalidParameterNoStorageFoundForVolume(vArray.getId(), vPool.getId(), blockObject.getId());
        }

        // create list of VolumeRecommendation(s) for volumes
        for (Recommendation recommendation : recommendations) {
            int count = recommendation.getResourceCount();
            while (count > 0) {
                VolumeRecommendation volumeRecommendation = new VolumeRecommendation(VolumeRecommendation.VolumeType.BLOCK_COPY,
                        capabilities.getSize(), vPool, vArray.getId());
                volumeRecommendation.addStoragePool(recommendation.getSourceStoragePool());
                volumeRecommendation.addStorageSystem(recommendation.getSourceStorageSystem());
                volumeRecommendations.add(volumeRecommendation);
                if (capabilities.getBlockConsistencyGroup() != null) {
                    volumeRecommendation.setParameter(VolumeRecommendation.ARRAY_CG, capabilities.getBlockConsistencyGroup());
                }
                count--;
            }
        }

        return volumeRecommendations;
    }

    /**
     * Sort list of storage pools based on its storage system's average usage port metrics usage. Its
     * secondary sorting components are free and subscribed capacity
     *
     * @param storagePools
     */
    public void sortPools(List<StoragePool> storagePools) {
        // compute and set storage pools'average port usage metrics before sorting.
        _portMetricsProcessor.computeStoragePoolsAvgPortMetrics(storagePools);

        /**
         * Sort all pools in ascending order of its storage system's average port usage metrics (first order),
         * descending order by free capacity (second order) and in ascending order by ratio
         * of pool's subscribed capacity to total capacity(suborder).
         * This order is kept through the selection procedure.
         */
        Collections.sort(storagePools, new StoragePoolDefaultComparator());
    }

    /**
     * Select candidate storage pools for placement. Wrapper for the
     * 4 parameter version (below), which uses an optional parameter
     * for passing in attributes.
     *
     * @param varray The VirtualArray for matching storage pools.
     * @param vpool The virtualPool that must be satisfied by the storage pool.
     * @param capabilities The VirtualPool params that must be satisfied.
     *
     * @return A list of matching storage pools.
     */
    protected List<StoragePool> getMatchingPools(VirtualArray varray, VirtualPool vpool,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        return getMatchingPools(varray, vpool, capabilities, null);
    }

    /**
     * Select candidate storage pools for placement.
     *
     * @param varray The VirtualArray for matching storage pools.
     * @param vpool The virtualPool that must be satisfied by the storage pool.
     * @param capabilities The VirtualPool params that must be satisfied.
     * @param optionalAttributes Optional addition attributes to consider for placement
     *
     * @return A list of matching storage pools.
     */
    protected List<StoragePool> getMatchingPools(VirtualArray varray, VirtualPool vpool,
            VirtualPoolCapabilityValuesWrapper capabilities, Map<String, Object> optionalAttributes) {

        capabilities.put(VirtualPoolCapabilityValuesWrapper.VARRAYS, varray.getId().toString());
        if (null != vpool.getAutoTierPolicyName()) {
            capabilities.put(VirtualPoolCapabilityValuesWrapper.AUTO_TIER__POLICY_NAME,
                    vpool.getAutoTierPolicyName());
        }

        List<StoragePool> storagePools = new ArrayList<StoragePool>();
        String varrayId = varray.getId().toString();

        // Verify that if the VirtualPool is assigned to one or more VirtualArrays
        // there is a match with the passed VirtualArray.
        StringSet vpoolVarrays = vpool.getVirtualArrays();
        if ((vpoolVarrays != null) && (!vpoolVarrays.contains(varrayId))) {
            _log.error("VirtualPool {} is not in virtual array {}", vpool.getId(), varrayId);
            return storagePools;
        }
        // Get pools for VirtualPool and VirtualArray
        List<StoragePool> matchedPoolsForCos = VirtualPool.getValidStoragePools(vpool, _dbClient, true);

        AttributeMapBuilder provMapBuilder = new ProvisioningAttributeMapBuilder(capabilities.getSize(),
                varrayId, capabilities.getThinVolumePreAllocateSize());
        provMapBuilder.putAttributeInMap(AttributeMatcher.Attributes.provisioning_type.toString(),
                vpool.getSupportedProvisioningType());

        // Set CG related attributes for placement
        final BlockConsistencyGroup consistencyGroup = capabilities.getBlockConsistencyGroup() == null ? null : _dbClient
                .queryObject(BlockConsistencyGroup.class, capabilities.getBlockConsistencyGroup());
        if (consistencyGroup != null) {

            URI cgStorageSystemURI = consistencyGroup.getStorageController();
            if (cgStorageSystemURI != null && !NullColumnValueGetter.getNullURI().equals(cgStorageSystemURI)) {
                StorageSystem cgStorageSystem = _dbClient.queryObject(StorageSystem.class, cgStorageSystemURI);

                Set<String> storageSystemSet = new HashSet<String>();
                // if srdf, then select Storage System based on whether the matching Pools runs on source or target.
                // this logic is added, as we don't want to add any relationships between source and target CGs explicitly in ViPR.
                if (VirtualPoolCapabilityValuesWrapper.SRDF_TARGET.equalsIgnoreCase(capabilities.getPersonality())) {
                    // then update storage system corresponding to target CG
                    // source Label is set as alternate name for target Cgs, so that the same name can be used to create targte CGs in
                    // Array.
                    List<URI> cgUris = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                            .getBlockConsistencyGroupByAlternateNameConstraint(consistencyGroup.getLabel()));
                    if (!cgUris.isEmpty()) {
                        BlockConsistencyGroup targetCgGroup = _dbClient.queryObject(BlockConsistencyGroup.class, cgUris.get(0));
                        if (null != targetCgGroup && !targetCgGroup.getInactive() && null != targetCgGroup.getStorageController() &&
                                !NullColumnValueGetter.getNullURI().equals(targetCgGroup.getStorageController())) {
                            storageSystemSet.add(targetCgGroup.getStorageController().toString());
                            provMapBuilder.putAttributeInMap(AttributeMatcher.Attributes.storage_system.name(), storageSystemSet);
                            provMapBuilder.putAttributeInMap(AttributeMatcher.Attributes.multi_volume_consistency.name(), true);
                        }
                    }

                } else if (!DiscoveredDataObject.Type.vplex.name().equals(cgStorageSystem.getSystemType())) {
                    // If this is not a VPLEX CG, add the ConsistencyGroup's StorageSystem
                    // so that the matching pools are in the same system
                    storageSystemSet.add(cgStorageSystemURI.toString());
                    provMapBuilder.putAttributeInMap(AttributeMatcher.Attributes.storage_system.name(), storageSystemSet);
                    provMapBuilder.putAttributeInMap(AttributeMatcher.Attributes.multi_volume_consistency.name(), true);

                    // IBM XIV requires all volumes of a CG in the same StoragePool
                    if (DiscoveredDataObject.Type.ibmxiv.name().equals(cgStorageSystem.getSystemType())) {
                        List<Volume> activeCGVolumes = CustomQueryUtility
                                .queryActiveResourcesByConstraint(
                                        _dbClient,
                                        Volume.class,
                                        AlternateIdConstraint.Factory
                                                .getBlockObjectsByConsistencyGroup(consistencyGroup
                                                        .getId().toString()));
                        if (!activeCGVolumes.isEmpty()) {
                            URI cgPoolURI = activeCGVolumes.get(0).getPool();
                            if (!NullColumnValueGetter.isNullURI(cgPoolURI)) {
                                Iterator<StoragePool> itr = matchedPoolsForCos.iterator();
                                while (itr.hasNext()) {
                                    // remove all pools from matchedPoolsForCos except the one matched
                                    URI poolURI = itr.next().getId();
                                    if (!cgPoolURI.equals(poolURI)) {
                                        _log.debug("Remove pool {} from list", poolURI);
                                        itr.remove();
                                    }
                                }
                            }

                            if (!matchedPoolsForCos.isEmpty()) {
                                _log.debug("Pool {} is used by CG {}", cgPoolURI, consistencyGroup.getId());
                            } else {
                                _log.warn("vPool {} does not have required IBM XIV storage pool in vArray {}.",
                                        vpool.getId(), varray.getId());
                                throw APIException.badRequests.noStoragePoolsForVpoolInVarray(varray.getId(), vpool.getId());
                            }
                        }
                    }
                } else {
                    provMapBuilder.putAttributeInMap(AttributeMatcher.Attributes.multi_volume_consistency.name(), true);
                }
            } else {
                provMapBuilder.putAttributeInMap(AttributeMatcher.Attributes.multi_volume_consistency.name(), true);
            }
        }

        // populate DriveType,and Raid level and Policy Name for FAST Initial Placement Selection
        provMapBuilder.putAttributeInMap(Attributes.auto_tiering_policy_name.toString(), vpool.getAutoTierPolicyName());
        provMapBuilder.putAttributeInMap(Attributes.unique_policy_names.toString(), vpool.getUniquePolicyNames());
        provMapBuilder.putAttributeInMap(AttributeMatcher.PLACEMENT_MATCHERS, true);
        provMapBuilder.putAttributeInMap(AttributeMatcher.Attributes.drive_type.name(), vpool.getDriveType());
        StringSetMap arrayInfo = vpool.getArrayInfo();
        if (null != arrayInfo) {
            Set<String> raidLevels = arrayInfo.get(VirtualPoolCapabilityValuesWrapper.RAID_LEVEL);
            if (null != raidLevels && !raidLevels.isEmpty()) {
                provMapBuilder.putAttributeInMap(AttributeMatcher.Attributes.raid_levels.name(), raidLevels);
            }

            Set<String> systemTypes = arrayInfo.get(AttributeMatcher.Attributes.system_type.name());
            if (null != systemTypes && !systemTypes.isEmpty()) {
                provMapBuilder.putAttributeInMap(AttributeMatcher.Attributes.system_type.name(), systemTypes);

                // put quota value for ecs storage
                if (systemTypes.contains("ecs") && capabilities.getQuota() != null) {
                    provMapBuilder.putAttributeInMap(AttributeMatcher.Attributes.quota.name(), capabilities.getQuota());
                }
            }
        }

        Map<URI, VpoolRemoteCopyProtectionSettings> remoteProtectionSettings = vpool.getRemoteProtectionSettings(vpool, _dbClient);
        if (null != remoteProtectionSettings && !remoteProtectionSettings.isEmpty()) {
            provMapBuilder.putAttributeInMap(Attributes.remote_copy.toString(),
                    VirtualPool.groupRemoteCopyModesByVPool(vpool.getId(), remoteProtectionSettings));
        }

        if (VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_SOURCE.equalsIgnoreCase(capabilities.getPersonality())) {
            // Run the placement algorithm for file replication!!!
            if (vpool.getFileReplicationType() != null &&
                    !FileReplicationType.NONE.name().equalsIgnoreCase(vpool.getFileReplicationType())) {

                provMapBuilder.putAttributeInMap(Attributes.file_replication_type.toString(), vpool.getFileReplicationType());
                if (vpool.getFileReplicationCopyMode() != null) {
                    provMapBuilder.putAttributeInMap(Attributes.file_replication_copy_mode.toString(), vpool.getFileReplicationCopyMode());
                }
                Map<URI, VpoolRemoteCopyProtectionSettings> remoteCopySettings = VirtualPool.getFileRemoteProtectionSettings(vpool,
                        _dbClient);
                if (null != remoteCopySettings && !remoteCopySettings.isEmpty()) {
                    provMapBuilder.putAttributeInMap(Attributes.file_replication.toString(),
                            VirtualPool.groupRemoteCopyModesByVPool(vpool.getId(), remoteCopySettings));
                }

            }
        }

        Map<String, Object> attributeMap = provMapBuilder.buildMap();
        if (optionalAttributes != null) {
            attributeMap.putAll(optionalAttributes);
        }
        _log.info("Populated attribute map: {}", attributeMap);

        // Execute basic precondition check to verify that vArray has active storage pools in the vPool.
        // We will return a more accurate error condition if this basic check fails.
        List<StoragePool> matchedPools = _matcherFramework.matchAttributes(
                matchedPoolsForCos, attributeMap, _dbClient, _coordinator,
                AttributeMatcher.BASIC_PLACEMENT_MATCHERS);
        if (matchedPools == null || matchedPools.isEmpty()) {
            _log.warn("vPool {} does not have active storage pools in vArray  {} .",
                    vpool.getId(), varray.getId());
            throw APIException.badRequests.noStoragePoolsForVpoolInVarray(varray.getId(), vpool.getId());
        }

        // Matches the pools against the VolumeParams.
        // Use a set of matched pools returned from the basic placement matching as the input for this call.
        matchedPools = _matcherFramework.matchAttributes(
                matchedPools, attributeMap, _dbClient, _coordinator,
                AttributeMatcher.PLACEMENT_MATCHERS);
        if (matchedPools == null || matchedPools.isEmpty()) {
            _log.warn("Varray {} does not have storage pools which match vpool {} properties and have specified  capabilities.",
                    varray.getId(), vpool.getId());
            return storagePools;
        }

        storagePools.addAll(matchedPools);
        return storagePools;
    }

    /**
     * Returns first storage pool from the passed list of candidate storage
     * pools that has at least the passed free capacity.
     * Note: do not change order of candidate pools.
     *
     * @param capacity The desired free capacity.
     * @param resourceSize The desired resource size
     * @param newResourceCount The desired number of resources
     * @param candidatePools The list of candidate storage pools.
     * @param isThinlyProvisioned Indication if this is thin provisioning (thin volume).
     *
     * @return A storage pool that have the passed free capacity.
     */
    protected StoragePool getPoolMatchingCapacity(long capacity, long resourceSize,
            Integer newResourceCount, List<StoragePool> candidatePools,
            boolean isThinlyProvisioned, Long thinVolumePreAllocationResourceSize) {
        StoragePool poolWithCapacity = null;
        CapacityMatcher capacityMatcher = new CapacityMatcher();
        capacityMatcher.setCoordinatorClient(_coordinator);
        capacityMatcher.setObjectCache(new ObjectLocalCache(_dbClient, false));

        Iterator<StoragePool> storagePoolsIter = candidatePools.iterator();
        while (storagePoolsIter.hasNext()) {
            StoragePool candidatePool = storagePoolsIter.next();
            // First check if max Resources limit is violated for the pool
            if (MaxResourcesMatcher.checkPoolMaximumResourcesApproached(candidatePool, _dbClient, newResourceCount)) {
                continue;
            }

            // TODO Used to force placement to VNX Concrete pools.
            // if
            // (candidatePool.getPoolClassName().equalsIgnoreCase(StoragePool.PoolClassNames.Clar_UnifiedStoragePool.toString()))
            // continue;
            if (capacityMatcher.poolMatchesCapacity(candidatePool, capacity, resourceSize, false,
                    isThinlyProvisioned, thinVolumePreAllocationResourceSize)) {
                poolWithCapacity = candidatePool;
                break;
            }
        }
        return poolWithCapacity;
    }

    /**
     * Returns all storage pools from the passed list of candidate storage
     * pools that have at least the passed free capacity.
     * Note: do not change order of candidate pools.
     *
     * @param capacity The desired free capacity.
     * @param resourceSize The desired resource size
     * @param newResourceCount The desired number of resources
     * @param candidatePools The list of candidate storage pools.
     * @param isThinlyProvisioned Indication if this is thin provisioning (thin volume).
     *
     * @return All storage pools that have the passed free capacity.
     */
    protected List<StoragePool> getPoolsMatchingCapacity(long capacity, long resourceSize,
            Integer newResourceCount, List<StoragePool> candidatePools,
            boolean isThinlyProvisioned, Long thinVolumePreAllocationResourceSize) {
        List<StoragePool> poolsWithCapacity = new ArrayList<StoragePool>();
        CapacityMatcher capacityMatcher = new CapacityMatcher();
        capacityMatcher.setCoordinatorClient(_coordinator);
        capacityMatcher.setObjectCache(new ObjectLocalCache(_dbClient, false));

        for (StoragePool candidatePool : candidatePools) {
            // First check if max Resources limit is violated for the pool
            if (MaxResourcesMatcher.checkPoolMaximumResourcesApproached(candidatePool, _dbClient, newResourceCount)) {
                continue;
            }

            // TODO Used to force placement to VNX Concrete pools.
            // if
            // (candidatePool.getPoolClassName().equalsIgnoreCase(StoragePool.PoolClassNames.Clar_UnifiedStoragePool.toString()))
            // continue;
            if (capacityMatcher.poolMatchesCapacity(candidatePool, capacity, resourceSize, false,
                    isThinlyProvisioned, thinVolumePreAllocationResourceSize)) {
                poolsWithCapacity.add(candidatePool);
            }
        }

        return poolsWithCapacity;
    }

    /**
     * Select one storage pool out a list of candidates. Use static and dynamic loads, capacity etc
     * criteria to narrow the selection.
     *
     * @param poolList - List of StoragePools that meet the placement criteria for the
     *            volume.
     * @return - A StoragePool that can be used to allocate the volume,
     *         which meets the VirtualPool and capacity requirements.
     */
    public StoragePool selectPool(List<StoragePool> poolList) {
        if (poolList == null || poolList.isEmpty()) {
            return null;
        }

        // compute and set storage pools' average port usage metrics before sorting.
        _portMetricsProcessor.computeStoragePoolsAvgPortMetrics(poolList);

        // Select the from the pool the one that has the largest free capacity with least usage port metric
        Collections.sort(poolList, _storagePoolComparator);

        return poolList.get(0);
    }

    /**
     * Try to determine a list of storage pools from the passed list of storage
     * pools that can accommodate the passed number of resources of the passed
     * size.
     *
     * @param varrayId The VirtualArray for the recommendations.
     * @param candidatePools The list of candidate storage pools.
     *
     * @return The list of Recommendation instances reflecting the recommended
     *         pools.
     */
    protected List<Recommendation> getRecommendationsForPools(String varrayId, List<StoragePool> candidatePools,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        // If the capabilities specify a CG and the CG has yet to actually be
        // created on a physical device, we need to make sure that the call
        // returns recommended storage pools that are all on the same storage
        // system. If the CG is created, the passed list of candidate pools
        // will already filtered to that storage system. However, when it is
        // not created, the candidate pools are all pools on all systems that
        // / satisfy the placement criteria. Also, if only one volume is being
        // provisioned, it becomes irrelevant as only one storage pool will
        // be recommended.
        URI cgURI = capabilities.getBlockConsistencyGroup();
        int count = capabilities.getResourceCount();
        if ((count > 1) && (!NullColumnValueGetter.isNullURI(cgURI))) {
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            if (cg == null) {
                throw APIException.internalServerErrors.invalidObject(cgURI.toString());
            }

            if (!cg.created()) {
                // Sorting the pools here ensures they are in the desired order
                // and will be in the desired order for each storage system.
                sortPools(candidatePools);

                // Organize the candidate pools by storage system. We use a
                // linked map to ensure that when we iterate over the key set
                // the order will be the order the entries were inserted to
                // the map. Because we sorted the candidate pools first, this
                // ensures the first system in the map has the most desirable
                // pool according to the sorting algorithm.
                Map<URI, List<StoragePool>> systemPoolMap = new LinkedHashMap<URI, List<StoragePool>>();
                for (StoragePool candidatePool : candidatePools) {
                    URI systemURI = candidatePool.getStorageDevice();
                    List<StoragePool> systemPools = null;
                    if (systemPoolMap.containsKey(systemURI)) {
                        systemPools = systemPoolMap.get(systemURI);
                    } else {
                        systemPools = new ArrayList<StoragePool>();
                        systemPoolMap.put(systemURI, systemPools);
                    }
                    systemPools.add(candidatePool);
                }

                // Now attempt to find a recommendation from the set of
                // storage pools for a storage system until we find a
                // a recommendation or run out of systems.
                Iterator<URI> systemIter = systemPoolMap.keySet().iterator();
                while (systemIter.hasNext()) {
                    URI systemURI = systemIter.next();
                    List<StoragePool> systemCandidatePools = systemPoolMap.get(systemURI);
                    List<Recommendation> recommendations = getRecommendedPools(varrayId,
                            systemCandidatePools, capabilities, false);
                    if (!recommendations.isEmpty()) {
                        return recommendations;
                    }
                }

                // No recommendations found on any storage system.
                return new ArrayList<Recommendation>();
            }
        }

        return getRecommendedPools(varrayId, candidatePools, capabilities, true);
    }

    /**
     * Try to determine a list of storage pools from the passed list of storage
     * pools that can accommodate the passed number of resources of the passed
     * size.
     *
     * @param varrayId The VirtualArray for the recommendations.
     * @param candidatePools The list of candidate storage pools.
     * @param capabilities The characteristics of the recommendation request.
     * @param orderPools true if candidate pools should be ordered before
     *            determining the recommendation, false otherwise
     *
     * @return The list of Recommendation instances reflecting the recommended
     *         pools.
     */
    protected List<Recommendation> getRecommendedPools(String varrayId, List<StoragePool> candidatePools,
            VirtualPoolCapabilityValuesWrapper capabilities, boolean orderPools) {

        List<Recommendation> recommendations = new ArrayList<Recommendation>();
        long thinVolumePreAllocateSize = capabilities.getThinVolumePreAllocateSize();

        if (orderPools) {
            if (capabilities.getResourceCount() == 1) {
                // For single resource request, select storage pool randomly from
                // all candidate pools (to minimize collisions).
                Collections.shuffle(candidatePools);
            } else {
                // Sort all pools in descending order by free capacity (first order)
                // and in ascending order by ratio of pool's subscribed capacity to
                // total capacity(suborder). This order is kept through the
                // selection procedure.
                sortPools(candidatePools);
            }
        }

        // We need to create recommendations for one or more pools
        // that can accommodate the number of requested resources.
        // We start by trying to place all resources in a single
        // pool if one exists that can accommodate all requested
        // resources and work our way down as necessary trying to
        // minimize the number of pools used to satisfy the request.
        int recommendedCount = 0;
        int currentCount = capabilities.getResourceCount();
        while ((!candidatePools.isEmpty())
                && (recommendedCount < capabilities.getResourceCount()) && (currentCount > 0)) {
            long requiredPoolCapacity = capabilities.getSize() * currentCount;
            long reqThinVolumePreAllocateSize = thinVolumePreAllocateSize * currentCount;
            StoragePool poolWithRequiredCapacity = getPoolMatchingCapacity(requiredPoolCapacity,
                    capabilities.getSize(), currentCount, candidatePools, capabilities.getThinProvisioning(),
                    reqThinVolumePreAllocateSize);
            if (poolWithRequiredCapacity != null) {
                StoragePool recommendedPool = poolWithRequiredCapacity;
                candidatePools.remove(recommendedPool);

                // IBM XIV needs all volumes in the same pool in order to add to a CG
                if (recommendedPool.getPoolClassName() != null
                        && recommendedPool.getPoolClassName().equals(PoolClassNames.IBMTSDS_VirtualPool.name())
                        && capabilities.getBlockConsistencyGroup() != null
                        && currentCount != capabilities.getResourceCount()) {
                    // can not put all resources to the same IBM XIV pool
                    _log.info("Skip IBM XIV pool {} as it doesn't have enough space to support all the resources", recommendedPool.getId());
                    continue;
                }

                _log.debug("Recommending storage pool {} for {} resources.",
                        recommendedPool.getId(), currentCount);
                Recommendation recommendation = new Recommendation();
                recommendation.setSourceStoragePool(recommendedPool.getId());
                recommendation.setResourceCount(currentCount);
                recommendation.setSourceStorageSystem(recommendedPool.getStorageDevice());
                recommendations.add(recommendation);

                // Update the count of resources for which we have created
                // a recommendation.
                recommendedCount += currentCount;

                // Update the current count. The conditional prevents
                // unnecessary attempts to look for pools of a given
                // free capacity that we already know don't exist. For
                // example, say we want 100 resources and the first pool
                // we find that can hold multiple resources can hold only
                // 10. We don't want to continue looking for pools that
                // can hold 90,89,88,...11 resources. We just want to
                // see if there is another pool that can hold 10 resources,
                // then 9,8, and so on.
                currentCount = (capabilities.getResourceCount() - recommendedCount < currentCount
                        ? capabilities.getResourceCount() - recommendedCount : currentCount);
            } else {
                // If we can't find a pool that can hold the current
                // count of resources, decrease the count so that we look
                // for pools that can hold the next smaller number.
                currentCount--;
            }
        }

        // We need to place all the resources. If we can't then
        // log an error and clear the list of recommendations.
        if (recommendedCount != capabilities.getResourceCount()) {
            recommendations.clear();
        }

        return recommendations;
    }

    /**
     * Used in StoragePool selection.
     */
    static public class StoragePoolFreeCapacityComparator implements Comparator<StoragePool> {
        @Override
        public int compare(StoragePool rhs, StoragePool lhs) {
            int result;

            // if avg port metrics was not computable, consider its usage is max out for sorting purpose
            double rhsAvgPortMetrics = rhs.getAvgStorageDevicePortMetrics() == null ? Double.MAX_VALUE : rhs
                    .getAvgStorageDevicePortMetrics();
            double lhsAvgPortMetrics = lhs.getAvgStorageDevicePortMetrics() == null ? Double.MAX_VALUE : lhs
                    .getAvgStorageDevicePortMetrics();

            if (rhs.getFreeCapacity() > 0 && rhsAvgPortMetrics < lhsAvgPortMetrics) {
                result = -1;
            } else if (rhs.getFreeCapacity() < lhs.getFreeCapacity()) {
                result = -1;
            } else if (rhs.getFreeCapacity() > lhs.getFreeCapacity()) {
                result = 1;
            } else {
                result = 0;
            }
            return result;
        }
    }

    /**
     * Sort all pools in ascending order of its storage system's average port usage metrics (first order),
     * descending order by free capacity (second order) and in ascending order by ratio
     * of pool's subscribed capacity to total capacity(suborder).
     */
    private class StoragePoolDefaultComparator implements Comparator<StoragePool> {
        @Override
        public int compare(StoragePool sp1, StoragePool sp2) {
            int result;

            // if avg port metrics was not computable, consider its usage is max out for sorting purpose
            double sp1AvgPortMetrics = sp1.getAvgStorageDevicePortMetrics() == null || sp1.getAvgStorageDevicePortMetrics() <= 0.0
                    ? Double.MAX_VALUE : sp1.getAvgStorageDevicePortMetrics();
            double sp2AvgPortMetrics = sp2.getAvgStorageDevicePortMetrics() == null || sp2.getAvgStorageDevicePortMetrics() <= 0.0
                    ? Double.MAX_VALUE : sp2.getAvgStorageDevicePortMetrics();

            if (sp1.getFreeCapacity() > 0 && sp1AvgPortMetrics < sp2AvgPortMetrics) {
                result = -1;
            } else if (sp1.getFreeCapacity() > sp2.getFreeCapacity()) {
                result = -1;
            } else if (sp1.getFreeCapacity() < sp2.getFreeCapacity()) {
                result = 1;  // swap
            } else if (sp1.getSubscribedCapacity().doubleValue() / sp1.getTotalCapacity() < sp2.getSubscribedCapacity().doubleValue()
                    / sp2.getTotalCapacity()) {
                result = -1;
            } else if (sp1.getSubscribedCapacity().doubleValue() / sp1.getTotalCapacity() > sp2.getSubscribedCapacity().doubleValue()
                    / sp2.getTotalCapacity()) {
                result = 1;  // swap
            } else {
                result = 0;
            }

            return result;
        }
    }

    /**
     * Create volumes from recommendation object.
     *
     * @param param volume creation parameters
     * @param task task
     * @param taskList task list
     * @param project project
     * @param neighborhood virtual array
     * @param vPool virtual pool
     * @param volumeCount number of volumes to create
     * @param recommendations recommendation structures
     * @param consistencyGroup consistency group to use
     * @param volumeCounter how many volumes are created
     * @param volumeLabel volume label
     * @param preparedVolumes volumes that have been prepared
     * @param cosCapabilities virtual pool wrapper
     * @param createInactive create the device in an inactive state
     */
    public void prepareRecommendedVolumes(VolumeCreate param, String task, TaskList taskList,
            Project project, VirtualArray neighborhood, VirtualPool vPool, Integer volumeCount,
            List<Recommendation> recommendations, BlockConsistencyGroup consistencyGroup, int volumeCounter,
            String volumeLabel, List<Volume> preparedVolumes, VirtualPoolCapabilityValuesWrapper cosCapabilities,
            Boolean createInactive) {
        Iterator<Recommendation> recommendationsIter = recommendations.iterator();
        while (recommendationsIter.hasNext()) {
            VolumeRecommendation recommendation = (VolumeRecommendation) recommendationsIter.next();
            // if id is already set in recommendation, do not prepare the volume (volume already exists)
            if (recommendation.getId() != null) {
                continue;
            }
            // prepare block volume
            if (recommendation.getType().toString().equals(VolumeRecommendation.VolumeType.BLOCK_VOLUME.toString())) {
                String newVolumeLabel = AbstractBlockServiceApiImpl.generateDefaultVolumeLabel(volumeLabel, volumeCounter++, volumeCount);

                // Grab the existing volume and task object from the incoming task list
                Volume volume = getPrecreatedVolume(_dbClient, taskList, newVolumeLabel);
                boolean volumePrecreated = false;
                if (volume != null) {
                    volumePrecreated = true;
                }

                long size = SizeUtil.translateSize(param.getSize());
                long thinVolumePreAllocationSize = 0;
                if (null != vPool.getThinVolumePreAllocationPercentage()) {
                    thinVolumePreAllocationSize = VirtualPoolUtil.getThinVolumePreAllocationSize(
                            vPool.getThinVolumePreAllocationPercentage(), size);
                }

                volume = prepareVolume(_dbClient, volume, size, thinVolumePreAllocationSize, project,
                        neighborhood, vPool, recommendation, newVolumeLabel, consistencyGroup, cosCapabilities, createInactive);
                // set volume id in recommendation
                recommendation.setId(volume.getId());
                // add volume to reserved capacity map of storage pool
                addVolumeCapacityToReservedCapacityMap(_dbClient, volume);

                preparedVolumes.add(volume);

                if (!volumePrecreated) {
                    Operation op = _dbClient.createTaskOpStatus(Volume.class, volume.getId(),
                            task, ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME);
                    volume.getOpStatus().put(task, op);
                    TaskResourceRep volumeTask = toTask(volume, task, op);
                    // This task addition is inconsequential since we've already returned the source volume tasks.
                    // It is good to continue to have a task associated with this volume AND store its status in the volume.
                    taskList.getTaskList().add(volumeTask);
                }

            } else if (recommendation.getType().toString().equals(VolumeRecommendation.VolumeType.BLOCK_LOCAL_MIRROR.toString())) {
                // prepare local mirror based on source volume and storage pool recommendation
                VolumeRecommendation volumeRecommendation = (VolumeRecommendation) recommendation
                        .getParameter(VolumeRecommendation.BLOCK_VOLUME);
                URI volumeId = volumeRecommendation.getId();
                Volume volume = _dbClient.queryObject(Volume.class, volumeId);

                String mirrorLabel = volumeLabel;
                if (volume.isInCG()) {
                    mirrorLabel = ControllerUtils.getMirrorLabel(volume.getLabel(), volumeLabel);
                }
                if (volumeCount > 1) {
                    mirrorLabel = ControllerUtils.getMirrorLabel(mirrorLabel, volumeCounter++);
                }
                // Prepare a single mirror based on source volume and storage pool recommendation
                BlockMirror mirror = initializeMirror(volume, vPool, recommendation.getCandidatePools().get(0),
                        mirrorLabel, _dbClient);

                // set mirror id in recommendation
                recommendation.setId(mirror.getId());
                preparedVolumes.add(mirror);

                // add mirror to reserved capacity map of storage pool
                addVolumeCapacityToReservedCapacityMap(_dbClient, mirror);
            }
        }
    }

    /**
     * Convenience method to return a volume from a task list with a pre-labeled volume number.
     *
     * @param dbClient dbclient
     * @param taskList task list
     * @param label base label
     * @return Volume object
     */
    public static Volume getPrecreatedVolume(DbClient dbClient, TaskList taskList, String label) {
        // The label we've been given has already been appended with the appropriate volume number
        String volumeLabel = AbstractBlockServiceApiImpl.generateDefaultVolumeLabel(label, 0, 1);
        if (taskList == null) {
            return null;
        }

        for (TaskResourceRep task : taskList.getTaskList()) {
            Volume volume = dbClient.queryObject(Volume.class, task.getResource().getId());
            if (volume.getLabel().equalsIgnoreCase(volumeLabel)) {
                return volume;
            }
        }
        return null;
    }

    public static Volume prepareFullCopyVolume(DbClient dbClient, String name, BlockObject sourceVolume,
            VolumeRecommendation recommendation, int volumeCounter,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        return prepareFullCopyVolume(dbClient, name, sourceVolume, recommendation, volumeCounter, capabilities, false);
    }

    public static Volume prepareFullCopyVolume(DbClient dbClient, String name, BlockObject sourceVolume,
            VolumeRecommendation recommendation, int volumeCounter,
            VirtualPoolCapabilityValuesWrapper capabilities,
            Boolean createInactive) {

        if (sourceVolume instanceof BlockSnapshot) {

            return prepareFullCopyVolumeFromSnapshot(dbClient, name, ((BlockSnapshot) sourceVolume), recommendation, volumeCounter,
                    capabilities, createInactive);
        } else {

            return prepareFullCopyVolumeFromVolume(dbClient, name, ((Volume) sourceVolume), recommendation, volumeCounter, capabilities,
                    createInactive);
        }

    }

    private static Volume prepareFullCopyVolumeFromVolume(DbClient dbClient, String name, Volume sourceVolume,
            VolumeRecommendation recommendation, int volumeCounter,
            VirtualPoolCapabilityValuesWrapper capabilities,
            Boolean createInactive) {

        long size = sourceVolume.getCapacity();
        long preAllocateSize = 0;
        if (null != sourceVolume.getThinVolumePreAllocationSize()) {
            preAllocateSize = sourceVolume.getThinVolumePreAllocationSize();
        }
        NamedURI projectUri = sourceVolume.getProject();
        Project project = dbClient.queryObject(Project.class, projectUri);

        URI vArrayUri = sourceVolume.getVirtualArray();
        VirtualArray vArray = dbClient.queryObject(VirtualArray.class, vArrayUri);

        URI vPoolUri = sourceVolume.getVirtualPool();
        VirtualPool vPool = dbClient.queryObject(VirtualPool.class, vPoolUri);

        String label = name + (volumeCounter > 0 ? ("-" + volumeCounter) : "");
        Volume volume = prepareVolume(dbClient, null, size, preAllocateSize,
                project, vArray, vPool, recommendation, label, null, capabilities, createInactive);

        // Since this is a full copy, update it with URI of the source volume
        volume.setAssociatedSourceVolume(sourceVolume.getId());
        StringSet associatedFullCopies = sourceVolume.getFullCopies();
        if (associatedFullCopies == null) {
            associatedFullCopies = new StringSet();
            sourceVolume.setFullCopies(associatedFullCopies);
        }
        associatedFullCopies.add(volume.getId().toString());

        dbClient.persistObject(volume);
        dbClient.persistObject(sourceVolume);

        addVolumeCapacityToReservedCapacityMap(dbClient, volume);
        return volume;

    }

    private static Volume prepareFullCopyVolumeFromSnapshot(DbClient dbClient, String name, BlockSnapshot sourceSnapshot,
            VolumeRecommendation recommendation, int volumeCounter,
            VirtualPoolCapabilityValuesWrapper capabilities,
            Boolean createInactive) {

        // Get the parent of the snapshot, to know vpool
        NamedURI parentVolUri = sourceSnapshot.getParent();
        Volume parentVolume = dbClient.queryObject(Volume.class, parentVolUri);
        URI vPoolUri = parentVolume.getVirtualPool();

        URI projectUri = sourceSnapshot.getProject().getURI();
        long size = sourceSnapshot.getProvisionedCapacity();
        long preAllocateSize = sourceSnapshot.getAllocatedCapacity();

        Project project = dbClient.queryObject(Project.class, projectUri);

        URI vArrayUri = sourceSnapshot.getVirtualArray();
        VirtualArray vArray = dbClient.queryObject(VirtualArray.class, vArrayUri);

        VirtualPool vPool = dbClient.queryObject(VirtualPool.class, vPoolUri);

        String label = name + (volumeCounter > 0 ? ("-" + volumeCounter) : "");
        Volume volume = prepareVolume(dbClient, null, size, preAllocateSize,
                project, vArray, vPool, recommendation, label, null, capabilities, createInactive);

        // Since this is a full copy, update it with URI of the source snapshot
        volume.setAssociatedSourceVolume(sourceSnapshot.getId());

        dbClient.persistObject(volume);
        dbClient.persistObject(sourceSnapshot);

        addVolumeCapacityToReservedCapacityMap(dbClient, volume);
        return volume;

    }

    public static Volume prepareVolume(DbClient dbClient, Volume volume, long size, long thinVolumePreAllocationSize,
            Project project, VirtualArray neighborhood, VirtualPool vpool,
            VolumeRecommendation placement, String label,
            BlockConsistencyGroup consistencyGroup, VirtualPoolCapabilityValuesWrapper capabilities) {
        return prepareVolume(dbClient, volume, size, thinVolumePreAllocationSize, project, neighborhood, vpool,
                placement, label, consistencyGroup, capabilities, false);
    }

    /**
     * Prepare a new volume object in the database that can be tracked and overridden as the volume goes through the
     * placement process.
     *
     * @param dbClient dbclient
     * @param size size of volume
     * @param project project
     * @param varray virtual array
     * @param vpool virtual pool
     * @param label base volume label
     * @param volNumber a temporary label for this volume to mark which one it is
     * @param volumesRequested how many volumes were requested overall
     * @return a Volume object
     */
    public static Volume prepareEmptyVolume(DbClient dbClient, long size, Project project, VirtualArray varray, VirtualPool vpool,
            String label, int volNumber, int volumesRequested) {

        Volume volume = new Volume();
        volume.setId(URIUtil.createId(Volume.class));
        String volumeLabel = AbstractBlockServiceApiImpl.generateDefaultVolumeLabel(label, volNumber, volumesRequested);

        List<Volume> volumeList = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, Volume.class,
                ContainmentPrefixConstraint.Factory.getFullMatchConstraint(Volume.class, "project",
                        project.getId(), volumeLabel));
        if (!volumeList.isEmpty()) {
            throw APIException.badRequests.duplicateLabel(volumeLabel);
        }

        volume.setLabel(volumeLabel);
        volume.setCapacity(size);
        volume.setThinlyProvisioned(VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(vpool.getSupportedProvisioningType()));
        volume.setVirtualPool(vpool.getId());
        volume.setProject(new NamedURI(project.getId(), volume.getLabel()));
        volume.setTenant(new NamedURI(project.getTenantOrg().getURI(), volume.getLabel()));
        volume.setVirtualArray(varray.getId());
        volume.setOpStatus(new OpStatusMap());
        dbClient.createObject(volume);
        return volume;
    }

    /**
     * Prepare Volume for an unprotected traditional block volume.
     *
     * @param volume pre-created volume (optional)
     * @param size volume size
     * @param project project requested
     * @param neighborhood varray requested
     * @param vpool vpool requested
     * @param placement recommendation for placement
     * @param label volume label
     * @param consistencyGroup cg ID
     * @param createInactive
     *
     *
     * @return a persisted volume
     */
    public static Volume prepareVolume(DbClient dbClient, Volume volume, long size, long thinVolumePreAllocationSize,
            Project project, VirtualArray neighborhood, VirtualPool vpool,
            VolumeRecommendation placement, String label,
            BlockConsistencyGroup consistencyGroup, VirtualPoolCapabilityValuesWrapper cosCapabilities, Boolean createInactive) {

        // In the case of a new volume that wasn't pre-created, make sure that volume doesn't already exist
        if (volume == null) {
            List<Volume> volumeList = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, Volume.class,
                    ContainmentPrefixConstraint.Factory.getFullMatchConstraint(Volume.class, "project",
                            project.getId(), label));
            if (!volumeList.isEmpty()) {
                throw APIException.badRequests.duplicateLabel(label);
            }
        }

        boolean newVolume = false;
        StoragePool pool = null;
        if (volume == null) {
            newVolume = true;
            volume = new Volume();
            volume.setId(URIUtil.createId(Volume.class));
            volume.setOpStatus(new OpStatusMap());
        } else {
            // Reload volume object from DB
            volume = dbClient.queryObject(Volume.class, volume.getId());
        }

        volume.setSyncActive(!Boolean.valueOf(createInactive));
        volume.setLabel(label);
        volume.setCapacity(size);
        if (0 != thinVolumePreAllocationSize) {
            volume.setThinVolumePreAllocationSize(thinVolumePreAllocationSize);
        }
        volume.setThinlyProvisioned(VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(vpool.getSupportedProvisioningType()));
        volume.setVirtualPool(vpool.getId());
        volume.setProject(new NamedURI(project.getId(), volume.getLabel()));
        volume.setTenant(new NamedURI(project.getTenantOrg().getURI(), volume.getLabel()));
        volume.setVirtualArray(neighborhood.getId());
        URI poolId = placement.getCandidatePools().get(0);
        if (null != poolId) {
            pool = dbClient.queryObject(StoragePool.class, poolId);
            if (null != pool) {
                volume.setProtocol(new StringSet());
                volume.getProtocol().addAll(
                        VirtualPoolUtil.getMatchingProtocols(vpool.getProtocols(), pool.getProtocols()));
            }
        }
        volume.setStorageController(placement.getCandidateSystems().get(0));
        volume.setPool(poolId);
        if (consistencyGroup != null) {
            volume.setConsistencyGroup(consistencyGroup.getId());
            if (!consistencyGroup.isProtectedCG()) {
                volume.setReplicationGroupInstance(consistencyGroup.getLabel());

                // if other volumes in the same CG are in an application, add this volume to the same application
                VolumeGroup volumeGroup = ControllerUtils.getApplicationForCG(dbClient, consistencyGroup, volume.getReplicationGroupInstance());
                if (volumeGroup != null) {
                    volume.getVolumeGroupIds().add(volumeGroup.getId().toString());
                }
            }
        }

        if (null != cosCapabilities.getAutoTierPolicyName()) {
            URI autoTierPolicyUri = getAutoTierPolicy(poolId,
                    cosCapabilities.getAutoTierPolicyName(), dbClient);
            if (null != autoTierPolicyUri) {
                volume.setAutoTieringPolicyUri(autoTierPolicyUri);
            }
        }

        if (newVolume) {
            dbClient.createObject(volume);
        } else {
            dbClient.updateAndReindexObject(volume);
        }

        return volume;
    }

    /**
     * Get the AutoTierPolicy URI for a given StoragePool and auto tier policy name.
     *
     * @param pool
     *            -- Storage Pool URI
     * @param policyName
     *            -- Policy name
     * @param dbClient
     * @return URI of AutoTierPolicy, null if not found
     */
    public static URI getAutoTierPolicy(URI pool, String policyName, DbClient dbClient) {
        URIQueryResultList result = new URIQueryResultList();
        // check if pool fast policy name is not
        dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getFASTPolicyByNameConstraint(policyName),
                result);
        Iterator<URI> iterator = result.iterator();
        while (iterator.hasNext()) {
            AutoTieringPolicy policy = dbClient.queryObject(AutoTieringPolicy.class,
                    iterator.next());
            if (null == policy.getStorageSystem()) {
                continue;
            }
            // pool's storage system
            // Note that the pool can be null when the function is called while
            // preparing a VPLEX volume, which does not have a storage pool.
            StoragePool poolObj = dbClient.queryObject(StoragePool.class, pool);
            if ((poolObj != null) &&
                    (policy.getStorageSystem().toString().equalsIgnoreCase(poolObj.getStorageDevice().toString()))) {
                return policy.getId();
            }
        }
        return null;
    }

    /**
     * Adds a BlockMirror structure for a Volume. It also calls addMirrorToVolume to
     * link the mirror into the volume's mirror set.
     *
     * @param volume Volume
     * @param vPool
     * @param recommendedPoolURI Pool that should be used to create the mirror
     * @param volumeLabel
     * @param dbClient
     * @return BlockMirror (persisted)
     */
    public static BlockMirror initializeMirror(Volume volume, VirtualPool vPool, URI recommendedPoolURI,
            String volumeLabel, DbClient dbClient) {
        BlockMirror createdMirror = new BlockMirror();
        createdMirror.setSource(new NamedURI(volume.getId(), volume.getLabel()));
        createdMirror.setId(URIUtil.createId(BlockMirror.class));
        URI cgUri = volume.getConsistencyGroup();
        if (!NullColumnValueGetter.isNullURI(cgUri)) {
            createdMirror.setConsistencyGroup(cgUri);
        }
        createdMirror.setLabel(volumeLabel);
        createdMirror.setStorageController(volume.getStorageController());
        createdMirror.setVirtualArray(volume.getVirtualArray());
        // Setting the source Volume autoTieringPolicy in Mirror.
        // @TODO we must accept the policy as an input for mirrors and requires API changes.
        // Hence for timebeing, we are setting the source policy in mirror.
        if (!NullColumnValueGetter.isNullURI(volume.getAutoTieringPolicyUri())) {
            createdMirror.setAutoTieringPolicyUri(volume.getAutoTieringPolicyUri());
        }
        createdMirror.setProtocol(new StringSet());
        createdMirror.getProtocol().addAll(volume.getProtocol());
        createdMirror.setCapacity(volume.getCapacity());
        createdMirror.setProject(new NamedURI(volume.getProject().getURI(), createdMirror.getLabel()));
        createdMirror.setTenant(new NamedURI(volume.getTenant().getURI(), createdMirror.getLabel()));
        createdMirror.setPool(recommendedPoolURI);
        createdMirror.setVirtualPool(vPool.getId());
        createdMirror.setSyncState(SynchronizationState.UNKNOWN.toString());
        createdMirror.setSyncType(BlockMirror.MIRROR_SYNC_TYPE);
        createdMirror.setThinlyProvisioned(volume.getThinlyProvisioned());
        dbClient.createObject(createdMirror);
        addMirrorToVolume(volume, createdMirror, dbClient);
        return createdMirror;
    }

    public static BlockMirror initializeMirror(Volume volume, VirtualPool vPool, URI recommendedPoolURI,
            DbClient dbClient) {
        return initializeMirror(volume, vPool, recommendedPoolURI, volume.getLabel(), dbClient);
    }

    /**
     * Adds a Mirror structure to a Volume's mirror set.
     *
     * @param volume
     * @param mirror
     */
    private static void addMirrorToVolume(Volume volume, BlockMirror mirror, DbClient dbClient) {
        StringSet mirrors = volume.getMirrors();
        if (mirrors == null) {
            mirrors = new StringSet();
        }
        mirrors.add(mirror.getId().toString());
        volume.setMirrors(mirrors);
        // Persist changes
        dbClient.persistObject(volume);
    }

    public static void addVolumeCapacityToReservedCapacityMap(DbClient _dbClient, Volume volume) {
        Long reservedCapacity = 0L;
        // For thin volumes reserve only capacity required for pre-allocation (when set)
        if (volume.getThinlyProvisioned() && volume.getThinVolumePreAllocationSize() != null) {
            reservedCapacity = volume.getThinVolumePreAllocationSize();
        } else if (!volume.getThinlyProvisioned()) {
            reservedCapacity = volume.getCapacity();
        }

        URI poolId = volume.getPool();
        StoragePool pool = _dbClient.queryObject(StoragePool.class, poolId);
        StringMap reservationMap = pool.getReservedCapacityMap();
        reservationMap.put(volume.getId().toString(), String.valueOf(reservedCapacity));
        _dbClient.persistObject(pool);

    }

    public static void addVolumeExpansionSizeToReservedCapacityMap(DbClient _dbClient, Volume volume, long expandCapacity) {
        Long reservedCapacity = 0L;
        // Add reservation for capacity to storage pool's reservation map.
        // Need to account for expand capacity only for thick provisioning. Expand of thin volumes do not account
        // for free capacity.
        if (!volume.getThinlyProvisioned() && expandCapacity > 0) {
            reservedCapacity = expandCapacity;
        }

        URI poolId = volume.getPool();
        StoragePool pool = _dbClient.queryObject(StoragePool.class, poolId);
        StringMap reservationMap = pool.getReservedCapacityMap();
        reservationMap.put(volume.getId().toString(), String.valueOf(reservedCapacity));
        _dbClient.persistObject(pool);
    }

}
