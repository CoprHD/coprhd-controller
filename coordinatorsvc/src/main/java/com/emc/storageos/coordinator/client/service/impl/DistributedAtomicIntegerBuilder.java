package com.emc.storageos.coordinator.client.service.impl;

import org.apache.curator.framework.recipes.atomic.DistributedAtomicInteger;
import org.apache.curator.retry.RetryNTimes;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.impl.ZkPath;


public class DistributedAtomicIntegerBuilder {

    public static final String PLANNED_FAILOVER_STANDBY_NODECOUNT = "plannedFailoverStandbyNodeCount";
    public static final String PLANNED_FAILOVER_PRIMARY_NODECOUNT = "plannedFailoverPrimayNodeCount";
    
    private static final int RETRY_INTERVAL_MS = 1000;
    private static final int RETRY_TIME = 5;
    private static final String ZK_PATH_FORMAT = "%s/%s/%s";
    
    private CoordinatorClient coordinatorClient;
    private String siteId;
    private String pathName;
    
    public DistributedAtomicIntegerBuilder client(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
        return this;
    }
    
    public DistributedAtomicIntegerBuilder siteId(String siteId) {
        this.siteId = siteId;
        return this;
    }
    
    public DistributedAtomicIntegerBuilder path(String pathName) {
        this.pathName = pathName;
        return this;
    }
    
    public DistributedAtomicInteger build() {
        DistributedAtomicInteger distributedAtomicInteger = new DistributedAtomicInteger(((CoordinatorClientImpl) coordinatorClient)
                .getZkConnection().curator(), String.format(ZK_PATH_FORMAT, ZkPath.SITES, siteId, pathName), new RetryNTimes(RETRY_TIME, RETRY_INTERVAL_MS));

        return distributedAtomicInteger;
    }
}
