/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.PlacementManager.SchedulerType;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.BlockService;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.VPlexRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.AttributeMatcherFramework;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class VPlexScheduler implements Scheduler {

    public static final Logger _log = LoggerFactory.getLogger(VPlexScheduler.class);
    @Autowired
    protected PermissionsHelper _permissionsHelper = null;

    private DbClient _dbClient;
    private StorageScheduler _blockScheduler;
    private AttributeMatcherFramework _matcherFramework;
    private PlacementManager _placementManager;

    public void setBlockScheduler(StorageScheduler blockScheduler) {
        _blockScheduler = blockScheduler;
    }

    public StorageScheduler getBlockScheduler() {
        return _blockScheduler;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setMatcherFramework(AttributeMatcherFramework matcherFramework) {
        _matcherFramework = matcherFramework;
    }
    
    public void setPlacementManager(PlacementManager placementManager) {
        _placementManager = placementManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Recommendation> getRecommendationsForResources(VirtualArray vArray, Project project, VirtualPool vPool,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        return getRecommendationsForResources(vArray, project, vPool, capabilities, new HashMap<VpoolUse, List <Recommendation>>());
    }
    public List<Recommendation> getRecommendationsForResources(VirtualArray vArray, Project project, VirtualPool vPool,
            VirtualPoolCapabilityValuesWrapper capabilities, Map<VpoolUse, List<Recommendation>> currentRecommendations) {

        _log.info("Getting recommendations for VPlex volume placement");

        // Validate the VirtualPool specifies VPlex high availability, which
        // currently is the only supported means for creating high
        // availability volumes.
        if (!VirtualPool.HighAvailabilityType.vplex_distributed.name().equals(vPool.getHighAvailability())
                && !VirtualPool.HighAvailabilityType.vplex_local.name().equals(vPool.getHighAvailability())) {
            throw APIException.badRequests.invalidHighAvailability(vPool.getHighAvailability());
        }

        _log.info("VirtualPool has high availability {}", vPool.getHighAvailability());

        Set<URI> vplexSystemsForPlacement = getVPlexSystemsForPlacement(vArray, vPool, capabilities);

        // Determine if the volume creation request is for HA volumes.
        boolean isHAVolumeRequest = VirtualPool.HighAvailabilityType.vplex_distributed.name()
                .equals(vPool.getHighAvailability());

        // Get and validate the high availability VirtualArray and VirtualPool.
        // Note that the HA VirtualPool is optional. When not specified, the
        // high availability VirtualPool is the passed VirtualPool is use.
        VirtualPool haVPool = vPool;
        VirtualArray haVArray = null;
        StringMap haVaVpMap = vPool.getHaVarrayVpoolMap();
        if ((isHAVolumeRequest) && (haVaVpMap != null)) {
            _log.info("Is HA request and with an HA VirtualArray VirtualPool map");
            Iterator<String> vaIter = haVaVpMap.keySet().iterator();
            while (vaIter.hasNext()) {
                String haVaId = vaIter.next();
                _log.info("HA VirtualArray is {}", haVaId);
                if (!haVaId.equals(NullColumnValueGetter.getNullURI().toString())) {
                    _log.info("HA VirtualArray is not a null URI");
                    haVArray = getVirtualArrayForVolumeCreateRequest(project, URI.create(haVaId));

                    if (vArray.getId().toString().equals(haVArray.getId().toString())) {
                        throw APIException.badRequests.sameVirtualArrayAndHighAvailabilityArray();
                    }
                }

                // Now get the VirtualPool.
                String haVpId = haVaVpMap.get(haVaId);
                _log.info("HA VirtualPool is {}", haVpId);
                if (!haVpId.equals(NullColumnValueGetter.getNullURI().toString())) {
                    _log.info("HA VirtualPool is not a null URI");
                    haVPool = BlockService.getVirtualPoolForRequest(project, URI.create(haVpId),
                            _dbClient, _permissionsHelper);
                }
            }
        }

        // Get the volume placement based on passed parameters.
        _log.info("VirtualPool: {}, HA VirtualPool: {}", vPool.getId().toString(), haVPool.getId()
                .toString());
        List<Recommendation> recommendations = scheduleStorage(
                vArray, vplexSystemsForPlacement, null, vPool, isHAVolumeRequest, 
                haVArray, haVPool, capabilities, project, VpoolUse.ROOT, currentRecommendations);

        return recommendations;
    }

    /**
     * Gets the VPlex storage system associated with the specified consistency
     * group.
     * 
     * @param vArray The virtual array specified for the new volume
     * @param vPool The virtual pool specified for the new volume
     * @param capabilities The virtual pool capabilities.
     * @return The VPlex systems for placement
     */
    protected Set<URI> getVPlexSystemsForPlacement(VirtualArray vArray, VirtualPool vPool,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        // If a consistency group is specified, then it is created or not
        // created. It is not in the process of being created based on checks
        // in the block service. If it is not created, that is fine. However,
        // if it is created, then it must be for a VPlex storage system.
        // Further, it must be in the virtual array specified for the volume
        // create request. In this way, when the volumes are placed, they
        // will be placed on the VPlex system for the consistency group.
        BlockConsistencyGroup consistencyGroup = null;
        Set<URI> vplexSystemsForPlacement = null;
        URI cgURI = capabilities.getBlockConsistencyGroup();
        if (cgURI != null) {
            consistencyGroup = _permissionsHelper.getObjectById(cgURI,
                    BlockConsistencyGroup.class);
        }
        if ((consistencyGroup != null) && (consistencyGroup.created())) {
            // Verify the storage system.
            URI cgSystemURI = consistencyGroup.getStorageController();
            StorageSystem cgSystem = _permissionsHelper.getObjectById(cgSystemURI,
                    StorageSystem.class);
            String cgSystemType = cgSystem.getSystemType();

            if (!DiscoveredDataObject.Type.vplex.name().equals(cgSystemType)) {
                throw APIException.badRequests.invalidParameterConsistencyGroupNotForVplexStorageSystem(consistencyGroup.getId());
            }

            // The volumes in a VPLEX consistency group must
            // have the same high availability type.
            List<Volume> cgVolumes = BlockConsistencyGroupUtils
                    .getActiveVplexVolumesInCG(consistencyGroup, _dbClient, null);
            Iterator<Volume> cgVolumesIter = cgVolumes.iterator();
            if (cgVolumesIter.hasNext()) {
                Volume cgVolume = cgVolumesIter.next();
                VirtualPool cgVolumeVPool = _permissionsHelper.getObjectById(
                        cgVolume.getVirtualPool(), VirtualPool.class);
                if (!vPool.getHighAvailability().equals(
                        cgVolumeVPool.getHighAvailability())) {
                    throw APIException.badRequests
                            .invalidParameterConsistencyGroupVolumeHasIncorrectHighAvailability(
                                    consistencyGroup.getId(), cgVolumeVPool.getHighAvailability());
                }
            }

            // Verify the virtual array.
            URI cgVaURI = consistencyGroup.getVirtualArray();
            if (!vArray.getId().toString().equals(cgVaURI.toString())) {
                throw APIException.badRequests.invalidParameterConsistencyGroupVirtualArrayMismatch(consistencyGroup.getId());
            }

            // To satisfy the request, placement must be restricted to the
            // storage systems connected to the VPlex system for the passed
            // consistency group.
            vplexSystemsForPlacement = new HashSet<URI>();
            vplexSystemsForPlacement.add(cgSystemURI);
        }

        return vplexSystemsForPlacement;
    }

    /**
     * Get recommendations for resource placement based on the passed
     * parameters.
     * 
     * @param vArray The VirtualArray in which the resources were requested.
     * @param project The source volume project
     * @param sourceVPool The source volume virtual pool
     * @param mirrorVPool The virtual pool to be used for mirror
     * @param capabilities The VirtualPool capabilities.
     * @param vplexStorageSystemURI The URI of the VPLEX system to which resources should be connected
     * @param excludeStorageSystem The URI of the storage system that needs to be excluded
     * @param cluster The VPLEX cluster to which resources should be connected
     */
    public List getRecommendationsForMirrors(VirtualArray vArray, Project project, VirtualPool sourceVPool, VirtualPool mirrorVPool,
            VirtualPoolCapabilityValuesWrapper capabilities, URI vplexStorageSystemURI, URI excludeStorageSystem, String cluster) {

        _log.info("Getting recommendations for VPlex volume placement");

        _log.info("Source VirtualPool has high availability {}", sourceVPool.getHighAvailability());

        List<Recommendation> recommendations = scheduleStorageForMirror(
                vArray, sourceVPool, mirrorVPool, capabilities, vplexStorageSystemURI, excludeStorageSystem, cluster);

        return recommendations;
    }

    /**
     * Get recommendations for resource placement based on the passed
     * parameters.
     * 
     * @param srcVarray The VirtualArray in which the resources were requested.
     * @param srcVpool Source volume virtual pool
     * @param mirrorVpool The virtual pool to be used for mirror
     * @param srcVpool The VirtualPool requested for the source resources.
     * @param capabilities The VirtualPool capabilities.
     * @param vplexStorageSystemURI The URI of the VPLEX system to which resources should be connected
     * @param excludeStorageSystem The URI of the storage system that needs to be excluded
     * @param cluster The VPLEX cluster to which resources should be connected
     * 
     * @return A list of VPlexRecommendation instances specifying the
     *         recommended resource placement resources.
     */
    public List<Recommendation> scheduleStorageForMirror(VirtualArray srcVarray, VirtualPool srcVpool, VirtualPool mirrorVpool,
            VirtualPoolCapabilityValuesWrapper capabilities, URI vplexStorageSystemURI, URI excludeStorageSystem, String cluster) {

        _log.info("Executing VPlex Mirror placement strategy");

        // Initialize the list of recommendations.
        List<Recommendation> recommendations = new ArrayList<Recommendation>();

        // Get all storage pools that match the passed VirtualPool params,
        // and virtual array. In addition, the pool must
        // have enough capacity to hold at least one resource of the
        // requested size.
        _log.info("Getting placement recommendations for srcVarray {}", srcVarray.getId());
        List<StoragePool> allMatchingPools = getMatchingPools(srcVarray, null, excludeStorageSystem,
                mirrorVpool, capabilities);

        _log.info("Found {} Matching pools for VirtualArray for the Mirror", allMatchingPools.size());

        // Due to VirtualPool attribute matching, we should only get storage
        // pools on storage systems that are connected to a VPlex
        // storage system. We find these storage pools and associate
        // them to the VPlex storage systems to which their storage
        // systems are connected.

        Map<String, List<StoragePool>> vplexPoolMapForSrcVarray = sortPoolsByVPlexStorageSystem(
                allMatchingPools, srcVarray.getId().toString(), cluster);

        // If only specified VPlex from source volume is desired, filter the vplexPoolMapForSrcVarray
        // to only use pools from the vplexStorageSystemURI.
        Iterator<Entry<String, List<StoragePool>>> it = vplexPoolMapForSrcVarray.entrySet().iterator();
        if (vplexStorageSystemURI != null) {
            while (it.hasNext()) {
                Entry<String, List<StoragePool>> entry = it.next();
                String vplexKey = entry.getKey();
                URI vplexURI = null;
                try {
                    vplexURI = URI.create(vplexKey);
                } catch (IllegalArgumentException ex) {
                    _log.error("Bad VPLEX URI: " + vplexURI);
                    continue;
                }
                if (false == vplexStorageSystemURI.equals(vplexURI)) {
                    it.remove();
                }
            }
        }

        if (vplexPoolMapForSrcVarray.isEmpty()) {
            _log.info("No matching pools on storage systems connected to a VPlex");
            // There are no matching pools in the source virtual array
            // on storage systems connected to a VPlex storage system
            // or there are, but a specific VPlex system was requested
            // and there are none for that VPlex system.
            return recommendations;
        }

        // The list of potential VPlex storage systems.
        Set<String> vplexStorageSystemIds = vplexPoolMapForSrcVarray.keySet();
        vplexStorageSystemIds = vplexPoolMapForSrcVarray.keySet();
        _log.info("{} VPlex storage systems have matching pools",
                vplexStorageSystemIds.size());

        Iterator<String> vplexSystemIdsIter = vplexStorageSystemIds.iterator();
        while ((vplexSystemIdsIter.hasNext()) && (recommendations.isEmpty())) {
            String vplexStorageSystemId = vplexSystemIdsIter.next();
            _log.info("Check matching pools for VPlex {}", vplexStorageSystemId);

            // Check if the resource can be placed on the matching
            // pools for this VPlex storage system.
            if (VirtualPool.ProvisioningType.Thin.toString()
                    .equalsIgnoreCase(mirrorVpool.getSupportedProvisioningType())) {
                capabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.TRUE);
            }

            List<Recommendation> recommendationsForMirrorVarray = _blockScheduler.getRecommendationsForPools(
                    srcVarray.getId().toString(), vplexPoolMapForSrcVarray.get(vplexStorageSystemId),
                    capabilities);
            if (recommendationsForMirrorVarray.isEmpty()) {
                _log.info("Matching pools insufficient for placement");
                // For this VPlex, the pools for the source varray are
                // not sufficient, so we need to try another VPlex.
                continue;
            }
            _log.info("Matching pools sufficient for placement");

            recommendations.addAll(createVPlexRecommendations(vplexStorageSystemId,
                    srcVarray, srcVpool, recommendationsForMirrorVarray));
            continue;
        }
        return recommendations;
    }

    /**
     * Get recommendations for resource placement based on the passed
     * parameters.
     * 
     * @param srcVarray The VirtualArray in which the resources were requested.
     * @param requestedVPlexSystems The URIs of the VPlex systems to which
     *            placement should be limited, or null when it doesn't matter.
     * @param srcStorageSystem The URI of a specific backend storage system to
     *            which the source resource should be limited, or null when it
     *            doesn't matter.
     * @param srcVpool The VirtualPool requested for the source resources.
     * @param isHARequest Whether or not HA recommendations are also required.
     * @param requestedHaVarray The desired HA varray or null when not
     *            specified.
     * @param haVpool The VirtualPool for the HA resources.
     * @param capabilities The VirtualPool capabilities.
     * 
     * @return A list of VPlexRecommendation instances specifying the
     *         recommended resource placement resources.
     */
    public List<Recommendation> scheduleStorage(VirtualArray srcVarray,
            Set<URI> requestedVPlexSystems,  
            URI srcStorageSystem, VirtualPool srcVpool,
            boolean isHARequest, VirtualArray requestedHaVarray, VirtualPool haVpool,
            VirtualPoolCapabilityValuesWrapper capabilities, 
            Project project, VpoolUse vpoolUse, Map<VpoolUse, List<Recommendation>> currentRecommendations) {

        _log.info("Executing VPlex high availability placement strategy");

        if (!isHARequest) {
            return scheduleStorageForLocalVPLEXVolume(srcVarray, requestedVPlexSystems,
                    srcStorageSystem, srcVpool, capabilities, project, vpoolUse, currentRecommendations);
        } else {
            return scheduleStorageForDistributedVPLEXVolume(srcVarray,
                    requestedVPlexSystems, srcStorageSystem, srcVpool, requestedHaVarray,
                    haVpool, capabilities, project, vpoolUse, currentRecommendations);
        }
    }

    /**
     * Get recommendations for resource placement for local VPLEX volumes.
     * 
     * @param varray The virtual array in which the resources were requested.
     * @param requestedVPlexSystems The URIs of the VPlex systems to which
     *            placement should be limited, or null when it doesn't matter.
     * @param storageSystem The URI of a specific backend storage system to
     *            which the source resource should be limited, or null when it
     *            doesn't matter.
     * @param vpool The virtual pool requested for the source resources.
     * @param capabilities The virtual pool capabilities.
     * 
     * @return A list of VPlexRecommendation instances specifying the
     *         recommended resource placement.
     */
    private List<Recommendation> scheduleStorageForLocalVPLEXVolume(
            VirtualArray varray, Set<URI> requestedVPlexSystems, URI storageSystem,
            VirtualPool vpool, VirtualPoolCapabilityValuesWrapper capabilities, 
            Project project, VpoolUse vPoolUse, Map<VpoolUse, List<Recommendation>> currentRecommendations) {

        _log.info("Executing VPlex high availability placement strategy for Local VPLEX volumes");

        // Initialize the list of recommendations and baseRecommendations (from lower schedulers).
        List<Recommendation> recommendations = new ArrayList<Recommendation>();
        List<Recommendation> baseRecommendations = new ArrayList<Recommendation>();
        
        // Call the lower level scheduler to get it's recommendations.
        Scheduler nextScheduler = _placementManager.getNextScheduler(
                SchedulerType.vplex, vpool, vPoolUse);
        _log.info(String.format("Calling next scheduler: %s", nextScheduler.getClass().getSimpleName()));
        Set<List<Recommendation>> baseRecommendationSets = 
                nextScheduler.getRecommendationsForVpool(
                        varray, project, vpool, vPoolUse, capabilities, currentRecommendations);
        // For now take the first recommendation set.
        Iterator<List<Recommendation>> setIterator = baseRecommendationSets.iterator();
        if (setIterator.hasNext()) {
            baseRecommendations.addAll(setIterator.next());
        }
        _log.info(String.format("Received %d recommendations from %s", 
                baseRecommendations.size(), nextScheduler.getClass().getSimpleName()));
        List<StoragePool> allMatchingPools = _placementManager
                .getStoragePoolsFromRecommendations(baseRecommendations);
        _log.info("Found {} matching pools for varray", allMatchingPools.size());

        // Sort the matching pools by VPLEX system.
        Map<String, List<StoragePool>> vplexPoolMapForSrcVarray =
                getVPlexConnectedMatchingPools(varray, requestedVPlexSystems,
                        capabilities, allMatchingPools);
        if (vplexPoolMapForSrcVarray.isEmpty()) {
            _log.info("No matching pools on storage systems connected to a VPlex");
            // There are no matching pools in the source virtual array
            // on storage systems connected to a VPlex storage system
            // or there are, but a specific VPlex system was requested
            // and there are none for that VPlex system.
            return recommendations;
        }
        
        // See if any one VPlex system can utilize all the specified pools.
        for (Map.Entry<String, List<StoragePool>> entry : vplexPoolMapForSrcVarray.entrySet()) {
            if (entry.getValue().containsAll(allMatchingPools)) {
                _log.info(String.format("Generating local recommendations for VPLEX %s", entry.getKey()));
                recommendations.addAll(createVPlexRecommendations(baseRecommendations,
                        entry.getKey(), varray, vpool));
            }
        }
        
        if (recommendations.isEmpty()) {
            _log.info("No single VPLEX could front the entire set of recommendations");
        }
        _placementManager.logRecommendations("VPLEX Local", recommendations);
        return recommendations;
    }
//    private List<Recommendation> scheduleStorageForLocalVPLEXVolume(
//            VirtualArray varray, Set<URI> requestedVPlexSystems, URI storageSystem,
//            VirtualPool vpool, VirtualPoolCapabilityValuesWrapper capabilities) {
//
//        _log.info("Executing VPlex high availability placement strategy for Local VPLEX volumes");
//
//        // Initialize the list of recommendations.
//        List<Recommendation> recommendations = new ArrayList<Recommendation>();
//
//        // Get all storage pools that match the passed VirtualPool params,
//        // and virtual array. In addition, the pool must have enough
//        // capacity to hold at least one resource of the requested size.
//        _log.info("Getting all matching pools for varray {}", varray.getId());
//        List<StoragePool> allMatchingPools = getMatchingPools(varray, storageSystem,
//                vpool, capabilities);
//        _log.info("Found {} matching pools for varray", allMatchingPools.size());
//
//        // Sort the matching pools by VPLEX system.
//        Map<String, List<StoragePool>> vplexPoolMapForSrcVarray =
//                getVPlexConnectedMatchingPools(varray, requestedVPlexSystems,
//                        capabilities, allMatchingPools);
//        if (vplexPoolMapForSrcVarray.isEmpty()) {
//            _log.info("No matching pools on storage systems connected to a VPlex");
//            // There are no matching pools in the source virtual array
//            // on storage systems connected to a VPlex storage system
//            // or there are, but a specific VPlex system was requested
//            // and there are none for that VPlex system.
//            return recommendations;
//        }
//
//        // Loop over the list of potential VPlex storage systems.
//        Set<String> vplexStorageSystemIds = vplexPoolMapForSrcVarray.keySet();
//        vplexStorageSystemIds = vplexPoolMapForSrcVarray.keySet();
//        _log.info("{} VPlex storage systems have matching pools",
//                vplexStorageSystemIds.size());
//        Iterator<String> vplexSystemIdsIter = vplexStorageSystemIds.iterator();
//        while (vplexSystemIdsIter.hasNext()) {
//            String vplexStorageSystemId = vplexSystemIdsIter.next();
//            _log.info("Attempting placement on VPlex {}", vplexStorageSystemId);
//            List<Recommendation> baseRecommendations = getRecommendationsForPools(varray.getId().toString(),
//                    vpool, vplexPoolMapForSrcVarray.get(vplexStorageSystemId), capabilities);
//            if (!baseRecommendations.isEmpty()) {
//                _log.info("Matching pools sufficient for placement");
//                // For this VPlex, the pools for the source varray are
//                // sufficient, so we are done.
//                recommendations.addAll(createVPlexRecommendations(
//                        vplexStorageSystemId, varray, vpool, baseRecommendations));
//                break;
//            }
//        }
//
//        return recommendations;
//    }

    /**
     * Get recommendations for resource placement for distributed VLPEX volumes.
     * 
     * @param srcVarray The virtual array in which the resources were requested.
     * @param requestedVPlexSystems The URIs of the VPlex systems to which
     *            placement should be limited, or null when it doesn't matter.
     * @param srcStorageSystem The URI of a specific backend storage system to
     *            which the source resource should be limited, or null when it
     *            doesn't matter.
     * @param srcVpool The virtual pool requested for the source resources.
     * @param haVarray The desired HA varray.
     * @param haVpool The virtual pool for the HA resources.
     * @param capabilities The virtual pool capabilities.
     * 
     * @return A list of VPlexRecommendation instances specifying the
     *         recommended resource placement.
     */
    private List<Recommendation> scheduleStorageForDistributedVPLEXVolume(
            VirtualArray srcVarray, Set<URI> requestedVPlexSystems, URI srcStorageSystem,
            VirtualPool srcVpool, VirtualArray haVarray, VirtualPool haVpool,
            VirtualPoolCapabilityValuesWrapper capabilities, Project project,
            VpoolUse srcVpoolUse, Map<VpoolUse, List<Recommendation>> currentRecommendations) {

//        
//        // See if any one VPlex system can utilize all the specified pools.
//        for (Map.Entry<String, List<StoragePool>> entry : vplexPoolMapForSrcVarray.entrySet()) {
//            if (entry.getValue().containsAll(allMatchingPools)) {
//                _log.info(String.format("Generating local recommendations for VPLEX %s", entry.getKey()));
//                recommendations.addAll(createVPlexRecommendations(baseRecommendations,
//                        entry.getKey(), varray, vpool));
//            }
//        }
//        
//        if (recommendations.isEmpty()) {
//            _log.info("No single VPLEX could front the entire set of recommendations");
//        }
//        _placementManager.logRecommendations("VPLEX Local", recommendations);
//        return recommendations;

        _log.info("Executing VPlex high availability placement strategy for Distributed VPLEX Volumes.");

        // Initialize the list of recommendations.
        List<Recommendation> recommendations = new ArrayList<Recommendation>();
        List<Recommendation> baseRecommendations = new ArrayList<Recommendation>();
      
      // Call the lower level scheduler to get it's recommendations.
      Scheduler nextScheduler = _placementManager.getNextScheduler(
              SchedulerType.vplex, srcVpool, srcVpoolUse);
      _log.info(String.format("Calling next scheduler: %s", nextScheduler.getClass().getSimpleName()));
      Set<List<Recommendation>> baseRecommendationSets = 
              nextScheduler.getRecommendationsForVpool(
                      srcVarray, project, srcVpool, srcVpoolUse, capabilities, currentRecommendations);
      // For now take the first recommendation set.
      Iterator<List<Recommendation>> setIterator = baseRecommendationSets.iterator();
      if (setIterator.hasNext()) {
          baseRecommendations.addAll(setIterator.next());
      }
      _log.info(String.format("Received %d recommendations from %s", 
              baseRecommendations.size(), nextScheduler.getClass().getSimpleName()));
      List<StoragePool> allMatchingPoolsForSrcVarray = _placementManager
              .getStoragePoolsFromRecommendations(baseRecommendations);
      _log.info("Found {} matching pools for source varray", allMatchingPoolsForSrcVarray.size());

        URI cgURI = capabilities.getBlockConsistencyGroup();
        BlockConsistencyGroup cg = (cgURI == null ? null : _dbClient.queryObject(BlockConsistencyGroup.class, cgURI));

        // Sort the matching pools for the source varray by VPLEX system.
        Map<String, List<StoragePool>> vplexPoolMapForSrcVarray =
                getVPlexConnectedMatchingPools(srcVarray, requestedVPlexSystems,
                        capabilities, allMatchingPoolsForSrcVarray);
        if (vplexPoolMapForSrcVarray.isEmpty()) {
            _log.info("No matching pools on storage systems connected to a VPlex");
            // There are no matching pools in the source virtual array
            // on storage systems connected to a VPlex storage system
            // or there are, but a specific VPlex system was requested
            // and there are none for that VPlex system.
            return recommendations;
        }

        // Get all storage pools that match the passed HA VirtualPool params,
        // and HA virtual array. In addition, the pool must have enough
        // capacity to hold at least one resource of the requested size.
       
        _log.info("Getting all matching pools for HA varray {}", haVarray.getId());
        URI haStorageSystem = null;
        
        VirtualPoolCapabilityValuesWrapper haCapabilities = new VirtualPoolCapabilityValuesWrapper(capabilities);
        // Don't look for SRDF in the HA side.
        haCapabilities.put(VirtualPoolCapabilityValuesWrapper.PERSONALITY, null);
        // We don't require that the HA side have the same storage controller.
        haCapabilities.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, null);;
        List<StoragePool> allMatchingPoolsForHaVarray = getMatchingPools(
                haVarray, haStorageSystem, haVpool, haCapabilities);
        _log.info("Found {} matching pools for HA varray", allMatchingPoolsForHaVarray.size());

        // Sort the matching pools for the HA varray by VPLEX system.
        Map<String, List<StoragePool>> vplexPoolMapForHaVarray = sortPoolsByVPlexStorageSystem(
                allMatchingPoolsForHaVarray, haVarray.getId().toString());
        if (vplexPoolMapForHaVarray.isEmpty()) {
            _log.info("No matching pools on storage systems connected to a VPlex");
            // There are no matching pools in the HA virtual array
            // on storage systems connected to a VPlex storage system.
            return recommendations;
        }

        // Get the list of potential VPlex storage systems for the source
        // virtual array.
        Set<String> vplexStorageSystemIds = vplexPoolMapForSrcVarray.keySet();
        vplexStorageSystemIds = vplexPoolMapForSrcVarray.keySet();
        _log.info("{} VPlex storage systems have matching pools",
                vplexStorageSystemIds.size());

        // Get the possible high availability varrays for each of these
        // potential VPlex storage system.
        Map<String, List<String>> vplexHaVarrayMap = ConnectivityUtil.getVPlexVarrays(
                _dbClient, vplexStorageSystemIds, srcVarray.getId());

        // Loop over the potential VPlex storage systems, and attempt
        // to place the resources.
        Iterator<String> vplexSystemIdsIter = vplexStorageSystemIds.iterator();
        while ((vplexSystemIdsIter.hasNext()) && (recommendations.isEmpty())) {
            String vplexStorageSystemId = vplexSystemIdsIter.next();
            _log.info("Attempting placement on VPlex {}", vplexStorageSystemId);

            // Check if this VPLEX can satisfy the requested HA varray.
            List<String> vplexHaVarrays = vplexHaVarrayMap.get(vplexStorageSystemId);
            if (!vplexHaVarrays.contains(haVarray.getId().toString())) {
                // It cannot, try the next VPLEX.
                continue;
            }

            // Check if there are HA storage pools for this VPLEX.
            if (!vplexPoolMapForHaVarray.containsKey(vplexStorageSystemId)) {
                // There are no HA pools for this VPLEX, try the next.
                continue;
            }

            // Check if the resource can be placed on the matching
            // pools for this VPlex storage system in the source varray.
            List<Recommendation> recommendationsForSrcVarray = new ArrayList<Recommendation>();
            recommendationsForSrcVarray.addAll(
                    createVPlexRecommendations(baseRecommendations, 
                    vplexStorageSystemId, srcVarray, srcVpool));
            if (recommendationsForSrcVarray.isEmpty()) {
                _log.info("Matching pools for source varray insufficient for placement");
                // For this VPlex, the pools for the source varray are
                // not sufficient, so we need to try another VPlex.
                continue;
            }

            // Get the storage systems specified by these recommendations.
            // We don't want to use these same storage systems on the HA
            // side when the same system is available to both, else you
            // could create a distributed volume with both backend volumes
            // on the same physical array.
            Set<URI> recommendedSrcSystems = new HashSet<URI>();
            for (Recommendation recommendation : recommendationsForSrcVarray) {
                recommendedSrcSystems.add(recommendation.getSourceStorageSystem());
            }

            // Remove any storage pools on these systems from the list of
            // matching pools for the HA varray for this VPLEX system.
            boolean haPoolsLimitedBySrcSelections = false;
            List<StoragePool> vplexPoolsForHaVarray = new ArrayList<StoragePool>(vplexPoolMapForHaVarray.get(vplexStorageSystemId));
            Iterator<StoragePool> vplexPoolsForHaVarrayIter = vplexPoolsForHaVarray.iterator();
            while (vplexPoolsForHaVarrayIter.hasNext()) {
                StoragePool haPool = vplexPoolsForHaVarrayIter.next();
                URI poolSystem = haPool.getStorageDevice();
                if (recommendedSrcSystems.contains(poolSystem)) {
                    _log.info("Removing pool {} on system {} from consideration for HA placement", haPool.getId(), poolSystem);
                    vplexPoolsForHaVarrayIter.remove();
                    haPoolsLimitedBySrcSelections = true;
                }
            }

            // Now check if the resource can be placed on the matching
            // pools for this VPlex storage system in the HA varray.
            List<Recommendation> recommendationsForHaVarray = getRecommendationsForPools(
                    haVarray.getId().toString(), haVpool, vplexPoolsForHaVarray, capabilities);
            if (recommendationsForHaVarray.isEmpty()) {
                _log.info("Matching pools for HA varray insufficient for placement");
                if (haPoolsLimitedBySrcSelections) {
                    // If we limited the pools on the HA side and failed to place,
                    // then let's reverse and use all pools on the HA side and limit
                    // the source side. This is certainly not perfect, but at least
                    // will try and use the pools on both sides before giving up.
                    recommendationsForHaVarray = getRecommendationsForPools(
                            haVarray.getId().toString(), haVpool, vplexPoolMapForHaVarray.get(vplexStorageSystemId), capabilities);
                    if (recommendationsForHaVarray.isEmpty()) {
                        // Still can't place them on the HA side.
                        _log.info("Matching pools for HA varray still insufficient for placement");
                        continue;
                    } else {
                        // Remove the systems from the source side and see
                        // if the source side can still be placed when limited.
                        _log.info("Matching pools for HA varray now sufficient for placement");
// TODO - something about this code
//                        List<StoragePool> vplexPoolsForSrcVarray = new ArrayList<StoragePool>(
//                                vplexPoolMapForSrcVarray.get(vplexStorageSystemId));
//                        Iterator<StoragePool> vplexPoolsForSrcVarrayIter = vplexPoolsForSrcVarray.iterator();
//                        while (vplexPoolsForSrcVarrayIter.hasNext()) {
//                            StoragePool srcPool = vplexPoolsForSrcVarrayIter.next();
//                            URI poolSystem = srcPool.getStorageDevice();
//                            if (recommendedSrcSystems.contains(poolSystem)) {
//                                _log.info("Removing pool {} on system {} from consideration for source placement", srcPool.getId(),
//                                        poolSystem);
//                                vplexPoolsForSrcVarrayIter.remove();
//                            }
//                        }
//                        recommendationsForSrcVarray = getRecommendationsForPools(
//                                srcVarray.getId().toString(), srcVpool, vplexPoolsForSrcVarray, capabilities);
                        if (recommendationsForSrcVarray.isEmpty()) {
                            _log.info("Matching pools for source varray no longer sufficient for placement");
                            // Now we can't place the source side.
                            continue;
                        }
                    }
                } else {
                    // For this VPlex, the pools for the source varray are
                    // not sufficient, so we need to try another VPlex.
                    continue;
                }
            }

            // We have recommendations for pools in both the source and HA varrays.
            recommendations.addAll(recommendationsForSrcVarray);
            recommendations.addAll(createVPlexRecommendations(
                    vplexStorageSystemId, haVarray, haVpool, recommendationsForHaVarray));

            _log.info("Done trying to place resources for VPlex.");
        }
        _placementManager.logRecommendations("VPLEX Distributed", recommendations);

        return recommendations;
    }

    /**
     * Uses the block scheduler to get the placement recommendations.
     * 
     * @param varrayId The virtual array id.
     * @param vpool A reference to the virtual pool
     * @param candidatePools The list of candidate pools.
     * @param capabilities The virtual pool capabilities.
     * 
     * @return The list of placement recommendations.
     */
    private List<Recommendation> getRecommendationsForPools(String varrayId,
            VirtualPool vpool, List<StoragePool> candidatePools,
            VirtualPoolCapabilityValuesWrapper capabilities) {

        VirtualPoolCapabilityValuesWrapper updatedCapabilities = capabilities;
        if (VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(
                vpool.getSupportedProvisioningType())) {
            updatedCapabilities = new VirtualPoolCapabilityValuesWrapper(capabilities);
            updatedCapabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.TRUE);
        }

        return _blockScheduler.getRecommendationsForPools(varrayId, candidatePools, updatedCapabilities);
    }

    /**
     * Gets the storage pools that are VPlex connected.
     * 
     * @param srcVarray The source virtual array
     * @param requestedVPlexSystems VPlex storage systems associated with the specified consistency
     *            group
     * @param capabilities The virtual pool capabilities
     * @param allMatchingPools The list of matching pools that needs to be refined
     * @return Map of VPlex storage systems to connected storage pools
     */
    protected Map<String, List<StoragePool>> getVPlexConnectedMatchingPools(VirtualArray srcVarray,
            Set<URI> requestedVPlexSystems, VirtualPoolCapabilityValuesWrapper capabilities,
            List<StoragePool> allMatchingPools) {
        _log.info("Found {} Matching pools for VirtualArray", allMatchingPools.size());

        // Due to VirtualPool attribute matching, we should only get storage
        // pools on storage systems that are connected to a VPlex
        // storage system. We find these storage pools and associate
        // them to the VPlex storage systems to which their storage
        // systems are connected.
        Map<String, List<StoragePool>> vplexPoolMapForSrcVarray = sortPoolsByVPlexStorageSystem(
                allMatchingPools, srcVarray.getId().toString());

        // If only specified VPlexes are desired, filter the vplexPoolMapForSrcNH
        // to only use pools from the requestedVPlexSystems.
        if (requestedVPlexSystems != null && requestedVPlexSystems.isEmpty() == false) {
            Iterator<Map.Entry<String, List<StoragePool>>> it = vplexPoolMapForSrcVarray.entrySet().iterator();
            while (it.hasNext()) {
                String vplexKey = it.next().getKey();
                URI vplexURI = null;
                try {
                    vplexURI = new URI(vplexKey);
                } catch (URISyntaxException ex) {
                    _log.error("Bad VPLEX URI: " + vplexURI);
                    continue;
                }
                if (false == requestedVPlexSystems.contains(vplexURI)) {
                    it.remove();
                }
            }
        }

        return vplexPoolMapForSrcVarray;
    }

    /**
     * Gets and verifies that the VirtualArray passed in the request is
     * accessible to the tenant.
     * 
     * @param project A reference to the project.
     * @param neighborhoodURI The URI for the VirtualArray.
     * 
     * @return A reference to the VirtualArray.
     */
    private VirtualArray getVirtualArrayForVolumeCreateRequest(Project project,
            URI neighborhoodURI) {
        VirtualArray neighborhood = _dbClient.queryObject(VirtualArray.class, neighborhoodURI);
        ArgValidator.checkEntity(neighborhood, neighborhoodURI, false);
        _permissionsHelper.checkTenantHasAccessToVirtualArray(project.getTenantOrg().getURI(), neighborhood);

        return neighborhood;
    }

    /**
     * Schedule Storage for a VPLEX import operation where we are creating the
     * HA volume.
     * 
     * @param srcNH Source Neighborhood
     * @param vplexs Set<URI> Set of Vplex System URIs that can be used
     * @param requestedHaNH Optional requested HA Neighborhood. Can be null.
     * @param cos CoS to be used for new volumes
     * @param capabilities CoS capabilities to be used for new volume
     * @return List<Recommendation>
     */
    public List<Recommendation> scheduleStorageForImport(VirtualArray srcNH,
            Set<URI> vplexs, VirtualArray requestedHaNH, VirtualPool cos,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        Set<String> vplexSystemIds = new HashSet<String>();
        for (URI vplexURI : vplexs) {
            vplexSystemIds.add(vplexURI.toString());
        }

        List<Recommendation> recommendations = new ArrayList<Recommendation>();

        // For an HA request, get the possible high availability neighborhoods
        // for each potential VPlex storage system.
        Map<String, List<String>> vplexHaNHMap = ConnectivityUtil.getVPlexVarrays(
                _dbClient, vplexSystemIds, srcNH.getId());

        for (URI vplexSystemURI : vplexs) {
            StorageSystem vplexSystem = _dbClient.queryObject(StorageSystem.class, vplexSystemURI);

            // See if there is an HA varray
            // for the VPlex that also contains pools suitable to place
            // the resources.
            List<String> vplexHaNHIds = vplexHaNHMap.get(vplexSystem.getId().toString());
            if (vplexHaNHIds == null) {
                continue;
            }
            _log.info("Found {} HA varrays", vplexHaNHIds.size());
            for (String vplexHaNHId : vplexHaNHIds) {
                _log.info("Check HA varray {}", vplexHaNHId);
                // If a specific HA varray was specified and this
                // varray is not it, then skip the varray.
                if ((requestedHaNH != null)
                        && (!vplexHaNHId.equals(requestedHaNH.getId().toString()))) {
                    _log.info("Not the requested HA varray, skip");
                    continue;
                }

                // Get all storage pools that match the passed CoS params,
                // protocols, and this HA varray. In addition, the
                // pool must have enough capacity to hold at least one
                // resource of the requested size.
                VirtualArray vplexHaNH = _dbClient.queryObject(VirtualArray.class,
                        URI.create(vplexHaNHId));
                List<StoragePool> allMatchingPools = getMatchingPools(vplexHaNH, null, cos,
                        capabilities);
                _log.info("Found {} matching pools for HA varray", allMatchingPools.size());

                // Now from the list of candidate pools, we only want pools
                // on storage systems that are connected to the VPlex
                // storage system. We find these storage pools and associate
                // them to the VPlex storage systems to which their storage
                // system is connected.
                Map<String, List<StoragePool>> vplexPoolMapForHaNH =
                        sortPoolsByVPlexStorageSystem(allMatchingPools, vplexHaNHId);

                // If the HA varray has candidate pools for this
                // VPlex, see if the candidate pools in this HA
                // varray are sufficient to place the resources.
                List<Recommendation> recommendationsForHaNH = new ArrayList<Recommendation>();
                if (vplexPoolMapForHaNH.containsKey(vplexSystem.getId().toString())) {
                    _log.info("Found matching pools in HA NH for VPlex {}",
                            vplexSystem.getId());
                    recommendationsForHaNH = _blockScheduler.getRecommendationsForPools(vplexHaNH.getId().toString(),
                            vplexPoolMapForHaNH.get(vplexSystem.getId().toString()), capabilities);
                } else {
                    _log.info("No matching pools in HA NH for VPlex {}",
                            vplexSystem.getId());
                }
                recommendations.addAll(createVPlexRecommendations(vplexSystem.getId().toString(),
                        vplexHaNH, cos, recommendationsForHaNH));
            }
        }
        return recommendations;
    }

    /**
     * Gets all storage pools in the passed varray, satisfying the passed
     * CoS and capable of holding a resource of the requested size. Additionally
     * filters the storage pools to those on the storage system with the
     * passed URI, when the passed storage system is not null.
     * 
     * @param virtualArray The desired varray.
     * @param storageSystemURI The desired storage system, or null.
     * @param virtualPool The required CoS.
     * 
     * @return A list of storage pools.
     */
    protected List<StoragePool> getMatchingPools(VirtualArray virtualArray,
            URI storageSystemURI, VirtualPool virtualPool,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        return getMatchingPools(virtualArray, storageSystemURI, null, virtualPool,
                capabilities);
    }

    /**
     * Gets all storage pools in the passed varray, satisfying the passed
     * CoS and capable of holding a resource of the requested size. Additionally
     * filters the storage pools to those on the storage system with the
     * passed URI, when the passed storage system is not null.
     * 
     * @param varray The desired virtual array.
     * @param storageSystemURI The desired storage system or null.
     * @param excludeStorageSystemURI The storage system that should be excluded or null.
     * @param vpool The required virtual pool.
     * @param capabilities The virtual pool capabilities.
     * 
     * @return A list of storage pools.
     */
    protected List<StoragePool> getMatchingPools(VirtualArray varray,
            URI storageSystemURI, URI excludeStorageSystemURI, VirtualPool vpool,
            VirtualPoolCapabilityValuesWrapper capabilities) {

        List<StoragePool> storagePools = _blockScheduler.getMatchingPools(varray, vpool, capabilities);
        Iterator<StoragePool> storagePoolIter = storagePools.iterator();
        while (storagePoolIter.hasNext()) {
            StoragePool storagePool = storagePoolIter.next();
            StringSet storagePoolNHs = storagePool.getTaggedVirtualArrays();
            if ((storagePoolNHs == null)
                    || (!storagePoolNHs.contains(varray.getId().toString()))
                    || ((storageSystemURI != null) && (!storageSystemURI.toString()
                            .equals(storagePool.getStorageDevice().toString())))
                    || ((excludeStorageSystemURI != null) && (excludeStorageSystemURI.toString()
                            .equals(storagePool.getStorageDevice().toString())))) {
                storagePoolIter.remove();
            }
        }

        return storagePools;
    }

    /**
     * Gets all storage pools in the passed varray, satisfying the passed
     * CoS and capable of holding a resource of the requested size. Calls out
     * too getMatchingPools to filter the storagepools to those on the storage system
     * with the passed URI, when the passed storage system is not null.
     * 
     * @param virtualArray
     * @param storageSystemURI
     * @param virtualPool
     * @param capabilities
     * @return
     */
    protected List<StoragePool> getMatchingPools(VirtualArray virtualArray,
            URI storageSystemURI, VirtualPool virtualPool,
            List<StoragePool> storagePools) {
        return getMatchingPools(virtualArray, storageSystemURI, virtualPool, storagePools);
    }

    /**
     * Determines if each pool is on storage system that is connected to a VPlex
     * storage system that has connectivity to the passed varray. If so, the
     * pool is mapped to that VPlex storage system.
     * 
     * @param storagePools A list of storage pools.
     * @param varrayId The varray to which the VPLEX must have connectivity.
     * 
     * @return A map of storage pools keyed by the VPlex storage system to which
     *         they have connectivity.
     */
    protected Map<String, List<StoragePool>> sortPoolsByVPlexStorageSystem(
            List<StoragePool> storagePools, String varrayId) {
        return sortPoolsByVPlexStorageSystem(storagePools, varrayId, null);
    }

    /**
     * Determines if each pool is on storage system that is connected to a VPlex
     * storage system that has connectivity to the passed varray. If so, the
     * pool is mapped to that VPlex storage system.
     * 
     * @param storagePools A list of storage pools.
     * @param varrayId The varray to which the VPLEX must have connectivity.
     * @param cluster The VPLEX cluster to which storage system should be connected.
     * 
     * @return A map of storage pools keyed by the VPlex storage system to which
     *         they have connectivity.
     */
    protected Map<String, List<StoragePool>> sortPoolsByVPlexStorageSystem(
            List<StoragePool> storagePools, String varrayId, String cluster) {
        Map<String, List<StoragePool>> vplexPoolMap = new HashMap<String, List<StoragePool>>();

        // group the pools by system
        Map<URI, List<StoragePool>> poolsBySystem = getPoolsBySystem(storagePools);
        for (URI systemUri : poolsBySystem.keySet()) {
            // for each system, find the associated vplexes in the requested varray
            Set<URI> vplexSystemURIs = ConnectivityUtil
                    .getVPlexSystemsAssociatedWithArray(_dbClient, systemUri,
                            new HashSet<String>(Arrays.asList(varrayId)), cluster);
            for (URI vplexUri : vplexSystemURIs) {
                StorageSystem vplexSystem = _dbClient.queryObject(StorageSystem.class, vplexUri);
                String vplexId = vplexUri.toString();
                // fill the map
                if (vplexSystem != null) {
                    if (!vplexPoolMap.containsKey(vplexId)) {
                        List<StoragePool> vplexPoolList = new ArrayList<StoragePool>();
                        vplexPoolList.addAll(poolsBySystem.get(systemUri));
                        vplexPoolMap.put(vplexId, vplexPoolList);
                    } else {
                        List<StoragePool> vplexPoolList = vplexPoolMap.get(vplexId);
                        vplexPoolList.addAll(poolsBySystem.get(systemUri));
                    }
                }
            }
        }
        return vplexPoolMap;
    }

    /**
     * Creates and returns a map of storage pools grouped by storage system
     * 
     * @param storagePools a list of storage pools
     * @return a map of storage pools grouped by storage system
     */
    private Map<URI, List<StoragePool>> getPoolsBySystem(List<StoragePool> storagePools) {
        Map<URI, List<StoragePool>> map = new HashMap<URI, List<StoragePool>>();
        for (StoragePool storagePool : storagePools) {
            if (!map.containsKey(storagePool.getStorageDevice())) {
                map.put(storagePool.getStorageDevice(), new ArrayList<StoragePool>());
            }
            map.get(storagePool.getStorageDevice()).add(storagePool);
        }
        return map;
    }

    /**
     * Gets the List of storage systems with the passed ids.
     * 
     * @param storageSystemIds The storage system ids.
     * 
     * @return A list of the storage systems.
     */
    private List<StorageSystem> getStorageSystemsWithIds(Set<String> storageSystemIds) {
        List<StorageSystem> storageSystems = new ArrayList<StorageSystem>();
        for (String storageSystemId : storageSystemIds) {
            storageSystems.add(_dbClient.queryObject(StorageSystem.class,
                    URI.create(storageSystemId)));
        }
        return storageSystems;
    }

    /**
     * Get the Ids of the varrays for the passed storage pools.
     * 
     * @param storagePools The list of storage pools.
     * 
     * @return A list of the ids for the storage pool varrays.
     */
    private List<String> getNeighborhoodsForPools(List<StoragePool> storagePools) {
        List<String> poolNHIds = new ArrayList<String>();
        for (StoragePool storagePool : storagePools) {
            StringSet nhIds = storagePool.getTaggedVirtualArrays();
            if (nhIds == null) {
                continue;
            }
            for (String nhId : nhIds) {
                if (!poolNHIds.contains(nhId)) {
                    poolNHIds.add(nhId);
                }
            }
        }

        return poolNHIds;
    }

    /**
     * Sets the Id of the VPlex storage system into the passed recommendations.
     * 
     * @param vplexStorageSystemId The id of the VPlex storage system.
     * @param varray The varray for the recommendation.
     * @param vpool The vpool for the recommendation.
     * @param recommendations The list of recommendations.
     */
    protected List<VPlexRecommendation> createVPlexRecommendations(
            String vplexStorageSystemId, VirtualArray varray, VirtualPool vpool,
            List<Recommendation> recommendations) {

        List<VPlexRecommendation> vplexRecommendations = new ArrayList<VPlexRecommendation>();
        for (Recommendation recommendation : recommendations) {
            VPlexRecommendation vplexRecommendation = new VPlexRecommendation();
            vplexRecommendation.setSourceStorageSystem(recommendation.getSourceStorageSystem());
            vplexRecommendation.setSourceStoragePool(recommendation.getSourceStoragePool());
            vplexRecommendation.setResourceCount(recommendation.getResourceCount());
            //vplexRecommendation.setSourceDevice(URI.create(vplexStorageSystemId));
            vplexRecommendation.setVPlexStorageSystem(URI.create(vplexStorageSystemId));
            vplexRecommendation.setVirtualArray(varray.getId());
            vplexRecommendation.setVirtualPool(vpool);
            vplexRecommendations.add(vplexRecommendation);
        }

        return vplexRecommendations;
    }
    
    protected List<VPlexRecommendation> createVPlexRecommendations(
            List<Recommendation> baseRecommendations, 
            String vplexStorageSystemId, VirtualArray varray, VirtualPool vpool) {
        List<VPlexRecommendation> vplexRecommendations = new ArrayList<VPlexRecommendation>();
            
        for (Recommendation recommendation : baseRecommendations) {
            VPlexRecommendation vplexRecommendation = new VPlexRecommendation();
            vplexRecommendation.setSourceStorageSystem(recommendation.getSourceStorageSystem());
            vplexRecommendation.setSourceStoragePool(recommendation.getSourceStoragePool());
            vplexRecommendation.setResourceCount(recommendation.getResourceCount());
            vplexRecommendation.setVPlexStorageSystem(URI.create(vplexStorageSystemId));
            vplexRecommendation.setVirtualArray(varray.getId());
            vplexRecommendation.setVirtualPool(vpool);
            vplexRecommendation.setRecommendation(recommendation);
            vplexRecommendations.add(vplexRecommendation);
        }
        return vplexRecommendations;
    }

    /**
     * Gets the HA virtual array if the volume creation request is for HA
     * volumes.
     * 
     * @param vArray The source virtual array.
     * @param project A reference to the project.
     * @param vPool The HA virtual pool.
     * @return the HA virtual array
     */
    protected VirtualArray getHaVirtualArray(VirtualArray vArray, Project project, VirtualPool vPool) {
        // Determine if the volume creation request is for HA volumes.
        boolean isHAVolumeRequest = VirtualPool.HighAvailabilityType.vplex_distributed.name()
                .equals(vPool.getHighAvailability());

        // Get and validate the high availability VirtualArray. The HA Virtual Array
        // is optional. When not specified, the high availability VirtualArray will
        // be selected by the placement logic.
        VirtualArray haVArray = null;
        StringMap haVaVpMap = vPool.getHaVarrayVpoolMap();
        if ((isHAVolumeRequest) && (haVaVpMap != null)) {
            _log.info("Is HA request and with an HA VirtualArray VirtualPool map");
            for (String haVaId : haVaVpMap.keySet()) {
                _log.info("HA VirtualArray is {}", haVaId);
                if (!haVaId.equals(NullColumnValueGetter.getNullURI().toString())) {
                    _log.info("HA VirtualArray is not a null URI");
                    haVArray = getVirtualArrayForVolumeCreateRequest(project, URI.create(haVaId));

                    if (vArray.getId().toString().equals(haVArray.getId().toString())) {
                        throw APIException.badRequests.sameVirtualArrayAndHighAvailabilityArray();
                    }
                }
            }
        }

        return haVArray;
    }

    /**
     * Gets the HA virtual pool if the volume creation request is for HA
     * volumes.
     * 
     * @param vArray The source virtual array.
     * @param project A reference to the project.
     * @param vPool The HA virtual pool.
     * @return the HA virtual pool
     */
    protected VirtualPool getHaVirtualPool(VirtualArray vArray, Project project, VirtualPool vPool) {
        // Determine if the volume creation request is for HA volumes.
        boolean isHAVolumeRequest = VirtualPool.HighAvailabilityType.vplex_distributed.name()
                .equals(vPool.getHighAvailability());

        // Get and validate the high availability VirtualPool. This is optional.
        // If no VirtualPool is specified for the HA VirtualArray, then the
        // passed VirtualPool is use.
        VirtualPool haVPool = vPool;
        StringMap haVaVpMap = vPool.getHaVarrayVpoolMap();
        if ((isHAVolumeRequest) && (haVaVpMap != null)) {
            _log.info("Is HA request and with an HA VirtualArray VirtualPool map");
            for (String haVaId : haVaVpMap.keySet()) {
                _log.info("HA VirtualArray is {}", haVaId);

                // Now get the VirtualPool.
                String haVpId = haVaVpMap.get(haVaId);
                _log.info("HA VirtualPool is {}", haVpId);
                if (!haVpId.equals(NullColumnValueGetter.getNullURI().toString())) {
                    _log.info("HA VirtualPool is not a null URI");
                    haVPool = BlockService.getVirtualPoolForRequest(project, URI.create(haVpId),
                            _dbClient, _permissionsHelper);
                }
            }
        }

        return haVPool;
    }

    @Override
    public Set<List<Recommendation>> getRecommendationsForVpool(VirtualArray vArray, Project project, VirtualPool vPool, VpoolUse vPoolUse,
            VirtualPoolCapabilityValuesWrapper capabilities, Map<VpoolUse, List<Recommendation>> currentRecommendations) {
        _log.info("Getting recommendations for VPlex volume placement");
        
        Set<List<Recommendation>> recommendationSet = new HashSet<List<Recommendation>>();
       

        // Validate the VirtualPool specifies VPlex high availability, which
        // currently is the only supported means for creating high
        // availability volumes.
        if (!VirtualPool.vPoolSpecifiesHighAvailability(vPool)) {
            throw APIException.badRequests.invalidHighAvailability(vPool.getHighAvailability());
        }

        _log.info("VirtualPool has high availability {}", vPool.getHighAvailability());

        Set<URI> vplexSystemsForPlacement = new HashSet<URI>();
        if (vPoolUse == VpoolUse.ROOT) {
            // For now only validate that we're using the same vplex systems on the ROOT request
            vplexSystemsForPlacement = getVPlexSystemsForPlacement(vArray, vPool, capabilities);
        }

        // Determine if the volume creation request is for HA volumes.
        boolean isHAVolumeRequest = VirtualPool.vPoolSpecifiesHighAvailabilityDistributed(vPool);

        // Get and validate the high availability VirtualArray and VirtualPool.
        // Note that the HA VirtualPool is optional. When not specified, the
        // high availability VirtualPool is the passed VirtualPool is use.
        VirtualPool haVPool = vPool;
        VirtualArray haVArray = null;
        StringMap haVaVpMap = vPool.getHaVarrayVpoolMap();
        if ((isHAVolumeRequest) && (haVaVpMap != null)) {
            _log.info("Is HA request and with an HA VirtualArray VirtualPool map");
            Iterator<String> vaIter = haVaVpMap.keySet().iterator();
            while (vaIter.hasNext()) {
                String haVaId = vaIter.next();
                _log.info("HA VirtualArray is {}", haVaId);
                if (!haVaId.equals(NullColumnValueGetter.getNullURI().toString())) {
                    _log.info("HA VirtualArray is not a null URI");
                    haVArray = getVirtualArrayForVolumeCreateRequest(project, URI.create(haVaId));

                    if (vArray.getId().toString().equals(haVArray.getId().toString())) {
                        throw APIException.badRequests.sameVirtualArrayAndHighAvailabilityArray();
                    }
                }

                // Now get the VirtualPool.
                String haVpId = haVaVpMap.get(haVaId);
                _log.info("HA VirtualPool is {}", haVpId);
                if (!haVpId.equals(NullColumnValueGetter.getNullURI().toString())) {
                    _log.info("HA VirtualPool is not a null URI");
                    haVPool = BlockService.getVirtualPoolForRequest(project, URI.create(haVpId),
                            _dbClient, _permissionsHelper);
                }
            }
        }

        // Get the volume placement based on passed parameters.
        _log.info("VirtualPool: {}, HA VirtualPool: {}", vPool.getId().toString(), haVPool.getId()
                .toString());
        List<Recommendation> recommendations = scheduleStorage(
                vArray, vplexSystemsForPlacement, null, vPool, isHAVolumeRequest, haVArray, haVPool, 
                capabilities, project, vPoolUse, currentRecommendations);

        recommendationSet.add(recommendations);
        return recommendationSet;
    }

}