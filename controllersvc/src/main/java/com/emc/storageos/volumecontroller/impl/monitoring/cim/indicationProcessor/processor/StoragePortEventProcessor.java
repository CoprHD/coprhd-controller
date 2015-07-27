/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor.processor;

import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.event.CIMStoragePortUpdatableDeviceEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor.BaseProcessor;

@Component("StoragePortEventProcessor")
public class StoragePortEventProcessor extends BaseProcessor {
    
    @Autowired
    private DbClient dbClient;
    
    /**
     * {@inheritDoc}
     */
    public void setDbClient(DbClient client) {
        dbClient = client;
    }
    
    /**
     * Logger to log the information
     */
    private static final Logger _logger = LoggerFactory.getLogger(StoragePortEventProcessor.class);
    
    /**
     * Process FC Port/iSCSI Port status change event for VMAX and VNX
     */
    @Override
    public void processIndication(Hashtable<String, String> notification) {
        _logger.debug("Entering {}",Thread.currentThread().getStackTrace()[1].getMethodName());
        _logger.debug(notification.toString());
        CIMStoragePortUpdatableDeviceEvent vEvent;
        vEvent = (CIMStoragePortUpdatableDeviceEvent) getApplicationContext()
                .getBean(
                        CIMStoragePortUpdatableDeviceEvent.class
                                .getSimpleName());
        vEvent.setIndication(notification);
        
        try {
            Boolean updateStatus = vEvent.updateStoragePortOperationalStatus();
            _logger.info("StoargePort:{} update Status :{}",vEvent.getNativeGuid(),updateStatus);
            
            getRecordableEventManager().recordEvents(vEvent);
            _logger.info("StoragePort Event for {} persisted in db", vEvent.getNativeGuid());
        } catch (Exception e) {
            _logger.error("Exception occured while processing StoragePort indication",e);
        }
        
        _logger.debug("Exiting {}",Thread.currentThread().getStackTrace()[1].getMethodName());
    }

}
