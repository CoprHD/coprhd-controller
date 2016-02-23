/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.PlacementManager.SchedulerType;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.RemoteDirectorGroup.SupportedCopyModes;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.model.block.VirtualPoolChangeParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.SRDFCopyRecommendation;
import com.emc.storageos.volumecontroller.SRDFRecommendation;
import com.emc.storageos.volumecontroller.SRDFRecommendation.Target;
import com.emc.storageos.volumecontroller.impl.smis.MetaVolumeRecommendation;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;
import com.emc.storageos.volumecontroller.impl.utils.MetaVolumeUtils;
import com.emc.storageos.volumecontroller.impl.utils.ObjectLocalCache;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.volumecontroller.impl.utils.attrmatchers.SRDFMetroMatcher;

/**
 * Advanced SRDF based scheduling function for block storage. StorageScheduler is done based on
 * desired class-of-service parameters for the provisioned storage.
 */
public class SRDFScheduler implements Scheduler {

    /**
     * A valid combination of
     * "given this storage system, this pool can be SRDF linked against this pool" object.
     */
    public class SRDFPoolMapping implements Comparable<SRDFPoolMapping> {
        // Source varray
        public VirtualArray sourceVarray;
        // Source storage pool allowed
        public StoragePool sourceStoragePool;
        // Destination varray
        public VirtualArray destVarray;
        // Destination storage pool allowed
        public StoragePool destStoragePool;

        @Override
        public int compareTo(final SRDFPoolMapping ppMapCompare) {
            int res = this.sourceVarray.getId().compareTo(ppMapCompare.sourceVarray.getId());

            if (res != 0) {
                return res;
            }

            res = this.sourceStoragePool.getId().compareTo(ppMapCompare.sourceStoragePool.getId());

            if (res != 0) {
                return res;
            }

            res = this.destVarray.getId().compareTo(ppMapCompare.destVarray.getId());

            if (res != 0) {
                return res;
            }

            return this.destStoragePool.getId().compareTo(ppMapCompare.destStoragePool.getId());
        }

        @Override
        public String toString() {
            return "SRDFPoolMapping [sourceVarray=" + sourceVarray.getLabel()
                    + ", sourceStoragePool=" + sourceStoragePool.getLabel() + ", destVarray="
                    + destVarray.getLabel() + ", destStoragePool=" + destStoragePool.getLabel()
                    + "]";
        }
    }

    private static final int BYTESCONVERTER = 1024;
    public static final Logger _log = LoggerFactory.getLogger(SRDFScheduler.class);
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

    /**
     * Gets and verifies that the target varrays passed in the request are accessible to the tenant.
     * 
     * @param project
     *            A reference to the project.
     * @param vpool
     *            class of service, contains target varrays
     * @return A reference to the varrays
     * @throws java.net.URISyntaxException
     * @throws com.emc.storageos.db.exceptions.DatabaseException
     */
    static public List<VirtualArray> getTargetVirtualArraysForVirtualPool(final Project project,
            final VirtualPool vpool, final DbClient dbClient,
            final PermissionsHelper permissionHelper) {
        List<VirtualArray> targetVirtualArrays = new ArrayList<VirtualArray>();
        if (VirtualPool.getRemoteProtectionSettings(vpool, dbClient) != null) {
            for (URI targetVirtualArray : VirtualPool.getRemoteProtectionSettings(vpool, dbClient)
                    .keySet()) {
                VirtualArray nh = dbClient.queryObject(VirtualArray.class, targetVirtualArray);
                targetVirtualArrays.add(nh);
                permissionHelper.checkTenantHasAccessToVirtualArray(
                        project.getTenantOrg().getURI(), nh);
            }
        }
        return targetVirtualArrays;
    }

    /**
     * Select and return one or more storage pools where the volume(s) should be created. The
     * placement logic is based on: - varray, only storage devices in the given varray are
     * candidates - destination varrays - vpool, specifies must-meet & best-meet service
     * specifications - access-protocols: storage pools must support all protocols specified in
     * vpool - snapshots: if yes, only select storage pools with this capability -
     * snapshot-consistency: if yes, only select storage pools with this capability - performance:
     * best-match, select storage pools which meets desired performance - provision-mode: thick/thin
     * - numPaths: select storage pools with required number of paths to the volume - size: Place
     * the resources in the minimum number of storage pools that can accommodate the size and number
     * of resource requested.
     * 
     * @param varray
     *            varray requested for source
     * @param project
     *            for the storage
     * @param vpool
     *            vpool requested
     * @param capabilities
     *            vpool capabilities parameters
     * @return list of Recommendation objects to satisfy the request
     */
    @Override
    public List<Recommendation> getRecommendationsForResources(final VirtualArray varray,
            final Project project, final VirtualPool vpool,
            final VirtualPoolCapabilityValuesWrapper capabilities) {

        _log.debug("Schedule storage for {} resource(s) of size {}.",
                capabilities.getResourceCount(), capabilities.getSize());

        capabilities.put(VirtualPoolCapabilityValuesWrapper.PERSONALITY, VirtualPoolCapabilityValuesWrapper.SRDF_SOURCE);
        // Get all storage pools that match the passed vpool params and
        // protocols. In addition, the pool must have enough capacity
        // to hold at least one resource of the requested size.
        List<StoragePool> pools = _blockScheduler.getMatchingPools(varray, vpool, capabilities);

        if (pools == null || pools.isEmpty()) {
            _log.error(
                    "No matching storage pools found for the source varray: {0}. There are no storage pools that "
                            + "match the passed vpool parameters and protocols and/or there are no pools that have enough capacity to "
                            + "hold at least one resource of the requested size.",
                    varray.getLabel());
            throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(vpool.getLabel(),
                    varray.getLabel());
        }

        // skip StoragePools, which had been used as R2 targets for given consistencyGroup earlier.
        // If we don't skip then for same CG, then our existing R2 targets will act as source
        List<StoragePool> candidatePools = new ArrayList<StoragePool>();
        if (VirtualPool.vPoolSpecifiesSRDF(vpool)
                && null != capabilities.getBlockConsistencyGroup()) {
            for (StoragePool pool : pools) {
                URI systemUri = pool.getStorageDevice();
                if (null == systemUri) {
                    continue;
                }
                StorageSystem system = _dbClient.queryObject(StorageSystem.class, systemUri);
                if (null != system.getTargetCgs()
                        && system.getTargetCgs().contains(
                                capabilities.getBlockConsistencyGroup().toString())) {
                    _log.info(
                            "Storage System {} is used as target R2 for given CG earlier, hence cannot be used as source R1 again for the same CG.",
                            system.getNativeGuid());
                } else {
                    candidatePools.add(pool);
                }
            }
        } else {
            candidatePools.addAll(pools);
        }
        // Schedule storage based on the source pool constraint.
        return scheduleStorageSourcePoolConstraint(varray, project, vpool, capabilities,
                candidatePools, null, capabilities.getBlockConsistencyGroup());
    }

