/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.io.Serializable;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Job for Array Affinity data collection.
 */
public class DataCollectionArrayAffinityJob extends DataCollectionJob implements Serializable {
    private static final long serialVersionUID = -6256870762267299638L;
    private static final Logger logger = LoggerFactory
            .getLogger(DataCollectionArrayAffinityJob.class);
    private final ArrayAffinityDataCollectionTaskCompleter _completer;

    public DataCollectionArrayAffinityJob(ArrayAffinityDataCollectionTaskCompleter completer) {
        this(completer, JobOrigin.USER_API);
    }

    DataCollectionArrayAffinityJob(ArrayAffinityDataCollectionTaskCompleter completer, JobOrigin origin) {
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
        return _completer.getJobType();
    }

    public String systemString() {
        String sys = null;
        try {
            sys = getCompleter().getId().toString();
        } catch (Exception ex) {
            logger.error("Exception occurred while geting system id from completer", ex);
        }
        return sys;
    }

    public boolean isActiveJob(DbClient dbClient) {
        DataObject dbObject = dbClient.queryObject(_completer.getType(), _completer.getId());
        return (dbObject != null && !dbObject.getInactive()) ? true : false;
    }

}
