/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.discovery;

import java.util.Map;
import java.util.Set;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.common.PartitionManager;

/**
 * 
 * @author gangak
 *
 */
public abstract class AbstractDiscoverer {

    protected HDSApiFactory hdsApiFactory;

    protected final int BATCH_SIZE = 200;

    protected DbClient dbClient;

    protected CoordinatorClient coordinator;

    protected NetworkDeviceController networkController;

    protected Map<String, Set<UnManagedExportMask>> volumeMasks;

    protected PartitionManager partitionManager;

    /**
     * @param hdsApiFactory the hdsApiFactory to set
     */
    public void setHdsApiFactory(HDSApiFactory hdsApiFactory) {
        this.hdsApiFactory = hdsApiFactory;
    }

    /**
     * @return the dbclient
     */
    public DbClient getDbClient() {
        return dbClient;
    }

    /**
     * @param dbclient the dbclient to set
     */
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * @return the coordinator
     */
    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    /**
     * @param coordinator the coordinator to set
     */
    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * @return the networkController
     */
    public NetworkDeviceController getNetworkController() {
        return networkController;
    }

    /**
     * @param networkController the networkController to set
     */
    public void setNetworkController(NetworkDeviceController networkController) {
        this.networkController = networkController;
    }

    /**
     * @return the volumeMasks
     */
    public Map<String, Set<UnManagedExportMask>> getVolumeMasks() {
        return volumeMasks;
    }

    /**
     * @param volumeMasks the volumeMasks to set
     */
    public void setVolumeMasks(Map<String, Set<UnManagedExportMask>> volumeMasks) {
        this.volumeMasks = volumeMasks;
    }

    /**
     * @return the partitionManager
     */
    public PartitionManager getPartitionManager() {
        return partitionManager;
    }

    /**
     * @param partitionManager the partitionManager to set
     */
    public void setPartitionManager(PartitionManager partitionManager) {
        this.partitionManager = partitionManager;
    }

    public abstract void discover(AccessProfile accessProfile) throws Exception;

}
