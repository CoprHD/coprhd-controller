/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.RemoteDirectorGroup.SupportedCopyModes;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.CopyTypes;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.FileReplicationType;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class FileMirrorSchedular implements Scheduler {

    /**
     * A valid combination of
     * "given this storage system, this pool can be SRDF linked against this pool" object.
     */
    public class RemoteReplicationPoolMapping implements Comparable<RemoteReplicationPoolMapping> {
        // Source varray
        public VirtualArray sourceVarray;
        // Source storage pool allowed
        public StoragePool sourceStoragePool;
        // Destination varray
        public VirtualArray destVarray;
        // Destination storage pool allowed
        public StoragePool destStoragePool;

        @Override
        public int compareTo(final RemoteReplicationPoolMapping ppMapCompare) {
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
            return "RemoteReplicationPoolMapping [sourceVarray=" + sourceVarray.getLabel()
                    + ", sourceStoragePool=" + sourceStoragePool.getLabel() + ", destVarray="
                    + destVarray.getLabel() + ", destStoragePool=" + destStoragePool.getLabel()
                    + "]";
        }
    }

    public final Logger _log = LoggerFactory
            .getLogger(FileMirrorSchedular.class);

    private DbClient _dbClient;
    private StorageScheduler _storageScheduler;
    private FileStorageScheduler _fileScheduler;

    public void setStorageScheduler(final StorageScheduler storageScheduler) {
        _storageScheduler = storageScheduler;
    }

    public void setDbClient(final DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setFileScheduler(FileStorageScheduler fileScheduler) {
        _fileScheduler = fileScheduler;
    }

    @Autowired
    protected PermissionsHelper _permissionsHelper = null;

    /**
     * Select and return one or more storage pools where the filesystem(s) should be created. The
     * placement logic is based on: - varray, only storage devices in the given varray are
     * candidates - destination varrays - vpool, specifies must-meet & best-meet service
     * specifications - access-protocols: storage pools must support all protocols specified in
     * vpool - file replication: if yes, only select storage pools with this capability -
     * best-match, select storage pools which meets desired performance - provision-mode: thick/thin
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
    public List<FileRecommendation> getRecommendationsForResources(VirtualArray varray,
            Project project, VirtualPool vpool,
            VirtualPoolCapabilityValuesWrapper capabilities) {

        _log.debug("Schedule storage for {} resource(s) of size {}.",
                capabilities.getResourceCount(), capabilities.getSize());

        capabilities.put(VirtualPoolCapabilityValuesWrapper.PERSONALITY, VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_SOURCE);
        // Get all storage pools that match the passed vpool params and
        // protocols. In addition, the pool must have enough capacity
        // to hold at least one resource of the requested size.
        List<StoragePool> pools = _storageScheduler.getMatchingPools(varray, vpool, capabilities);

        if (pools == null || pools.isEmpty()) {
            _log.error(
                    "No matching storage pools found for the source varray: {0}. There are no storage pools that "
                            + "match the passed vpool parameters and protocols and/or there are no pools that have enough capacity to "
                            + "hold at least one resource of the requested size.",
                    varray.getLabel());
            throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(vpool.getId(),
                    varray.getId());
        }

        // skip StoragePools, which had been used as R2 targets for given consistencyGroup earlier.
        // If we don't skip then for same CG, then our existing R2 targets will act as source
        List<StoragePool> candidatePools = new ArrayList();
        if (VirtualPool.vPoolSpecifiesFileReplication(vpool)
                && FileReplicationType.REMOTE.name().equalsIgnoreCase(vpool.getFileReplicationType())) {
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
                            "Storage System {} is used as target R2 for given CG earlier, hence cannot be used as source R1 agaan for the same CG.",
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
                candidatePools, null);
    }

    /**
     * Schedule storage based on the incoming storage pools for source file system. Find a source
     * storage pool that can provide a source file system that satisfies the vpool's criteria for all
     * targets varrays required and build a recommendation structure to describe the findings.
     * 
     * Strategy:
     * 
     * When we come into method, we already have a list of candidate source pools, which may be
     * in multiple arrays
     * 1. Get matching pools for each of the target virtual arrays based on the
     * target virtual pool.
     * 2. Make a map of virtual array to potential pools in step 1.
     * 3. Find a pool from each entry in the map that belongs to a storage system and the target storage pool
     * from different remote storage system of same type.
     * 4. Generate an FileReplication Recommendation object that reflects the combination we found.
     * 
     * @param varray
     *            varray requested for source
     * @param vpool
     *            vpool requested
     * @param capabilities
     *            parameters
     * @param candidatePools
     *            candidate pools to use for source
     * @param vpoolChangeFs
     *            vpool change file system, if applicable
     * @return list of Recommendation objects to satisfy the request
     */
    private Set<RemoteReplicationPoolMapping> scheduleStorageSourcePoolConstraint(final VirtualArray varray,
            final Project project, final VirtualPool vpool,
            final VirtualPoolCapabilityValuesWrapper capabilities,
            final List<StoragePool> candidatePools, final FileShare vpoolChangeFs) {
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
            _storageScheduler.sortPools(candidatePools);
        }

        List<VirtualArray> targetVarrays = getTargetVirtualArraysForVirtualPool(project, vpool,
                _dbClient, _permissionsHelper);

        // Attempt to use these pools for selection based on target
        StringBuffer sb = new StringBuffer("Determining if File replication is possible from " + varray.getId()
                + " to: ");
        for (VirtualArray targetVarray : targetVarrays) {
            sb.append(targetVarray.getId()).append(" ");
        }
        _log.info(sb.toString());

        // Get the target storage pools for each target!!!
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
                tmpTargetVarrays.add(targetVarray.getId().toString());
            }

            sb.append("]. There are no storage pools that match the passed vpool parameters and protocols and/or "
                    + "there are no pools that have enough capacity to hold at least one resource of the requested size.");

            _log.error(sb.toString());
            throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarrays(
                    vpool.getId(), tmpTargetVarrays);

        }

        // Reduce the source and target pool down to the pools available via target.
        Set<RemoteReplicationPoolMapping> srcDestPoolsList = getReplicationPoolMappings(varray, candidatePools,
                varrayPoolMap, vpool, vpoolChangeFs, capabilities.getSize());

        if (srcDestPoolsList == null || srcDestPoolsList.isEmpty()) {
            // There are no target pools from any of the target varrays that
            // file replication. Placement cannot be achieved.
            Set<String> tmpTargetVarrays = new HashSet<String>();
            sb = new StringBuffer("No matching target pool found for varray: ");
            sb.append(varray.getId());
            sb.append(" and vpool: ");
            sb.append(vpool.getId());
            sb.append(" to varrays: ");

            for (VirtualArray targetVarray : targetVarrays) {
                sb.append(targetVarray.getId()).append(" ");
                tmpTargetVarrays.add(targetVarray.getId().toString());
            }

            // No matching target pool found for varray so throw an exception
            // indicating a placement error.
            _log.error(sb.toString());
            throw APIException.badRequests.noMatchingStoragePoolsForRemoteFileReplication(vpool.getId(),
                    tmpTargetVarrays);
        }

        // Get a new source pool list for pool selection
        Set<StoragePool> sourceCandidatePoolList = new HashSet<StoragePool>();
        for (RemoteReplicationPoolMapping storaePoolMapping : srcDestPoolsList) {
            sourceCandidatePoolList.add(storaePoolMapping.sourceStoragePool);
        }

        return srcDestPoolsList;
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
        if (VirtualPool.getFileRemoteProtectionSettings(vpool, dbClient) != null) {
            for (URI targetVirtualArray : VirtualPool.getFileRemoteProtectionSettings(vpool, dbClient)
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
                .getFileRemoteProtectionSettings(vpool, _dbClient);

        for (VirtualArray varray : varrays) {
            // If there was no vpool specified with the target settings, use the base vpool for this
            // varray.
            VirtualPool targetVpool = vpool;
            VpoolRemoteCopyProtectionSettings settings = settingsMap.get(varray.getId());
            if (settings != null && settings.getVirtualPool() != null) {
                targetVpool = _dbClient.queryObject(VirtualPool.class, settings.getVirtualPool());
            }
            capabilities.put(VirtualPoolCapabilityValuesWrapper.PERSONALITY, VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TARGET);
            // Find a matching pool for the target vpool
            varrayStoragePoolMap.put(varray,
                    _storageScheduler.getMatchingPools(varray, targetVpool, capabilities));
        }

        return varrayStoragePoolMap;
    }

    /**
     * This method takes the source varray and a list of possible pools provided by common file
     * placement logic and returns a list of source/destination pairs that are capable doing file replication
     * 
     * When this method returns, we have a list of the possible source and destination pools
     * 
     * @param varray
     *            Source Varray
     * @param poolList
     *            Pool List from common placement logic for source
     * @param varrayPoolMap
     *            Pool List from common placement logic for target varrays
     * @return a list of source/destination pools
     */
    private Set<RemoteReplicationPoolMapping> getReplicationPoolMappings(final VirtualArray varray,
            final List<StoragePool> poolList,
            final Map<VirtualArray, List<StoragePool>> varrayPoolMap, final VirtualPool vpool, final FileShare vpoolChangeFs,
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

        Set<RemoteReplicationPoolMapping> srcDestPoolList = new TreeSet<RemoteReplicationPoolMapping>();

        // For each storage pool that is considered a candidate for source...
        for (StoragePool sourcePool : sourcePoolStorageMap.keySet()) {
            // Go through each target virtual array and attempt to add an entry in the pool mapping
            for (VirtualArray targetVarray : varrayPoolMap.keySet()) {
                // Add an entry to the mapping if the source and destination storage systems can see
                // each other.
                populateReplicationPoolList(varray, destPoolStorageMap, srcDestPoolList, sourcePool,
                        targetVarray, vpool, vpoolChangeFs, size);
            }
        }

        return srcDestPoolList;
    }

    /**
     * Create an entry in the replication pool list if the source and destination storage systems
     * can perform replication between them
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
     * @param vpoolChangeFs
     *            source file system (null if not exists)
     * @param size
     *            required volume size
     */
    private void populateReplicationPoolList(final VirtualArray sourceVarray,
            final Map<StoragePool, StorageSystem> destPoolStorageMap,
            final Set<RemoteReplicationPoolMapping> srcDestPoolList, final StoragePool sourcePool,
            final VirtualArray targetVarray,
            final VirtualPool vpool, final FileShare vpoolChangeFs, final long size) {

        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, sourcePool.getStorageDevice());
        _log.debug(String.format("Check which target pools match source pool %s based on the required file system configuration",
                sourcePool.getNativeId()));
        for (StoragePool targetPool : destPoolStorageMap.keySet()) {
            // This will check to see if the target pool candidate is on a storage system that
            // supports replication!!!
            if (!remotePoolSupportReplication(sourcePool, targetPool, vpool, storageSystem)) {
                continue;
            }

            RemoteReplicationPoolMapping poolMapping = new RemoteReplicationPoolMapping();
            poolMapping.sourceStoragePool = sourcePool;
            poolMapping.sourceVarray = sourceVarray;
            poolMapping.destStoragePool = targetPool;
            poolMapping.destVarray = targetVarray;
            srcDestPoolList.add(poolMapping);
        }
    }

    private boolean remotePoolSupportReplication(StoragePool srcPool, StoragePool trgtPool,
            VirtualPool vpool, StorageSystem system) {
        StorageSystem targetSystem = _dbClient.queryObject(StorageSystem.class, trgtPool.getStorageDevice());

        // Verify target storage system supports the required replication type!!!
        if (targetSystem.getSupportedReplicationTypes() == null ||
                !targetSystem.getSupportedReplicationTypes().contains(vpool.getFileReplicationType())) {
            return false;
        }

        // Source and target storage system should not be the same!!!
        if (targetSystem.getNativeGuid().equalsIgnoreCase(system.getNativeGuid())) {
            return false;
        }

        // Target storage system type should same as source!!!
        if (!targetSystem.getSystemType().equalsIgnoreCase(system.getSystemType())) {
            return false;
        }

        // Verify the target storage pool copy mode!!!
        if (trgtPool.getSupportedCopyTypes() != null &&
                trgtPool.getSupportedCopyTypes().contains(getPoolCopyTypeFromCopyModes(vpool.getFileReplicationCopyMode()))) {
            return true;
        }
        return false;
    }

    private String getPoolCopyTypeFromCopyModes(String supportedCopyMode) {
        String copyType = CopyTypes.ASYNC.name();
        if (SupportedCopyModes.SYNCHRONOUS.name().equals(supportedCopyMode)) {
            copyType = CopyTypes.SYNC.name();
        }
        return copyType;
    }
}
