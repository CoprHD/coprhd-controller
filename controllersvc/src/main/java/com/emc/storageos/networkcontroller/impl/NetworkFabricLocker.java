/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerException;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

/**
 * NetworkFabricLocker -- The idea of locking a SAN fabric is to avoid simultaneous
 * zoning operations on the same SAN fabric (from different threads) on the Brocade BNA/CMCNE.
 * If multiple concurrent zoning operations are attempted various session locking / commit
 * failures have been observed on the Brocade.
 * This will also avoid simultaneous operations on the same VSAN on the Ciscos.
 * 
 * Failure to acquire session locks or commit sessions can still occur, however now will
 * be caused by zoning operations initiated externally to this ViPR instance (either in
 * a different ViPR instance or directly on the networking equipment.)
 * 
 * Locking will also slightly help throughput... previously when we could not obtain a
 * session lock we retried periodically (once per minute).
 * Now as soon as the fabric lock is released,
 * another thread is awakened to receive the fabric lock and can commence right away.
 * 
 * @author watson
 */
public class NetworkFabricLocker {
    private static final Logger _log = LoggerFactory.getLogger(NetworkFabricLocker.class);

    private static String getLockName(String fabricId) {
        return "san-fabrics/" + fabricId;
    }

    /**
     * Creates a new or retrieves an existing fabric lock from the coordinator.
     * 
     * @param fabricId - String
     * @param coordinator
     * @return InterProcessLock
     */
    private static InterProcessLock getFabricLock(String fabricId, CoordinatorClient coordinator) {
        String lockName = getLockName(fabricId);
        try {
            InterProcessLock lock = coordinator.getLock(lockName);
            return lock;
        } catch (Exception ex) {
            _log.error("Could not get lock: " + lockName);
            throw NetworkDeviceControllerException.exceptions.couldNotGetFabricLock(fabricId, ex);
        }
    }

    /**
     * Acquires a fabric exclusive lock based on the fabricId.
     * 
     * @param fabricId - String
     * @param coordinator
     * @return InterProcessLock (must be passed to unlock)
     */
    public static InterProcessLock lockFabric(String fabricId, CoordinatorClient coordinator) {
        boolean acquired = false;
        InterProcessLock lock = getFabricLock(fabricId, coordinator);
        try {
            acquired = lock.acquire(60, TimeUnit.MINUTES);

        } catch (Exception ex) {
            _log.error("Exception locking fabric: " + fabricId);
            throw NetworkDeviceControllerException.exceptions.exceptionAcquiringFabricLock(fabricId, ex);
        }
        if (acquired == false) {
            _log.error("Unable to lock fabric lock: " + fabricId);
            throw NetworkDeviceControllerException.exceptions.couldNotAcquireFabricLock(fabricId);
        }
        return lock;
    }

    /**
     * Releases a fabric exclusive lock based on the fabricId.
     * 
     * @param fabricId
     * @param lock
     */
    public static void unlockFabric(String fabricId, InterProcessLock lock) {
        try {
            if (lock != null) {
                lock.release();
            }
        } catch (Exception ex) {
            _log.error("Exception unlocking fabric: " + fabricId);
            throw NetworkDeviceControllerException.exceptions.exceptionReleasingFabricLock(fabricId, ex);
        }
    }
}
