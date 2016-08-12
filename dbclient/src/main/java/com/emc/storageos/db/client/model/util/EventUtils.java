/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.util;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ActionableEvent;
import com.google.common.collect.Lists;

public class EventUtils {

    public static List<ActionableEvent> findResourceEvents(DbClient dbClient, URI resourceId) {
        return getEvents(dbClient, ContainmentConstraint.Factory.getResourceEventConstraint(resourceId));
    }

    private static List<ActionableEvent> getEvents(DbClient dbClient, Constraint constraint) {
        URIQueryResultList results = new URIQueryResultList();
        dbClient.queryByConstraint(constraint, results);

        List<ActionableEvent> events = Lists.newArrayList();
        Iterator<URI> it = results.iterator();
        while (it.hasNext()) {
            ActionableEvent event = dbClient.queryObject(ActionableEvent.class, it.next());
            if (event != null) {
                events.add(event);
            }
        }

        return events;
    }

}
