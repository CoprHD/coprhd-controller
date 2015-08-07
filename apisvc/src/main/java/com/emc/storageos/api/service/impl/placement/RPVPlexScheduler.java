/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.RPSiteArray;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.block.VirtualPoolChangeParam;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.Protection;
import com.emc.storageos.volumecontroller.RPProtectionRecommendation;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.VPlexProtection;
import com.emc.storageos.volumecontroller.VPlexProtectionRecommendation;
import com.emc.storageos.volumecontroller.VPlexRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.volumecontroller.impl.utils.attrmatchers.CapacityMatcher;
import com.google.common.base.Joiner;
//import com.emc.storageos.api.service.impl.placement.RecoverPointScheduler.ProtectionPoolMapping;
import com.google.common.collect.Lists;

public class RPVPlexScheduler implements Scheduler {

    public static final Logger _log = LoggerFactory.getLogger(RPVPlexScheduler.class);

    @Autowired
    protected PermissionsHelper permissionsHelper = null;

    private VPlexScheduler vplexScheduler;
    private RecoverPointScheduler recoverPointScheduler;
    private DbClient dbClient;
    private StorageScheduler blockScheduler;
    private RPHelper rpHelper;
    private Map<String, List<String>> storagePoolStorageSystemCache;
    private Map<VirtualArray, Boolean> tgtVarrayHasHaVpool =
            new HashMap<VirtualArray, Boolean>();
    private List<Recommendation> srcVPlexHaRecommendations =
            new ArrayList<Recommendation>();
    private Map<URI, List<Recommendation>> tgtVPlexHaRecommendations =
            new HashMap<URI, List<Recommendation>>();

    public void setRpHelper(RPHelper rpHelper) {
        this.rpHelper = rpHelper;
    }

    public void setBlockScheduler(StorageScheduler blockScheduler) {
        this.blockScheduler = blockScheduler;
    }

    public StorageScheduler getBlockScheduler() {
        return blockScheduler;
    }

    public VPlexScheduler getVplexScheduler() {
        return vplexScheduler;
    }

    public void setVplexScheduler(VPlexScheduler vplexScheduler) {
        this.vplexScheduler = vplexScheduler;
    }

    public RecoverPointScheduler getRecoverPointScheduler() {
        return recoverPointScheduler;
    }

