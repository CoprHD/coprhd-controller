/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.utils;

import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.EventMarshaller;
import com.emc.storageos.api.service.impl.resource.utils.EventRetriever;
import com.emc.storageos.api.service.impl.resource.utils.JSONEventMarshaller;
import com.emc.storageos.api.service.impl.resource.utils.MarshallingExcetion;
import com.emc.storageos.api.service.impl.resource.utils.XMLEventMarshaller;
import com.emc.storageos.db.client.TimeSeriesMetadata;
import com.emc.storageos.db.client.model.Event;

/**
 * Implemation of EventRetriever to retrieve events from underlying Cassandra DB
 * 
 * @author yifengc
 * 
 */
public class DummyEventRetriever implements EventRetriever {

    // @TODO - dummy events for test.

    final private Logger _logger = LoggerFactory.getLogger(DummyEventRetriever.class);

    @Override
    public void getBulkEvents(DateTime time, TimeSeriesMetadata.TimeBucket bucket,
            MediaType type, Writer writer) throws MarshallingExcetion {

        EventMarshaller marshaller = null;

        if (type == MediaType.APPLICATION_XML_TYPE) {
            marshaller = new XMLEventMarshaller();
        } else if (type == MediaType.APPLICATION_JSON_TYPE) {
            marshaller = new JSONEventMarshaller();
        }

        marshaller.header(writer);

        List<Event> events = null;
        try {
            events = getDummyEvents();
        } catch (URISyntaxException e) {
            _logger.error("Error getting events", e);
        }

       
        for (Event event : events) {
            if (type == MediaType.APPLICATION_XML_TYPE) {
                marshaller.marshal(event, writer);
            } else if (type == MediaType.APPLICATION_JSON_TYPE) {
                marshaller.marshal(event, writer);
            }
        }

        marshaller.tailer(writer);
    }

    private List<Event> getDummyEvents() throws URISyntaxException {

        // @TODO - dummy events at the moment.
        List<Event> elist = new ArrayList<Event>();

        for (int i = 0; i < 100; i++) {
            Event e = new Event();
            e.setEventId(String.valueOf(i));
            e.setDescription("Test Event " + e.getEventId());
            e.setProjectId(new URI("http://p." + e.getEventId()));
            e.setTenantId(new URI("http://t." + e.getEventId()));
            e.setUserId(new URI("http://u." + e.getEventId()));
            e.setVirtualPool(new URI("http://vpool.gold"));
            e.setExtensions("");
            e.setEventType("some type");
            e.setResourceId(new URI("http://r." + e.getEventId()));
            e.setEventSource("Some Source");
            e.setOperationalStatusCodes("Test Codes");
            e.setOperationalStatusDescriptions("Test Descriptions");
            // e._time = new DateTime().toString();
            elist.add(e);
        }

        return elist;
    }

}
