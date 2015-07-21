/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import org.junit.Assert;
import org.junit.Test;
import com.emc.storageos.api.service.impl.resource.utils.MarshallingExcetion;
import com.emc.storageos.api.service.impl.resource.utils.XMLAuditLogMarshaller;
import com.emc.storageos.db.client.model.AuditLog;

public class XMLAuditLogMarshallerTest {
    private static final String XmlTestOutputFile = "XMLAuditLogMarshallerTest.xml";

    @Test
    public void testXmlAuditLogMarshalling() throws URISyntaxException, IOException,
            MarshallingExcetion, JAXBException {

    	XMLAuditLogMarshaller xm = new XMLAuditLogMarshaller();

        AuditLog log = new AuditLog();
        log.setProductId("productId.1");
        log.setTenantId(new URI("http://tenant.1"));
        log.setUserId(new URI("http://user.1"));
        log.setServiceType("serviceType.1");
        log.setAuditType("auditType.1");
        log.setDescription("description.1");
        log.setOperationalStatus("operationalStatus.1");

        OutputStream output = new OutputStream() {
            private StringBuilder string = new StringBuilder();

            @Override
            public void write(int b) throws IOException {
                this.string.append((char) b);
            }

            public String toString() {
                return this.string.toString();
            }
        };

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                output));
        xm.marshal(log, writer);
        writer.close();

        FileWriter fileWriter = new FileWriter(XmlTestOutputFile);
        fileWriter.write(output.toString());
        fileWriter.close();

        JAXBContext context = null;
        Unmarshaller unmarshaller = null;
        context = JAXBContext.newInstance(AuditLog.class);
        unmarshaller = context.createUnmarshaller();

        File f = new File(XmlTestOutputFile);

        AuditLog auditLog = (AuditLog) unmarshaller.unmarshal(f);
        Assert.assertEquals("productId.1", auditLog.getProductId().toString());
        Assert.assertEquals("http://tenant.1", auditLog.getTenantId().toString());
        Assert.assertEquals("http://user.1", auditLog.getUserId().toString());
        Assert.assertEquals("serviceType.1", auditLog.getServiceType().toString());
        Assert.assertEquals("auditType.1", auditLog.getAuditType().toString());
        Assert.assertEquals("description.1", auditLog.getDescription().toString());
        Assert.assertEquals("operationalStatus.1", auditLog.getOperationalStatus().toString());
        
        deleteIfExists(XmlTestOutputFile);

    }

    @Test
    public void testXmlAuditLogMarshallingForNullLog() throws URISyntaxException, IOException,
            MarshallingExcetion, JAXBException {

        deleteIfExists(XmlTestOutputFile);
        XMLAuditLogMarshaller jm = new XMLAuditLogMarshaller();
        AuditLog log = null;
        
        OutputStream output = new OutputStream() {
            private StringBuilder string = new StringBuilder();

            @Override
            public void write(int b) throws IOException {
                this.string.append((char) b);
            }

            public String toString() {
                return this.string.toString();
            }
        };

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                output));
        jm.header(writer);
        jm.marshal(log, writer);
        jm.tailer(writer);
        writer.close();

        JAXBContext context = null;
        Unmarshaller unmarshaller = null;
        context = JAXBContext.newInstance(AuditLog.class);
        unmarshaller = context.createUnmarshaller();

        File f = new File(XmlTestOutputFile);
        try{
            @SuppressWarnings("unused")
            AuditLog auditLog = (AuditLog) unmarshaller.unmarshal(f);
        }
        catch (Exception e){
            Assert.assertTrue(e.toString().contains("java.io.FileNotFoundException"));
        }
       
        deleteIfExists(XmlTestOutputFile);

    } 
    
    @Test
    public void testXmlAuditLogMarshallingForIOExceptions() throws URISyntaxException, IOException,
            MarshallingExcetion {

        deleteIfExists(XmlTestOutputFile);
        XMLAuditLogMarshaller jm = new XMLAuditLogMarshaller();
        AuditLog log = new AuditLog();
        log.setProductId("productId.2");
        log.setTenantId(new URI("http://tenant.2"));
        log.setUserId(new URI("http://user.2"));
        log.setServiceType("serviceType.2");
        log.setAuditType("auditType.2");
        log.setDescription("description.2");
        log.setOperationalStatus("operationalStatus.2");

        OutputStream output = new OutputStream() {
            private StringBuilder string = new StringBuilder();

            @Override
            public void write(int b) throws IOException {
                this.string.append((char) b);
            }

            public String toString() {
                return this.string.toString();
            }
        };
       
        try{
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    output));
            writer.close();
            jm.header(writer);           
        } catch(MarshallingExcetion e){
            Assert.assertTrue(e.toString().contains("XML head Streaming failed"));
        }

        try{
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    output));
            writer.close();
            jm.marshal(log, writer);           
        } catch(MarshallingExcetion e){
            Assert.assertTrue(e.toString().contains("XML Streaming Error"));
        }
        
        try{
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    output));
            writer.close();
            jm.tailer(writer);           
        } catch(MarshallingExcetion e){
            Assert.assertTrue(e.toString().contains("XML tail Streaming failed"));
        }
        deleteIfExists(XmlTestOutputFile);

    }
    
    
    
    
    private void deleteIfExists(String fname) {
        File f = new File(fname);

        if (f.exists()) {
            f.delete();
        }
    }

}