    public void setRecoverPointScheduler(RecoverPointScheduler recoverPointScheduler) {
        this.recoverPointScheduler = recoverPointScheduler;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public class RPVPlexVarrayVpool {
        private VirtualArray srcVarray = null;
        private VirtualPool srcVpool = null;
        private VirtualArray haVarray = null;
        private VirtualPool haVpool = null;

        public VirtualArray getSrcVarray() {
            return srcVarray;
        }

        public void setSrcVarray(VirtualArray srcVarray) {
            this.srcVarray = srcVarray;
        }

        public VirtualPool getSrcVpool() {
            return srcVpool;
        }

        public void setSrcVpool(VirtualPool srcVpool) {
            this.srcVpool = srcVpool;
        }

        public VirtualArray getHaVarray() {
            return haVarray;
        }

        public void setHaVarray(VirtualArray haVarray) {
            this.haVarray = haVarray;
        }

        public VirtualPool getHaVpool() {
            return haVpool;
        }

        public void setHaVpool(VirtualPool haVpool) {
            this.haVpool = haVpool;
        }
    }

    @Override
    public List<Recommendation> getRecommendationsForResources(VirtualArray srcVarray,
            Project project, VirtualPool srcVpool,
            VirtualPoolCapabilityValuesWrapper srcVpoolCapabilities) {
        _log.info("Getting recommendations for RP + VPLEX volume placement...");
        this.initResources();

        RPVPlexVarrayVpool container = this.swapSrcAndHAIfNeeded(srcVarray, srcVpool);

        List<StoragePool> srcCandidateStoragePools = getSourceCandidatePools(container.getSrcVarray(), container.getSrcVpool(),
                container.getHaVarray(), container.getHaVpool(),
                project, srcVpoolCapabilities);

        // Obtain a list of RP protection Virtual Arrays.
        List<VirtualArray> tgtVarrays =
                RecoverPointScheduler.getProtectionVirtualArraysForVirtualPool(
                        project, container.getSrcVpool(), dbClient, permissionsHelper);

        List<Recommendation> recommendations = null;

        if (VirtualPool.vPoolSpecifiesMetroPoint(srcVpool)) {
            // MetroPoint has been enabled. get the HA virtual array and virtual pool. This will allow us to obtain
            // candidate storage pool and secondary cluster protection recommendations.
            VirtualArray haVarray = vplexScheduler.getHaVirtualArray(container.getSrcVarray(), project, container.getSrcVpool());
            VirtualPool haVpool = vplexScheduler.getHaVirtualPool(container.getSrcVarray(), project, container.getSrcVpool());

            // Get the candidate source pools for the distributed cluster. The 2 null params are ignored in the pool matching
            // because they are used to build the HA recommendations, which will not be done if MetroPoint is enabled.
            List<StoragePool> haCandidateStoragePools = getSourceCandidatePools(haVarray, haVpool,
                    null, null, project, srcVpoolCapabilities);

            // MetroPoint has been enabled so we need to obtain recommendations for the primary (active) and secondary (HA/Stand-by)
            // VPlex clusters.
            recommendations = createMetroPointRecommendations(container.getSrcVarray(), tgtVarrays, container.getSrcVpool(), haVarray,
                    haVpool, project, srcVpoolCapabilities, srcCandidateStoragePools, haCandidateStoragePools, null, null);
        } else {
            recommendations =
                    scheduleStorageSourcePoolConstraint(container.getSrcVarray(), tgtVarrays, container.getSrcVpool(), project,
                            srcVpoolCapabilities,
                            srcCandidateStoragePools, null, null);
        }

        if (recommendations != null && !recommendations.isEmpty()) {
            _log.info("Created VPlex Protection recommendations:\n");
            for (Recommendation rec : recommendations) {
                VPlexProtectionRecommendation protectionRec = (VPlexProtectionRecommendation) rec;
                _log.info(protectionRec.toString(dbClient));
            }
        }

        return recommendations;
    }

    /**
     * This method is driven by the flag on the RP+VPLEX Source Vpool to use HA as RP Source or not.
     * When that flag is set we use the HA Varray and HA Vpool as the Sources for placement. Consequently,
     * the Source Varray and Source VPool are then used for HA placement.
     * 
     * We may not need to swap if the flag isn't set, but this method returns the RPVPlexVarryVpool object
     * regardless so we can then just reference the resources in that object to pass down to the rest of the
     * scheduler methods.
     * 
     * @param srcVarray Original src varray
     * @param srcVpool Original src vpool
     * @return RPVPlexVarryVpool which contains references to the src varray, src vpool, ha varray, ha vpool
     */
    private RPVPlexVarrayVpool swapSrcAndHAIfNeeded(VirtualArray srcVarray,
            VirtualPool srcVpool) {
        VirtualArray haVarray = null;
        VirtualPool haVpool = null;

        RPVPlexVarrayVpool container = new RPVPlexVarrayVpool();
        container.setSrcVarray(srcVarray);
        container.setSrcVpool(srcVpool);
        container.setHaVarray(haVarray);
        container.setHaVpool(haVpool);

        // Potentially will swap src and ha, returns the container.
        container = setActiveProtectionAtHAVarray(container, dbClient);

        return container;
    }

    /**
     * Initialize common resources
     */
    private void initResources() {
        // initialize the storage pool -> storage systems map
        this.storagePoolStorageSystemCache = new HashMap<String, List<String>>();
        // Reset the HA Recommendations
        this.srcVPlexHaRecommendations = new ArrayList<Recommendation>();
        this.tgtVPlexHaRecommendations = new HashMap<URI, List<Recommendation>>();
        this.tgtVarrayHasHaVpool = new HashMap<VirtualArray, Boolean>();
    }

    /**
     * 
     * @param srcVarray
     * @param srcVpool
     * @param project
     * @param srcVpoolCapabilities
     * @return
     */
    private List<StoragePool> getSourceCandidatePools(VirtualArray srcVarray, VirtualPool srcVpool,
            VirtualArray haVarray, VirtualPool haVpool,
            Project project, VirtualPoolCapabilityValuesWrapper srcVpoolCapabilities) {

        List<StoragePool> srcCandidateStoragePools = new ArrayList<StoragePool>();

        // Determine if the source vpool specifies VPlex Protection
        if (VirtualPool.vPoolSpecifiesHighAvailability(srcVpool)) {
            srcCandidateStoragePools =
                    this.getMatchingPools(srcVarray, srcVpool, haVarray, haVpool, project, srcVpoolCapabilities);
        }
        else {
            srcCandidateStoragePools =
                    blockScheduler.getMatchingPools(srcVarray, srcVpool, srcVpoolCapabilities);
        }

        if (srcCandidateStoragePools == null || srcCandidateStoragePools.isEmpty()) {
            // There are no matching storage pools found for the source varray
            _log.error("No matching storage pools found for the source varray: {0}. There are no storage pools that " +
                    "match the passed vpool parameters and protocols and/or there are no pools that have enough capacity to " +
                    "hold at least one resource of the requested size.", srcVarray.getLabel());
            throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(srcVpool.getId(), srcVarray.getId());
        }

        // Verify that any storage pool(s) requiring a VPLEX front end for data protection have
        // HA enabled on the vpool, if not remove the storage pool(s) from consideration.
        srcCandidateStoragePools = recoverPointScheduler.removePoolsRequiringHaIfNotEnabled(srcCandidateStoragePools, srcVpool,
                RPHelper.SOURCE);

        return srcCandidateStoragePools;
    }

    /**
     * Retrieve valid Storage Pools from the VPLEX Scheduler.
     * 
     * @param srcVarray
     * @param srcVpool
     * @param haVpool
     * @param haVarray
     * @param srcVpoolCapabilities
     * @return
     */
    private List<StoragePool> getMatchingPools(VirtualArray srcVarray, VirtualPool srcVpool, VirtualArray haVarray, VirtualPool haVpool,
            Project project, VirtualPoolCapabilityValuesWrapper srcVpoolCapabilities) {
        List<StoragePool> srcCandidateStoragePools = new ArrayList<StoragePool>();

        _log.info("Get matching pools for Varray[{}] and Vpool[{}]...", srcVarray.getLabel(), srcVpool.getLabel());
        List<StoragePool> allMatchingPools = vplexScheduler.getMatchingPools(srcVarray, null,
                srcVpool, srcVpoolCapabilities);

        _log.info("Get VPlex systems for placement...");
        // TODO Fixing CTRL-3360 since it's a blocker, will revisit this after. This VPLEX
        // method isn't doing what it indicates. It's looking at the CG to see if it's created
        // or not for VPLEX. Which is unneeded for RP+VPLEX.
        // Set<URI> requestedVPlexSystems =
        // vplexScheduler.getVPlexSystemsForPlacement(srcVarray, srcVpool, srcVpoolCapabilities);
        Set<URI> requestedVPlexSystems = null;

        _log.info("Get VPlex connected matching pools...");
        // Get the VPLEX connected storage pools
        Map<String, List<StoragePool>> vplexPoolMapForSrcVarray =
                vplexScheduler.getVPlexConnectedMatchingPools(srcVarray, requestedVPlexSystems,
                        srcVpoolCapabilities, allMatchingPools);

        if (vplexPoolMapForSrcVarray != null && !vplexPoolMapForSrcVarray.isEmpty()) {
            _log.info("Remove non RP connected storage pools...");
            // We only care about RP-connected VPLEX storage systems
            vplexPoolMapForSrcVarray = getRPConnectedVPlexStoragePools(vplexPoolMapForSrcVarray);

            if (vplexPoolMapForSrcVarray.isEmpty()) {
                // There are no RP connected VPLEX storage systems so we cannot provide
                // any placement recommendations.
                _log.info("No matching pools because there are no VPlex connected storage systems for the requested virtual array.");
                return null;
            }

            // Add all the appropriately matched source storage pools
            for (String vplexId : vplexPoolMapForSrcVarray.keySet()) {
                srcCandidateStoragePools.addAll(vplexPoolMapForSrcVarray.get(vplexId));
            }

            _log.info("VPLEX pools matching completed: {}",
                    Joiner.on("\t").join(getURIsFromPools(srcCandidateStoragePools)));
        }
        else {
            // There are no matching pools in the source virtual array
            // on any arrays connected to a VPlex storage system
            // or there are, but a specific VPlex system was requested
            // and there are none for that VPlex system.
            _log.info("No matching pools on storage systems connected to a VPlex");
            return null;
        }

        // If the source Vpool specifies VPlex, we need to check if this is VPLEX local or VPLEX
        // distributed. If it's VPLEX distributed, there will be a separate recommendation just for that
        // which will be used by VPlexBlockApiService to create the distributed volumes in VPLEX.

        // If HA / VPlex Distributed is specified we need to get the VPLEX recommendations
        boolean isVplexDistributed = VirtualPool.HighAvailabilityType.vplex_distributed.name()
                .equals(srcVpool.getHighAvailability());
        boolean metroPointEnabled = VirtualPool.vPoolSpecifiesMetroPoint(srcVpool);

        // Only find the HA recommendations if MetroPoint is not enabled. The HA/secondary cluster
        // Recommendations for MetroPoint need to involve RP connectivity so there is no sense executing
        // this logic.
        if (isVplexDistributed && !metroPointEnabled) {
            this.srcVPlexHaRecommendations = findVPlexHARecommendations(srcVarray, srcVpool,
                    haVarray, haVpool,
                    project, srcVpoolCapabilities,
                    vplexPoolMapForSrcVarray);
            if (this.srcVPlexHaRecommendations == null ||
                    this.srcVPlexHaRecommendations.isEmpty()) {
                _log.error("No HA Recommendations could be created.");
                throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(srcVpool.getId(), srcVarray.getId());
            }
        }

        return srcCandidateStoragePools;
    }

    /**
     * For debug purpose, which lists the matched Pool URIs
     * 
     * @param pools
     * @return
     */
    protected List<URI> getURIsFromPools(List<StoragePool> pools) {
        List<URI> poolURIList = new ArrayList<URI>();
        for (StoragePool pool : pools) {
            poolURIList.add(pool.getId());
        }
        return poolURIList;
    }

    /**
     * 
     * @param srcVpool
     * @param srcVarray
     * @param project
     * @param srcVpoolCapabilities
     * @param vplexPoolMapForSrcVarray
     * @return
     */
    private List<Recommendation> findVPlexHARecommendations(VirtualArray srcVarray, VirtualPool srcVpool,
            VirtualArray haVarray, VirtualPool haVpool,
            Project project,
            VirtualPoolCapabilityValuesWrapper srcVpoolCapabilities,
            Map<String, List<StoragePool>> vplexPoolMapForSrcVarray) {
        List<Recommendation> vplexHaVArrayRecommendations = null;

        if (haVarray == null) {
            haVarray = vplexScheduler.getHaVirtualArray(srcVarray, project, srcVpool);
        }
        if (haVpool == null) {
            haVpool = vplexScheduler.getHaVirtualPool(srcVarray, project, srcVpool);
        }

        vplexHaVArrayRecommendations = convertHARecommendations(
                getAllHARecommendations(
                        srcVarray, srcVpool,
                        haVarray, haVpool,
                        srcVpoolCapabilities,
                        vplexPoolMapForSrcVarray));

        return vplexHaVArrayRecommendations;
    }

    /**
     * Gets all the HA placement recommendations.
     * 
     * @param srcVarray The source virtual array
     * @param srcVpool The source virtual pool
     * @param requestedHaVarray The requested highly available virtual array
     * @param haVpool The highly available virtual pool
     * @param capabilities The virtual pool capabilities
     * @param vplexPoolMapForSrcVarray The source virtual array, VPlex connected storage pools
     * @param srcStorageSystem The selected VPlex source leg storage system
     * @param isRpTarget true if the request is specific to a RecoverPoint target, false otherwise
     * 
     * @return A list of VPlexRecommendation instances specifying the
     *         HA recommended resource placement resources
     */
    protected List<Recommendation> getAllHARecommendations(VirtualArray srcVarray,
            VirtualPool srcVpool, VirtualArray requestedHaVarray, VirtualPool haVpool,
            VirtualPoolCapabilityValuesWrapper capabilities,
            Map<String, List<StoragePool>> vplexPoolMapForSrcVarray) {

        _log.info("Executing VPlex high availability placement strategy");

        // Initialize the list of recommendations.
        List<Recommendation> recommendations = new ArrayList<Recommendation>();

        // The list of potential VPlex storage systems.
        Set<String> vplexStorageSystemIds = vplexPoolMapForSrcVarray.keySet();
        _log.info("{} VPlex storage systems have matching pools",
                vplexStorageSystemIds.size());

        // For an HA request, get the possible high availability varrays
        // for each potential VPlex storage system.
        Map<String, List<String>> vplexHaVarrayMap = ConnectivityUtil.getVPlexVarrays(
                dbClient, vplexStorageSystemIds, srcVarray.getId());

        // Note that both the high availability VirtualArray and VirtualPool are optional.
        // When not specified, the high availability VirtualArray will be selected by the placement
        // logic. If no VirtualPool is specified for the HA VirtualArray, then the
        // passed VirtualPool is use.
        if (haVpool == null) {
            haVpool = srcVpool;
        }

        _log.info("Requested HA varray is {}", (requestedHaVarray != null ? requestedHaVarray.getId()
                : "not specified"));

        // Loop over the potential VPlex storage systems, and attempt
        // to place the resources.
        Iterator<String> vplexSystemIdsIter = vplexStorageSystemIds.iterator();
        while (vplexSystemIdsIter.hasNext()) {
            String vplexStorageSystemId = vplexSystemIdsIter.next();
            _log.info("Check matching pools for VPlex {}", vplexStorageSystemId);

            // Check if the resource can be placed on the matching
            // pools for this VPlex storage system.
            if (VirtualPool.ProvisioningType.Thin.toString()
                    .equalsIgnoreCase(srcVpool.getSupportedProvisioningType())) {
                capabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.TRUE);
            }

            // Otherwise we now have to see if there is an HA varray
            // for the VPlex that also contains pools suitable to place
            // the resources.
            List<String> vplexHaVarrayIds = vplexHaVarrayMap.get(vplexStorageSystemId);
            _log.info("Found {} HA varrays", vplexHaVarrayIds.size());
            for (String vplexHaVarrayId : vplexHaVarrayIds) {
                _log.info("Check HA varray {}", vplexHaVarrayId);
                // If a specific HA varray was specified and this
                // varray is not it, then skip the varray.
                if ((requestedHaVarray != null)
                        && (!vplexHaVarrayId.equals(requestedHaVarray.getId().toString()))) {
                    _log.info("Not the requested HA varray, skip");
                    continue;
                }

                // Get all storage pools that match the passed VirtualPool params,
                // and this HA VirtualArray. In addition, the
                // pool must have enough capacity to hold at least one
                // resource of the requested size.
                VirtualArray vplexHaVarray = dbClient.queryObject(VirtualArray.class,
                        URI.create(vplexHaVarrayId));

                List<StoragePool> allMatchingPoolsForHaVarray = vplexScheduler.getMatchingPools(
                        vplexHaVarray, null, haVpool, capabilities);
                _log.info("Found {} matching pools for HA varray", allMatchingPoolsForHaVarray.size());

                // Now from the list of candidate pools, we only want pools
                // on storage systems that are connected to the VPlex
                // storage system. We find these storage pools and associate
                // them to the VPlex storage systems to which their storage
                // system is connected.
                Map<String, List<StoragePool>> vplexPoolMapForHaVarray =
                        vplexScheduler.sortPoolsByVPlexStorageSystem(allMatchingPoolsForHaVarray, vplexHaVarrayId);

                // If the HA varray has candidate pools for this
                // VPlex, see if the candidate pools in this HA
                // varray are sufficient to place the resources.
                List<Recommendation> recommendationsForHaVarray = new ArrayList<Recommendation>();
                if (vplexPoolMapForHaVarray.containsKey(vplexStorageSystemId)) {
                    _log.info("Found matching pools in HA varray for VPlex {}",
                            vplexStorageSystemId);
                    if (VirtualPool.ProvisioningType.Thin.toString()
                            .equalsIgnoreCase(haVpool.getSupportedProvisioningType())) {
                        capabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.TRUE);
                    }
                    recommendationsForHaVarray = blockScheduler.getRecommendationsForPools(vplexHaVarray.getId().toString(),
                            vplexPoolMapForHaVarray.get(vplexStorageSystemId), capabilities);
                } else {
                    _log.info("No matching pools in HA varray for VPlex {}",
                            vplexStorageSystemId);
                }

                // We have recommendations for pools in both
                // the source and HA varrays.
                if (!recommendationsForHaVarray.isEmpty()) {
                    _log.info("Matching pools in HA varray sufficient for placement.");
                    recommendations.addAll(vplexScheduler.createVPlexRecommendations(
                            vplexStorageSystemId, vplexHaVarray, haVpool, recommendationsForHaVarray));
                }

                // If we found recommendations for this HA varray
                // or we did not, but the user specifically requested a
                // HA varray, then we are done checking the HA
                // varrays for this VPlex.
                if (!recommendations.isEmpty() || (requestedHaVarray != null)) {
                    _log.info("Done trying to place resource for VPlex.");
                    break;
                }
            }
        }

