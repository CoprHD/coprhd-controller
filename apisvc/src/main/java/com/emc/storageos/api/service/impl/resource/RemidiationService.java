/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Alerts;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.alerts.AlertsBulkResponse;
import com.emc.storageos.model.alerts.AlertsCreateResponse;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

@Path("/object/remidiation")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        readAcls = { ACL.OWN, ACL.ALL },
        writeRoles = { Role.TENANT_ADMIN },
        writeAcls = { ACL.OWN, ACL.ALL })
public class RemidiationService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(RemidiationService.class);

    @SuppressWarnings("unchecked")
    @Override
    public Class<Alerts> getResourceClass() {
        return Alerts.class;
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/newbulk")
    public BulkIdParam getNotification() {
        List<URI> storageContainerUris = _dbClient.queryByType(Alerts.class, true);
        List<Alerts> alerts = _dbClient.queryObject(Alerts.class, storageContainerUris);
        BulkIdParam bulkIdParam = new BulkIdParam();
        if (null != alerts) {
            for (Alerts alert : alerts) {
                if (null != alert) {
                    bulkIdParam.getIds().add(alert.getId());
                }
            }
        }
        return bulkIdParam;
    }
    
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{notificationID}")
    public AlertsCreateResponse getStorageContainerDetail(@PathParam("notificationID") URI storageContainerId){
        if(null != storageContainerId){
            Alerts storageContainer = _dbClient.queryObject(Alerts.class, storageContainerId);
            if(storageContainer != null){
                return toStorageContainer(storageContainer);
            }
        }
        return null;
    }
        
    @Override
    public AlertsBulkResponse queryBulkResourceReps(List<URI> ids) {
        AlertsBulkResponse storageContainerBulkResponse = new AlertsBulkResponse();
        if (!ids.iterator().hasNext()) {
            return storageContainerBulkResponse;
        } else {
            return getBulkStorageContainerResponse(storageContainerBulkResponse, ids);
        }
    }
    
    private AlertsBulkResponse getBulkStorageContainerResponse(AlertsBulkResponse storageContainerBulkResponse, List<URI> ids){
        try {
            List<Alerts> storageContainers = _dbClient
                    .queryObject(Alerts.class, ids);
            if (null != storageContainers) {
                for (Alerts storageContainer : storageContainers) {
                    if (null != storageContainer) {
                        storageContainerBulkResponse.getStorageContainers()
                                .add(toStorageContainer(storageContainer));
                    }
                }
            }
        } catch (Exception ex) {
            throw APIException.internalServerErrors.genericApisvcError(
                    "error retrieving storage container", ex);
        }
        return storageContainerBulkResponse;
    }

    @Override
    protected DataObject queryResource(URI id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        // TODO Auto-generated method stub
        return null;
    }
    
    public static AlertsCreateResponse toStorageContainer(Alerts from) {
        if (from == null) {
            return null;
        }
        AlertsCreateResponse to = new AlertsCreateResponse();

        to.setAffectedResourceID(from.getAffectedResourceId());
        to.setProblemDescription(from.getProblemDescription());

        return to;
    }
}
