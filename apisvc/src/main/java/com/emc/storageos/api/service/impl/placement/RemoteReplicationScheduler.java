/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;


import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.remotereplicationcontroller.RemoteReplicationUtils;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class RemoteReplicationScheduler implements Scheduler {

    public static final Logger _log = LoggerFactory.getLogger(RemoteReplicationScheduler.class);
    public static final String CG_NAME_FORMAT = "%s-TARGET-%s";
    @Autowired
    protected PermissionsHelper _permissionsHelper = null;

    private DbClient _dbClient;
    private StorageScheduler _blockScheduler;
    private CoordinatorClient _coordinator;

    public void setBlockScheduler(final StorageScheduler blockScheduler) {
        _blockScheduler = blockScheduler;
    }

    public void setDbClient(final DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    public CoordinatorClient getCoordinator() {
        return _coordinator;
    }

    @Override
    public List getRecommendationsForResources(VirtualArray vArray, Project project, VirtualPool vPool, VirtualPoolCapabilityValuesWrapper capabilities) {
        /**
         * 1. We call basic storage scheduler to get matching storage pools for source volumes (based on source storage systems and
         * based on source virtual array and source virtual pool)
         *
         * 2. We call basic storage scheduler to get matching storage pools for target volumes (based on target storage system and
         * based on target varray and optionally target virtual pool)
         *
         * 3. Build recommendations for source and target storage volumes based on sets of matching source and target storage pools.
         */
        _log.info("Schedule storage for {} resource(s) of size {}.",
                capabilities.getResourceCount(), capabilities.getSize());
        Map<String, Object> attributeMap = new HashMap<>();

        Set<String> sourceStorageSystems = new HashSet<>();
        Set<String> targetStorageSystems = new HashSet<>();
        // Get all storage pools which can be used for source volumes

        // Get source and target storage systems from remote replication configuration
        if (capabilities.getRemoteReplicationGroup() != null) {
            RemoteReplicationGroup rrGroup = _dbClient.queryObject(RemoteReplicationGroup.class, capabilities.getRemoteReplicationGroup());
            sourceStorageSystems.add(rrGroup.getSourceSystem().toString());
            targetStorageSystems.add(rrGroup.getTargetSystem().toString());
        } else if (capabilities.getRemoteReplicationSet() != null) {
            RemoteReplicationSet rrSet = _dbClient.queryObject(RemoteReplicationSet.class, capabilities.getRemoteReplicationSet());
            sourceStorageSystems = rrSet.getSourceSystems();
            targetStorageSystems = rrSet.getTargetSystems();
        } else {
            throw APIException.badRequests.invalidRemoteReplicationProvisioningRequest("RemoteReplicationSet is not specified in the request");

        }

        // Get all storage pools for source volumes that match set of source storage systems and also match
        // passed vpool params and protocols. In addition, the pool must have enough capacity
        // to hold at least one resource of the requested size.

        // if cg is specified, check that storage system in cg is part of source systems
        final BlockConsistencyGroup consistencyGroup = capabilities.getBlockConsistencyGroup() == null ? null : _dbClient
                .queryObject(BlockConsistencyGroup.class, capabilities.getBlockConsistencyGroup());
        if (consistencyGroup != null) {
            URI cgStorageSystemURI = consistencyGroup.getStorageController();
            if (!NullColumnValueGetter.isNullURI(cgStorageSystemURI) && !sourceStorageSystems.contains(cgStorageSystemURI.toString())) {
                // error no pools for source
                String errorMessage = String.format("Consistency group storage system %s does not belong to remote replication source source storage systems: %s ",
                        cgStorageSystemURI, sourceStorageSystems);
                throw APIException.badRequests.noStoragePools(vArray.getLabel(), vPool.getLabel(), errorMessage);
            }
        }

        attributeMap.put(AttributeMatcher.Attributes.storage_system.name(), sourceStorageSystems);
        // set multi-volume consistency
        if (capabilities.getBlockConsistencyGroup() != null) {
            attributeMap.put(AttributeMatcher.Attributes.multi_volume_consistency.name(), true);
        }
        List<StoragePool> sourcePools = _blockScheduler.getMatchingPools(vArray, vPool, capabilities, attributeMap);

        if (sourcePools == null || sourcePools.isEmpty()) {
            _log.error(
                    "No matching storage pools found for the source side of remotely replicated volumes. /n" +
                            "Source storage systems: {0} . /n" +
                            "There are no storage pools that "
                            + "match the passed vpool parameters and protocols and/or there are no pools that have enough capacity to "
                            + "hold at least one resource of the requested size.",
                    sourceStorageSystems);
            StringBuffer errorMessage = new StringBuffer();
            if (attributeMap.get(AttributeMatcher.ERROR_MESSAGE) != null) {
                errorMessage = (StringBuffer) attributeMap.get(AttributeMatcher.ERROR_MESSAGE);
            }
            throw APIException.badRequests.noStoragePools(vArray.getLabel(), vPool.getLabel(),
                    errorMessage.toString());
        }

        // Get all storage pools for target volumes that match set of target storage systems
        // and also match passed target vpool params and
        // protocols. In addition, the pool must have enough capacity
        // to hold at least one resource of the requested size.
        List<StoragePool> targetPools = new ArrayList<>();
        VirtualPool targetVirtualPool = RemoteReplicationUtils.getTargetVPool(vPool, _dbClient);
        VirtualArray targetVirtualArray  = RemoteReplicationUtils.getTargetVArray(vPool, _dbClient);
        VirtualPoolCapabilityValuesWrapper targetCapabilities = buildTargetCapabilities(targetVirtualArray, targetVirtualPool, capabilities, _dbClient);

        // if cg is specified, check that storage system in cg is part of target systems
        final BlockConsistencyGroup targetConsistencyGroup = targetCapabilities.getBlockConsistencyGroup() == null ? null : _dbClient
                .queryObject(BlockConsistencyGroup.class, targetCapabilities.getBlockConsistencyGroup());
        if (targetConsistencyGroup != null) {
            URI targetCgStorageSystemURI = targetConsistencyGroup.getStorageController();
            if (!NullColumnValueGetter.isNullURI(targetCgStorageSystemURI) && !targetStorageSystems.contains(targetCgStorageSystemURI.toString())) {
                // error no pools for target
                String errorMessage = String.format("Target consistency group storage system %s does not belong to remote replication target storage systems: %s ",
                        targetCgStorageSystemURI, targetStorageSystems);
                throw APIException.badRequests.noStoragePools(targetVirtualArray.getLabel(), targetVirtualPool.getLabel(), errorMessage);
            }
        }
        attributeMap.put(AttributeMatcher.Attributes.storage_system.name(), targetStorageSystems);
        List<StoragePool> targetPoolsForTargetVArray = _blockScheduler.getMatchingPools(targetVirtualArray, targetVirtualPool, targetCapabilities, attributeMap);
        addNewPools(targetPools, targetPoolsForTargetVArray);


        if (targetPools.isEmpty()) {
            _log.error(
                    "No matching storage pools found for the target side of remotely replicated volumes. /n" +
                            "Target storage systems: {0} . /n" +
                            "There are no storage pools that "
                            + "match the passed vpool parameters and protocols and/or there are no pools that have enough capacity to "
                            + "hold at least one resource of the requested size.",
                    targetStorageSystems);
            StringBuffer errorMessage = new StringBuffer();
            if (attributeMap.get(AttributeMatcher.ERROR_MESSAGE) != null) {
                errorMessage = (StringBuffer) attributeMap.get(AttributeMatcher.ERROR_MESSAGE);
            }
            throw APIException.badRequests.noStoragePools(vArray.getLabel(), vPool.getLabel(), errorMessage.toString());
        }

        // list of recommendations for all volumes
        List<Recommendation> volumeRecommendations = new ArrayList<>();
        // We have candidate source and target storage pools now. Get recommendations for the source and target volumes.
        List<Recommendation> sourceRecommendationsForPools = _blockScheduler.getRecommendationsForPools(vArray.getId().toString(), sourcePools, capabilities);
        if (sourceRecommendationsForPools.isEmpty()) {
            String msg = String.format(
                    "Could not find matching source pools for VArray %s & VPool %s",
                    vArray.getId(), vPool.getId());
            _log.error(msg);
            throw APIException.badRequests.noStoragePools(vArray.getLabel(), vPool.getLabel(), msg);
        }

        List<Recommendation> targetRecommendationsForPools = _blockScheduler.getRecommendationsForPools(targetVirtualArray.getId().toString(),
                targetPools, targetCapabilities);
        if (targetRecommendationsForPools.isEmpty()) {
            String msg = String.format(
                    "Could not build recommendations for target volumes for source vpool %s", vPool.getLabel());
            _log.error(msg);
            throw APIException.badRequests.noStoragePools(vArray.getLabel(), vPool.getLabel(), msg);
        }

        // Build recommendation for each volume
        // Create recommendation for each source volume
        List<VolumeRecommendation> sourceVolumeRecommendations = new ArrayList<>();
        for (Recommendation recommendation : sourceRecommendationsForPools) {
            int count = recommendation.getResourceCount();
            while (count > 0) {
                VolumeRecommendation volumeRecommendation = new VolumeRecommendation(VolumeRecommendation.VolumeType.BLOCK_VOLUME,
                        capabilities.getSize(), vPool, vArray.getId());
                volumeRecommendation.setSourceStoragePool(recommendation.getSourceStoragePool());
                volumeRecommendation.setSourceStorageSystem(recommendation.getSourceStorageSystem());
                volumeRecommendation.setVirtualArray(vArray.getId());
                volumeRecommendation.setVirtualPool(vPool);
                volumeRecommendation.setResourceCount(1);
                volumeRecommendation.addStoragePool(recommendation.getSourceStoragePool());
                volumeRecommendation.addStorageSystem(recommendation.getSourceStorageSystem());
                sourceVolumeRecommendations.add(volumeRecommendation);
                if (capabilities.getBlockConsistencyGroup() != null) {
                    volumeRecommendation.setParameter(VolumeRecommendation.ARRAY_CG, capabilities.getBlockConsistencyGroup());
                }
                count--;
            }
        }
        _log.info("Recommendations for source RR volumes: {}", sourceVolumeRecommendations);

        // Create recommendation for each target volume. Use source volume recommendations as underlying recommendations.
        int sourceVolumeRecommendationIndex = 0;
        for (Recommendation targetRecommendation : targetRecommendationsForPools) {
            int count = targetRecommendation.getResourceCount();
            while (count > 0) {
                VolumeRecommendation volumeRecommendation = new VolumeRecommendation(VolumeRecommendation.VolumeType.BLOCK_VOLUME,
                        targetCapabilities.getSize(), targetVirtualPool, targetVirtualArray.getId());
                volumeRecommendation.setSourceStoragePool(targetRecommendation.getSourceStoragePool());
                volumeRecommendation.setSourceStorageSystem(targetRecommendation.getSourceStorageSystem());
                volumeRecommendation.setVirtualArray(targetVirtualArray.getId());
                volumeRecommendation.setVirtualPool(targetVirtualPool);
                volumeRecommendation.setResourceCount(1);
                volumeRecommendation.addStoragePool(targetRecommendation.getSourceStoragePool());
                volumeRecommendation.addStorageSystem(targetRecommendation.getSourceStorageSystem());
                if (targetCapabilities.getBlockConsistencyGroup() != null) {
                    volumeRecommendation.setParameter(VolumeRecommendation.ARRAY_CG, targetCapabilities.getBlockConsistencyGroup());
                }
                // set source volume recommendation as underlying recommendation
                volumeRecommendation.setRecommendation(sourceVolumeRecommendations.get(sourceVolumeRecommendationIndex++));
                volumeRecommendations.add(volumeRecommendation);
                count--;
            }
        }

        _log.info("Recommendations for target RR volumes: {}", volumeRecommendations);

        return volumeRecommendations;
    }

    /**
     * Adds new pools to the current pools.
     *
     * @param currentPools
     * @param newPools
     */
    private void addNewPools(List<StoragePool> currentPools, List<StoragePool> newPools) {
        Set<URI> poolIdSet= new HashSet<>();

        for (StoragePool pool : currentPools) {
            poolIdSet.add(pool.getId());
        }
        for (StoragePool pool : newPools) {
            if (!poolIdSet.contains(pool.getId())) {
                currentPools.add(pool);
            }
        }
    }

    /**
     * Builds capabilities for target virtual pool based on source capabilities
     *
     * @param targetVirtualPool target vpool for target volumes
     * @param sourceCapabilities capabilities built based on source vpool
     * @return capabilities based on target vpool
     */
    public static VirtualPoolCapabilityValuesWrapper  buildTargetCapabilities(VirtualArray targetVirtualArray, VirtualPool targetVirtualPool,
                                                                        VirtualPoolCapabilityValuesWrapper sourceCapabilities, DbClient dbClient) {
        VirtualPoolCapabilityValuesWrapper targetCapabilities = new VirtualPoolCapabilityValuesWrapper(sourceCapabilities);

        Long volumeSize = sourceCapabilities.getSize();
        if (null != targetVirtualPool.getThinVolumePreAllocationPercentage()
                && 0 < targetVirtualPool.getThinVolumePreAllocationPercentage()) {
            targetCapabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_VOLUME_PRE_ALLOCATE_SIZE, VirtualPoolUtil
                    .getThinVolumePreAllocationSize(targetVirtualPool.getThinVolumePreAllocationPercentage(), volumeSize));
        }

        if (VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(
                targetVirtualPool.getSupportedProvisioningType())) {
            targetCapabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.TRUE);
        }

        // Does vpool supports dedup
        if (null != targetVirtualPool.getDedupCapable() && targetVirtualPool.getDedupCapable()) {
            targetCapabilities.put(VirtualPoolCapabilityValuesWrapper.DEDUP, Boolean.TRUE);
        }

        // Check if we need to set consistency group for target volumes in the target capabilities
        URI sourceCGURI = sourceCapabilities.getBlockConsistencyGroup();
        if (!URIUtil.isNull(sourceCGURI) && URIUtil.isValid(sourceCGURI)) {
            BlockConsistencyGroup sourceCG = dbClient.queryObject(BlockConsistencyGroup.class, sourceCGURI);

            // Generate the target BlockConsistencyGroup name
            String targetCGName = String.format(CG_NAME_FORMAT, sourceCG.getLabel(), targetVirtualArray.getLabel());

            // Check for existing target group
            List<BlockConsistencyGroup> groups = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient,
                    BlockConsistencyGroup.class, PrefixConstraint.Factory
                            .getFullMatchConstraint(BlockConsistencyGroup.class, "label", targetCGName));
            BlockConsistencyGroup targetCG = null;
            if (!groups.isEmpty()) {
                targetCG = groups.get(0);
                _log.info("Using existing target consistency group: {}", targetCG.getLabel());
            } else {
                _log.info("Creating target consistency group: {}", targetCGName);
                Project project = dbClient.queryObject(Project.class, sourceCG.getProject().getURI());
                // create CG
                targetCG = new BlockConsistencyGroup();
                targetCG.setId(URIUtil.createId(BlockConsistencyGroup.class));
                targetCG.setLabel(targetCGName );
                targetCG.setProject(sourceCG.getProject());
                targetCG.setTenant(new NamedURI(project.getTenantOrg().getURI(),
                        project.getTenantOrg().getName()));
                targetCG.setAlternateLabel(sourceCG.getLabel());
                dbClient.createObject(targetCG);
            }
            targetCapabilities.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, targetCG.getId());
        }

        return targetCapabilities;
    }

    @Override
    public String getSchedulerName() {
        return null;
    }

    @Override
    public boolean handlesVpool(VirtualPool vPool, VpoolUse vPoolUse) {
        return (VirtualPool.vPoolSpecifiesRemoteReplication(vPool));
    }


    @Override
    public List<Recommendation> getRecommendationsForVpool(VirtualArray vArray, Project project, VirtualPool vPool, VpoolUse vPoolUse,
                                                           VirtualPoolCapabilityValuesWrapper capabilities, Map<VpoolUse, List<Recommendation>> currentRecommendations) {

        return getRecommendationsForResources(vArray, project, vPool, capabilities);
    }
}
