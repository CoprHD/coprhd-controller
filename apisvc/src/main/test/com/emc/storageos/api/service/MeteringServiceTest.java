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

import com.emc.storageos.api.service.impl.resource.MeteringService;
import com.emc.storageos.api.service.utils.DummyHttpHeaders;
import com.emc.storageos.api.service.utils.DummyStatRetriever;
import com.emc.storageos.api.service.utils.Stats;
import com.sun.jersey.api.client.ClientResponse.Status;

public class MeteringServiceTest {

    /**
     * test feed output files
     */
    private static final String XmlTestOutputFile = "xmlStatsOutput.xml";
    private static final String JsonTestOutputFile = "xmlStatsOutput.json";

    @Test
    public void meteringServiceTestXML() throws WebApplicationException,
            IOException, JAXBException {

        deleteIfExists(XmlTestOutputFile);

        DummyStatRetriever dbStatRetriever = new DummyStatRetriever();
        MeteringService statResource = new MeteringService();
        statResource.setStatRetriever(dbStatRetriever);

        DummyHttpHeaders header = new DummyHttpHeaders(
                MediaType.APPLICATION_XML_TYPE);

        Response r = statResource.getStats("2012-08-08T00:00", header);

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
        context = JAXBContext.newInstance(Stats.class);
        unmarshaller = context.createUnmarshaller();

        Object o = unmarshaller.unmarshal(new File(XmlTestOutputFile));
        Assert.assertTrue(o instanceof Stats);

        Stats stats = (Stats) o;

        // expected number of stats unmarshaled
        Assert.assertEquals(100, stats.stats.size());
        deleteIfExists(XmlTestOutputFile);
    }

    @Test
    public void testMeteringServiceJSON() throws WebApplicationException,
            IOException, JsonParseException {

        deleteIfExists(JsonTestOutputFile);

        DummyStatRetriever dbStatRetriever = new DummyStatRetriever();
        MeteringService statResource = new MeteringService();
        statResource.setStatRetriever(dbStatRetriever);

        DummyHttpHeaders header = new DummyHttpHeaders(
                MediaType.APPLICATION_JSON_TYPE);

        Response r = statResource.getStats("2012-08-08T00", header);

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

        Stats stats = mapper.readValue(new File(JsonTestOutputFile),
                Stats.class);

        Assert.assertEquals(100, stats.stats.size());
        deleteIfExists(JsonTestOutputFile);
    }

    
    private void deleteIfExists(String fname) {
        File f = new File(fname);

        if (f.exists()) {
            f.delete();
        }
    }
}