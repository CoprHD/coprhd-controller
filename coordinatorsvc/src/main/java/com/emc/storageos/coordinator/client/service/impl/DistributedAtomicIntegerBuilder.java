package com.emc.storageos.coordinator.client.service.impl;

import org.apache.curator.framework.recipes.atomic.DistributedAtomicInteger;
import org.apache.curator.retry.RetryNTimes;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.impl.ZkPath;


public class DistributedAtomicIntegerBuilder {
    
    private CoordinatorClient coordinatorClient;
    private String siteId;
    private String pathName;
    
    public static DistributedAtomicIntegerBuilder create() {
        return new DistributedAtomicIntegerBuilder();
    }
    
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
        DistributedAtomicInteger distributedAtomicInteger = new DistributedAtomicInteger(((CoordinatorClientImpl)coordinatorClient).getZkConnection().curator(), String.format("%s/%s/%s", ZkPath.SITES, siteId, pathName), new RetryNTimes(5, 1000));
        
        return distributedAtomicInteger;
    }
}
