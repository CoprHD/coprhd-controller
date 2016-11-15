/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class RemoteReplicationScheduler implements Scheduler {

    public static final Logger _log = LoggerFactory.getLogger(RemoteReplicationScheduler.class);

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
    /**
     * 1. We call basic storage scheduler to get matching storage pools for source volumes (based on source virtual array and source virtual pool)
     * 2. We call basic storage scheduler to get matching storage pools for target volumes (based on target varray and optionally target virtual pool)
     * 3. Filter out source pools which do not match replication group or replication set.
     * 4. The same step for target pools.
     */
    public List getRecommendationsForResources(VirtualArray vArray, Project project, VirtualPool vPool, VirtualPoolCapabilityValuesWrapper capabilities) {
        _log.info("Schedule storage for {} resource(s) of size {}.",
                capabilities.getResourceCount(), capabilities.getSize());
        Map<String, Object> attributeMap = new HashMap<>();
        // Get all storage pools that match the passed vpool params and
        // protocols. In addition, the pool must have enough capacity
        // to hold at least one resource of the requested size.
        List<StoragePool> pools = _blockScheduler.getMatchingPools(vArray, vPool, capabilities, attributeMap);

        if (pools == null || pools.isEmpty()) {
            _log.error(
                    "No matching storage pools found for the source varray: {0}. There are no storage pools that "
                            + "match the passed vpool parameters and protocols and/or there are no pools that have enough capacity to "
                            + "hold at least one resource of the requested size.",
                    vArray.getLabel());
            StringBuffer errorMessage = new StringBuffer();
            if (attributeMap.get(AttributeMatcher.ERROR_MESSAGE) != null) {
                errorMessage = (StringBuffer) attributeMap.get(AttributeMatcher.ERROR_MESSAGE);
            }
            throw APIException.badRequests.noStoragePools(vArray.getLabel(), vPool.getLabel(),
                    errorMessage.toString());
        }

        return null;

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
