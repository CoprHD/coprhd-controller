/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.text.MessageFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemDiscoveryEngine;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

public class ComputeSystemCommunicationInterface extends ExtendedCommunicationInterfaceImpl {

    @Autowired
    private ComputeSystemDiscoveryEngine _discoveryEngine;
    
    public void setDiscoveryEngine(ComputeSystemDiscoveryEngine _discoveryEngine) {
        this._discoveryEngine = _discoveryEngine;
    }
    
    private static final Logger _log = LoggerFactory
            .getLogger(ComputeSystemCommunicationInterface.class);
    
    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile)
            throws BaseCollectionException {
        // do nothing
    }

    @Override
    public void scan(AccessProfile accessProfile)
            throws BaseCollectionException {
        // do nothing
    }

    @Override
    public void discover(AccessProfile accessProfile)
            throws BaseCollectionException {      
        try {
            _discoveryEngine.setDbClient(_dbClient);
            _discoveryEngine.setCoordinatorClient(_coordinator);
            _discoveryEngine.discover(accessProfile.getSystemId().toString());
        } catch( InternalException ex){
            throw ex;
        } catch (Exception e) {
            String msg = MessageFormat.format("Failed to discover system type {0}: {1}", accessProfile.getSystemType(), e.getMessage());
            _log.error(msg);
            
            throw ComputeSystemControllerException.exceptions.discoverFailed(accessProfile.getSystemId().toString(), e);
        }
    }
}
