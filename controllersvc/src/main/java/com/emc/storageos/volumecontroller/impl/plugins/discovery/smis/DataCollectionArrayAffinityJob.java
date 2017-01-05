/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.io.Serializable;
import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;

/**
 * Job for Array Affinity data collection.
 */
public class DataCollectionArrayAffinityJob extends DataCollectionJob implements Serializable {
    private static final long serialVersionUID = -6256870762267299638L;
    private static final Logger logger = LoggerFactory
            .getLogger(DataCollectionArrayAffinityJob.class);
    private List<URI> _hostIds;
    private List<URI> _systemIds;
    private ArrayAffinityDataCollectionTaskCompleter _completer;
    private String _namespace;

    public DataCollectionArrayAffinityJob(List<URI> hostIds, List<URI> systemIds, ArrayAffinityDataCollectionTaskCompleter completer,
            String namespace) {
        this(hostIds, systemIds, completer, JobOrigin.USER_API, namespace);
    }

    DataCollectionArrayAffinityJob(List<URI> hostIds, List<URI> systemIds, ArrayAffinityDataCollectionTaskCompleter completer,
            JobOrigin origin, String namespace) {
        super(origin);
        _hostIds = hostIds;
        _systemIds = systemIds;
        _completer = completer;
        _namespace = namespace;
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
        return ControllerServiceImpl.ARRAYAFFINITY_DISCOVERY;
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
        return (dbObject != null && !dbObject.getInactive());
    }

    public String getNamespace() {
        return _namespace;
    }

    public List<URI> getHostIds() {
        return _hostIds;
    }

    public List<URI> getSystemIds() {
        return _systemIds;
    }
    
    private boolean uriListsMatch(List<URI> list1, List<URI> list2) {
        if (list1 == null && list2 == null) {
            return true;
        }
        if ((list1 == null && list2 != null)
                || (list1 != null && list2 == null)
                || list1.size() != list2.size()) {
            return false;
        }
        
        return list1.containsAll(list2);
    }
    
    @Override
    public boolean matches(DataCollectionJob job) {
        return (this.getClass().equals(job.getClass())
                && getCompleter().getJobType().equals(job.getCompleter().getJobType())
                && getNamespace().equals(job.getNamespace())
                && uriListsMatch(getHostIds(), ((DataCollectionArrayAffinityJob) job).getHostIds())
                && uriListsMatch(getSystemIds(), ((DataCollectionArrayAffinityJob) job).getSystemIds()) );
    }
}
