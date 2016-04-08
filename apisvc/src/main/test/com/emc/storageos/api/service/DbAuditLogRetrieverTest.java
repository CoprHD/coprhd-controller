/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service;

import java.io.*;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import com.emc.storageos.api.service.impl.resource.utils.MarshallingExcetion;
import com.emc.storageos.security.audit.AuditLogRequest;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Assert;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.junit.Test;

import com.emc.storageos.api.service.impl.resource.AuditService;
import com.emc.storageos.api.service.impl.resource.utils.DbAuditLogRetriever;
import com.emc.storageos.api.service.utils.DummyDBClient;
import com.emc.storageos.api.service.utils.DummyHttpHeaders;
import com.emc.storageos.api.service.utils.AuditLogs;
import com.emc.storageos.db.exceptions.RetryableDatabaseException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.sun.jersey.api.client.ClientResponse.Status;

public class DbAuditLogRetrieverTest {
    /**
     * test feed output files
     */
    private static final String XmlTestOutputFile = "xmlAuditLogsOutput.xml";
    private static final String JsonTestOutputFile = "jsonAuditLogsOutput.json";
    private static final int queryThreadCount = 10;

    @Test
    public void auditServiceDBRetrieverTestXML()
            throws WebApplicationException, IOException, JAXBException {

        deleteIfExists(XmlTestOutputFile);

        DummyDBClient dbClient = new DummyDBClient();
        DbAuditLogRetriever dummyDbAuditLogRetriever = new DbAuditLogRetriever();
        dummyDbAuditLogRetriever.setDbClient(dbClient);
        String timeBucket = "2012-01-07T00";
        AuditLogRequest auditLogRequest = getAuditLogRequest(timeBucket);
        MediaType mediaType = MediaType.APPLICATION_XML_TYPE;

        File of = new File(XmlTestOutputFile);
        OutputStream os = new FileOutputStream(of);
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(os));
        try {
            dummyDbAuditLogRetriever.getBulkAuditLogs(auditLogRequest,mediaType,out);
        } catch (MarshallingExcetion e ) {
            Assert.fail(e.getMessage());
        }
        out.close();

        JAXBContext context = null;
        Unmarshaller unmarshaller = null;
        context = JAXBContext.newInstance(AuditLogs.class);
        unmarshaller = context.createUnmarshaller();

        Object o = unmarshaller.unmarshal(new File(XmlTestOutputFile));
        Assert.assertTrue(o instanceof AuditLogs);

        AuditLogs auditLogs = (AuditLogs) o;

        // expected number of events unmarshaled
        Assert.assertEquals(10, auditLogs.auditLogs.size());
        deleteIfExists(XmlTestOutputFile);


    }
    @Test
    public void auditServiceDBExceptionsTestXML()
            throws WebApplicationException, IOException, JAXBException {

        deleteIfExists(XmlTestOutputFile);

        DummyDBClient dbClient = new DummyDBClient();
        DbAuditLogRetriever dummyDbAuditLogRetriever = new DbAuditLogRetriever();
        dummyDbAuditLogRetriever.setDbClient(dbClient);
        String timeBucket = "2012-01-02T00";
        AuditLogRequest auditLogRequest = getAuditLogRequest(timeBucket);
        MediaType mediaType = MediaType.APPLICATION_XML_TYPE;

        File of = new File(XmlTestOutputFile);
        OutputStream os = new FileOutputStream(of);
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(os));
        try {
            dummyDbAuditLogRetriever.getBulkAuditLogs(auditLogRequest,mediaType,out);
        } catch (RetryableDatabaseException e ) {
            Assert.assertTrue(e.toString().contains("I/O"));
        } catch (MarshallingExcetion e ) {
            Assert.fail(e.getMessage());
        }
        out.close();
    }

    @Test
    public void auditServiceMarshallingExceptionsTestXML()
            throws WebApplicationException, IOException, JAXBException {

        deleteIfExists(XmlTestOutputFile);

        DummyDBClient dbClient = new DummyDBClient();
        DbAuditLogRetriever dummyDbAuditLogRetriever = new DbAuditLogRetriever();
        dummyDbAuditLogRetriever.setDbClient(dbClient);
        String timeBucket = "2012-01-08T00";
        AuditLogRequest auditLogRequest = getAuditLogRequest(timeBucket);
        MediaType mediaType = MediaType.APPLICATION_XML_TYPE;

        File of = new File(XmlTestOutputFile);
        OutputStream os = new FileOutputStream(of);
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(os));
        try {
            dummyDbAuditLogRetriever.getBulkAuditLogs(auditLogRequest,mediaType,out);
        } catch (MarshallingExcetion e ) {
            Assert.assertTrue(true);
        }
        out.close();
    }

    @Test
    public void auditServiceDBExceptionsTestJSON()
            throws WebApplicationException, IOException, JAXBException {

        deleteIfExists(JsonTestOutputFile);

        DummyDBClient dbClient = new DummyDBClient();
        DbAuditLogRetriever dummyDbAuditLogRetriever = new DbAuditLogRetriever();
        dummyDbAuditLogRetriever.setDbClient(dbClient);
        String timeBucket = "2012-01-07T00";
        DateTime startTime = new DateTime(timeBucket, DateTimeZone.UTC);
        DateTime endTime =  startTime.plusMinutes(59);
        AuditLogRequest auditLogRequest = new AuditLogRequest.Builder().timeBucket(timeBucket)
                .start(startTime).end(endTime).lang("en_US").build();
        MediaType mediaType = MediaType.APPLICATION_JSON_TYPE;

        File of = new File(JsonTestOutputFile);

        OutputStream os = new FileOutputStream(of);

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(os));
        try {
            dummyDbAuditLogRetriever.getBulkAuditLogs(auditLogRequest,mediaType,out);
        } catch (MarshallingExcetion e ) {
            Assert.fail(e.getMessage());
        }
        out.close();

        ObjectMapper mapper = null;
        mapper = new ObjectMapper();
        AnnotationIntrospector introspector = new JaxbAnnotationIntrospector();
        mapper.getDeserializationConfig().withAnnotationIntrospector(
                introspector);

        AuditLogs auditLogs = mapper.readValue(new File(JsonTestOutputFile),
                AuditLogs.class);

        Assert.assertEquals(10, auditLogs.auditLogs.size());
        deleteIfExists(JsonTestOutputFile);
    }

    @Test
    public void auditServiceNullDBclientTestXML()
            throws WebApplicationException, IOException, JAXBException {

        deleteIfExists(XmlTestOutputFile);

        DummyDBClient dbClient = null;
        AuditService auditResource = new AuditService();
        DbAuditLogRetriever dummyDbAuditLogRetriever = new DbAuditLogRetriever();
        dummyDbAuditLogRetriever.setDbClient(dbClient);
        auditResource.setAuditLogRetriever(dummyDbAuditLogRetriever);

        DummyHttpHeaders header = new DummyHttpHeaders(
                MediaType.APPLICATION_XML_TYPE);

        Response r = auditResource.getAuditLogs("2012-01-05T00:00", "en_US", header);
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
        }

        os.close();

    }

    private void deleteIfExists(String fname) {
        File f = new File(fname);

        if (f.exists()) {
            f.delete();
        }
    }

    private AuditLogRequest getAuditLogRequest(String timeBucket) {
        DateTime startTime = new DateTime(timeBucket, DateTimeZone.UTC);
        DateTime endTime =  startTime.plusMinutes(59);
        return new AuditLogRequest.Builder().timeBucket(timeBucket)
                .start(startTime).end(endTime).lang("en_US").build();
    }

}
