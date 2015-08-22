/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.ecs.api.ECSApiFactory;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;

/**
 * Class for ECS discovery object storage device
 */
public class ECSCommunicationInterface extends ExtendedCommunicationInterfaceImpl {
	URI storageSystemURI = null;
	private static final Logger _logger = LoggerFactory
            .getLogger(ECSCommunicationInterface.class);
	
	private ECSApiFactory ecsApiFactory;
	
	
    /**
     * @param ecsApiFactory the ecsApiFactory to set
     */
    public void setEcsApiFactory(ECSApiFactory ecsApiFactory) {
        this.ecsApiFactory = ecsApiFactory;
    }
	
	@Override
	public void collectStatisticsInformation(AccessProfile accessProfile)
			throws BaseCollectionException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void scan(AccessProfile accessProfile)
			throws BaseCollectionException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void discover(AccessProfile accessProfile)
			throws BaseCollectionException {
        URI storageSystemURI = null;
        StorageSystem storageSystem = null;
        String detailedStatusMessage = "Unknown Status";
        long startTime = System.currentTimeMillis();

		try {
            _logger.info("Access Profile Details :" + accessProfile.toString());
            
		}  catch (Exception e) {
            detailedStatusMessage = String.format("Discovery failed for Storage System: %s because %s",
                    storageSystemURI.toString(), e.getMessage());
            _logger.error(detailedStatusMessage, e);
            //throw new SMIPluginException(detailedStatusMessage);
		}finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (DatabaseException ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
            //releaseResources();
            long totalTime = System.currentTimeMillis() - startTime;
            _logger.info(String.format("Discovery of Storage System %s took %f seconds", storageSystemURI.toString(), (double) totalTime
                    / (double) 1000));
        }
	}

}