        return recommendations;
    }

    /**
     * Utility method used to convert an RPProtectionRecommendation to a VPlexProteciontRecommendation.
     * 
     * @param rpRec the RPProtectionRecommendation to convert.
     * @param varray the VirtualArray.
     * @param vpool the VirtualPool.
     * @param haVpool the HA VirtualPool. This param can be null if there is no HA vpool specified.
     * @return the converted recommendation.
     */
    private VPlexProtectionRecommendation convertRPProtectionRecommendation(RPProtectionRecommendation rpRec, VirtualArray varray,
            VirtualPool vpool, VirtualPool haVpool) {
        VPlexProtectionRecommendation vplexProtectionRec = new VPlexProtectionRecommendation();
        vplexProtectionRec.setSourceInternalSiteName(rpRec.getSourceInternalSiteName());
        vplexProtectionRec.setSourceDevice(rpRec.getSourceDevice());
        vplexProtectionRec.setSourcePool(rpRec.getSourcePool());
        vplexProtectionRec.setSourceJournalVarray(rpRec.getSourceJournalVarray());
        vplexProtectionRec.setSourceJournalVpool(rpRec.getSourceJournalVpool());
        vplexProtectionRec.setSourceJournalStoragePool(rpRec.getSourceJournalStoragePool());
        vplexProtectionRec.setProtectionDevice(rpRec.getProtectionDevice());
        vplexProtectionRec.setVpoolChangeVolume(rpRec.getVpoolChangeVolume());
        vplexProtectionRec.setVpoolChangeVpool(rpRec.getVpoolChangeVpool());
        vplexProtectionRec.setVpoolChangeProtectionAlreadyExists(rpRec.isVpoolChangeProtectionAlreadyExists());
        vplexProtectionRec.setResourceCount(rpRec.getResourceCount());
        vplexProtectionRec.setVirtualArrayProtectionMap(rpRec.getVirtualArrayProtectionMap());
        vplexProtectionRec.setRpSiteAssociateStorageSystem(rpRec.getRpSiteAssociateStorageSystem());

        if (rpRec.getStandbySourceJournalVarray() != null) {
            vplexProtectionRec.setStandbySourceJournalVarray(rpRec.getStandbySourceJournalVarray());
        }

        if (rpRec.getStandbySourceJournalVpool() != null) {
            vplexProtectionRec.setStandbySourceJournalVpool(rpRec.getStandbySourceJournalVpool());
        }

        vplexProtectionRec.setVPlexStorageSystem(rpRec.getSourceInternalSiteStorageSystem());

        vplexProtectionRec.setVirtualArray(varray.getId());

        if (haVpool != null) {
            vplexProtectionRec.setVirtualPool(haVpool);
        } else {
            vplexProtectionRec.setVirtualPool(vpool);
        }

        vplexProtectionRec.setVarrayVPlexProtection(new HashMap<URI, VPlexProtection>());

        List<URI> tgtVarraysToRemove = new ArrayList<URI>();

        for (Entry<URI, Protection> entry : vplexProtectionRec.getVirtualArrayProtectionMap().entrySet()) {
            URI tgtVarrayURI = entry.getKey();
            Protection rpProtection = entry.getValue();
            VirtualArray targetVarray = dbClient.queryObject(VirtualArray.class, tgtVarrayURI);
            VirtualPool targetVpool = getTargetVirtualPool(targetVarray, vpool);

            // Populate the VarrayVPlexProtection map if this target vpool specifies RP+VPLEX or MetroPoint
            if (VirtualPool.vPoolSpecifiesHighAvailability(targetVpool)) {
                VPlexProtection vplexProtection = new VPlexProtection();
                vplexProtection.setTargetVplexDevice(rpProtection.getTargetInternalSiteStorageSystem());
                vplexProtection.setTargetVarray(tgtVarrayURI);
                vplexProtection.setTargetVpool(targetVpool);
                vplexProtection.setTargetInternalSiteName(rpProtection.getTargetInternalSiteName());
                vplexProtection.setTargetVPlexHaRecommendations(tgtVPlexHaRecommendations.get(tgtVarrayURI));
                vplexProtection.setTargetDevice(rpProtection.getTargetDevice());
                vplexProtection.setTargetStoragePool(rpProtection.getTargetStoragePool());
                vplexProtection.setTargetJournalDevice(rpProtection.getTargetJournalDevice());
                vplexProtection.setTargetJournalVarray(rpProtection.getTargetJournalVarray());
                vplexProtection.setTargetJournalVpool(rpProtection.getTargetJournalVpool());
                vplexProtection.setTargetJournalStoragePool(rpProtection.getTargetJournalStoragePool());
                StoragePool tgtJrnlPool = dbClient.queryObject(StoragePool.class, rpProtection.getTargetJournalStoragePool());
                vplexProtection.setTargetJournalDevice(tgtJrnlPool.getStorageDevice());
                vplexProtection.setProtectionType(rpProtection.getProtectionType());

                // Add this vplex protection to the actual recommendation
                vplexProtectionRec.getVarrayVPlexProtection().put(tgtVarrayURI, vplexProtection);

                // This is a VPlex Protection, track it so it can be removed from the regular protection map
                tgtVarraysToRemove.add(tgtVarrayURI);
            }
        }

        // Remove the VPlex protection from the regular protection map
        for (URI uri : tgtVarraysToRemove) {
            vplexProtectionRec.getVirtualArrayProtectionMap().remove(uri);
        }

        return vplexProtectionRec;
    }

    /**
     * Creates recommendations for MetroPoint. This consists of single recommendations that include
     * the both the primary and secondary HA clusters and their associated RP protection details.
     * 
     * @param srcVarray the source virtual array.
     * @param tgtVarrays the target protection virtual arrays.
     * @param srcVpool the source virtual pool.
     * @param haVarray the HA (second cluster) virtual array.
     * @param haVpool the HA (second cluster) virtual array.
     * @param project the project.
     * @param srcVpoolCapabilities the capability params.
     * @param candidatePrimaryPools candidate source pools to use for the primary cluster.
     * @param candidateSecondaryPools candidate source pools to use for the primary cluster.
     * @return list of Recommendation objects to satisfy the request
     */
    private List<Recommendation> createMetroPointRecommendations(VirtualArray srcVarray, List<VirtualArray> tgtVarrays,
            VirtualPool srcVpool,
            VirtualArray haVarray, VirtualPool haVpool, Project project, VirtualPoolCapabilityValuesWrapper srcVpoolCapabilities,
            List<StoragePool> candidatePrimaryPools, List<StoragePool> candidateSecondaryPools, Volume vpoolChangeVolume,
            VirtualPoolChangeParam vpoolChangeParam) {
        // Initialize a list of recommendations to be returned.
        List<Recommendation> recommendations = new ArrayList<Recommendation>();
        Map<Recommendation, Recommendation> metroPointRecommendations = new HashMap<Recommendation, Recommendation>();

        recoverPointScheduler.sortCandidatePools(candidatePrimaryPools, srcVpoolCapabilities);
        recoverPointScheduler.sortCandidatePools(candidateSecondaryPools, srcVpoolCapabilities);

        // Get all the matching pools for each target virtual array. If the target varray's
        // vpool specifies HA, we will only look for VPLEX connected storage pools.
        Map<VirtualArray, List<StoragePool>> tgtVarrayStoragePoolsMap = getTargetMatchingPools(tgtVarrays,
                srcVpool, project, srcVpoolCapabilities, vpoolChangeVolume);

        metroPointRecommendations = recoverPointScheduler.createMetroPointRecommendations(srcVarray, tgtVarrays, srcVpool, haVarray,
                haVpool,
                srcVpoolCapabilities, candidatePrimaryPools, candidateSecondaryPools,
                tgtVarrayStoragePoolsMap,
                vpoolChangeVolume, vpoolChangeParam);

        _log.info("Produced {} recommendations for MetroPoint placement.", metroPointRecommendations.size());
        // We've let the RPScheduler do it's job and find recommendations with passing in the gathered
        // VPlex info.
        // Now let's expand upon the recommendations and add the missing VPlex info required to fulfill
        // the RP+VPlex placement request.
        for (Recommendation primary : metroPointRecommendations.keySet()) {
            RPProtectionRecommendation rpPrimaryRec = (RPProtectionRecommendation) primary;
            RPProtectionRecommendation rpSecondaryRec = (RPProtectionRecommendation) metroPointRecommendations.get(primary);

            VPlexProtectionRecommendation primaryVPlexProtectionRec = convertRPProtectionRecommendation(rpPrimaryRec, srcVarray, srcVpool,
                    null);
            VPlexProtectionRecommendation secondaryVPlexProtectionRec = convertRPProtectionRecommendation(rpSecondaryRec, haVarray,
                    srcVpool, haVpool);

            List<Recommendation> rpSecondaryRecs = new ArrayList<Recommendation>();
            rpSecondaryRecs.add(secondaryVPlexProtectionRec);
            primaryVPlexProtectionRec.setSourceVPlexHaRecommendations(rpSecondaryRecs);

            recommendations.add(primaryVPlexProtectionRec);
        }

        return recommendations;
    }

    /**
     * Schedule storage based on the incoming storage pools for source volumes.
     * 
     * @param srcVarray varray requested for source
     * @param tgtVarrays Varray to protect this volume to.
     * @param srcVpool vpool requested
     * @param srcVpoolCapabilities parameters
     * @param vplexSourceCandidateStoragePools candidate pools to use for source
     * @param vpoolChangeVolume vpool change volume, if applicable
     * @param vplexHaVArrayRecommendations HA/Distributed Vplex recommendations
     * @return list of Recommendation objects to satisfy the request
     */
    private List<Recommendation> scheduleStorageSourcePoolConstraint(VirtualArray srcVarray,
            List<VirtualArray> tgtVarrays, VirtualPool srcVpool, Project project, VirtualPoolCapabilityValuesWrapper srcVpoolCapabilities,
            List<StoragePool> vplexSourceCandidateStoragePools, Volume vpoolChangeVolume, VirtualPoolChangeParam vpoolChangeParam) {
        // Initialize a list of recommendations to be returned.
        List<Recommendation> recommendations = new ArrayList<Recommendation>();
        List<Recommendation> rpRecommendations = new ArrayList<Recommendation>();

        // If this is a change vpool, first check to see if we can build recommendations from the existing RP CG.
        // If this is the first volume in a new CG proceed as normal.
        if (vpoolChangeVolume != null) {
            rpRecommendations = recoverPointScheduler.buildCgRecommendations(srcVpoolCapabilities, srcVpool, tgtVarrays, vpoolChangeVolume);
        }

        if (rpRecommendations.isEmpty()) {
            recoverPointScheduler.sortCandidatePools(vplexSourceCandidateStoragePools, srcVpoolCapabilities);

            // Get all the matching pools for each target virtual array. If the target varray's
            // vpool specifies HA, we will only look for VPLEX connected storage pools.
            Map<VirtualArray, List<StoragePool>> tgtVarrayStoragePoolsMap = getTargetMatchingPools(tgtVarrays,
                    srcVpool, project, srcVpoolCapabilities, null);

            rpRecommendations = recoverPointScheduler.scheduleStorageSourcePoolConstraint(srcVarray, tgtVarrays, srcVpool,
                    srcVpoolCapabilities, vplexSourceCandidateStoragePools, vpoolChangeVolume,
                    tgtVarrayStoragePoolsMap);
        }

        _log.info("Produced {} recommendations for placement.", rpRecommendations.size());
        // We've let the RPScheduler do it's job and find recommendations with passing in the gathered
        // VPlex info.
        // Now let's expand upon the recommendations and add the missing VPlex info required to fulfill
        // the RP+VPlex placement request.
        for (Recommendation rec : rpRecommendations) {
            RPProtectionRecommendation rpRec = (RPProtectionRecommendation) rec;
            VPlexProtectionRecommendation vplexProtectionRec = this.convertRPProtectionRecommendation(rpRec, srcVarray, srcVpool, null);
            vplexProtectionRec.setSourceVPlexHaRecommendations(srcVPlexHaRecommendations);
            recommendations.add(vplexProtectionRec);
        }

        return recommendations;
    }

    /**
     * Scheduler for a Vpool change from an unprotected VPLEX Virtual volume to a RP+VPLEX protected Virtual volume.
     * 
     * @param changeVpoolVolume volume that is being changed to a protected vpool
     * @param newVpool vpool requested to change to (must be protected)
     * @param protectionVarrays Varrays to protect this volume to.
     * @return list of Recommendation objects to satisfy the request
     */
    public List<Recommendation> scheduleStorageForVpoolChangeUnprotected(Volume changeVpoolVolume, VirtualPool newVpool,
            List<VirtualArray> protectionVarrays, VirtualPoolChangeParam param) {
        _log.info("Schedule storage for vpool change to vpool {} for volume {}.",
                newVpool.getLabel() + "[" + String.valueOf(newVpool.getId()) + "]",
                changeVpoolVolume.getLabel() + "[" + String.valueOf(changeVpoolVolume.getId()) + "]");
        this.initResources();

        VirtualArray varray = dbClient.queryObject(VirtualArray.class, changeVpoolVolume.getVirtualArray());

        // Swap src and ha if the flag has been set on the vpool
        RPVPlexVarrayVpool container = this.swapSrcAndHAIfNeeded(varray, newVpool);

        CapacityMatcher capacityMatcher = new CapacityMatcher();
        Project project = dbClient.queryObject(Project.class, changeVpoolVolume.getProject());
        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        capabilities.put(VirtualPoolCapabilityValuesWrapper.SIZE, changeVpoolVolume.getCapacity());
        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, new Integer(1));
        capabilities.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, param.getConsistencyGroup());

        List<StoragePool> allMatchingPools = getSourceCandidatePools(container.getSrcVarray(), container.getSrcVpool(),
                container.getHaVarray(), container.getHaVpool(),
                project, capabilities);

        List<StoragePool> sourcePools = new ArrayList<StoragePool>();

        // Find out how much space we'll need to fit the Journals for the source
        long journalSize = RPHelper.getJournalSizeGivenPolicy(String.valueOf(changeVpoolVolume.getCapacity()), container.getSrcVpool()
                .getJournalSize(), capabilities.getResourceCount());

        // Make sure our pool is in this list; this is a check to ensure the pool is in our existing varray and new Vpool
        // and can fit the new sizes needed. If it can not, send down pools that can.
        Iterator<StoragePool> iter = allMatchingPools.iterator();
        while (iter.hasNext()) {
            StoragePool pool = (StoragePool) iter.next();
            if (pool.getId().equals(changeVpoolVolume.getPool())) {
                // Make sure there's enough space for this journal volume in the current pool; it's preferred to use it.
                if (capacityMatcher.poolMatchesCapacity(pool, journalSize, journalSize, false, false, null)) {
                    sourcePools.add(pool);
                    break;
                } else {
                    _log.warn(String
                            .format("Not enough capacity found to place RecoverPoint journal volume on pool: %s, searching for other less preferable pools.",
                                    pool.getNativeGuid()));
                    break;
                }
            }
        }

        if (sourcePools.isEmpty()) {
            // Fall-back: if the existing source pool couldn't be used, let's find a different pool.
            sourcePools.addAll(allMatchingPools);

            if (sourcePools.isEmpty()) {
                // We could not verify the source pool exists in the CoS, return appropriate error
                _log.error("No matching storage pools found for the source varray: {0}. There are no storage pools that " +
                        "match the passed vpool parameters and protocols and/or there are no pools that have enough capacity to " +
                        "hold at least one resource of the requested size.", container.getSrcVarray().getLabel());
                throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(container.getSrcVpool().getId(), container
                        .getSrcVarray().getId());
            }
        }

        // Schedule storage based on the determined storage pools.
        List<Recommendation> recommendations = scheduleStorageSourcePoolConstraint(container.getSrcVarray(), protectionVarrays,
                container.getSrcVpool(), project,
                capabilities, sourcePools, changeVpoolVolume, param);

        if (recommendations != null && !recommendations.isEmpty()) {
            _log.info("Created VPlex Protection recommendations:\n");
            for (Recommendation rec : recommendations) {
                VPlexProtectionRecommendation protectionRec = (VPlexProtectionRecommendation) rec;
                _log.info(protectionRec.toString(dbClient));
            }
        }
        return recommendations;
    }

    /**
     * Gather matching pools for a collection of protection varrays. Collects
     * a list of vplex connected storage pools if the protection virtual pool
     * specifies high availability.
     * 
     * @param tgtVarrays The protection varrays
     * @param srcVpool the requested vpool that must be satisfied by the storage pool
     * @param srcVpoolCapabilities capabilities
     * @param vpoolChangeVolume The main volume for the change vpool operation
     * @return A list of matching storage pools and varray mapping
     */
    private Map<VirtualArray, List<StoragePool>> getTargetMatchingPools(List<VirtualArray> tgtVarrays,
            VirtualPool srcVpool, Project project, VirtualPoolCapabilityValuesWrapper srcVpoolCapabilities, Volume vpoolChangeVolume) {
        _log.info("Getting a list of pools matching each protection Virtual Array.");

        Map<VirtualArray, List<StoragePool>> tgtVarrayStoragePoolMap = new HashMap<VirtualArray, List<StoragePool>>();
        VirtualPool tgtVpool = null;

        for (VirtualArray tgtVarray : tgtVarrays) {
            tgtVpool = getTargetVirtualPool(tgtVarray, srcVpool);

            List<StoragePool> tgtVarrayMatchingPools = new ArrayList<StoragePool>();

            // Check to see if this is a change vpool request for an existing RP+VPLEX/MetroPoint protected volume.
            // If it is, we want to isolate already provisioned targets to the single storage pool that they are already in.
            if (vpoolChangeVolume != null) {
                Volume alreadyProvisionedTarget = RPHelper.findAlreadyProvisionedTargetVolume(vpoolChangeVolume, tgtVarray.getId(),
                        dbClient);
                if (alreadyProvisionedTarget != null) {
                    _log.info(String.format("Existing target volume [%s] found for varray [%s].", alreadyProvisionedTarget.getLabel(),
                            tgtVarray.getLabel()));

                    URI storagePoolURI = null;
                    if (alreadyProvisionedTarget.getAssociatedVolumes() != null
                            && !alreadyProvisionedTarget.getAssociatedVolumes().isEmpty()) {
                        Volume sourceBackingVol = VPlexUtil.getVPLEXBackendVolume(alreadyProvisionedTarget, true, dbClient, true);
                        storagePoolURI = sourceBackingVol.getPool();
                    }
                    else {
                        storagePoolURI = alreadyProvisionedTarget.getPool();
                    }

                    // Add the single existing storage pool for this varray
                    StoragePool storagePool = dbClient.queryObject(StoragePool.class, storagePoolURI);
                    tgtVarrayMatchingPools.add(storagePool);
                    tgtVarrayStoragePoolMap.put(tgtVarray, tgtVarrayMatchingPools);

                    // No need to go further, continue on to the next target varray
                    continue;
                }
            }

            tgtVarrayMatchingPools = blockScheduler.getMatchingPools(tgtVarray, tgtVpool, srcVpoolCapabilities);

            // Verify that any storage pool(s) requiring a VPLEX front end for data protection have
            // HA enabled on the target vpool, if not remove the storage pool(s) from consideration.
            tgtVarrayMatchingPools = recoverPointScheduler.removePoolsRequiringHaIfNotEnabled(tgtVarrayMatchingPools, tgtVpool,
                    RPHelper.TARGET);

            _log.info("Matched pools for target virtual array {} and target virtual pool {}:\n",
                    tgtVarray.getLabel(), tgtVpool.getLabel());

            for (StoragePool matchedPool : tgtVarrayMatchingPools) {
                _log.info(matchedPool.getLabel());
            }

            if (VirtualPool.vPoolSpecifiesHighAvailability(tgtVpool)) {

                // Get all the VPLEX connected storage pools from the matched pools
                Map<String, List<StoragePool>> sortedTargetVPlexStoragePools =
                        vplexScheduler.sortPoolsByVPlexStorageSystem(tgtVarrayMatchingPools, String.valueOf(tgtVarray.getId()));

                // We only care about RP-connected VPLEX storage systems
                sortedTargetVPlexStoragePools = getRPConnectedVPlexStoragePools(sortedTargetVPlexStoragePools);

                if (sortedTargetVPlexStoragePools != null && !sortedTargetVPlexStoragePools.isEmpty()) {
                    // Add the protection virtual array and list of VPLEX connected storage pools
                    tgtVarrayStoragePoolMap.put(
                            tgtVarray, sortedTargetVPlexStoragePools.get(sortedTargetVPlexStoragePools.keySet().iterator().next()));
                }
                else {
                    // There are no RP connected VPLEX storage systems so we cannot provide
                    // any placement recommendations for the target.
                    _log.error(
                            "No matching pools because there are no RP connected VPlex storage systems for the requested virtual array[{}] and virtual pool[{}].",
                            tgtVarray.getLabel(), tgtVpool.getLabel());
                    throw APIException.badRequests.noRPConnectedVPlexStorageSystemsForTarget(tgtVpool.getLabel(), tgtVarray.getLabel());
                }

                tgtVarrayHasHaVpool.put(tgtVarray, true);

                // If the target vpool specifies VPlex, we need to check if this is VPLEX local or VPLEX
                // distributed. If it's VPLEX distributed, there will be a separate recommendation just for that
                // which will be used by VPlexBlockApiService to create the distributed volumes in VPLEX.
                if (tgtVpool != null) {
                    boolean isVplexDistributed = VirtualPool.HighAvailabilityType.vplex_distributed.name()
                            .equals(tgtVpool.getHighAvailability());

                    if (isVplexDistributed) {
                        this.tgtVPlexHaRecommendations.put(
                                tgtVarray.getId(),
                                findVPlexHARecommendations(tgtVarray, tgtVpool, null, null, project, srcVpoolCapabilities,
                                        sortedTargetVPlexStoragePools));
                    }
                }
            }
            else {
                tgtVarrayStoragePoolMap.put(tgtVarray, tgtVarrayMatchingPools);
                tgtVarrayHasHaVpool.put(tgtVarray, false);
            }
        }

        return tgtVarrayStoragePoolMap;
    }

    /**
     * Filters out all the non-RP connected storage pools from the passed
     * vplex to storage pool map.
     * 
     * @param vplexStoragePoolMap the mapping of vplex storage systems to storage pools.
     * @return a map of VPLEX to StoragePool
     */
    private Map<String, List<StoragePool>> getRPConnectedVPlexStoragePools(Map<String, List<StoragePool>> vplexStoragePoolMap) {

        Map<String, List<StoragePool>> poolsToReturn = vplexStoragePoolMap;

        if (vplexStoragePoolMap != null) {
            // Narrow down the list of candidate VPLEX storage systems/pools to those
            // that are RP connected.
            Set<String> vplexStorageSystemIds = vplexStoragePoolMap.keySet();
            _log.info("{} VPlex storage systems have matching pools",
                    vplexStorageSystemIds.size());

            URIQueryResultList results = new URIQueryResultList();
            boolean rpConnection = false;

            for (String vplexId : vplexStorageSystemIds) {
                rpConnection = false;
                dbClient.queryByConstraint(
                        AlternateIdConstraint.Factory.
                                getRPSiteArrayByStorageSystemConstraint(vplexId),
                        results);
                while (results.iterator().hasNext()) {
                    URI uri = results.iterator().next();
                    RPSiteArray siteArray = dbClient.queryObject(RPSiteArray.class, uri);
                    if (siteArray != null) {
                        // There is at least 1 RP connection to this VPLEX
                        rpConnection = true;
                        break;
                    }
                }

                if (!rpConnection) {
                    poolsToReturn.remove(vplexId);
                }
            }
        }

        return poolsToReturn;
    }

    /**
     * Gets the virtual pool associated with the virtual array.
     * 
     * @param tgtVarray
     * @param srcVpool the base virtual pool
     * @return
     */
    private VirtualPool getTargetVirtualPool(VirtualArray tgtVarray, VirtualPool srcVpool) {
        VpoolProtectionVarraySettings settings = rpHelper.getProtectionSettings(srcVpool, tgtVarray);
        // If there was no vpool specified use the source vpool for this varray.
        VirtualPool tgtVpool = srcVpool;
        if (settings.getVirtualPool() != null) {
            tgtVpool = dbClient.queryObject(VirtualPool.class, settings.getVirtualPool());
        }

        return tgtVpool;
    }

    /**
     * Convert the HA recommendations of type VPlexRecommendation to
     * VPlexProtectionRecommendation.
     * 
     * @param vplexRecommendations
     * @return
     */
    private List<Recommendation> convertHARecommendations(List<Recommendation> vplexRecommendations) {
        List<Recommendation> vPlexProtectionRecommendations =
                new ArrayList<Recommendation>();

        for (Recommendation recommendation : vplexRecommendations) {
            // All HA recommendations are of type VPlexRecommendation but just in case
            if (recommendation instanceof VPlexRecommendation) {
                vPlexProtectionRecommendations.add(
                        new VPlexProtectionRecommendation((VPlexRecommendation) recommendation));
            }
        }

        return vPlexProtectionRecommendations;
    }

    /**
     * This function will swap src and ha varrays and src and ha vpools IF
     * the src vpool has specified this.
     * 
     * @param srcVarray
     * @param srcVpool
     * @param haVarray
     * @param haVpool
     * @param dbClient
     */
    public static RPVPlexVarrayVpool setActiveProtectionAtHAVarray(RPVPlexVarrayVpool varrayVpool, DbClient dbClient) {
        // Refresh vpools in case previous activities have changed their temporal representation.
        VirtualArray srcVarray = varrayVpool.getSrcVarray();
        VirtualPool srcVpool = dbClient.queryObject(VirtualPool.class, varrayVpool.getSrcVpool().getId());
        VirtualArray haVarray = varrayVpool.getHaVarray();
        VirtualPool haVpool = varrayVpool.getHaVpool();

        // Check to see if the user has selected that the HA Varray should be used
        // as the RP Source.
        if (VirtualPool.isRPVPlexProtectHASide(srcVpool)) {

            haVarray = dbClient.queryObject(VirtualArray.class,
                    URI.create(srcVpool.getHaVarrayConnectedToRp()));

            _log.info("Source Vpool[{}] indicates that we should use HA Varray[{}] as RP Source.", srcVpool.getLabel(), haVarray.getLabel());

            String haVpoolId = srcVpool.getHaVarrayVpoolMap().get(srcVpool.getHaVarrayConnectedToRp());

            if (haVpoolId != null
                    && !haVpoolId.isEmpty()
                    && !haVpoolId.equals(NullColumnValueGetter.getNullStr())) {
                haVpool = dbClient.queryObject(VirtualPool.class,
                        URI.create(haVpoolId));
                _log.info("HA Vpool has been defined [{}]", haVpool.getLabel());

                // Temporarily inherit the HA and Protection Settings/RP specific info from Source Vpool.
                // These modifications will not be persisted to the db.
                haVpool.setProtectionVarraySettings(srcVpool.getProtectionVarraySettings());
                haVpool.setRpCopyMode(srcVpool.getRpCopyMode());
                haVpool.setRpRpoType(srcVpool.getRpRpoType());
                haVpool.setRpRpoValue(srcVpool.getRpRpoValue());
                haVpool.setMultivolumeConsistency(srcVpool.getMultivolumeConsistency());
                haVpool.setHighAvailability(srcVpool.getHighAvailability());
                haVpool.setMetroPoint(srcVpool.getMetroPoint());
                haVpool.setHaVarrayConnectedToRp(srcVarray.getId().toString());
                haVpool.setJournalSize(NullColumnValueGetter.isNotNullValue(srcVpool.getJournalSize()) ? srcVpool.getJournalSize() : null);
            }
            else {
                _log.info("HA Vpool has not been defined, using Source Vpool[{}].", srcVpool.getLabel());
                // Use source vpool. That means the source vpool will have to have the HA varray
                // added to it, otherwise this will not work. That is done during vpool create via the UI
                // by the user.
                // This is the same behaviour as VPLEX. So we're just reusing that behaviour.
                // If not done, placement will fail.
                haVpool = srcVpool;
            }

            // Ensure that we define the haVarrayVpoolMap on the haVpool to use srcVarray and srcVpool.
            StringMap haVarrayVpoolMap = new StringMap();
            haVarrayVpoolMap.put(srcVarray.getId().toString(), srcVpool.getId().toString());
            haVpool.setHaVarrayVpoolMap(haVarrayVpoolMap);

            _log.info("HA Varray[{}] and HA Vpool[{}] will be used as Source Varray and Source Vpool.", haVarray.getLabel(),
                    haVpool.getLabel());
            _log.info("Source Varray[{}] and Source Vpool[{}] will be used as HA Varray and HA Vpool.", srcVarray.getLabel(),
                    srcVpool.getLabel());

            // Now HA becomes Source and Source becomes HA.
            VirtualArray tempVarray = srcVarray;
            VirtualPool tempVpool = srcVpool;

            varrayVpool.setSrcVarray(haVarray);
            varrayVpool.setSrcVpool(haVpool);

            varrayVpool.setHaVarray(tempVarray);
            varrayVpool.setHaVpool(tempVpool);
        }

        return varrayVpool;
    }

    public List<Recommendation> getSrcVPlexHaRecommendations() {
        return srcVPlexHaRecommendations;
    }

    public void setSrcVPlexHaRecommendations(
            List<Recommendation> srcVPlexHaRecommendations) {
        this.srcVPlexHaRecommendations = srcVPlexHaRecommendations;
    }

    public Map<URI, List<Recommendation>> getTgtVPlexHaRecommendations() {
        return tgtVPlexHaRecommendations;
    }

    public void setTgtVPlexHaRecommendations(
            Map<URI, List<Recommendation>> tgtVPlexHaRecommendations) {
        this.tgtVPlexHaRecommendations = tgtVPlexHaRecommendations;
    }

    /**
     * Scheduler for a Vpool change from a protected VPLEX Virtual volume to a different type
     * pf protection. Ex: RP+VPLEX upgrade to MetroPoint
     * 
     * @param volume volume that is being changed to a protected vpool
     * @param newVpool vpool requested to change to (must be protected)
     * @param protectionVarrays Varrays to protect this volume to.
     * @param vpoolChangeParam The change param for the vpool change operation
     * @return list of Recommendation objects to satisfy the request
     */
    public List<Recommendation> scheduleStorageForVpoolChangeRequest(Volume volume, VirtualPool newVpool,
                                                                        List<VirtualArray> protectionVirtualArraysForVirtualPool,
                                                                        VirtualPoolChangeParam vpoolChangeParam) {
        
        


         
//         List<StoragePool> allMatchingPools = getSourceCandidatePools(container.getSrcVarray(), container.getSrcVpool(), 
//                                                                         container.getHaVarray(), container.getHaVpool(),
//                                                                         project, capabilities);
//                 
//         List<StoragePool> sourcePools = new ArrayList<StoragePool>();
//         
//         // Find out how much space we'll need to fit the Journals for the source
//         long journalSize = RPHelper.getJournalSizeGivenPolicy(String.valueOf(volume.getCapacity()), container.getSrcVpool().getJournalSize(), capabilities.getResourceCount());
 //
//         // Make sure our pool is in this list; this is a check to ensure the pool is in our existing varray and new Vpool
//         // and can fit the new sizes needed.  If it can not, send down pools that can.
//         Iterator<StoragePool> iter = allMatchingPools.iterator();
//         while (iter.hasNext()) {
//             StoragePool pool = (StoragePool)iter.next();
//             if (pool.getId().equals(volume.getPool())) {
//                 // Make sure there's enough space for this journal volume in the current pool; it's preferred to use it.
//                 if (capacityMatcher.poolMatchesCapacity(pool, journalSize, journalSize, false, false, null)) {
//                     sourcePools.add(pool);
//                     break;
//                 } else {
//                     _log.warn(String.format("Not enough capacity found to place RecoverPoint journal volume on pool: %s, searching for other less preferable pools.", pool.getNativeGuid()));
//                     break;
//                 }
//             }
//         }
 //
//         if (sourcePools.isEmpty()) {
//             // Fall-back: if the existing source pool couldn't be used, let's find a different pool.
//             sourcePools.addAll(allMatchingPools);
 //
//             if (sourcePools.isEmpty()) {
//                 // We could not verify the source pool exists in the CoS, return appropriate error
//                 _log.error("No matching storage pools found for the source varray: {0}. There are no storage pools that " +
//                             "match the passed vpool parameters and protocols and/or there are no pools that have enough capacity to " +
//                             "hold at least one resource of the requested size.", container.getSrcVarray().getLabel());
//                 throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(container.getSrcVpool().getId(), container.getSrcVarray().getId());              
//             }
//         }
//         
//         // Flow through the MetroPoint scheduling workflow to get the recommendations, we already placement for the existing 
//         // resources but we need placement for the HA/Stand-by/Secondary Journal
//         recommendations = createMetroPointRecommendations(container.getSrcVarray(), tgtVarrays, container.getSrcVpool(), haVarray, 
//                                                 haVpool, project, capabilities, sourcePools, haPools, 
//                                                 volume, vpoolChangeParam);
//         
//         // Schedule storage based on the determined storage pools.
//         return scheduleStorageSourcePoolConstraint(container.getSrcVarray(), protectionVarrays, container.getSrcVpool(), project, 
//                                                     capabilities, sourcePools, volume, param);
//     }
        
        
        _log.info("Schedule storage for vpool change to vpool {} for volume {}.", 
                newVpool.getLabel() + "[" + String.valueOf(newVpool.getId()) + "]", 
                volume.getLabel() + "[" + String.valueOf(volume.getId()) + "]");

        VirtualPool currentVpool = dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
        VirtualArray varray = dbClient.queryObject(VirtualArray.class, volume.getVirtualArray());
        
        this.initResources();                

        // Swap src and ha if the flag has been set on the vpool
        RPVPlexVarrayVpool container = this.swapSrcAndHAIfNeeded(varray, newVpool);
                
        CapacityMatcher capacityMatcher = new CapacityMatcher();
        Project project = dbClient.queryObject(Project.class, volume.getProject());        
        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        capabilities.put(VirtualPoolCapabilityValuesWrapper.SIZE, volume.getCapacity());
        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, new Integer(1));
        capabilities.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, vpoolChangeParam.getConsistencyGroup());
                                
        List<StoragePool> sourcePools = new ArrayList<StoragePool>();
        List<StoragePool> haPools = new ArrayList<StoragePool>();
                
        VirtualArray haVarray = vplexScheduler.getHaVirtualArray(container.getSrcVarray(), project, container.getSrcVpool());
        VirtualPool haVpool = vplexScheduler.getHaVirtualPool(container.getSrcVarray(), project, container.getSrcVpool());        
               
        // Recommendations to return
        List<Recommendation> recommendations = Lists.newArrayList();

        // Upgrade RP+VPLEX to MetroPoint
        if (VirtualPool.vPoolSpecifiesRPVPlex(currentVpool)
                && VirtualPool.vPoolSpecifiesMetroPoint(newVpool)) {                    
            // We already have our VPLEX Metro source and targets provisioned.
            // We're going to leverage this for placement.
            _log.info("Scheduling storage for upgrade to MetroPoint, we need to place a HA/Stand-by/Secondary Journal");

            // Get a handle on the existing source and ha volumes, we want to use the references to their
            // existing storage pools to pass to the RP Scheduler.
            Volume sourceBackingVolume = null;
            Volume haBackingVolume = null;
            for (String associatedVolumeId : volume.getAssociatedVolumes()) {
                URI associatedVolumeURI = URI.create(associatedVolumeId);
                Volume backingVolume = dbClient.queryObject(Volume.class, associatedVolumeURI);
                if (backingVolume.getVirtualArray().equals(volume.getVirtualArray())) {
                    sourceBackingVolume = backingVolume;
                }
                else {
                    haBackingVolume = backingVolume;
                }
            }

            // We already have a source vpool from the (the existing one), so just add that one only to the list.
            sourcePools.add(dbClient.queryObject(StoragePool.class, sourceBackingVolume.getPool()));
            haPools.add(dbClient.queryObject(StoragePool.class, haBackingVolume.getPool()));

            // Obtain a list of RP protection Virtual Arrays.
            List<VirtualArray> tgtVarrays =
                    RecoverPointScheduler.getProtectionVirtualArraysForVirtualPool(
                            project, container.getSrcVpool(), dbClient, permissionsHelper);

            recommendations = createMetroPointRecommendations(container.getSrcVarray(), tgtVarrays, container.getSrcVpool(), haVarray,
                    haVpool, project, capabilities, sourcePools, haPools,
                    volume, vpoolChangeParam);
        }
        else if (VirtualPool.vPoolSpecifiesMetroPoint(newVpool)) {
            
        }
        else if (VirtualPool.vPoolSpecifiesRPVPlex(newVpool)) {
            
        }
        
        if (recommendations != null && !recommendations.isEmpty()) {
            _log.info("Created VPlex Protection recommendations:\n");
            for (Recommendation rec : recommendations) {
                VPlexProtectionRecommendation protectionRec = (VPlexProtectionRecommendation) rec;
                _log.info(protectionRec.toString(dbClient));
            }
        }

        return recommendations;
    }
}
