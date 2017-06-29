/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VolumeTopology;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class PlacementManager {
    public static final Logger _log = LoggerFactory.getLogger(PlacementManager.class);

    /**
     * An ordered list of schedulers from the top of a hierarchy down.
     * Currently RecoverPointScheduler is the top and BlockScheduler is the bottom.
     */
    private List<String> schedulerStack;
    

    private DbClient dbClient;
    // Storage schedulers
    private Map<String, Scheduler> storageSchedulers;
    
    // Determine the scheduler to be called.
    public Scheduler getNextScheduler(String currentScheduler, 
            VirtualPool virtualPool, VpoolUse use) {
        int index = 0;
        // If there is a currentScheduler, we only call down the stack
        if (currentScheduler != null) {
            while (!schedulerStack.get(index).equals(currentScheduler)) {
                index++;
            }
            // Start with next scheduler after current
            index++;
        }
        
        // Check the predicate for each scheduler to see if applicable.
        while (index < schedulerStack.size()) {
        
            String schedulerName = schedulerStack.get(index);
            Scheduler scheduler = storageSchedulers.get(schedulerName);
            if (scheduler.handlesVpool(virtualPool, use)) {
                return scheduler;
            }
            index++;
        }
        // No scheduler type found
        return null;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setStorageSchedulers(Map<String, Scheduler> storageSchedulers) {
        this.storageSchedulers = storageSchedulers;
    }

    public Scheduler getStorageScheduler(String type) {
        return storageSchedulers.get(type);
    }

    /**
     * Gets a list of recommendations for a Volume Create Request.
     * Maintained for compatibility with RP and other existing callers. 
     * @param vArray - Virtual Array object
     * @param project - Project object
     * @param vPool - Virtual Pool object
     * @param capabilities - VirtualPoolCapabilityValuesWrapper contains parameters
     * @return List of Recommendation
     */
    public List getRecommendationsForVolumeCreateRequest(VirtualArray vArray,
            Project project, VirtualPool vPool, VirtualPoolCapabilityValuesWrapper capabilities) {

        // Get the volume placement based on passed parameters.
        Map<VpoolUse, List<Recommendation>> currentRecommendations = new HashMap<VpoolUse, List<Recommendation>>();
        Scheduler scheduler = getBlockServiceImpl(vPool);
        List<Recommendation> recommendations = scheduler.getRecommendationsForVpool(vArray, project, vPool,
                new VolumeTopology(), VpoolUse.ROOT, capabilities, currentRecommendations);
        return recommendations;
    }
    
    /**
     * A call that can return multiple placement results, one for the ROOT level Vpool, but also others
     * for example for SRDF_COPY. The output is a map of Vpool use to the list of recommendations for that Vpool.
     * @param virtualArray - Virtual Array object
     * @param project - Project object
     * @param virtualPool- Virtual Pool object
     * @param volumeTopology - A reference to a volume topology instance.
     * @param capabilities - VirtualPoolCapabilityValuesWrapper contains parameters
     * @return Map of VpoolUse to List of Recommendations for that use.
     */
    public Map<VpoolUse, List<Recommendation>> getRecommendationsForVirtualPool(VirtualArray virtualArray,
            Project project, VirtualPool virtualPool, VolumeTopology volumeTopology,
            VirtualPoolCapabilityValuesWrapper capabilities) {
        Map<VpoolUse, List<Recommendation>> recommendationMap = new HashMap<VpoolUse, List<Recommendation>>();
        
        
        // Invoke scheduling on the top level Virtual Pool (termed ROOT). This virtual pool
        // may have within it other virtual pools that may need to be separately scheduled.
        VpoolUse use = VpoolUse.ROOT;       // the apisvc vpool
        Scheduler scheduler = getNextScheduler(null, virtualPool, use);
        List<Recommendation> newRecommendations = scheduler.getRecommendationsForVpool(
                virtualArray, project, virtualPool, volumeTopology, use, capabilities, recommendationMap);
        if (newRecommendations.isEmpty()) {
           return recommendationMap;
        }
        recommendationMap.put(use, newRecommendations);
        
        // VPLEX will automatically take care of the VPLEX_HA virtual pool,
        // so it is not invoked specifically here.
        
        // Loop over the SRDF Copies, invoking a scheduler on them.
        if (VirtualPool.vPoolSpecifiesSRDF(virtualPool)) {
            Map<URI, VpoolRemoteCopyProtectionSettings> remoteCopyMap = 
                    VirtualPool.getRemoteProtectionSettings(virtualPool, dbClient);
            for (Map.Entry<URI, VpoolRemoteCopyProtectionSettings> entry : remoteCopyMap.entrySet()) {
                // Invoke scheduler on SRDF copies
                use = VpoolUse.SRDF_COPY;
                URI vArrayURI = entry.getValue().getVirtualArray();
                VirtualArray vArray = dbClient.queryObject(VirtualArray.class, vArrayURI);
                URI vPoolURI = entry.getValue().getVirtualPool();
                VirtualPool vPool = dbClient.queryObject(VirtualPool.class, vPoolURI);
                scheduler = getNextScheduler(null, vPool, use);
                newRecommendations  = scheduler.getRecommendationsForVpool(
                        vArray, project, vPool, volumeTopology, use, capabilities, recommendationMap);
                if (recommendationMap.containsKey(use)) {
                    recommendationMap.get(use).addAll(newRecommendations);
                } else {
                    recommendationMap.put(use, newRecommendations);
                }
            }
        }
        logRecommendations(recommendationMap);
        return recommendationMap;
    }
    

    /**
     * Returns the scheduler responsible for scheduling resources
     * 
     * @param vpool Virtual Pool
     * @return storage scheduler
     */
    private Scheduler getBlockServiceImpl(VirtualPool vpool) {
        // Select an implementation of the right scheduler
        Scheduler scheduler;
        if (VirtualPool.vPoolSpecifiesProtection(vpool)) {
            scheduler = storageSchedulers.get("rp");
        } else if (VirtualPool.vPoolSpecifiesHighAvailability(vpool)) {
            scheduler = storageSchedulers.get("vplex");
        } else if (VirtualPool.vPoolSpecifiesSRDF(vpool)) {
            scheduler = storageSchedulers.get("srdf");
        } else {
            scheduler = storageSchedulers.get("block");
        }

        return scheduler;
    }

    /**
     * Determines if any of the RP targets has HA (VPlex local/distributed)
     * specified. This method should be called in determining if the
     * rpvplex scheduler should be used, specifically for non-virtual to
     * virtual protection. This is currently not supported and is why the
     * condition was removed from getBlockServiceImpl().
     * 
     * @return true if there is at least 1 HA RP copy target.
     */
    private boolean hasHaRpCopyTarget(VirtualPool vpool) {
        // Check if any of the RP copies contains a protection virtual pool that specifies
        // high availability. This would indicate mixed protection, therefore we need
        // to use the RP/VPLEX scheduler.
        boolean rpVPlex = false;
        if (vpool.getProtectionVarraySettings() != null &&
                !vpool.getProtectionVarraySettings().isEmpty()) {
            for (String protectionVarraURI : vpool.getProtectionVarraySettings().keySet()) {
                String settingsURI = vpool.getProtectionVarraySettings().get(protectionVarraURI);
                VpoolProtectionVarraySettings settings = dbClient.queryObject(
                        VpoolProtectionVarraySettings.class, URI.create(settingsURI));
                URI protectionVpoolId = vpool.getId();
                if (settings.getVirtualPool() != null) {
                    protectionVpoolId = settings.getVirtualPool();
                }
                VirtualPool protectionVpool = dbClient.queryObject(VirtualPool.class, protectionVpoolId);
                if (VirtualPool.vPoolSpecifiesHighAvailability(protectionVpool)) {
                    rpVPlex = true;
                    break;
                }
            }
        }

        return rpVPlex;
    }
    
    /**
     * Return the StoragePools from a list of Recommendations.
     * @param recommendations List of Recommendation objects.
     * @return List<StoragePool
     */
    public List<StoragePool> getStoragePoolsFromRecommendations(List<Recommendation> recommendations) {
        List<StoragePool> storagePools = new ArrayList<StoragePool>();
        for (Recommendation recommendation : recommendations) {
            StoragePool storagePool = dbClient.queryObject(
                    StoragePool.class, recommendation.getSourceStoragePool());
            storagePools.add(storagePool);
        }
        return storagePools;
    }
    
    /**
     * Logs the recommendations for all VpoolUses.
     * @param recommendationMap - Map of VpoolUse to List<Recommendation>
     */
    public void logRecommendations(Map<VpoolUse, List<Recommendation>> recommendationMap) {
        for (Map.Entry<VpoolUse, List<Recommendation>> entry : recommendationMap.entrySet()) {
            logRecommendations(entry.getKey().name(), entry.getValue());
        }
    }
    public void logRecommendations(String label, List<Recommendation> recommendations) {
        _log.info("Recommendations for: " + label);
        for (Recommendation recommendation : recommendations) {
            logRecommendation(recommendation, 0);
        }
    }
    
    public void logRecommendation(Recommendation recommendation, int indent) {
        StringBuilder indentString = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            indentString.append("  ->");
        }
        if (recommendation == null) {
            _log.info("null recommendations");
            return;
        }
        indentString.substring(indentString.length() - indent);
        String type = recommendation.getClass().getSimpleName();

	    // some RP recommendations don't set varray, vpool, pool ... must be defensive
        String varrayLabel = "varray-unknown";
        String vpoolLabel = "vpool-unknown";
        String storagePoolLabel = "storagePool-unknown";
	    String systemLabel = "systemLabel-unknown";
        if (recommendation.getVirtualArray() != null) {
            VirtualArray va = dbClient.queryObject(VirtualArray.class, recommendation.getVirtualArray());
            varrayLabel = va.getLabel();
        }
        if (recommendation.getVirtualPool() != null) {
            VirtualPool vp = recommendation.getVirtualPool();
            vpoolLabel = vp.getLabel();
        }
        if (recommendation.getSourceStoragePool() != null) {
            StoragePool pool = dbClient.queryObject(StoragePool.class, recommendation.getSourceStoragePool());
            storagePoolLabel = pool.getLabel();
        }
	    if (recommendation.getSourceStorageSystem() != null) {
            StorageSystem sys = dbClient.queryObject(StorageSystem.class, recommendation.getSourceStorageSystem());
            systemLabel = sys.getLabel();
        }
        _log.info(String.format("%s%s va %s vp %s pool %s sys %s", 
                indentString, type, varrayLabel, vpoolLabel, storagePoolLabel, systemLabel));
        if (recommendation.getRecommendation() != null) {
            logRecommendation(recommendation.getRecommendation(), indent+1);
        }
        
    }

    public List<String> getSchedulerStack() {
        return schedulerStack;
    }

    public void setSchedulerStack(List<String> schedulerStack) {
        this.schedulerStack = schedulerStack;
    }
}
