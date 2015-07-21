/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.junit.Assert;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.junit.Test;

import com.emc.storageos.api.service.impl.resource.MonitoringService;
import com.emc.storageos.api.service.impl.resource.utils.DbEventRetriever;
import com.emc.storageos.api.service.utils.DummyDBClient;
import com.emc.storageos.api.service.utils.DummyHttpHeaders;
import com.emc.storageos.api.service.utils.Events;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.sun.jersey.api.client.ClientResponse.Status;

public class DbEventRetrieverTest {
    /**
     * test feed output files
     */
    private static final String XmlTestOutputFile = "xmlEventsOutput.xml";
    private static final String JsonTestOutputFile = "jsonEventsOutput.json";
    private static final int queryThreadCount = 10;
    @Test
    public void monitoringServiceDBRetrieverTestXML()
            throws WebApplicationException, IOException, JAXBException {

        deleteIfExists(XmlTestOutputFile);

        DummyDBClient dbClient = new DummyDBClient();
        MonitoringService eventResource = new MonitoringService();
        DbEventRetriever dummyDbStatRetriever = new DbEventRetriever();
        dummyDbStatRetriever.setDbClient(dbClient);
        eventResource.setEventRetriever(dummyDbStatRetriever);

        DummyHttpHeaders header = new DummyHttpHeaders(
                MediaType.APPLICATION_XML_TYPE);

        Response r =eventResource.getEvents("2012-01-04T00:00", header);
        Assert.assertNotNull(r);
        Assert.assertEquals(Status.OK.getStatusCode(), r.getStatus());
        Assert.assertTrue(r.getEntity() instanceof StreamingOutput);
        StreamingOutput so = (StreamingOutput) r.getEntity();

        File of = new File(XmlTestOutputFile);

        OutputStream os = new FileOutputStream(of);
        try {
            so.write(os);
        } finally {
            os.close();
        }

        JAXBContext context = null;
        Unmarshaller unmarshaller = null;
        context = JAXBContext.newInstance(Events.class);
        unmarshaller = context.createUnmarshaller();

        Object o = unmarshaller.unmarshal(new File(XmlTestOutputFile));
        Assert.assertTrue(o instanceof Events);

        Events events = (Events) o;

        // expected number of events unmarshaled
        Assert.assertEquals(10, events.events.size());
        deleteIfExists(XmlTestOutputFile);
    }

    @Test
    public void meteringServiceDBExceptionsTestXML()
            throws WebApplicationException, IOException, JAXBException {

        deleteIfExists(XmlTestOutputFile);

        DummyDBClient dbClient = new DummyDBClient();
        MonitoringService eventResource = new MonitoringService();
        // statResource.setDbClient(dbClient);
        DbEventRetriever dummyDbStatRetriever = new DbEventRetriever();
        dummyDbStatRetriever.setDbClient(dbClient);
        eventResource.setEventRetriever(dummyDbStatRetriever);

        DummyHttpHeaders header = new DummyHttpHeaders(
                MediaType.APPLICATION_XML_TYPE);

        Response r = eventResource.getEvents("2012-01-02T00:00", header);
        Assert.assertNotNull(r);
        Assert.assertEquals(Status.OK.getStatusCode(), r.getStatus());
        Assert.assertTrue(r.getEntity() instanceof StreamingOutput);
        StreamingOutput so = (StreamingOutput) r.getEntity();

        File of = new File(XmlTestOutputFile);

        OutputStream os = new FileOutputStream(of);
        try {
            so.write(os);
        } catch (InternalServerErrorException e) {
            Assert.assertTrue(e.toString().contains("I/O"));
        } finally {
            os.close();
        }
    }

    @Test
    public void meteringServiceMarshallingExceptionsTestXML()
            throws WebApplicationException, IOException, JAXBException {

        deleteIfExists(XmlTestOutputFile);

        DummyDBClient dbClient = new DummyDBClient();
        MonitoringService eventResource = new MonitoringService();
        // statResource.setDbClient(dbClient);
        DbEventRetriever dummyDbStatRetriever = new DbEventRetriever();
        dummyDbStatRetriever.setQueryThreadCount(queryThreadCount);
        dummyDbStatRetriever.setDbClient(dbClient);
        eventResource.setEventRetriever(dummyDbStatRetriever);

        DummyHttpHeaders header = new DummyHttpHeaders(
                MediaType.APPLICATION_XML_TYPE);

        Response r = eventResource.getEvents("2012-01-05T00:00", header);
        Assert.assertNotNull(r);
        Assert.assertEquals(Status.OK.getStatusCode(), r.getStatus());
        Assert.assertTrue(r.getEntity() instanceof StreamingOutput);
        StreamingOutput so = (StreamingOutput) r.getEntity();

        File of = new File(XmlTestOutputFile);

        OutputStream os = new FileOutputStream(of);
        try {
            so.write(os);
        } finally {
            os.close();
        }
    }
    
    
    @Test
    public void meteringServiceDBExceptionsTestJSON()
            throws WebApplicationException, IOException, JAXBException {

        deleteIfExists(JsonTestOutputFile);

        DummyDBClient dbClient = new DummyDBClient();
        MonitoringService eventResource = new MonitoringService();
        DbEventRetriever dummyDbStatRetriever = new DbEventRetriever();
        dummyDbStatRetriever.setDbClient(dbClient);
        eventResource.setEventRetriever(dummyDbStatRetriever);

        DummyHttpHeaders header = new DummyHttpHeaders(
                MediaType.APPLICATION_JSON_TYPE);

        Response r = eventResource.getEvents("2012-01-04T00:00", header);
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

        Assert.assertEquals(10, events.events.size());
        deleteIfExists(JsonTestOutputFile);
    }




    
    @Test
    public void meteringServiceNullDBclientTestXML()
            throws WebApplicationException, IOException, JAXBException {

        deleteIfExists(XmlTestOutputFile);

        DummyDBClient dbClient = null;
        MonitoringService eventResource = new MonitoringService();
        // statResource.setDbClient(dbClient);
        DbEventRetriever dummyDbStatRetriever = new DbEventRetriever();
        dummyDbStatRetriever.setDbClient(dbClient);
        eventResource.setEventRetriever(dummyDbStatRetriever);

        DummyHttpHeaders header = new DummyHttpHeaders(
                MediaType.APPLICATION_XML_TYPE);

        Response r = eventResource.getEvents("2012-01-02T00:00", header);
        Assert.assertNotNull(r);
        Assert.assertEquals(Status.OK.getStatusCode(), r.getStatus());
        Assert.assertTrue(r.getEntity() instanceof StreamingOutput);
        StreamingOutput so = (StreamingOutput) r.getEntity();

        File of = new File(XmlTestOutputFile);

        OutputStream os = new FileOutputStream(of);
        try {
            so.write(os);
        } catch (InternalServerErrorException e) {
            Assert.assertTrue(e.toString().contains("DB"));
        } finally {
            os.close();
        }
    }
    
    private void deleteIfExists(String fname) {
        File f = new File(fname);

        if (f.exists()) {
            f.delete();
        }
    }
}
