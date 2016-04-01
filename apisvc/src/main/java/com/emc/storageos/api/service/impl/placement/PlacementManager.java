/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class PlacementManager {

    // Enumeration specifying the valid scheduler keys.
    public enum SchedulerType {
        block, rp, srdf, vplex, rpvplex
    }

    private DbClient dbClient;
    // Storage schedulers
    private Map<String, Scheduler> storageSchedulers;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setStorageSchedulers(Map<String, Scheduler> storageSchedulers) {
        this.storageSchedulers = storageSchedulers;
    }

    public Scheduler getStorageScheduler(String type) {
        return storageSchedulers.get(type);
    }

    public List getRecommendationsForVolumeCreateRequest(VirtualArray virtualArray,
            Project project, VirtualPool vPool, VirtualPoolCapabilityValuesWrapper capabilities) {

        // Get the volume placement based on passed parameters.
        Scheduler scheduler = getBlockServiceImpl(vPool);
        return scheduler.getRecommendationsForResources(virtualArray, project, vPool, capabilities);
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
    	if (vpool ==null){
    		scheduler = storageSchedulers.get("block");
    		return scheduler;
    	}


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


}
