/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.simulators.impl.resource;

import com.emc.storageos.isilon.restapi.IsilonEvent;
import com.emc.storageos.simulators.eventmanager.EventManager;
import com.google.gson.Gson;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

@Path("/platform/2/event")
public class Events extends BaseResource {
    private final int NUM_PAGE = 100;

    public static class CustomEvents extends IsilonEvent {
        public CustomEvents(String start, String end, String acknowledged_time,
                            String devid, String id, String extreme_severity,
                            String event_type,
                            String extreme_value, String is_coalescing, String message,
                            String severity, String update_count, String value) {
            this.start = start;
            this.end = end;
            this.acknowledged_time = acknowledged_time;
            this.devid = devid;
            this.id = id;
            this.extreme_severity = extreme_severity;
            this.event_type = event_type;
            this.extreme_value = extreme_value;
            this.is_coalescing = is_coalescing;
            this.message = message;
            this.severity = severity;
            this.update_count = update_count;
            this.value = value;
        }

        public CustomEvents(String id, String event_type, String start, String devid) {
            this.id = id;
            this.event_type = event_type;
            this.start = start;
            this.devid = devid;
        }
    }

    public static class CustomSpecifier extends IsilonEvent.Specifiers {
        public CustomSpecifier(String devid, String lnn, String val) {
            this.devid = devid;
            this.lnn = lnn;
            this.val = val;
        }
    }

    public static class EventResponse {
        private ArrayList<IsilonEvent> events = new ArrayList<IsilonEvent>();
        private String total;
        private String resume;
    }

    @GET
    @Path("/events")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getEvents(@QueryParam("begin") String begin, @QueryParam("end") String end, @QueryParam("resume") String resume) {
        long currentTime = System.currentTimeMillis();
        long startTime = 0L;
        long endTime = Long.MAX_VALUE;

        if (begin != null && !"".equals(begin))
            startTime = currentTime + Long.parseLong(begin) * 1000;
        if (end != null && !"".equals(end))
            endTime = currentTime + Long.parseLong(end) * 1000;
        if (resume != null && !"".equals(resume)) {
            startTime = Math.max(startTime, Long.parseLong(resume));
        }

        EventResponse eventResponse = new EventResponse();

        ConcurrentLinkedQueue<EventManager.TimedEvent> eventList
                = EventManager.getInstance().getEventQueue();
        Iterator<EventManager.TimedEvent> iterator = eventList.iterator();
        int count = 0;
        while (iterator.hasNext()) {
            EventManager.TimedEvent event = iterator.next();
            if (event.getTime() >= startTime && event.getTime() <= endTime) {
                eventResponse.events.add(event);
                count++;
                if (count == NUM_PAGE) {
                    eventResponse.resume = event.getTime() + 1 + "";
                    eventResponse.total = "null";
                    break;
                } else {
                    eventResponse.resume = "";
                    eventResponse.total = "" + count;
                }
            }
        }

        if (eventResponse.events.size() == 0) {
            eventResponse.resume = "";
            eventResponse.total = "0";
        }

        return Response.status(200).entity(new Gson().toJson(eventResponse)).build();
    }
}
