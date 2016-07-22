/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.util;

import java.net.URI;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;

public class EventUtil {

    /**
     * Creates an actionable event and persists to the database
     * 
     * @param dbClient db client
     * @param tenant the tenant that owns the event
     * @param name the name of the event
     * @param description the description of what the event will do
     * @param resource the resource that owns the event (host, cluster, etc)
     * @param approveMethod the method to invoke when approving the event
     * @param approveParameters the parameters to pass to the approve method
     * @param declineMethod the method to invoke when declining the event
     * @param declineParameters the parameters to pass to the decline method
     */
    public static void createActionableEvent(DbClient dbClient, URI tenant, String name, String description,
            DataObject resource, String approveMethod, Object[] approveParameters,
            String declineMethod, Object[] declineParameters) {
        ActionableEvent event = new ActionableEvent();
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
    }

    /**
     * Creates an actionable event and persists to the database
     * 
     * @param dbClient db client
     * @param tenant the tenant that owns the event
     * @param name the name of the event
     * @param description the description of what the event will do
     * @param resource the resource that owns the event (host, cluster, etc)
     * @param approveMethod the method to invoke when approving the event
     * @param approveParameters the parameters to pass to the approve method
     */
    public static void createActionableEvent(DbClient dbClient, URI tenant, String name, String description,
            DataObject resource, String approveMethod, Object[] approveParameters) {
        createActionableEvent(dbClient, tenant, null, description,
                resource, approveMethod, approveParameters, null, null);
    }

}
