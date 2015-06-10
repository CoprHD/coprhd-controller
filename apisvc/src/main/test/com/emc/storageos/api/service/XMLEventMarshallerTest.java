/*
 * Copyright 2015 EMC Corporation
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
import com.emc.storageos.api.service.impl.resource.utils.XMLEventMarshaller;
import com.emc.storageos.db.client.model.Event;

public class XMLEventMarshallerTest {
    private static final String XmlTestOutputFile = "XMLEventMarshallerTest.xml";

    @Test
    public void testXmlEventMarshalling() throws URISyntaxException, IOException,
            MarshallingExcetion, JAXBException {

        XMLEventMarshaller xm = new XMLEventMarshaller();

        Event e = new Event();
        e.setEventId("eid1");
        e.setTenantId(new URI("http://tenant.1"));

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
        xm.marshal(e, writer);
        writer.close();

        FileWriter fileWriter = new FileWriter(XmlTestOutputFile);
        fileWriter.write(output.toString());
        fileWriter.close();

        JAXBContext context = null;
        Unmarshaller unmarshaller = null;
        context = JAXBContext.newInstance(Event.class);
        unmarshaller = context.createUnmarshaller();

        File f = new File(XmlTestOutputFile);

        Event event = (Event) unmarshaller.unmarshal(f);
        Assert.assertEquals("eid1", event.getEventId().toString());
        Assert.assertEquals("http://tenant.1", event.getTenantId().toString());
        deleteIfExists(XmlTestOutputFile);

    }

    @Test
    public void testXmlEventMarshallingForNullEvent() throws URISyntaxException, IOException,
            MarshallingExcetion, JAXBException {

        deleteIfExists(XmlTestOutputFile);
        XMLEventMarshaller jm = new XMLEventMarshaller();
        Event evt = null;
        
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
        jm.marshal(evt, writer);
        jm.tailer(writer);
        writer.close();

        JAXBContext context = null;
        Unmarshaller unmarshaller = null;
        context = JAXBContext.newInstance(Event.class);
        unmarshaller = context.createUnmarshaller();

        File f = new File(XmlTestOutputFile);
        try{
            @SuppressWarnings("unused")
            Event event = (Event) unmarshaller.unmarshal(f);
        }
        catch (Exception e){
            Assert.assertTrue(e.toString().contains("java.io.FileNotFoundException"));
        }
       
        deleteIfExists(XmlTestOutputFile);

    }
    
    
    
    @Test
    public void testXmlEventMarshallingForIOExceptions() throws URISyntaxException, IOException,
            MarshallingExcetion {

        deleteIfExists(XmlTestOutputFile);
        XMLEventMarshaller jm = new XMLEventMarshaller();
        Event evt = new Event();
        evt.setEventId("eid1");
        evt.setTenantId(new URI("http://tenant.1"));
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
            jm.marshal(evt, writer);           
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
