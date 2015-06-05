/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.isilon;

import java.net.URI;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.isilon.restapi.IsilonEvent;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableDeviceEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;

/**
 * Class for processing IsilonEvent
 */
public class RecordableIsilonEvent extends RecordableDeviceEvent {
    private static final int MAX_ISILON_DEV_ID = 100;
    private IsilonEvent _event;
    private StorageSystem _isilonDevice;

    /**
     * Overloaded constructor
     * 
     * @param deviceId
     * @param event
     */
    public RecordableIsilonEvent(StorageSystem isilonDevice, IsilonEvent event) {
        super(null);
        _event = event;
        _isilonDevice= isilonDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTimestamp() {
        return _event.getLatestTimeMilliSeconds();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return _event.getMessage();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getService() {
        return "file";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType() {
        // there is no information in the alerts
        return OperationTypeEnum.ArrayGeneric.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<DataObject> getResourceClass() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getExtensions() {
        return _event.getSpecifiers();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSource() {
        String source = null;
        if(_isilonDevice!=null){
            source = _isilonDevice.getIpAddress();
        }
        return source;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getEventId() {
        return RecordableBourneEvent.getUniqueEventId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSeverity() {
        String severity = _event.getSeverity();
        if(severity!=null)
            severity = severity.toUpperCase();
        return severity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAlertType() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRecordType() {
        return RecordType.Alert.name();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getNativeGuid() {
        String nativeGuid = null;
        if(_isilonDevice != null){
            nativeGuid = _isilonDevice.getNativeGuid();
        }
        return nativeGuid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOperationalStatusDescriptions() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOperationalStatusCodes() {
        return null;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public URI getResourceId() {
        URI resourceId = null;
        if(_isilonDevice != null){
            resourceId = _isilonDevice.getId();
        }
        return resourceId;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public URI getProjectId() {
       return null;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public URI getVirtualPool() {
       return null;
    }
}
