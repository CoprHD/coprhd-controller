/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.geo.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.beacon.ServiceBeacon;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.geo.service.GeoService;
import com.emc.storageos.geo.vdccontroller.impl.GeoServiceJobQueue;
import com.emc.storageos.security.AbstractSecuredWebServer;
import com.emc.storageos.security.validator.Validator;

/**
 * Geosvc default implementation
 */
public class GeoServiceImpl extends AbstractSecuredWebServer implements GeoService {
    private static final Logger _log = LoggerFactory.getLogger(GeoServiceImpl.class);

    @Autowired
    private GeoServiceJobQueue _geoServiceJobQueue;

    @Autowired
    private CoordinatorClient _coordinator;

    @Autowired
    private ServiceBeacon _svcBeacon;
    
    @Autowired
    private GeoBackgroundTasks _tasks;

    @Override
    public synchronized void start() throws Exception {
        _log.info("Starting geo service");
        initValidator();
        initServer();
        _server.start();
        _geoServiceJobQueue.start();
        _svcBeacon.start();

        // start background tasks
        _tasks.start();
        _log.info("Starting geo service done");
    }
    
    private void initValidator() {
        Validator.setCoordinator(_coordinator);
    }

    @Override
    public synchronized void stop() throws Exception {
        _log.info("Stopping geo service");
        _tasks.stop();
        _geoServiceJobQueue.stop();
        _server.stop();
        _dbClient.stop();
        _log.info("Stopping geo service done");
    }
}