    private List<StoragePool> filterPoolsForSupportedActiveModeProvider(List<StoragePool> candidatePools, VirtualPool vpool) {
        Map<URI, VpoolRemoteCopyProtectionSettings> remoteProtectionSettings = vpool.getRemoteProtectionSettings(vpool, _dbClient);
        if (remoteProtectionSettings != null) {
            for (URI varrayURI : remoteProtectionSettings.keySet()) {
                VpoolRemoteCopyProtectionSettings remoteCopyProtectionSettings = remoteProtectionSettings.get(varrayURI);
                String copyMode = remoteCopyProtectionSettings.getCopyMode();
                if (copyMode.equals(SupportedCopyModes.ACTIVE.toString())) {
                    SRDFMetroMatcher srdfMetroMatcher = new SRDFMetroMatcher();
                    srdfMetroMatcher.setCoordinatorClient(_coordinator);
                    srdfMetroMatcher.setObjectCache(new ObjectLocalCache(_dbClient, false));
                    return srdfMetroMatcher.filterPoolsForSRDFActiveMode(candidatePools);
                }
            }
        }
        return candidatePools;
    }

    /**
     * Schedule storage based on the incoming storage pools for source volumes. Find a source
     * storage pool that can provide a source volume that satisfies the vpool's criteria for all
     * targets varrays required and build a recommendation structure to describe the findings.
     * 
     * Strategy:
     * 
     * 0. When we come into method, we already have a list of candidate source pools, which may be
     * in multiple arrays 1. Get matching pools for each of the target virtual arrays based on the
     * target virtual pool. 2. Make a map of virtual array to potential pools in step 1. 3. Find a
     * pool from each entry in the map that belongs to a storage system that is connected via SRDF
     * (with the same policy) to the specific candidate pool we're looking at. 4. Generate an SRDF
     * Recommendation object that reflects the combination we found.
     * 
     * @param varray
     *            varray requested for source
     * @param srdfVarrays
     *            Varray to protect this volume to.
     * @param vpool
     *            vpool requested
     * @param capabilities
     *            parameters
     * @param candidatePools
     *            candidate pools to use for source
     * @param vpoolChangeVolume
     *            vpool change volume, if applicable
     * @return list of Recommendation objects to satisfy the request
     */
    private List<Recommendation> scheduleStorageSourcePoolConstraint(final VirtualArray varray,
            final Project project, final VirtualPool vpool,
            final VirtualPoolCapabilityValuesWrapper capabilities,
            final List<StoragePool> candidatePools, final Volume vpoolChangeVolume,
            final URI consistencyGroupUri) {
        // Initialize a list of recommendations to be returned.
        List<Recommendation> recommendations = new ArrayList<Recommendation>();

        if (capabilities.getResourceCount() == 1) {
            // For single resource request, select storage pool randomly from all candidate pools
            // (to minimize collisions).
            Collections.shuffle(candidatePools);
        } else {
            // Sort all pools in descending order by free capacity (first order) and in ascending
            // order by ratio
            // of pool's subscribed capacity to total capacity(suborder). This order is kept through
            // the selection procedure.
            _blockScheduler.sortPools(candidatePools);
        }

        List<VirtualArray> targetVarrays = getTargetVirtualArraysForVirtualPool(project, vpool,
                _dbClient, _permissionsHelper);

        // Attempt to use these pools for selection based on target
        StringBuffer sb = new StringBuffer("Determining if SRDF is possible from " + varray.getId()
                + " to: ");
        for (VirtualArray targetVarray : targetVarrays) {
            sb.append(targetVarray.getId()).append(" ");
        }
        _log.info(sb.toString());

        Map<VirtualArray, List<StoragePool>> varrayPoolMap = getMatchingPools(targetVarrays, vpool,
                capabilities);
        if (varrayPoolMap == null || varrayPoolMap.isEmpty()) {
            // No matching storage pools found for any of the target varrays. There are no target
            // storage pools that match the passed vpool parameters and protocols and/or there are
            // no pools that have enough
            // capacity to hold at least one resource of the requested size.
            Set<String> tmpTargetVarrays = new HashSet<String>();
            sb = new StringBuffer(
                    "No matching storage pools found for any of the target varrays: [ ");

            for (VirtualArray targetVarray : targetVarrays) {
                sb.append(targetVarray.getId()).append(" ");
                tmpTargetVarrays.add(targetVarray.getLabel());
            }

            sb.append("]. There are no storage pools that match the passed vpool parameters and protocols and/or "
                    + "there are no pools that have enough capacity to hold at least one resource of the requested size.");

            _log.error(sb.toString());
            throw APIException.badRequests.noMatchingRecoverPointStoragePoolsForVpoolAndVarrays(
                    vpool.getLabel(), tmpTargetVarrays);

        }

        // Reduce the source and target pool down to the pools available via target.
        Set<SRDFPoolMapping> tmpDestPoolsList = getSRDFPoolMappings(varray, candidatePools,
                varrayPoolMap, vpool, vpoolChangeVolume, capabilities.getSize());

        if (tmpDestPoolsList == null || tmpDestPoolsList.isEmpty()) {
            // There are no target pools from any of the target varrays that share the
            // same SRDF connectivity as any of the source varray pools. Placement cannot
            // be achieved.
            Set<String> tmpSRDFVarrays = new HashSet<String>();
            sb = new StringBuffer("No matching target pool found for varray: ");
            sb.append(varray.getId());
            sb.append(" and vpool: ");
            sb.append(vpool.getId());
            sb.append(" to varrays: ");

            for (VirtualArray targetVarray : targetVarrays) {
                sb.append(targetVarray.getId()).append(" ");
                tmpSRDFVarrays.add(targetVarray.getLabel());
            }

            // No matching target pool found for varray so throw an exception
            // indicating a placement error.
            _log.error(sb.toString());
            throw APIException.badRequests.noMatchingSRDFPools(varray.getLabel(), vpool.getLabel(),
                    tmpSRDFVarrays);
        }

        // Fire business rules to determine which SRDFPoolMappings can be eliminated
        // from consideration for placement.
        Set<SRDFPoolMapping> srcDestPoolsList = fireSRDFPlacementRules(tmpDestPoolsList,
                capabilities.getResourceCount());

        // If none of the target systems/sites have the available resources to protect
        // the volume request configuration throw an exception.
        if (srcDestPoolsList == null || srcDestPoolsList.isEmpty()) {
            throw APIException.badRequests.srdfNoSolutionsFoundError();
        }

        // Get a new source pool list for pool selection
        Set<StoragePool> sourceCandidatePoolList = new HashSet<StoragePool>();
        for (SRDFPoolMapping srdfPoolMapping : srcDestPoolsList) {
            sourceCandidatePoolList.add(srdfPoolMapping.sourceStoragePool);
        }

        // Try with the storagePoolList as it currently is.
        // If we get through the process and couldn't achieve full target, we should
        // take out the matched pool from the storagePoolList and try again.
        List<StoragePool> sourcePoolList = new ArrayList<StoragePool>();
        sourcePoolList.addAll(sourceCandidatePoolList);

        // We need to create recommendations for one or more pools
        // that can accommodate the number of requested resources.
        // We start by trying to place all resources in a single
        // pool if one exists that can accommodate all requested
        // resources and work our way down as necessary trying to
        // minimize the number of pools used to satisfy the request.
        int recommendedCount = 0;
        int currentCount = capabilities.getResourceCount();

        // Go through all of the source pools we have at our disposal until we've
        // satisfied all of the requests.
        while (!sourcePoolList.isEmpty() && recommendedCount < capabilities.getResourceCount()) {
            // This request will either decrement the count OR shrink the sourcePoolList
            // In the case of decrementing the count, it's because it was successful at
            // placing volume(s). If it wasn't, that source pool goes in the trash and we
            // try the next one.
            long resourceSize = capabilities.getSize();
            int resourceCount = capabilities.getResourceCount();
            // We need to find a pool that matches the capacity for all the source/target luns
            long requiredPoolCapacity = resourceSize * currentCount;
            _log.info("Required pool capacity: " + requiredPoolCapacity);
            StoragePool poolWithRequiredCapacity = _blockScheduler.getPoolMatchingCapacity(requiredPoolCapacity,
                    resourceSize, currentCount, sourcePoolList, VirtualPool.ProvisioningType.Thin
                            .toString().equalsIgnoreCase(vpool.getSupportedProvisioningType()), null);

            // When we find a pool capable of handling a specific number
            // of resources, we pick one, remove that pool from the list
            // of candidate pools, and create a recommendation for that
            // pool, setting the resource count for that recommendation.
            if (poolWithRequiredCapacity != null) {
                StoragePool recommendedPool = poolWithRequiredCapacity;
                _log.debug("Recommending storage pool {} for {} resources.", recommendedPool.getId(),
                        currentCount);

                // Now we know what pool was selected, we can grab the target pools that jive with that
                // source
                Map<VirtualArray, List<StoragePool>> targetVarrayPoolMap = findDestPoolsForSourcePool(
                        targetVarrays, srcDestPoolsList, recommendedPool, vpool);

                if (targetVarrayPoolMap == null || targetVarrayPoolMap.isEmpty()) {
                    // A valid source pool was found but there are no pools from any of the
                    // target varrays that can protect it.
                    _log.info(
                            "There are no pools from any of the target varrays that can protect the source "
                                    + "varray pool {}.  Will try using another source varray pool.",
                            recommendedPool.getLabel());

                    // Remove the source pool and try the next one.
                    sourcePoolList.remove(poolWithRequiredCapacity);
                } else {
                    // A single recommendation object will create a set of volumes for an SRDF pair.
                    SRDFRecommendation rec = new SRDFRecommendation();

        			// For each target varray, we start the process of matching source and destination
        			// pools to one storage system.
        			Map<VirtualArray, Set<StorageSystem>> varrayTargetDeviceMap = new HashMap<VirtualArray, Set<StorageSystem>>();
        			for (VirtualArray targetVarray1 : targetVarrayPoolMap.keySet()) {
        				if (rec.getSourceStoragePool() == null) {
        				    rec.setVirtualArray(varray.getId());
        				    rec.setVirtualPool(vpool);
        					rec.setSourceStoragePool(recommendedPool.getId());
        					rec.setResourceCount(currentCount);
        					rec.setSourceStorageSystem(recommendedPool.getStorageDevice());
        					rec.setVirtualArrayTargetMap(new HashMap<URI, Target>());
        					rec.setVpoolChangeVolume(vpoolChangeVolume != null ? vpoolChangeVolume
        							.getId() : null);
        					rec.setVpoolChangeVpool(vpoolChangeVolume != null ? vpool.getId() : null);
        				}

                        if (targetVarrayPoolMap.get(targetVarray1) == null
                                || targetVarrayPoolMap.get(targetVarray1).isEmpty()) {
                            _log.error("Could not find any suitable storage pool for target varray: "
                                    + targetVarray1.getLabel());
                            throw APIException.badRequests
                                    .unableToFindSuitablePoolForTargetVArray(targetVarray1.getLabel());
                        }

                        // Select the destination pool based on what was selected as source
                        StoragePool destinationPool = _blockScheduler.selectPool(targetVarrayPoolMap.get(targetVarray1));

                        _log.info("Destination target for varray " + targetVarray1.getLabel()
                                + " was determined to be in pool: " + destinationPool.getLabel());

                        Target target = new Target();
                        target.setTargetPool(destinationPool.getId());
                        target.setTargetStorageDevice(destinationPool.getStorageDevice());

                        // Set the copy mode
                        Map<URI, VpoolRemoteCopyProtectionSettings> settingsMap = VirtualPool
                                .getRemoteProtectionSettings(vpool, _dbClient);
                        target.setCopyMode(settingsMap.get(targetVarray1.getId()).getCopyMode());
                        if (target.getCopyMode() == null) {
                            // Set the default if not set
                            target.setCopyMode(RemoteDirectorGroup.SupportedCopyModes.ASYNCHRONOUS
                                    .toString());
                        }

                        // Generate a list of storage systems that match the src and dest pools lists.
                        Set<StorageSystem> targetDeviceList = findMatchingSRDFPools(targetVarray1,
                                srcDestPoolsList, recommendedPool, destinationPool);

                        if (targetDeviceList.isEmpty()) {
                            _log.error("Could not find a Storage pool for target varray: "
                                    + targetVarray1.getLabel());
                            throw APIException.badRequests
                                    .unableToFindSuitablePoolForTargetVArray(targetVarray1.getLabel());
                        }

                        rec.getVirtualArrayTargetMap().put(targetVarray1.getId(), target);

                        // Add this potential solution to the map.
                        varrayTargetDeviceMap.put(targetVarray1, targetDeviceList);
                    }

                    // Grab any element since all varrays need to have the same SRDF connectivity.
                    VirtualArray firstVarray = null;
                    for (VirtualArray baseVarray : varrayTargetDeviceMap.keySet()) {
                        firstVarray = baseVarray;
                        break;
                    }
                    _log.info("Chose the first varray for SRDF comparison: " + firstVarray.getLabel());

                    // Now go through each storage system in this varray and see if it matches up
                    findInsertRecommendation(rec, firstVarray, recommendations, candidatePools,
                            recommendedPool, varrayTargetDeviceMap, project, consistencyGroupUri);

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
                    currentCount = resourceCount - recommendedCount < currentCount ? resourceCount
                            - recommendedCount : currentCount;
                }
            } else {
                // If we can't find a pool that can hold the current
                // count of resources, decrease the count so that we look
                // for pools that can hold the next smaller number.
                currentCount--;

                // Clear out the source pool list (which will cause failure)
                sourcePoolList.clear();
            }

            // We need to place all the resources. If we can't then
            // log an error and clear the list of recommendations.
            if (recommendedCount != resourceCount) {
                _log.error("Could not find matching pools for varray {} & vpool {}", varray.getId(),
                        vpool.getId());
                recommendations.clear();

                // Remove the pool we chose from the list so we can try again.
                sourcePoolList.remove(poolWithRequiredCapacity);
            }
        }

        return recommendations;
    }

