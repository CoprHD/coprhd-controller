/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.util;

import java.net.URI;
import java.util.Calendar;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.util.EventUtils;

public class EventUtil {

    private static Logger log = LoggerFactory.getLogger(EventUtil.class);

    public enum EventCode {
        HOST_CLUSTER_CHANGE("101"),
        HOST_INITIATOR_ADD("102"),
        HOST_INITIATOR_DELETE("103"),
        HOST_DATACENTER_CHANGE("104");

        private String code;

        EventCode(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    };

    private EventUtil() {
    };

    /**
     * Creates an actionable event and persists to the database
     * 
     * @param dbClient db client
     * @param eventCode the code for the event
     * @param tenant the tenant that owns the event
     * @param name the name of the event
     * @param description the description of what the event will do
     * @param resource the resource that owns the event (host, cluster, etc)
     * @param approveMethod the method to invoke when approving the event
     * @param approveParameters the parameters to pass to the approve method
     * @param declineMethod the method to invoke when declining the event
     * @param declineParameters the parameters to pass to the decline method
     */
    public static void createActionableEvent(DbClient dbClient, EventCode eventCode, URI tenant, String name, String description,
            DataObject resource, String approveMethod, Object[] approveParameters,
            String declineMethod, Object[] declineParameters) {
        ActionableEvent duplicateEvent = getDuplicateEvent(dbClient, eventCode.getCode(), resource.getId());
        if (duplicateEvent != null) {
            log.info("Duplicate event " + duplicateEvent.getId() + " is already in a pending state for resource " + resource.getId()
                    + ". Will not create a new event");
            duplicateEvent.setCreationTime(Calendar.getInstance());
            dbClient.updateObject(duplicateEvent);
        } else {
            ActionableEvent event = new ActionableEvent();
            event.setEventCode(eventCode.getCode());
            event.setId(URIUtil.createId(ActionableEvent.class));
            event.setTenant(tenant);
            event.setDescription(description);
            event.setEventStatus(ActionableEvent.Status.pending.name());
            event.setResource(new NamedURI(resource.getId(), resource.getLabel()));
            if (approveMethod != null) {
                com.emc.storageos.db.client.model.ActionableEvent.Method method = new ActionableEvent.Method(
                        approveMethod, approveParameters);
                event.setApproveMethod(method.serialize());
            }
            if (declineMethod != null) {
                com.emc.storageos.db.client.model.ActionableEvent.Method method = new ActionableEvent.Method(
                        declineMethod, declineParameters);
                event.setDeclineMethod(method.serialize());
            }
            event.setLabel(name);
            dbClient.createObject(event);
            log.info("Created Actionable Event: " + event.getId() + " Tenant: " + event.getTenant() + " Description: "
                    + event.getDescription()
                    + " Event Status: " + event.getEventStatus() + " Resource: " + event.getResource() + " Event Code: "
                    + event.getEventCode()
                    + " Approve Method: " + approveMethod
                    + " Decline Method: " + declineMethod);
        }
    }

    /**
     * Creates an actionable event and persists to the database
     * 
     * @param dbClient db client
     * @param eventCode the code for the event
     * @param tenant the tenant that owns the event
     * @param name the name of the event
     * @param description the description of what the event will do
     * @param resource the resource that owns the event (host, cluster, etc)
     * @param approveMethod the method to invoke when approving the event
     * @param approveParameters the parameters to pass to the approve method
     */
    public static void createActionableEvent(DbClient dbClient, EventCode eventCode, URI tenant, String name, String description,
            DataObject resource, String approveMethod, Object[] approveParameters) {
        createActionableEvent(dbClient, eventCode, tenant, name, description,
                resource, approveMethod, approveParameters, null, null);
    }

    /**
     * Returns a duplicate pending event if one exists, else returns null
     * 
     * @param dbClient db client
     * @param the event code
     * @param resourceId the id of the resource to check
     * @return event if a duplicate is found, else null
     */
    public static ActionableEvent getDuplicateEvent(DbClient dbClient, String eventCode, URI resourceId) {
        List<ActionableEvent> events = EventUtils.findResourceEvents(dbClient, resourceId);
        for (ActionableEvent event : events) {
            if (event.getEventStatus().equalsIgnoreCase(ActionableEvent.Status.pending.name())
                    && event.getEventCode().equalsIgnoreCase(eventCode)) {
                return event;
            }
        }
        return null;
    }

    /**
     * Delete all actionable events for a given resource
     * 
     * @param dbClient db client
     * @param resourceId the resource id for events to delete
     */
    public static void deleteResourceEvents(DbClient dbClient, URI resourceId) {
        List<ActionableEvent> events = EventUtils.findResourceEvents(dbClient, resourceId);
        log.info("Deleting actionable events for resource " + resourceId);
        for (ActionableEvent event : events) {
            log.info("Deleting Actionable Event: " + event.getId() + " Tenant: " + event.getTenant() + " Description: "
                    + event.getDescription()
                    + " Event Status: " + event.getEventStatus() + " Resource: " + event.getResource() + " Event Code: "
                    + event.getEventCode());
            dbClient.markForDeletion(event);
        }
    }

}
