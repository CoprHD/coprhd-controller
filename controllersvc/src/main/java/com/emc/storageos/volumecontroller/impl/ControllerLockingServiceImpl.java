/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedPersistentLock;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.volumecontroller.ControllerLockingService;

public class ControllerLockingServiceImpl implements ControllerLockingService {
    private static final String LOCK_CLIENTNAME = "anynode";

	private static final Logger log = LoggerFactory.getLogger(ControllerLockingServiceImpl.class);
	
    private CoordinatorClient _coordinator;

    /**
     * Sets coordinator
     *
     * @param coordinator
     */
    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    @Override
    public boolean acquireLock(String lockName, long seconds) {
    	if (lockName == null || lockName.isEmpty()) {
    		return false;
    	}
    	try {
    		Throwable t = null;
    		DistributedPersistentLock lock = null;
    		if (seconds >= 0) {
    		    
    		    log.info("Attempting to acquire lock: " + lockName + (seconds > 0 ? (" for a maximum of " + seconds + " seconds.") : ""));

    			while (seconds-- >= 0) {
    				try {
    					lock = _coordinator.getPersistentLock(lockName);
    					lock.acquireLock(LOCK_CLIENTNAME);
    				} catch (CoordinatorException ce) {
    					t = ce;
    					Thread.sleep(1000);
    				}
    			}
    		} else if (seconds == -1){ 
    			log.info("Attempting to acquire lock: " + lockName + " for as long as it takes.");
    			while (true) {
    				try {
    					lock = _coordinator.getPersistentLock(lockName);
    					lock.acquireLock(LOCK_CLIENTNAME);
    				} catch (CoordinatorException ce) {
    					t = ce;
    					Thread.sleep(1000);
    				}
    			}
    		} else {
    			log.error("Invalid value for seconds to acquireLock");
    			return false;
    		}

    		if (lock == null) {
    			if (t != null) 
    	    		log.error(String.format("Acquisition of mutex lock: %s failed with Exception: ", lockName), t);
    			else 
    				log.error(String.format("Acquisition of mutex lock: %s failed", lockName));

        		return false;
    		}
    		
    		log.info("Acquired lock: " + lockName);
    		return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
    	}
    }

    @Override
    public boolean releaseLock(String lockName) {
        if (lockName == null || lockName.isEmpty()) {
            return false;
        }
        try {
        	DistributedPersistentLock lock = _coordinator.getPersistentLock(lockName);
            if (lock != null) {
                lock.releaseLock(LOCK_CLIENTNAME);
            	log.info("Released lock: " + lockName);        		
            } else { 
            	log.error(String.format("Release of mutex lock: %s failed: ", lockName));
                return false;
            }
            return true;
        } catch (Exception e) {
        	log.error(String.format("Release of mutex lock: %s failed with Exception: ", lockName), e);
            return false;
        }
    }
}
