/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.util.*;
import java.util.Map;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.fileorchestrationcontroller.FileDescriptor;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.SRDFRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.api.service.impl.placement.FileStorageScheduler;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class FileMirrorSchedular implements Scheduler {
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
     * A valid combination of
     * "given this storage system, this pool can be SRDF linked against this pool" object.
     */
    public class MirrorPoolMapping implements Comparable<MirrorPoolMapping> {
        // Source varray
        public VirtualArray sourceVarray;
        // Source storage pool allowed
        public StoragePool sourceStoragePool;
        // Destination varray
        public VirtualArray destVarray;
        // Destination storage pool allowed
        public StoragePool destStoragePool;

        @Override
        public int compareTo(final MirrorPoolMapping ppMapCompare) {
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
            capabilities.put(VirtualPoolCapabilityValuesWrapper.PERSONALITY, VirtualPoolCapabilityValuesWrapper.SRDF_TARGET);
            // Find a matching pool for the target vpool
            varrayStoragePoolMap.put(varray,
                    _storageScheduler.getMatchingPools(varray, targetVpool, capabilities));
        }

        return varrayStoragePoolMap;
    }

    private Set<MirrorPoolMapping> scheduleStorageSourcePoolConstraint(final VirtualArray varray,
                                                                       final Project project, final VirtualPool vpool,
                                                                       final VirtualPoolCapabilityValuesWrapper capabilities,
                                                                       final List<StoragePool> candidatePools) {
        // Initialize a list of recommendations to be returned.
        List<Recommendation> recommendations = new ArrayList<Recommendation>();
        //get target varray -step1
        List<VirtualArray> targetVarrays = getTargetVirtualArraysForVirtualPool(project, vpool,
                _dbClient, _permissionsHelper);

        // Attempt to use these pools for selection based on target
        StringBuffer sb = new StringBuffer("Determining if RemoteMirror is possible from " + varray.getId() + " to: ");
        for (VirtualArray targetVarray : targetVarrays) {
            sb.append(targetVarray.getId()).append(" ");
        }
        _log.info(sb.toString());

        //target varray and assicated storagepool -step2
        Map<VirtualArray, List<StoragePool>> varrayPoolMap = getMatchingPools(targetVarrays, vpool, capabilities);
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
            throw APIException.badRequests.noMatchingRecoverPointStoragePoolsForVpoolAndVarrays(
                    vpool.getId(), tmpTargetVarrays);
        }

        // Reduce the source and target pool down to the pools available via target.
        Set<MirrorPoolMapping> tmpDestPoolsList = getMirrorPoolMappings(varray, candidatePools,
                varrayPoolMap, vpool, null, capabilities.getSize());

        if (tmpDestPoolsList == null || tmpDestPoolsList.isEmpty()) {
            // There are no target pools from any of the target varrays that share the
            // same SRDF connectivity as any of the source varray pools. Placement cannot
            // be achieved.
            Set<String> tmpMirrorVarrays = new HashSet<String>();
            sb = new StringBuffer("No matching target pool found for varray: ");
            sb.append(varray.getId());
            sb.append(" and vpool: ");
            sb.append(vpool.getId());
            sb.append(" to varrays: ");

            for (VirtualArray targetVarray : targetVarrays) {
                sb.append(targetVarray.getId()).append(" ");
                tmpMirrorVarrays.add(targetVarray.getId().toString());
            }

            // No matching target pool found for varray so throw an exception
            // indicating a placement error.
            _log.error(sb.toString());
            throw APIException.badRequests.noMatchingSRDFPools(varray.getId(), vpool.getId(),
                    tmpMirrorVarrays);
        }

        return tmpDestPoolsList;
    }


    public VirtualNAS getRecommendedSourcePool(final Map<VirtualNAS, List<StoragePool>> vNASPoolMap, final Set<StoragePool> sourceCandidatePoolList) {
        VirtualNAS currvNAS = null;
        if (!vNASPoolMap.isEmpty()) {
            for (Map.Entry<VirtualNAS, List<StoragePool>> eachVNASEntry : vNASPoolMap.entrySet()) {
                // If No storage pools recommended!!!
                if (eachVNASEntry.getValue().isEmpty()) {
                    continue;
                } else {
                    currvNAS = eachVNASEntry.getKey();
                    boolean bcontains = sourceCandidatePoolList.containsAll(eachVNASEntry.getValue());
                    if(bcontains == true) {
                        currvNAS = eachVNASEntry.getKey();
                        break;
                    }
                }
            }
        }
        return currvNAS;
    }

