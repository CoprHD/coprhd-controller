/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package util;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.db.client.model.util.EventUtils.EventCode;
import com.emc.storageos.model.event.EventDetailsRestRep;
import com.emc.storageos.model.event.EventRestRep;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;

public class EventUtils {
    
    private static List<EventCode> AUTO_REMEDIATE_EVENTS = Lists.newArrayList( EventCode.VCENTER_DATASTORE_RENAME, 
            EventCode.VCENTER_DATASTORE_DELETE, 
            EventCode.VCENTER_DATASTORE_CREATE);

    // public static List<EventRestRep> getEvents(URI resourceId) {
    // if (resourceId != null) {
    // ViPRCoreClient client = getViprClient();
    // return client.events().findByResource(resourceId);
    // }
    // return null;
    // }

    /**
     * Returns the Details of a specified event or NULL if the event doesn't exist
     * 
     * @param eventId Id of the event
     * @return The Event details or NULL if the event doesn't exist
     */
    public static EventRestRep getEvent(URI eventId) {
        if (eventId != null) {
            try {
                return getViprClient().events().get(eventId);
            } catch (ViPRHttpException e) {
                // Anything other than 404 is an error
                if (e.getHttpCode() != 404) {
                    throw e;
                }
            }
        }
        return null;
    }

    public static EventDetailsRestRep getEventDetails(URI eventId) {
        if (eventId != null) {
            try {
                return getViprClient().events().getDetails(eventId);
            } catch (ViPRHttpException e) {
                // Anything other than 404 is an error
                if (e.getHttpCode() != 404) {
                    throw e;
                }
            }
        }
        return null;
    }
    
    /**
     * Returns if the event is an auto remediable event.
     * 
     * @param event
     * @return
     */
    public static boolean isAutoRemediateEvents(EventRestRep event) {
        if ((event.getEventStatus().equalsIgnoreCase(ActionableEvent.Status.pending.name()))
                || (AUTO_REMEDIATE_EVENTS.contains(event.getEventCode()))) {
            return true;
        }
        return false;
    }
}
