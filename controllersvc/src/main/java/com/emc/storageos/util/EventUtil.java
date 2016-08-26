/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.util;

import java.net.URI;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.util.EventUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

public class EventUtil {

    private static Logger log = LoggerFactory.getLogger(EventUtil.class);

    public static String hostVcenterUnassign = "hostVcenterUnassign";
    public static String hostVcenterChange = "hostVcenterChange";
    public static String hostDatacenterChange = "hostDatacenterChange";
    public static String hostClusterChange = "hostClusterChange";
    public static String removeInitiator = "removeInitiator";
    public static String addInitiator = "addInitiator";

    public static String hostVcenterUnassignDecline = "hostVcenterUnassignDecline";
    public static String hostVcenterChangeDecline = "hostVcenterChangeDecline";
    public static String hostDatacenterChangeDecline = "hostDatacenterChangeDecline";
    public static String hostClusterChangeDecline = "hostClusterChangeDecline";
    public static String removeInitiatorDecline = "removeInitiatorDecline";
    public static String addInitiatorDecline = "addInitiatorDecline";

    public enum EventCode {
        HOST_CLUSTER_CHANGE("101"),
        HOST_INITIATOR_ADD("102"),
        HOST_INITIATOR_DELETE("103"),
        HOST_DATACENTER_CHANGE("104"),
        HOST_VCENTER_CHANGE("105"),
        UNASSIGN_HOST_FROM_VCENTER("106");

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
     * @param warning the warning message to display to the user if the event will cause data loss or other impacting change
     * @param resource the resource that owns the event (host, cluster, etc)
     * @param affectedResources the resources that are affected by this event (host, cluster, initiator, etc)
     * @param approveMethod the method to invoke when approving the event
     * @param approveParameters the parameters to pass to the approve method
     * @param declineMethod the method to invoke when declining the event
     * @param declineParameters the parameters to pass to the decline method
     */
    public static void createActionableEvent(DbClient dbClient, EventCode eventCode, URI tenant, String name, String description,
            String warning,
            DataObject resource, Collection<URI> affectedResources, String approveMethod, Object[] approveParameters,
            String declineMethod, Object[] declineParameters) {
        ActionableEvent duplicateEvent = getDuplicateEvent(dbClient, eventCode.getCode(), resource.getId());
        if (duplicateEvent != null) {
            log.info("Duplicate event " + duplicateEvent.getId() + " is already in a pending state for resource " + resource.getId()
                    + ". Will not create a new event");
            duplicateEvent.setCreationTime(Calendar.getInstance());
            duplicateEvent.setDescription(description);
            duplicateEvent.setWarning(warning);
            duplicateEvent.setAffectedResources(getAffectedResources(affectedResources));
            setEventMethods(duplicateEvent, approveMethod, approveParameters, declineMethod, declineParameters);
            dbClient.updateObject(duplicateEvent);
        } else {
            ActionableEvent event = new ActionableEvent();
            event.setEventCode(eventCode.getCode());
            event.setId(URIUtil.createId(ActionableEvent.class));
            event.setTenant(tenant);
            event.setDescription(description);
            event.setWarning(warning);
            event.setEventStatus(ActionableEvent.Status.pending.name());
            event.setResource(new NamedURI(resource.getId(), resource.getLabel()));
            event.setAffectedResources(getAffectedResources(affectedResources));
            setEventMethods(event, approveMethod, approveParameters, declineMethod, declineParameters);
            event.setLabel(name);
            dbClient.createObject(event);
            log.info("Created Actionable Event: " + event.getId() + " Tenant: " + event.getTenant() + " Description: "
                    + event.getDescription() + " Warning: " + event.getWarning()
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
     * @param warning the warning message to display to the user if the event will cause data loss or other impacting change
     * @param resource the resource that owns the event (host, cluster, etc)
     * @param affectedResources the resources that are affected by this event (host, cluster, initiator, etc)
     * @param approveMethod the method to invoke when approving the event
     * @param approveParameters the parameters to pass to the approve method
     */
    public static void createActionableEvent(DbClient dbClient, EventCode eventCode, URI tenant, String name, String description,
            String warning,
            DataObject resource, List<URI> affectedResources, String approveMethod, Object[] approveParameters) {
        createActionableEvent(dbClient, eventCode, tenant, name, description, warning,
                resource, affectedResources, approveMethod, approveParameters, null, null);
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
                    + event.getDescription() + " Warning: " + event.getWarning()
                    + " Event Status: " + event.getEventStatus() + " Resource: " + event.getResource() + " Event Code: "
                    + event.getEventCode());
            dbClient.markForDeletion(event);
        }
    }

    /**
     * Serialize and set the approve and decline methods for an event
     * 
     * @param event
     * @param approveMethod
     * @param approveParameters
     * @param declineMethod
     * @param declineParameters
     */
    private static void setEventMethods(ActionableEvent event, String approveMethod, Object[] approveParameters, String declineMethod,
            Object[] declineParameters) {
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

    }

    /**
     * Create affected resources string set with all non-null URIs
     * 
     * @param ids the resource ids
     * @return stringset of non-null ids
     */
    private static StringSet getAffectedResources(Collection<URI> ids) {
        StringSet result = new StringSet();
        for (URI uri : ids) {
            if (!NullColumnValueGetter.isNullURI(uri)) {
                result.add(uri.toString());
            }
        }
        return result;
    }
}
