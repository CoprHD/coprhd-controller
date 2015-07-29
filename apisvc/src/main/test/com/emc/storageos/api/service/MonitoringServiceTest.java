/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.api.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.junit.Assert;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.junit.Test;

import com.emc.storageos.api.service.impl.resource.MonitoringService;
import com.emc.storageos.api.service.impl.resource.utils.EventRetriever;
import com.emc.storageos.api.service.utils.DummyEventRetriever;
import com.emc.storageos.api.service.utils.DummyHttpHeaders;
import com.emc.storageos.api.service.utils.Events;

public class MonitoringServiceTest {

    /**
     * test feed output files
     */
    private static final String XmlTestOutputFile = "testEventRetriver.xml";
    private static final String JsonTestOutputFile = "testEventRetriver.json";

    @Test
    public void testEventRetriverXML() throws WebApplicationException,
            IOException, JAXBException {

        deleteIfExists(XmlTestOutputFile);

        EventRetriever eventRetriever = new DummyEventRetriever();
        MonitoringService eventResource = new MonitoringService();
        eventResource.setEventRetriever(eventRetriever);

        DummyHttpHeaders header = new DummyHttpHeaders(
                MediaType.APPLICATION_XML_TYPE);

        Response r = eventResource.getEvents("2012-05-05T00:00", header);

        Assert.assertNotNull(r);
        Assert.assertEquals(Status.OK.getStatusCode(), r.getStatus());
        Assert.assertTrue(r.getEntity() instanceof StreamingOutput);

        StreamingOutput so = (StreamingOutput) r.getEntity();

        File of = new File(XmlTestOutputFile);

        OutputStream os = new FileOutputStream(of);
        so.write(os);

        os.close();

        JAXBContext context = null;
        Unmarshaller unmarshaller = null;
        context = JAXBContext.newInstance(Events.class);
        unmarshaller = context.createUnmarshaller();

        Object o = unmarshaller.unmarshal(new File(XmlTestOutputFile));
        Assert.assertTrue(o instanceof Events);

        Events events = (Events) o;

        // expected number of events unmarshaled
        Assert.assertEquals(100, events.events.size());
    }

    @Test
    public void testEventRetriverJSON() throws WebApplicationException,
            IOException, JsonParseException {

        deleteIfExists(JsonTestOutputFile);

        EventRetriever eventRetriever = new DummyEventRetriever();
        MonitoringService eventResource = new MonitoringService();
        eventResource.setEventRetriever(eventRetriever);

        DummyHttpHeaders header = new DummyHttpHeaders(
                MediaType.APPLICATION_JSON_TYPE);

        Response r = eventResource.getEvents("2012-05-05T00", header);

        Assert.assertNotNull(r);
        Assert.assertEquals(Status.OK.getStatusCode(), r.getStatus());
        Assert.assertTrue(r.getEntity() instanceof StreamingOutput);

        StreamingOutput so = (StreamingOutput) r.getEntity();

        File of = new File(JsonTestOutputFile);

        OutputStream os = new FileOutputStream(of);
        try {
            so.write(os);
        } finally {
            os.close();
        }

        ObjectMapper mapper = null;
        mapper = new ObjectMapper();
        AnnotationIntrospector introspector = new JaxbAnnotationIntrospector();
        mapper.getDeserializationConfig().withAnnotationIntrospector(
                introspector);

        Events events = mapper.readValue(new File(JsonTestOutputFile),
                Events.class);

        Assert.assertEquals(100, events.events.size());
    }

    @Test
    public void testEventRetriverNonSupportedType() {
        EventRetriever eventRetriever = new DummyEventRetriever();
        MonitoringService eventResource = new MonitoringService();
        eventResource.setEventRetriever(eventRetriever);

        DummyHttpHeaders header = new DummyHttpHeaders(MediaType.TEXT_PLAIN_TYPE);

        Response r = eventResource
                .getEvents("2012-05-05T00", header);

        Assert.assertNotNull(r);
        Assert.assertEquals(Status.OK.getStatusCode(), r.getStatus(), r.getStatus());
    }

    private void deleteIfExists(String fname) {
        File f = new File(fname);

        if (f.exists()) {
            f.delete();
        }
    }
}