    /**
     * Scheduler for a vpool change from an unprotected volume to a protected volume.
     * 
     * @param volume
     *            volume that is being changed to a protected vpool
     * @param vpool
     *            vpool requested to change to (must be protected)
     * @param targetVarrays
     *            Varrays to protect this volume to.
     * @return list of Recommendation objects to satisfy the request
     */
    public List<Recommendation> scheduleStorageForCosChangeUnprotected(final Volume volume,
            final VirtualPool vpool, final List<VirtualArray> targetVarrays,
            final VirtualPoolChangeParam param) {
        _log.debug("Schedule storage for vpool change to vpool {} for volume {}.",
                String.valueOf(vpool.getId()), String.valueOf(volume.getId()));
        List<StoragePool> matchedPoolsForVpool = VirtualPool.getValidStoragePools(vpool, _dbClient, true);

        // Make sure our pool is in this list; this is a check to ensure the pool is in our existing
        // varray and new vpool.
        StoragePool sourcePool = null;
        Iterator<StoragePool> iter = matchedPoolsForVpool.iterator();
        while (iter.hasNext()) {
            StoragePool pool = iter.next();
            if (pool.getId().equals(volume.getPool())) {
                sourcePool = pool;
                break;
            }
        }
        if (sourcePool == null) {
            // We could not verify the source pool exists in the new vpool and existing varray, return appropriate error
            _log.error(
                    "Volume's storage pool does not belong to vpool {} .", vpool.getLabel());
            throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(
                    vpool.getLabel(), volume.getVirtualArray().toString());
        }
        VirtualPoolCapabilityValuesWrapper wrapper = new VirtualPoolCapabilityValuesWrapper();
        wrapper.put(VirtualPoolCapabilityValuesWrapper.SIZE, volume.getCapacity());
        wrapper.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, new Integer(1));
        wrapper.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, volume.getConsistencyGroup());

        // Schedule storage based on source volume storage pool
        List<StoragePool> sourcePools = new ArrayList<StoragePool>();
        sourcePools.add(sourcePool);
        return scheduleStorageSourcePoolConstraint(
                _dbClient.queryObject(VirtualArray.class, volume.getVirtualArray()),
                _dbClient.queryObject(Project.class, volume.getProject().getURI()), vpool, wrapper,
                sourcePools, volume, volume.getConsistencyGroup());
    }

    /**
     * Executes a set of business rules against the <code>List</code> of <code>SRDFPoolMapping</code> objects to determine if they are
     * capable to perform volume SRDF.
     * We then use our knowledge of the storage systems to execute the following business rules:
     * <p>
     * <ul>
     * <li>Business rules unimplemented at this time, we let all mappings through.</li>
     * </ul>
     * 
     * @param srdfPoolMappings
     * @param resourceCount
     *            number of volumes being requested for creation/target
     * @return
     */
    private Set<SRDFPoolMapping> fireSRDFPlacementRules(
            final Set<SRDFPoolMapping> srdfPoolMappings, final Integer resourceCount) {

        final String validatingSRDF = "Validating storage systems to ensure they are capable of handling an SRDF configuration for %s production volume(s) with SRDF.";

        // Log messages used within this method - Use String.format()
        _log.info(String.format(validatingSRDF, resourceCount));

        Set<SRDFPoolMapping> validSRDFPoolMappings = new TreeSet<SRDFPoolMapping>();

        for (SRDFPoolMapping srdfPoolMapping : srdfPoolMappings) {
            // add it to the list of possibilities.
            validSRDFPoolMappings.add(srdfPoolMapping);
        }

        return validSRDFPoolMappings;
    }

    /**
     * This method takes the source varray and a list of possible pools provided by common Bourne
     * placement logic and returns a list of source/destination pairs that are capable of SRDF
     * 
     * When this method returns, we have a list of the possible source and destination pools This is
     * later used determine which entries in the list share a an SRDF link via the same source
     * storage system, and therefore can satisfy the request to protect to ALL virtual arrays.
     * 
     * @param varray
     *            Source Varray
     * @param poolList
     *            Pool List from common placement logic for source
     * @param varrayPoolMap
     *            Pool List from common placement logic for target varrays
     * @return a list of source/destination pools
     */
    private Set<SRDFPoolMapping> getSRDFPoolMappings(final VirtualArray varray,
            final List<StoragePool> poolList,
            final Map<VirtualArray, List<StoragePool>> varrayPoolMap, final VirtualPool vpool, final Volume vpoolChangeVolume,
            final long size) {
        // Maps to reduce hits on the database for objects we'll need more than one time during
        // calculations
        Map<StoragePool, StorageSystem> sourcePoolStorageMap = new HashMap<StoragePool, StorageSystem>();
        Map<StoragePool, StorageSystem> destPoolStorageMap = new HashMap<StoragePool, StorageSystem>();
        for (StoragePool sourcePool : poolList) {
            sourcePoolStorageMap.put(sourcePool,
                    _dbClient.queryObject(StorageSystem.class, sourcePool.getStorageDevice()));
        }
        for (VirtualArray targetVarray : varrayPoolMap.keySet()) {
            for (StoragePool destPool : varrayPoolMap.get(targetVarray)) {
                destPoolStorageMap.put(destPool,
                        _dbClient.queryObject(StorageSystem.class, destPool.getStorageDevice()));
            }
        }

        Set<SRDFPoolMapping> srcDestPoolList = new TreeSet<SRDFPoolMapping>();

        // For each storage pool that is considered a candidate for source...
        for (StoragePool sourcePool : sourcePoolStorageMap.keySet()) {
            // Go through each target virtual array and attempt to add an entry in the pool mapping
            for (VirtualArray targetVarray : varrayPoolMap.keySet()) {
                // Add an entry to the mapping if the source and destination storage systems can see
                // each other.
                populateSRDFPoolList(varray, destPoolStorageMap, srcDestPoolList, sourcePool,
                        targetVarray, vpool, vpoolChangeVolume, size);
            }
        }

        return srcDestPoolList;
    }

    /**
     * Create an entry in the SRDF pool list if the source and destination storage systems can see
     * each other via SRDF links.
     * 
     * @param sourceVarray
     *            source varray
     * @param destPoolStorageMap
     *            convenience, storage systems that belong to pools
     * @param srcDestPoolList
     *            source/dest pool lists from earlier pool mapping
     * @param sourcePool
     *            source pool being tested
     * @param targetVarray
     *            target varray
     * @param vpool
     *            source vpool
     * @param vpoolChangeVolume
     *            source volume (null if not exists)
     * @param size
     *            required volume size
     */
    private void populateSRDFPoolList(final VirtualArray sourceVarray,
            final Map<StoragePool, StorageSystem> destPoolStorageMap,
            final Set<SRDFPoolMapping> srcDestPoolList, final StoragePool sourcePool,
            final VirtualArray targetVarray,
            final VirtualPool vpool, final Volume vpoolChangeVolume, final long size) {

        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, sourcePool.getStorageDevice());
        boolean isThinlyProvisioned = vpool.getSupportedProvisioningType().equalsIgnoreCase(VirtualPool.ProvisioningType.Thin.toString());
        MetaVolumeRecommendation sourceVolumeRecommendation =
                MetaVolumeUtils.getCreateRecommendation(storageSystem, sourcePool, size, isThinlyProvisioned,
                        vpool.getFastExpansion(), null);

        long sourceMaxVolumeSizeLimitKb = isThinlyProvisioned ? sourcePool.getMaximumThinVolumeSize() : sourcePool
                .getMaximumThickVolumeSize();

        _log.debug(String.format("Check which target pools match source pool %s based on the required volume configuration",
                sourcePool.getNativeId()));
        for (StoragePool targetPool : destPoolStorageMap.keySet()) {
            // This will check to see if the target pool candidate is on a storage system that can
            // talk SRDF to it.
            // TODO: Also check the policy here too; only allow pools that are on an array that
            // supports the vpool policy (async/sync)
            if (!storageSystem.containsRemotelyConnectedTo(targetPool.getStorageDevice())) {
                continue;
            }
            // Check that target pool can support required volume configuration.
            if (!validateRequiredVolumeConfiguration(sourcePool, targetPool, vpoolChangeVolume,
                    destPoolStorageMap, sourceVolumeRecommendation, size, isThinlyProvisioned, vpool.getFastExpansion(),
                    sourceMaxVolumeSizeLimitKb)) {
                continue;
            }

            SRDFPoolMapping srdfPoolMapping = new SRDFPoolMapping();
            srdfPoolMapping.sourceStoragePool = sourcePool;
            srdfPoolMapping.sourceVarray = sourceVarray;
            srdfPoolMapping.destStoragePool = targetPool;
            srdfPoolMapping.destVarray = targetVarray;
            srcDestPoolList.add(srdfPoolMapping);
        }
    }

    /**
     * Check if target pool can support required volume configuration for srdf target volume.
     * 
     * @param sourcePool
     * @param targetPool
     * @param vpoolChangeVolume source volume (null if not exists)
     * @param destPoolStorageMap map target pool to storage system
     * @param sourceVolumeRecommendation
     * @param size required volume size
     * @param isThinlyProvisioned
     * @param fastExpansion
     * @param sourceMaxVolumeSizeLimitKb
     * @return true/false
     */
    private boolean validateRequiredVolumeConfiguration(StoragePool sourcePool, StoragePool targetPool, Volume vpoolChangeVolume,
            Map<StoragePool, StorageSystem> destPoolStorageMap,
            MetaVolumeRecommendation sourceVolumeRecommendation, long size,
            boolean isThinlyProvisioned, boolean fastExpansion,
            long sourceMaxVolumeSizeLimitKb) {

        long targetMaxVolumeSizeLimitKb = isThinlyProvisioned ? targetPool.getMaximumThinVolumeSize() : targetPool
                .getMaximumThickVolumeSize();
        if (sourceMaxVolumeSizeLimitKb == targetMaxVolumeSizeLimitKb) {
            // When source and target pools have the volume size limit we can guarantee that target
            // pool will support required volume configuration.
            return true;
        }

        _log.info(String.format("Target storage pool %s max volume size limit %s Kb. Source storage pool max volume size limit %s Kb.",
                targetPool.getNativeId(), targetMaxVolumeSizeLimitKb, sourceMaxVolumeSizeLimitKb));

        // Keeping the below information as it will be handy
        StorageSystem targetStorageSystem = destPoolStorageMap.get(targetPool);
        MetaVolumeRecommendation targetVolumeRecommendation = MetaVolumeUtils.getCreateRecommendation(targetStorageSystem, targetPool, size,
                isThinlyProvisioned,
                fastExpansion, null);

        if (vpoolChangeVolume != null) {
            // This is path to upgrade existing volume to srdf protected volume
            if (vpoolChangeVolume.getIsComposite()) {
                // Existing volume is composite volume.
                // Make sure that the target pool will allow to create META MEMBERS of required size if it is a VMAX2 Array.
                // If it is a VMAX3 Array, make sure that the target Pool will allow creation of the a SINGLE VOLUME
                long capacity = vpoolChangeVolume.getMetaMemberSize();
                long capacityKb = (capacity % BYTESCONVERTER == 0) ? capacity / BYTESCONVERTER : capacity / BYTESCONVERTER + 1;
                if (targetStorageSystem.checkIfVmax3()) {
                    // recompute the capacity for checks
                    capacityKb = capacityKb * vpoolChangeVolume.getMetaMemberCount();
                }
                if (capacityKb > targetMaxVolumeSizeLimitKb) {
                    // this target pool does not match --- does not support meta members of the required size
                    _log.debug(String
                            .format(
                                    "Target storage pool %s does not match. Limit for volume size is less than required by source volume configuration \n"
                                            +
                                            "Required capacity: %s Kb, actual limit: %s Kb", targetPool.getNativeId(), capacityKb,
                                    targetMaxVolumeSizeLimitKb));
                    return false;
                }
            } else {
                // Existing volume is a regular volume.
                // Check that the target pool will allow to create regular volume of the same size or META MEMBERS if
                // the target volume is going to be a META
                long capacity = vpoolChangeVolume.getCapacity();
                if (targetVolumeRecommendation.isCreateMetaVolumes() &&
                        (sourcePool.getPoolClassName().equalsIgnoreCase(StoragePool.PoolClassNames.Symm_SRPStoragePool.toString()))) {
                    // recompute the capacity for checks
                    capacity = targetVolumeRecommendation.getMetaMemberSize();
                }
                long capacityKb = (capacity % BYTESCONVERTER == 0) ? capacity / BYTESCONVERTER : capacity / BYTESCONVERTER + 1;
                if (capacityKb > targetMaxVolumeSizeLimitKb) {

                    // this target pool does not match --- does not support regular volumes of the required size
                    _log.debug(String
                            .format(
                                    "Target storage pool %s does not match. Limit for volume size is less than required by source volume configuration \n"
                                            +
                                            "Required capacity: %s Kb, actual limit: %s Kb", targetPool.getNativeId(), capacityKb,
                                    targetMaxVolumeSizeLimitKb));
                    return false;
                }
            }
        } else {
            // This is path to create a new srdf protected volume
            // Verify meta volume recommendation for target pool and check if we get the same volume spec as for the source volume.
            return validateMetaRecommednationsForSRDF(sourcePool, targetPool, sourceVolumeRecommendation, targetVolumeRecommendation);
        }
        return true;
    }

    /**
     * Validate the meta recommendations.
     * 
     * @param sourcePool
     * @param targetPool
     * @param sourceVolumeRecommendation
     * @param targetVolumeRecommendation
     * @return true/false
     */
    private boolean validateMetaRecommednationsForSRDF(final StoragePool sourcePool, final StoragePool targetPool,
            final MetaVolumeRecommendation sourceVolumeRecommendation, final MetaVolumeRecommendation targetVolumeRecommendation) {
        // compare source and target recommendations to make sure that source and target volumes have the same spec.
        if (!sourceVolumeRecommendation.equals(targetVolumeRecommendation)) {
            // this target pool does not match.
            // If the sourceVolume is a V2 Meta and the Target Volume is from V3, do not return false..
            if (sourceVolumeRecommendation.isCreateMetaVolumes()) {
                if (targetPool.getPoolClassName().equalsIgnoreCase(StoragePool.PoolClassNames.Symm_SRPStoragePool.toString())) {
                    _log.debug(String
                            .format("Source storage pool %s supports Meta Volume Creation. Target volume is a non Meta.",
                                    sourcePool.getNativeId()));
                    return true;
                }
            }
            // HY: If V3 is the Source, we can Have a V2 Device that is a Meta
            if (targetVolumeRecommendation.isCreateMetaVolumes()) {
                if (sourcePool.getPoolClassName().equalsIgnoreCase(StoragePool.PoolClassNames.Symm_SRPStoragePool.toString())) {
                    _log.debug(String
                            .format("Target storage pool %s supports Meta Volume Creation. Source volume is a non Meta.",
                                    targetPool.getNativeId()));
                    return true;
                }
            }
            _log.debug(String
                    .format("Target storage pool %s does not match. Target volume can not be created with the same configuration as the source volume.",
                            targetPool.getNativeId()));
            return false;
        }
        return true;
    }
    /**
     * Generate a list of storage pools for each varray that can provide a valid target path. The
     * seed data is the recommendedPool; the storage pool must have SRDF capability to the
     * destination pool.
     * 
     * @param targetVarrays
     *            target virtual arrays to cycle through
     * @param srcDestPoolsList
     *            src/dest pool list
     * @param recommendedPool
     *            current recommended pool to check
     * @return a map of storage pools that can be protected by recommendedPool
     */
    private Map<VirtualArray, List<StoragePool>> findDestPoolsForSourcePool(
            final List<VirtualArray> targetVarrays, final Set<SRDFPoolMapping> srcDestPoolsList,
            final StoragePool recommendedPool, VirtualPool vpool) {
        // Create a return map
        Map<VirtualArray, List<StoragePool>> targetVarrayPoolMap = new HashMap<VirtualArray, List<StoragePool>>();

        // For each target Varray, see if you have any src/dest entries that have the same pool.
        for (VirtualArray targetVarray : targetVarrays) {
            Set<StoragePool> uniquePools = new HashSet<StoragePool>();
            _log.info("Phase 1: Target Pool mapping for varray: " + targetVarray.getLabel());
            for (SRDFPoolMapping srdfPoolMapping : srcDestPoolsList) {
                if (srdfPoolMapping.destVarray.getId().equals(targetVarray.getId())
                        && srdfPoolMapping.sourceStoragePool.equals(recommendedPool)) {
                    _log.info("Phase 1: Target Pool mapping adding: "
                            + srdfPoolMapping.destStoragePool.getLabel());
                    uniquePools.add(srdfPoolMapping.destStoragePool);
                }
            }
            List<StoragePool> uniquePoolList = new ArrayList<StoragePool>();
            uniquePoolList.addAll(uniquePools);
            uniquePoolList = filterPoolsForSupportedActiveModeProvider(uniquePoolList, vpool);
            targetVarrayPoolMap.put(targetVarray, uniquePoolList);
        }
        return targetVarrayPoolMap;
    }

    /**
     * Find the list of storage systems that have source and destination pools that are capable of
     * SRDF protections to that target.
     * 
     * @param rec
     *            recommendation placement object
     * @param srdfVarray
     *            target varray
     * @param srcDestPoolsList
     *            src/dest pool list
     * @param recommendedPool
     *            recommended pool to compare
     * @param destinationPool
     *            destination pool to match with source and target system
     * @return list of storage systems that match the recommended and destination pool
     */
    private Set<StorageSystem> findMatchingSRDFPools(final VirtualArray srdfVarray,
            final Set<SRDFPoolMapping> srcDestPoolsList, final StoragePool recommendedPool,
            final StoragePool destinationPool) {

        Set<StorageSystem> storageDeviceList = new HashSet<StorageSystem>();
        // Find the target pool mapping that matches this combination
        for (SRDFPoolMapping srdfPoolMapping : srcDestPoolsList) {
            if (srdfPoolMapping.destVarray.getId().equals(srdfVarray.getId())) {
                _log.info("Comparison for SRDF target varray: " + srdfVarray.getLabel());
                _log.info("recommended pool: " + recommendedPool.getLabel()
                        + " vs. SRDF pool mapping source: "
                        + srdfPoolMapping.sourceStoragePool.getLabel());
                _log.info("destination pool: " + destinationPool.getLabel()
                        + " vs. SRDF pool mapping destination: "
                        + srdfPoolMapping.destStoragePool.getLabel());

                if (srdfPoolMapping.sourceStoragePool.equals(recommendedPool)
                        && srdfPoolMapping.destStoragePool.equals(destinationPool)) {
                    // Make sure the storage systems aren't the same, that won't fly. TODO: move
                    // this check up to findDestPoolsForSourcePool
                    if (!srdfPoolMapping.sourceStoragePool.getStorageDevice().equals(
                            srdfPoolMapping.destStoragePool.getStorageDevice())) {
                        // This is the storage system we can perform SRDF on
                        // TODO: Add check to make sure that none of the target storage systems are
                        // the same (hala, walk-through 11/20)
                        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                                srdfPoolMapping.destStoragePool.getStorageDevice());
                        storageDeviceList.add(storageSystem);
                        _log.info("SRDF potentially will be recommended to array: "
                                + storageSystem.getNativeGuid());
                    }
                }
            }
        }
        return storageDeviceList;
    }

    /**
     * Determine if the recommendation object contains a combination that is capable of being
     * protected via SRDF. If so, add the recommendation to the recommendation list.
     * 
     * @param rec
     *            Recommendation object under scrutiny
     * @param firstVarray
     *            first varray to start comparing to
     * @param recommendations
     *            recommendation list to add to
     * @param candidatePools
     *            candidate pools; remove pool in recommendation if inserted
     * @param recommendedPool
     *            pool we are comparing against
     * @param varrayTargetDeviceMap
     *            storage system per varray
     * @param project
     *            project requested
     */
    private void findInsertRecommendation(final SRDFRecommendation rec,
            final VirtualArray firstVarray, final List<Recommendation> recommendations,
            final List<StoragePool> candidatePools, final StoragePool recommendedPool,
            final Map<VirtualArray, Set<StorageSystem>> varrayTargetDeviceMap,
            final Project project, final URI consistencyGroupUri) {

        // This is our "home" storage system. We expect all varrays to have a storage pool from a
        // storage system that is contained in the SRDF list of this storage system.
        StorageSystem sourceStorageSystem = _dbClient.queryObject(StorageSystem.class,
                recommendedPool.getStorageDevice());

        // Go through each varray, looking for storage system that is contained in the SRDF
        // connectivity list for the source storage system.
        // If we find that ANY varray doesn't contain the source storage system, it's not a valid
        // solution, and we need to try something else.
        int found = 0;
        for (VirtualArray compareVarray : varrayTargetDeviceMap.keySet()) {
            _log.info("Testing to see if varray: " + compareVarray.getLabel()
                    + " contains storage system: " + sourceStorageSystem.getNativeGuid());
            for (StorageSystem targetStorageSystem : varrayTargetDeviceMap.get(compareVarray)) {
                _log.info("Detailed Testing to see if varray: " + compareVarray.getLabel()
                        + " matches with: " + targetStorageSystem.getNativeGuid());
                if (!sourceStorageSystem.getId().equals(targetStorageSystem.getId())
                        && sourceStorageSystem.containsRemotelyConnectedTo(targetStorageSystem
                                .getId())) {
                    _log.info("Found the storage system we're trying to use.");
                    URI raGroupID = findRAGroup(sourceStorageSystem, targetStorageSystem, rec
                            .getVirtualArrayTargetMap().get(compareVarray.getId()).getCopyMode(),
                            project, consistencyGroupUri);
                    if (raGroupID != null) {
                        found++;
                        // Set the RA Group ID, which will get set in the volume descriptor for the
                        // target device.
                        rec.getVirtualArrayTargetMap().get(compareVarray.getId())
                                .setSourceRAGroup(raGroupID);
                        break; // move on to the next storage system
                    }
                    _log.info("Did not find a qualifying RA Group that connects the two storage arrays.  Do you have an RDF Group preconfigured?");
                }
            }
        }

        if (found == varrayTargetDeviceMap.keySet().size()) {
            candidatePools.remove(recommendedPool);
            recommendations.add(rec);
            _log.info("Storage System " + sourceStorageSystem.getLabel()
                    + " is found with connectivity to targets in all varrays required");
        } else {
            // Bad News, we couldn't find a match in all of the varrays that all of the storage
            // systems
            _log.error("No matching storage system was found in all target varrays requested with the correct RDF groups.");
            throw APIException.badRequests
                    .unableToFindSuitableStorageSystemsforSRDF(StringUtils.join(SRDFUtils.getQualifyingRDFGroupNames(project), ","));
        }
    }

    private List<RemoteDirectorGroup> storeRAGroupsinList(final Iterator<URI> raGroupIter) {
        List<RemoteDirectorGroup> groups = new ArrayList<RemoteDirectorGroup>();
        while (raGroupIter.hasNext()) {
            URI raGroupId = raGroupIter.next();
            RemoteDirectorGroup raGroup = _dbClient.queryObject(RemoteDirectorGroup.class,
                    raGroupId);
            if (!raGroup.getInactive()) {
                groups.add(raGroup);
            }
        }
        return groups;
    }

    private List<RemoteDirectorGroup> findRAGroupAssociatedWithCG(final Iterator<URI> raGroupIter,
            final BlockConsistencyGroup cgObj) {
        List<RemoteDirectorGroup> groups = storeRAGroupsinList(raGroupIter);
        if (null == cgObj) {
            return groups;
        }
        String cgName = cgObj.getAlternateLabel();
        if (null == cgName) {
            cgName = cgObj.getLabel();
        }
        for (RemoteDirectorGroup raGroup : groups) {
            if ((null != raGroup.getSourceReplicationGroupName() && raGroup.getSourceReplicationGroupName().contains(cgName))
                    || (null != raGroup.getTargetReplicationGroupName() && raGroup.getTargetReplicationGroupName().contains(cgName))) {
                _log.info(
                        "Found the RDF Group {}  which contains the CG {}. Processing the RDF Group for other validations.",
                        raGroup.getId(), cgObj.getId());
                List<RemoteDirectorGroup> filteredGroups = new ArrayList<RemoteDirectorGroup>();
                filteredGroups.add(raGroup);
                return filteredGroups;
            }
        }
        return groups;
    }

    /**
     * Match up RA Groups to the source and target storage systems. If a match is found, return the
     * ID.
     * 
     * @param sourceStorageSystem
     *            potential source storage system
     * @param targetStorageSystem
     *            potential target storage system
     * @param copyMode
     *            async, sync mode literals
     * @param project
     *            project requested
     * @return Remote Group ID
     */
    private URI findRAGroup(final StorageSystem sourceStorageSystem,
            final StorageSystem targetStorageSystem, final String copyMode, final Project project,
            final URI consistencyGroupUri) {
        URIQueryResultList raGroupsInDB = new URIQueryResultList();

        BlockConsistencyGroup cgObj = null;
        if (null != consistencyGroupUri) {
            cgObj = _dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupUri);
        }
        // Primary name check, "V-<projectname>" or "<projectname>"
        StringSet grpNames = SRDFUtils.getQualifyingRDFGroupNames(project);

        // For placement requiring project label, at least warn if the project label is so long that
        // it may cause an issue now or in the future.
        // If placement doesn't require project-based label below, remove this check.
        if (project.getLabel().length() > SRDFUtils.RDF_GROUP_NAME_MAX_LENGTH - SRDFUtils.RDF_GROUP_PREFIX.length()) {
            _log.warn(String
                    .format("SRDF RA Group Placement: Project name is longer than the number of characters allowed by VMAX for an RA group name.  This will cause an issue if you have multiple projects that start with %s",
                            project.getLabel().substring(0,
                                    SRDFUtils.RDF_GROUP_NAME_MAX_LENGTH - SRDFUtils.RDF_GROUP_PREFIX.length())));
        }

        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getStorageDeviceRemoteGroupsConstraint(sourceStorageSystem.getId()), raGroupsInDB);
        Iterator<URI> raGroupIter = raGroupsInDB.iterator();
        List<RemoteDirectorGroup> raGroups = findRAGroupAssociatedWithCG(raGroupIter, cgObj);
        for (RemoteDirectorGroup raGroup : raGroups) {
            URI raGroupId = raGroup.getId();

            _log.info(String
                    .format("SRDF RA Group Placement: Checking to see if RA Group: %s is suitable for SRDF protection, given the request.",
                            raGroup.getLabel()));
            _log.info(String.format(
                    "SRDF RA Group Placement: Source Array: %s --> Target Array: %s",
                    sourceStorageSystem.getNativeGuid(), targetStorageSystem.getNativeGuid()));

            // Check to see if it exists in the DB and is active
            if (null == raGroup || raGroup.getInactive()) {
                _log.info("SRDF RA Group Placement: Found that the RA Group is either not in the database or in the deactivated state, not considering.");
                continue;
            }

            // Check to see if the RA Group contains (substring is OK) any of the desired labels
            if (raGroup.getLabel() == null || !SRDFUtils.containsRaGroupName(grpNames, raGroup.getLabel())) {
                _log.info(String
                        .format("SRDF RA Group Placement: Found that the RA Group does not have a label or does not contain any of the names (%s), which is currently required for leveraging existing RA Groups.",
                                StringUtils.join(grpNames, ",")));
                continue;
            }

            // Check to see if the source storage system ID matches
            if (!raGroup.getSourceStorageSystemUri().equals(sourceStorageSystem.getId())) {
                _log.info(String
                        .format("SRDF RA Group Placement: Found that the RA Group does not cater to the source storage system we require.  We require %s, but this group is defined as %s",
                                sourceStorageSystem.getNativeGuid(), raGroup.getNativeGuid()));
                continue;
            }

            // Check to see if the remote storage system ID matches
            if (!raGroup.getRemoteStorageSystemUri().equals(targetStorageSystem.getId())) {
                _log.info(String
                        .format("SRDF RA Group Placement: Found that the RA Group does not cater to the remote (target) storage system we require.  We require %s, but this group is defined as %s",
                                targetStorageSystem.getNativeGuid(), raGroup.getNativeGuid()));
                continue;
            }

            // Check to see if the connectivity status is UP
            if (!raGroup.getConnectivityStatus().equals(
                    RemoteDirectorGroup.ConnectivityStatus.UP.toString())) {
                _log.info(String
                        .format("SRDF RA Group Placement: Found that the RA Group is not in the proper connectivity state of UP, instead it is in the state: %s",
                                raGroup.getConnectivityStatus().toString()));
                continue;
            }

            // Just a warning in case the RA group isn't set properly, a sign of a possible bad
            // decision to come.
            if (raGroup.getSupportedCopyMode() == null) {
                _log.warn(String
                        .format("SRDF RA Group Placement: Copy Mode not set on RA Group %s, probably an unsupported SRDF Deployment: ",
                                raGroup.getLabel()));
            }

            // Check to see if the policy of the RDF group is set to ALL or the same as in our vpool
            // for that copy
            if (raGroup.getSupportedCopyMode() != null
                    && !raGroup.getSupportedCopyMode().equals(
                            RemoteDirectorGroup.SupportedCopyModes.ALL.toString())
                    && !raGroup.getSupportedCopyMode().equals(copyMode)) {
                _log.info(String
                        .format("SRDF RA Group Placement: Found that the RA Group does is using the proper copy policy of %s, instead it is using copy policy: %s",
                                copyMode, raGroup.getSupportedCopyMode().toString()));
                continue;
            }

            // More than 1 RA Group is available, only if RA Groups corresponding to given CGs is
            // not available.
            // Look for empty RA Groups alone, which can be used to create this new CG.
            if (raGroups.size() > 1 && null != cgObj && raGroup.getVolumes() != null
                    && !raGroup.getVolumes().isEmpty()) {
                _log.info(String
                        .format("Found that the RDF Group has existing volumes with a CG different from expected: %s .",
                                cgObj.getLabel()));
                continue;
            }

            _log.info(String
                    .format("SRDF RA Group Placement: RA Group: %s on %s --> %s is selected for SRDF protection",
                            raGroup.getLabel(), sourceStorageSystem.getNativeGuid(),
                            targetStorageSystem.getNativeGuid()));
            return raGroupId;
        }

        _log.warn("SRDF RA Group Placement: No RA Group was suitable for SRDF protection.  See previous log messages for specific failed criteria on each RA Group considered.");
        return null;
    }

    /**
     * Gather matching pools for a collection of varrays
     * 
     * @param varrays
     *            The target varrays
     * @param vpool
     *            the requested vpool that must be satisfied by the storage pool
     * @param capabilities
     *            capabilities
     * @return A list of matching storage pools and varray mapping
     */
    private Map<VirtualArray, List<StoragePool>> getMatchingPools(final List<VirtualArray> varrays,
            final VirtualPool vpool, final VirtualPoolCapabilityValuesWrapper capabilities) {
        Map<VirtualArray, List<StoragePool>> varrayStoragePoolMap = new HashMap<VirtualArray, List<StoragePool>>();
        Map<URI, VpoolRemoteCopyProtectionSettings> settingsMap = VirtualPool
                .getRemoteProtectionSettings(vpool, _dbClient);

        for (VirtualArray varray : varrays) {
            // If there was no vpool specified with the target settings, use the base vpool for this
            // varray.
            VirtualPool targetVpool = vpool;
            VpoolRemoteCopyProtectionSettings settings = settingsMap.get(varray.getId());
            if (settings != null && settings.getVirtualPool() != null) {
                targetVpool = _dbClient.queryObject(VirtualPool.class, settings.getVirtualPool());
            }
            capabilities.put(VirtualPoolCapabilityValuesWrapper.PERSONALITY, VirtualPoolCapabilityValuesWrapper.SRDF_TARGET);
            // Find a matching pool for the target vpool
            varrayStoragePoolMap.put(varray,
                    _blockScheduler.getMatchingPools(varray, targetVpool, capabilities));
        }

        return varrayStoragePoolMap;
    }

    @Override
    public Set<List<Recommendation>> getRecommendationsForVpool(VirtualArray vArray, Project project, 
            VirtualPool vPool, VpoolUse vPoolUse,
            VirtualPoolCapabilityValuesWrapper capabilities, Map<VpoolUse, List<Recommendation>> currentRecommendations) {
       Set<List<Recommendation>> recommendationSet = new HashSet<List<Recommendation>>();
       List<Recommendation> recommendations;
       if (vPoolUse == VpoolUse.SRDF_COPY) {
           recommendations = getRecommendationsForCopy(vArray, project, vPool, capabilities, currentRecommendations.get(VpoolUse.ROOT));
       } else {
           recommendations = getRecommendationsForResources(vArray, project, vPool, capabilities);
       } 
       recommendationSet.add(recommendations);
       return recommendationSet;
    }
    
    private List<Recommendation> getRecommendationsForCopy(VirtualArray vArray, Project project, 
            VirtualPool vPool, VirtualPoolCapabilityValuesWrapper capabilities, 
            List<Recommendation> currentRecommendations) {
        List<Recommendation> recommendations = new ArrayList<Recommendation>();
        if (currentRecommendations == null) {
            currentRecommendations = new ArrayList<Recommendation>();
        }
        // Look through the existing SRDF Recommendations for a SRDFRecommendation
        // that has has matching varray and vpool.
        for (Recommendation recommendation : currentRecommendations) {
            Recommendation rec = recommendation;
            while (rec != null) {
                if (rec instanceof SRDFRecommendation) {
                    SRDFRecommendation srdfrec = (SRDFRecommendation) rec;
                    if (srdfrec.getVirtualArrayTargetMap().containsKey(vArray.getId())) {
                        SRDFRecommendation.Target target = srdfrec.getVirtualArrayTargetMap().get(vArray.getId());
                        _log.info(String.format("Found SRDF target recommendation for va %s vpool %s", 
                                vArray.getLabel(), vPool.getLabel()));
                        SRDFCopyRecommendation targetRecommendation = new SRDFCopyRecommendation();
                        targetRecommendation.setVirtualArray(vArray.getId());
                        targetRecommendation.setVirtualPool(vPool);
                        targetRecommendation.setSourceStorageSystem(target.getTargetStorageDevice());
                        targetRecommendation.setSourceStoragePool(target.getTargetStoragePool());
                        targetRecommendation.setResourceCount(srdfrec.getResourceCount());
                        targetRecommendation.setRecommendation(srdfrec);
                        recommendations.add(targetRecommendation);
                    }
                }
                // Check child recommendations, if any
                rec = rec.getRecommendation();
            }
        }
        return recommendations;
    }
}
