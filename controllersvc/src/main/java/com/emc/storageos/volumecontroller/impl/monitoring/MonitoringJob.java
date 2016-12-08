/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.monitoring;

import java.io.Serializable;
import java.net.URI;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionJob;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionTaskCompleter;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.MonitorTaskCompleter;

public class MonitoringJob extends DataCollectionJob implements Serializable {

    private static final long serialVersionUID = 1L;

    private Type _deviceType;

    public MonitoringJob(JobOrigin origin) {
        super(origin);
    }

    public MonitoringJob() {
        super(JobOrigin.USER_API);
    }

    private final Logger _logger = LoggerFactory.getLogger(MonitoringJob.class);

    /**
     * Will have SMIS provider's URI
     */
    private DataCollectionTaskCompleter _completer;

    public void setCompleter(MonitorTaskCompleter completer) {
        _completer = completer;
    }

    @Override
    public DataCollectionTaskCompleter getCompleter() {
        return _completer;
    }

    /**
     * Gives SMIS provider's URI
     * 
     * @return {@link URI} SMIS provider's URI
     */
    public URI getId() {
        return _completer.getId();
    }

    @Override
    public void ready(DbClient dbClient) throws DeviceControllerException {
        _completer.ready(dbClient);
    }

    @Override
    public void schedule(DbClient dbClient) {
        _completer.schedule(dbClient);
    }

    @Override
    public void error(DbClient dbClient, ServiceCoded serviceCoded) throws DeviceControllerException {
        _completer.error(dbClient, serviceCoded);
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

    /**
     *
     */
    public String getType() {
        return ControllerServiceImpl.MONITORING;
    }

    public void setDeviceType(Type deviceType) {
        this._deviceType = deviceType;
    }

    public Type getDeviceType() {
        return this._deviceType;
    }

    @Override
    public String toString() {
        return String.format("Device Type :%1$s, URI :%2$s",
                getDeviceType(), getId().toString());
    }

    @Override
    public String systemString() {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean isActiveJob(DbClient dbClient) {
        DataObject dbObject = dbClient.queryObject(_completer.getType(), _completer.getId());
        return (dbObject != null && !dbObject.getInactive()) ? true : false;
    }
    
    @Override
    public boolean matches(DataCollectionJob o) {
        if (o == null || !(o instanceof MonitoringJob)) {
            return false;
        }
        
        MonitoringJob other = (MonitoringJob) o;
        
        String thisResource = null;
        String otherResource = null;
        String thisType = null;
        String otherType = null;
        String thisDeviceType = null;
        String otherDeviceType = null;
        
        if (this._completer != null) {
            thisResource = this._completer.getId().toString();
            thisType = this._completer.getJobType();
        }
        
        if (other.getCompleter() != null) {
            otherResource = other.getCompleter().getId().toString();
            otherType = other.getCompleter().getJobType();
        }
        
        if (this._deviceType != null) {
            thisDeviceType = this._deviceType.toString();
        }
        if (other._deviceType != null) {
            thisDeviceType = other._deviceType.toString();
        }
        
        return StringUtils.equals(thisResource, otherResource) && StringUtils.equals(thisType, otherType) && StringUtils.equals(thisDeviceType, otherDeviceType);
    }

}