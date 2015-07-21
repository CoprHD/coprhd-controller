/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring;


import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.db.client.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

/**
 * This abstract base class encapsulates behavior common for events generated
 * from monitored storage devices. RecordableDeviceEvent implements the methods
 * of the {@link RecordableEvent} interface for getting the tenant, project,
 * vpool, and user information to be recorded with the event. This information is
 * obtained from the database resource, i.e., Volume, Fileshare, etc..., that is
 * impacted by the event. This class will find the resource using the alternate
 * native identifier for the resource, which is provided by an implementation
 * specific class that extends the RecordableDeviceEvent. The resource reference
 * is then used to provide the tenant, project, vpool, and user when the
 * associated method is invoked.
 */
public abstract class RecordableDeviceEvent implements RecordableEvent {

    // A reference to the database client.
    protected DbClient _dbClient;

    // A reference to the resource, i.e., Volume, Fileshare, in the database
    // that is impacted by the event.
    private DataObject _resource = null;

    public void setResource(DataObject resource) {
        _resource = resource;
    }

    // The logger.
    protected static Logger s_logger = LoggerFactory.getLogger(RecordableDeviceEvent.class);

    /**
     * Constructor
     * 
     * @param dbClient
     *            A reference to the data base client.
     */
    public RecordableDeviceEvent(DbClient dbClient) {
        _dbClient = dbClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URI getTenantId() {
        URI tenantOrg = null;
        URI projectURI = getProjectId();
        if (projectURI != null) {
            tenantOrg = ControllerUtils.getProjectTenantOrgURI(_dbClient, getProjectId());
        }
        if (tenantOrg == null) {
        	// If none found we have to have a blank value in monitoring.
            tenantOrg = URI.create("");
        }
        return tenantOrg;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URI getUserId() {
        // Not currently available from the resource. To be added when
        // security is put in place
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URI getProjectId() {
        URI projectId = null;
        DataObject resource = getResource();
        if (resource != null) {
            if (resource instanceof Volume) {
                Volume volume = (Volume) resource;
                projectId = volume.getProject().getURI();
            } else if (resource instanceof FileShare) {
                FileShare fs = (FileShare) resource;
                projectId = fs.getProject().getURI();
            } else if(resource instanceof  BlockSnapshot){
                BlockSnapshot bs = (BlockSnapshot) resource;
                projectId = bs.getProject().getURI();
            } else {
                s_logger.info("Error getting projectId for event. Unexpected resource type {}.", resource.getClass().getName());
            }
        }
        return projectId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URI getVirtualPool() {
        URI vpool = null;
        DataObject resource = getResource();
        if (resource != null) {
            if (resource instanceof Volume) {
                Volume volume = (Volume) resource;
                vpool = volume.getVirtualPool();
            } else if (resource instanceof  BlockSnapshot) {
                BlockSnapshot bs = (BlockSnapshot) resource;
                Volume volume = _dbClient.queryObject(Volume.class, bs.getParent().getURI());
                if (volume != null) {
                    vpool = volume.getVirtualPool();
                }
            } else if (resource instanceof FileShare) {
                FileShare fs = (FileShare) resource;
                vpool = fs.getVirtualPool();
            } else {
                s_logger.info("Error getting vpool for event. Unexpected resource type {}.", resource.getClass().getName());
            }
        }
        return vpool;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URI getResourceId() {
        URI resourceId = null;
        DataObject resource = getResource();
        if (resource != null) {
            resourceId = resource.getId();
        }
        return resourceId;
    }

    /**
     * Returns the Class reference for the actual type of the resource impacted
     * by the event, which must be a subclass of {@link DataObject}. Implemented
     * by derived classes by extracting the information from the native event to
     * identify the resource type.
     * 
     * @return The Class reference for the actual type of the resource impacted
     *         by the event
     */
    
    protected abstract Class<? extends DataObject> getResourceClass();

    /**
     * Finds the resource in the database impacted by an event using the
     * alternate native id for the resource and the class of the resource as
     * provided by the concrete derived class.
     * 
     * @return A reference to the resource in the database impacted by the
     *         event.
     */
    
    private DataObject getResource() {
        String altNativeId = null;
        if (_resource == null && (altNativeId = getNativeGuid()) != null) {
            Class<? extends DataObject> resourceClass = getResourceClass();
            s_logger.debug("Looking up resource type: {} ", resourceClass.getSimpleName());
            if (resourceClass == null) {
                return null;
            }
            try {
                List<URI> resourceURIs = new ArrayList<URI>();
                if (resourceClass.getName().equals(Volume.class.getName())) {
                    resourceURIs = _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVolumeNativeGuidConstraint(altNativeId));
                } else if (resourceClass.getName().equals(BlockSnapshot.class.getName())) {
                    resourceURIs = _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getBlockSnapshotsByNativeGuid(altNativeId));
                }
                else if (resourceClass.getName().equals(FileShare.class.getName())) {
                    resourceURIs = _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getFileShareNativeIdConstraint(altNativeId));
                }else if (resourceClass.getName().equals(StoragePort.class.getName())) {
                    resourceURIs = _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getStoragePortByNativeGuidConstraint(altNativeId));
                }else if (resourceClass.getName().equals(StoragePool.class.getName())) {
                    resourceURIs = _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getStoragePoolByNativeGuidConstraint(altNativeId));
                }

                s_logger.debug("[Monitoring] -> Number of resources found {} having Native Guid: {}", resourceURIs.size(), altNativeId);
                if (resourceURIs.size() == 1)
                {
                    List<? extends DataObject> objects = _dbClient.queryObject(resourceClass, resourceURIs);
                    _resource = objects.get(0);
                }
                else if (resourceURIs.size() > 1 ) {
                    List<? extends DataObject> objects = _dbClient.queryObject(resourceClass, resourceURIs);
                    for(DataObject obj : objects) {
                        s_logger.info("[Monitoring] -> Resource found with native guid {} with its InActive status {}", altNativeId, obj.getInactive());
                       if(!obj.getInactive()) {
                           _resource = obj;
                           s_logger.info("[Monitoring] -> Found resource with label : {} and id : {}", _resource.getLabel(), _resource.getId());
                       }
                       else {
                           s_logger.info("[Monitoring] -> InActive resource found and Ignoring to update");
                       }
                    }
                } else {
                    s_logger.debug("Could not find resource of type {} with nativeGUID {}", resourceClass.getSimpleName(), altNativeId);
                }
            } catch (Exception t) {
                s_logger.error(MessageFormatter.format("Error finding resource of type {} with nativeGUID {}", resourceClass.getName(), altNativeId).getMessage(), t);
            }
        }
        return _resource;
    }

    /**
     * Event identifier
     * 
     * @return The eventId
     */
    public abstract String getEventId();

    /**
     * Event severity
     * 
     * @return the Severity of an Alert
     */
    public abstract String getSeverity();
}
