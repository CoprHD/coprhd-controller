/*
 * Copyright 2015 EMC Corporation
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
import org.junit.Test;

import com.emc.storageos.api.service.impl.resource.MeteringService;
import com.emc.storageos.api.service.impl.resource.utils.DbStatRetriever;
import com.emc.storageos.api.service.utils.DummyDBClient;
import com.emc.storageos.api.service.utils.DummyHttpHeaders;
import com.emc.storageos.api.service.utils.Stats;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.sun.jersey.api.client.ClientResponse.Status;

public class DbStatRetrieverTest {
    /**
     * test feed output files
     */
    private static final String XmlTestOutputFile = "xmlStatsOutput.xml";

    @Test
    public void meteringXmlServiceDBRetrieverTest() throws WebApplicationException,
            IOException, JAXBException {

        deleteIfExists(XmlTestOutputFile);

        DummyDBClient dbClient = new DummyDBClient();
        MeteringService statResource = new MeteringService();
        // statResource.setDbClient(dbClient);
        DbStatRetriever dummyDbStatRetriever = new DbStatRetriever();
        dummyDbStatRetriever.setDbClient(dbClient);
        statResource.setStatRetriever(dummyDbStatRetriever);

        DummyHttpHeaders header = new DummyHttpHeaders(
                MediaType.APPLICATION_XML_TYPE);

        Response r = statResource.getStats("2012-01-01T00:00", header);
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
        context = JAXBContext.newInstance(Stats.class);
        unmarshaller = context.createUnmarshaller();

        Object o = unmarshaller.unmarshal(new File(XmlTestOutputFile));
        Assert.assertTrue(o instanceof Stats);

        Stats stats = (Stats) o;

        // expected number of stats unmarshaled
        Assert.assertEquals(10, stats.stats.size());
        deleteIfExists(XmlTestOutputFile);

    }

    @Test
    public void statXmlServiceDBExceptionsTest() throws WebApplicationException,
            IOException, JAXBException {

        deleteIfExists(XmlTestOutputFile);

        DummyDBClient dbClient = new DummyDBClient();
        MeteringService statResource = new MeteringService();
        // statResource.setDbClient(dbClient);
        DbStatRetriever dummyDbStatRetriever = new DbStatRetriever();
        dummyDbStatRetriever.setDbClient(dbClient);
        statResource.setStatRetriever(dummyDbStatRetriever);

        DummyHttpHeaders header = new DummyHttpHeaders(
                MediaType.APPLICATION_XML_TYPE);

        Response r = statResource.getStats("2012-01-02T00:00", header);
        Assert.assertNotNull(r);
        Assert.assertEquals(Status.OK.getStatusCode(), r.getStatus());
        Assert.assertTrue(r.getEntity() instanceof StreamingOutput);
        StreamingOutput so = (StreamingOutput) r.getEntity();

        File of = new File(XmlTestOutputFile);
        OutputStream os = new FileOutputStream(of);
        try {
            so.write(os);
        } catch (Exception e) {
            Assert.assertTrue(e.toString().contains("I/O"));
        } finally {
            os.close();
        }
    }

    @Test
    public void xmlStatIllegalTimeBucketArgumentTest()
            throws WebApplicationException, IOException, JAXBException {

        deleteIfExists(XmlTestOutputFile);

        DummyDBClient dbClient = new DummyDBClient();
        MeteringService statResource = new MeteringService();
        statResource.setDbClient(dbClient);

        DummyHttpHeaders header = new DummyHttpHeaders(
                MediaType.APPLICATION_XML_TYPE);

        try {
            statResource.getStats("xxxyyy", header);
            Assert.fail("Expected a BadRequestException");
        } catch (BadRequestException e) {
            Assert.assertEquals(ServiceCode.API_PARAMETER_INVALID_TIME_FORMAT, e.getServiceCode());
        }
    }

    @Test
    public void xmlStatEmptyTimeBucketArgumentTest()
            throws WebApplicationException, IOException, JAXBException {

        deleteIfExists(XmlTestOutputFile);

        DummyDBClient dbClient = new DummyDBClient();
        MeteringService statResource = new MeteringService();
        statResource.setDbClient(dbClient);

        DummyHttpHeaders header = new DummyHttpHeaders(
                MediaType.APPLICATION_XML_TYPE);

        try {
            statResource.getStats("", header);
            Assert.fail("Expected a BadRequestException");
        } catch (BadRequestException e) {
            Assert.assertEquals(ServiceCode.API_PARAMETER_INVALID_TIME_FORMAT, e.getServiceCode());
        }
    }

    private void deleteIfExists(String fname) {
        File f = new File(fname);

        if (f.exists()) {
            f.delete();
        }
    }
}