//    public List<FileMirrorRecommendation> getRecommendationsForPools(VirtualArray vArray, List<StoragePool> recommendedPools,
//                                                               final VirtualPoolCapabilityValuesWrapper capabilities,
//                                                               VirtualNAS currvNAS,
//                                                               VirtualPool vPool) {
//        FileMirrorRecommendation rec = null;
//        // Get the recommendations for the current vnas pools.
//        List<FileRecommendation> fileRecommendations = null;
//        List<Recommendation> poolRecommendations = _storageScheduler
//                .getRecommendationsForPools(vArray.getId().toString(),
//                        recommendedPools, capabilities);
//        // If we did not find pool recommendation for current vNAS
//        // Pick the pools from next available vNas recommended pools!!!
//        if (poolRecommendations.isEmpty()) {
//            _log.info("Skipping vNAS {}, as pools are not having enough resources",
//                    currvNAS.getNasName());
//            continue;
//        }
//
//        // Get the file recommendations for pool recommendation!!!
//        fileRecommendations = _fileScheduler.getFileRecommendationsForVNAS(currvNAS,
//                vArray.getId(), vPool, poolRecommendations);
//
//        if (!fileRecommendations.isEmpty()) {
//            _log.info("Selected vNAS {} for placement",
//                    currvNAS.getNasName());
//            rec = new FileMirrorRecommendation();
//        }
//
//
//        return
//    }

    /**
     * This method takes the source varray and a list of possible pools provided by common Bourne
     * placement logic and returns a list of source/destination pairs that are capable of Mirror
     *
     * When this method returns, we have a list of the possible source and destination pools This is
     * later used determine which entries in the list share a an Mirror link via the same source
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
    private Set<MirrorPoolMapping> getMirrorPoolMappings(final VirtualArray varray,
                                                     final List<StoragePool> poolList,
                                                     final Map<VirtualArray, List<StoragePool>> varrayPoolMap, final VirtualPool vpool, final FileShare vpoolChangeFileShare,
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

        Set<MirrorPoolMapping> srcDestPoolList = new TreeSet<MirrorPoolMapping>();

        // For each storage pool that is considered a candidate for source...
        for (StoragePool sourcePool : sourcePoolStorageMap.keySet()) {
            // Go through each target virtual array and attempt to add an entry in the pool mapping
            for (VirtualArray targetVarray : varrayPoolMap.keySet()) {
                // Add an entry to the mapping if the source and destination storage systems can see
                // each other.
                populateMirrorPoolList(varray, destPoolStorageMap, srcDestPoolList, sourcePool,
                        targetVarray, vpool, vpoolChangeFileShare, size);
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
     * @vpoolChangeFileShare source File Share (null if not exists)
     * @param size
     *            required volume size
     */
    private void populateMirrorPoolList(final VirtualArray sourceVarray,
                                      final Map<StoragePool, StorageSystem> destPoolStorageMap,
                                      final Set<MirrorPoolMapping> srcDestPoolList, final StoragePool sourcePool,
                                      final VirtualArray targetVarray,
                                      final VirtualPool vpool, final FileShare vpoolChangeFileShare, final long size) {

        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, sourcePool.getStorageDevice());

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

            MirrorPoolMapping mirrorPoolMapping = new MirrorPoolMapping();
            mirrorPoolMapping.sourceStoragePool = sourcePool;
            mirrorPoolMapping.sourceVarray = sourceVarray;
            mirrorPoolMapping.destStoragePool = targetPool;
            mirrorPoolMapping.destVarray = targetVarray;
            srcDestPoolList.add(mirrorPoolMapping);
        }
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
            final List<VirtualArray> targetVarrays, final Set<MirrorPoolMapping> srcDestPoolsList,
            final StoragePool recommendedPool) {
        // Create a return map
        Map<VirtualArray, List<StoragePool>> targetVarrayPoolMap = new HashMap<VirtualArray, List<StoragePool>>();

        // For each target Varray, see if you have any src/dest entries that have the same pool.
        for (VirtualArray targetVarray : targetVarrays) {
            Set<StoragePool> uniquePools = new HashSet<StoragePool>();
            _log.info("Phase 1: Target Pool mapping for varray: " + targetVarray.getLabel());
            for (MirrorPoolMapping mirrorPoolMapping : srcDestPoolsList) {
                if (mirrorPoolMapping.destVarray.getId().equals(targetVarray.getId())
                        && mirrorPoolMapping.sourceStoragePool.equals(recommendedPool)) {
                    _log.info("Phase 1: Target Pool mapping adding: "
                            + mirrorPoolMapping.destStoragePool.getLabel());
                    uniquePools.add(mirrorPoolMapping.destStoragePool);
                }
            }
            List<StoragePool> uniquePoolList = new ArrayList<StoragePool>();
            uniquePoolList.addAll(uniquePools);
            targetVarrayPoolMap.put(targetVarray, uniquePoolList);
        }
        return targetVarrayPoolMap;
    }

    /**
     * get the vnas settings
     * @param vNAS
     * @param fRec
     * @return
     */
    private void setFileRecommendationsForVNAS(VirtualNAS vNAS, FileMirrorRecommendation fRec) {

        List<StoragePort> ports = _fileScheduler.getAssociatedStoragePorts(vNAS);

        List<URI> storagePortURIList = new ArrayList<URI>();
        for (Iterator<StoragePort> iterator = ports.iterator(); iterator.hasNext();) {
            StoragePort storagePort = iterator.next();
            storagePortURIList.add(storagePort.getId());
        }

        if (vNAS.getStorageDeviceURI().equals(fRec.getSourceStorageSystem())) {
            fRec.setStoragePorts(storagePortURIList);
            fRec.setvNAS(vNAS.getId());
        }
    }

    /**
     *
     * @param varray
     * @param vpoolSource
     * @return
     */
    private VirtualPool getTargetVpool(final VirtualArray varray,
                                       final VirtualPool vpoolSource) {

        Map<URI, VpoolRemoteCopyProtectionSettings> settingsMap = VirtualPool
                .getRemoteProtectionSettings(vpoolSource, _dbClient);

        VirtualPool targetVpool = vpoolSource;
        VpoolRemoteCopyProtectionSettings settings = settingsMap.get(varray.getId());
        if (settings != null && settings.getVirtualPool() != null) {
            targetVpool = _dbClient.queryObject(VirtualPool.class, settings.getVirtualPool());
        }
        return targetVpool;
    }

    @Override
    public List getRecommendationsForResources(VirtualArray varray,
                                               Project project, VirtualPool vpool,
                                               VirtualPoolCapabilityValuesWrapper capabilities) {
        // Initialize a list of recommendations to be returned.
        List<Recommendation> recommendations = new ArrayList<Recommendation>();
        List<StoragePool> candidatePools = new ArrayList();
        List<VirtualNAS> invalidNasServers = new ArrayList<VirtualNAS>();
        Map<VirtualNAS, List<StoragePool>> vNASSourcePoolMap = null;
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
        } else {
            vNASSourcePoolMap = _fileScheduler.getRecommendedVirtualNASBasedOnCandidatePools(vpool, varray.getId(),
                    pools, project, invalidNasServers);
            if (vNASSourcePoolMap != null && !vNASSourcePoolMap.isEmpty()) {
                for (Map.Entry<VirtualNAS, List<StoragePool>> eachVNASEntry : vNASSourcePoolMap.entrySet()) {
                    List<StoragePool> recommendedPools = eachVNASEntry.getValue();
                    candidatePools.addAll(recommendedPools);
                }
                if (candidatePools.isEmpty()) {
                    candidatePools.addAll(pools);
                }
            } else {
                candidatePools.addAll(pools);
            }
        }

        //step get the target storagepool -2
        Set<MirrorPoolMapping> mirrorPoolMappings = scheduleStorageSourcePoolConstraint(varray, project, vpool, capabilities,
                candidatePools);

        // Go through all of the source pools we have at our disposal until we've
        // satisfied all of the requests.
        // Get a new source pool list for pool selection
        Set<StoragePool> sourceCandidatePoolList = new HashSet<StoragePool>();
        for (MirrorPoolMapping mirrorPoolMapping : mirrorPoolMappings) {
            sourceCandidatePoolList.add(mirrorPoolMapping.sourceStoragePool);
        }



        int recommendedCount = 0;
        int currentCount = capabilities.getResourceCount();
        List<StoragePool> sourcePoolTemp = null;
        while (!sourceCandidatePoolList.isEmpty() && recommendedCount < capabilities.getResourceCount()) {
            // Now we know what pool was selected, we can grab the target pools that jive with that
            // source
            List<VirtualArray> targetVarrays = getTargetVirtualArraysForVirtualPool(project, vpool,
                    _dbClient, _permissionsHelper);
            //StoragePool recommendedPool = sourceCandidatePoolList.iterator();
            FileMirrorRecommendation rec = new FileMirrorRecommendation();
            VirtualNAS virtualNAS = getRecommendedSourcePool(vNASSourcePoolMap, sourceCandidatePoolList);

            sourcePoolTemp = vNASSourcePoolMap.get(virtualNAS);

            long resourceSize = capabilities.getSize();
            int resourceCount = capabilities.getResourceCount();
            // We need to find a pool that matches the capacity for all the source/target luns
            long requiredPoolCapacity = resourceSize * currentCount;
            _log.info("Required pool capacity: " + requiredPoolCapacity);
            StoragePool poolWithRequiredCapacity = _storageScheduler.getPoolMatchingCapacity(requiredPoolCapacity,
                    resourceSize, currentCount, sourcePoolTemp, VirtualPool.ProvisioningType.Thin
                            .toString().equalsIgnoreCase(vpool.getSupportedProvisioningType()), null);

            if (poolWithRequiredCapacity != null) {
                StoragePool recommendedPool = poolWithRequiredCapacity;
                _log.debug("Recommending storage pool {} for {} resources.", recommendedPool.getId(),
                        currentCount);

                Map<VirtualArray, List<StoragePool>> targetVarrayPoolMap = findDestPoolsForSourcePool(
                        targetVarrays, mirrorPoolMappings, recommendedPool);

                if (targetVarrayPoolMap == null || targetVarrayPoolMap.isEmpty()) {
                    // A valid source pool was found but there are no pools from any of the
                    // target varrays that can protect it.
                    _log.info(
                            "There are no pools from any of the target varrays that can protect the source "
                                    + "varray pool {}.  Will try using another source varray pool.",
                            recommendedPool.getLabel());

                    // Remove the source pool and try the next one.
                    sourceCandidatePoolList.remove(recommendedPool);
                } else {
                    // A single recommendation object will create a set of volumes for an SRDF pair.
                    FileMirrorRecommendation rec = new FileMirrorRecommendation();

                    // For each target varray, we start the process of matching source and destination
                    // pools to one storage system.
                    Map<VirtualArray, Set<StorageSystem>> varrayTargetDeviceMap = new HashMap<VirtualArray, Set<StorageSystem>>();
                    for (VirtualArray targetVarray1 : targetVarrayPoolMap.keySet()) {
                        if (rec.getSourceStoragePool() == null) {
                            rec.setSourceStoragePool(recommendedPool.getId());
                            rec.setResourceCount(1);
                            rec.setSourceStorageSystem(recommendedPool.getStorageDevice());
                            rec.setVirtualArrayTargetMap(new HashMap<URI, FileMirrorRecommendation.Target>());

                        }

                        if (targetVarrayPoolMap.get(targetVarray1) == null
                                || targetVarrayPoolMap.get(targetVarray1).isEmpty()) {
                            _log.error("Could not find any suitable storage pool for target varray: "
                                    + targetVarray1.getLabel());
                            throw APIException.badRequests
                                    .unableToFindSuitablePoolForTargetVArray(targetVarray1.getId());
                        }
                        StoragePool destinationPool = targetVarrayPoolMap.get(targetVarray1).get(0);
                        Map<VirtualNAS, List<StoragePool>> vNASTargetPoolMap = _fileScheduler.getRecommendedVirtualNASBasedOnCandidatePools(
                                getTargetVpool(targetVarray1, vpool), targetVarray1.getId(),
                                targetVarrayPoolMap.get(targetVarray1), project, invalidNasServers);
                        if (vNASTargetPoolMap != null && vNASTargetPoolMap.isEmpty()) {

                        } else {

                        }

                        _log.info("Destination target for varray " + targetVarray1.getLabel()
                                + " was determined to be in pool: " + destinationPool.getLabel());

                        FileMirrorRecommendation.Target target = new FileMirrorRecommendation.Target();
                        target.setTargetPool(destinationPool.getId());
                        target.setTargetStorageDevice(destinationPool.getStorageDevice());

                        // Set the copy mode
                        Map<URI, VpoolRemoteCopyProtectionSettings> settingsMap = VirtualPool
                                .getRemoteProtectionSettings(vpool, _dbClient);
                        target.setCopyMode(settingsMap.get(targetVarray1.getId()).getCopyMode());
                        if (target.getCopyMode() == null) {
                            // Set the default if not set
                            target.setCopyMode(RemoteDirectorGroup.SupportedCopyModes.ASYNCHRONOUS.toString());
                        }


                        rec.getVirtualArrayTargetMap().put(targetVarray1.getId(), target);

                        recommendations.add(rec);
                    }

                }
                recommendedCount += currentCount;
                // then 9,8, and so on.
                currentCount = resourceCount - recommendedCount < currentCount ? resourceCount
                        - recommendedCount : currentCount;
            } else {
                // If we can't find a pool that can hold the current
                // count of resources, decrease the count so that we look
                // for pools that can hold the next smaller number.
                currentCount--;

                // Clear out the source pool list (which will cause failure)
                sourceCandidatePoolList.clear();

            }

        }
    }



}
