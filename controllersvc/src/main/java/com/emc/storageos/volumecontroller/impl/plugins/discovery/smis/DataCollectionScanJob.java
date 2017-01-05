/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.google.common.base.Joiner;

/**
 * Jobs for Scanner.
 */
public class DataCollectionScanJob extends DataCollectionJob implements Serializable {

    private static final long serialVersionUID = -2787824457961906580L;

    ArrayList<ScanTaskCompleter> _completers = new ArrayList<ScanTaskCompleter>();

    public DataCollectionScanJob() {
        super(JobOrigin.USER_API);
    }

    DataCollectionScanJob(JobOrigin origin) {
        super(origin);
    }

    public void addCompleter(ScanTaskCompleter completer) {
        _completers.add(completer);
    }

    public ArrayList<ScanTaskCompleter> getCompleters() {
        return _completers;
    }

    @Override
    public ScanTaskCompleter getCompleter() throws ControllerException {
        throw new DeviceControllerException("Wrong job");
    }

    public List<URI> getProviders() {
        List<URI> providers = new ArrayList<URI>();
        for (ScanTaskCompleter completer : _completers) {
            providers.add(completer.getId());
        }
        return providers;
    }

    public ScanTaskCompleter findProviderTaskCompleter(URI provider) {
        for (ScanTaskCompleter completer : _completers) {
            if (completer.getId().equals(provider)) {
                return completer;
            }
        }
        return null;
    }

    @Override
    public void ready(DbClient dbClient) throws DeviceControllerException {
        for (DataCollectionTaskCompleter completer : _completers) {
            completer.ready(dbClient);
        }
    }

    @Override
    public void error(DbClient dbClient, ServiceCoded serviceCoded) throws DeviceControllerException {
        for (DataCollectionTaskCompleter completer : _completers) {
            completer.error(dbClient, serviceCoded);
        }
    }

    @Override
    public void schedule(DbClient dbClient) {
        for (ScanTaskCompleter completer : _completers) {
            completer.schedule(dbClient);
        }
    }

    @Override
    final public void setTaskError(DbClient dbClient, ServiceCoded code) {
        for (ScanTaskCompleter completer : _completers) {
            completer.statusError(dbClient, code);
        }
    }

    @Override
    final public void setTaskReady(DbClient dbClient, String message) {
        for (ScanTaskCompleter completer : _completers) {
            completer.statusReady(dbClient, message);
        }
    }

    @Override
    final public void updateTask(DbClient dbClient, String message) {
        for (ScanTaskCompleter completer : _completers) {
            completer.statusPending(dbClient, message);
        }
    }

    public String getType() {
        return ControllerServiceImpl.SCANNER;
    }

    public String systemString() {
        return Joiner.on("\t").join(getProviders());
    }

    public boolean isActiveJob(DbClient dbClient) {
        for (DataCollectionTaskCompleter completer : _completers) {
            DataObject dbObject = dbClient.queryObject(completer.getType(), completer.getId());
            if (dbObject != null && !dbObject.getInactive()) {
                return true;
            }
        }
        return false;
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
        
        Collections.sort(list1);
        Collections.sort(list2);
        
        return list1.equals(list2);
    }
    
    private boolean providersMatch(DataCollectionScanJob job) {
        if (_completers == null && job.getCompleters() == null) {
            return true;
        }
        
        if ((_completers == null && job.getCompleters() != null)
                || (_completers != null && job.getCompleters() == null)) {
            return false;
        }
        
        List<URI> thisProviderIds = new ArrayList<URI>();
        for (ScanTaskCompleter completer : _completers) {
            thisProviderIds.add(completer.getId());
        }
        
        List<URI> otherProviderIds = new ArrayList<URI>();
        for (ScanTaskCompleter completer : job.getCompleters()) {
            otherProviderIds.add(completer.getId());
        }
        
        return CollectionUtils.isEqualCollection(thisProviderIds, otherProviderIds);
    }
    
    @Override
    public boolean matches(DataCollectionJob job) {
        return (this.getClass().equals(job.getClass())
                && getCompleter().getJobType().equals(job.getCompleter().getJobType())
                && getNamespace().equals(job.getNamespace())
                && providersMatch((DataCollectionScanJob) job));
    }

}
