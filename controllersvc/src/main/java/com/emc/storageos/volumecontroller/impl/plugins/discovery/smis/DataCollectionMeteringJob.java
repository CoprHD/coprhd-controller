/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2012. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.io.Serializable;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jobs for Metering.
 */
public class DataCollectionMeteringJob extends DataCollectionJob implements Serializable {

    private static final long serialVersionUID = 6949883905186395658L;

    private static final Logger logger = LoggerFactory
            .getLogger(DataCollectionMeteringJob.class);

    private final DataCollectionTaskCompleter _completer;

    public DataCollectionMeteringJob(MeteringTaskCompleter completer) {
        super(JobOrigin.USER_API);
        _completer = completer;
    }

    DataCollectionMeteringJob(DataCollectionTaskCompleter completer,
            JobOrigin origin) {
        super(origin);
        _completer = completer;
    }

    @Override
    public DataCollectionTaskCompleter getCompleter() {
        return _completer;
    }

    @Override
    public void ready(DbClient dbClient) throws DeviceControllerException {
        _completer.ready(dbClient);
    }

    @Override
    public void error(DbClient dbClient, ServiceCoded serviceCoded) throws DeviceControllerException {
        _completer.error(dbClient, serviceCoded);
    }

    @Override
    public void schedule(DbClient dbClient) {
        _completer.schedule(dbClient);
    }

    @Override
    final public void setTaskError(DbClient dbClient, ServiceCoded code) {
        _completer.statusError(dbClient, code);
    }

    @Override
    final public void setTaskReady(DbClient dbClient, String message) {
        _completer.statusReady(dbClient, message);
    }

    @Override
    final public void updateTask(DbClient dbClient, String message) {
        _completer.statusPending(dbClient, message);
    }

    public String getType() {
        return ControllerServiceImpl.METERING;
    }

    public String systemString() {
        String sys = null;
        try {
            sys = _completer.getId().toString();
        } catch (Exception ex) {
            logger.error("Exception occurred while getting system id from completer", ex);
        }
        return sys;
    }

    public boolean isActiveJob(DbClient dbClient) {
        DataObject dbObject = dbClient.queryObject(_completer.getType(), _completer.getId());
        return (dbObject != null && !dbObject.getInactive()) ? true : false;
    }

}
