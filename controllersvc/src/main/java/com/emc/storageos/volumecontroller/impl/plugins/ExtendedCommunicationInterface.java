/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.util.Map;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.plugins.CommunicationInterface;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * 
 * This interface is very specific to Bourne, which has few dependent methods
 * like injectDBClient & CassandraInsertion classes.This interface had been put
 * up to eliminate the dependency of DBClient related code in export Libraries
 * Bourne specific plugins implement this interface,so that access to DBClient
 * and other dependencies are met. Prosphere plugins will directly implement
 * CommunicationInterface, which doesnt have any reference to DBClient.
 * 
 */
public interface ExtendedCommunicationInterface extends CommunicationInterface {
    /**
     * inject Cache
     * 
     * @param cache
     */
    public void injectCache(Map<String, Object> cache);

    /**
     * Inject DBClient
     * 
     * @param dbClient
     */
    public void injectDBClient(DbClient dbClient);

    /**
     * Inject coordinatorClient
     * 
     * @param coordinatorClient
     */
    public void injectCoordinatorClient(CoordinatorClient coordinatorClient);

    /**
     * Inject controller locking service
     * @param locker An instance of ControllerLockingService
     */
    public void injectControllerLockingService(ControllerLockingService locker);

    /**
     *  Inject Task Completer
     */
    public void injectTaskCompleter(TaskCompleter completer);

    public void injectNetworkDeviceController(
            NetworkDeviceController _networkDeviceController);

}
