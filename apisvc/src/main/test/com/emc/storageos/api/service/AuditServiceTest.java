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
import com.emc.storageos.api.service.utils.DummyHttpHeaders;
import com.emc.storageos.api.service.utils.DummyAuditLogRetriever;
import com.emc.storageos.api.service.utils.AuditLogs;
import com.emc.storageos.api.service.impl.resource.AuditService;
import com.sun.jersey.api.client.ClientResponse.Status;

public class AuditServiceTest {

    /**
     * test feed output files
     */
    private static final String XmlTestOutputFile = "xmlAuditLogsOutput.xml";
    private static final String JsonTestOutputFile = "xmlAuditLogsOutput.json";

    @Test
    public void auditServiceTestXML() throws WebApplicationException,
            IOException, JAXBException {

        deleteIfExists(XmlTestOutputFile);

        DummyAuditLogRetriever dbAuditLogRetriever = new DummyAuditLogRetriever();
        AuditService auditResource = new AuditService();
        auditResource.setAuditLogRetriever(dbAuditLogRetriever);

        DummyHttpHeaders header = new DummyHttpHeaders(
                MediaType.APPLICATION_XML_TYPE);

        Response r = auditResource.getAuditLogs("2012-08-08T00:00", "en_US", header);

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
        context = JAXBContext.newInstance(AuditLogs.class);
        unmarshaller = context.createUnmarshaller();

        Object o = unmarshaller.unmarshal(new File(XmlTestOutputFile));
        Assert.assertTrue(o instanceof AuditLogs);

        AuditLogs auditLogs = (AuditLogs) o;

        // expected number of auditLogs unmarshaled
        Assert.assertEquals(100, auditLogs.auditLogs.size());
        deleteIfExists(XmlTestOutputFile);
    }

    @Test
    public void auditServiceTestJSON() throws WebApplicationException,
            IOException, JsonParseException {

        deleteIfExists(JsonTestOutputFile);

        DummyAuditLogRetriever dbAuditLogRetriever = new DummyAuditLogRetriever();
        AuditService auditResource = new AuditService();
        auditResource.setAuditLogRetriever(dbAuditLogRetriever);

        DummyHttpHeaders header = new DummyHttpHeaders(
                MediaType.APPLICATION_JSON_TYPE);

        Response r = auditResource.getAuditLogs("2012-08-08T00", "en_US", header);

        Assert.assertNotNull(r);
        Assert.assertEquals(Status.OK.getStatusCode(), r.getStatus());
        Assert.assertTrue(r.getEntity() instanceof StreamingOutput);

        StreamingOutput so = (StreamingOutput) r.getEntity();

        File of = new File(JsonTestOutputFile);

        OutputStream os = new FileOutputStream(of);
        so.write(os);
        os.close();

        ObjectMapper mapper = null;
        mapper = new ObjectMapper();
        AnnotationIntrospector introspector = new JaxbAnnotationIntrospector();
        mapper.getDeserializationConfig().withAnnotationIntrospector(
                introspector);

        AuditLogs auditLogs = mapper.readValue(new File(JsonTestOutputFile),
        		AuditLogs.class);

        Assert.assertEquals(100, auditLogs.auditLogs.size());
        deleteIfExists(JsonTestOutputFile);
    }

    
    private void deleteIfExists(String fname) {
        File f = new File(fname);

        if (f.exists()) {
            f.delete();
        }
    }
}
