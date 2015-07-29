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

import org.junit.Assert;

import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.exc.UnrecognizedPropertyException;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.junit.Test;

import com.emc.storageos.api.service.impl.resource.utils.JSONEventMarshaller;
import com.emc.storageos.api.service.impl.resource.utils.MarshallingExcetion;
import com.emc.storageos.db.client.model.Event;

public class JSONEventMarchallerTest {
    private static final String JsonTestOutputFile = "JSONSEventMarshallerTest.json";

    @Test
    public void testJsonEventMarshalling() throws URISyntaxException, IOException,
            MarshallingExcetion {

        deleteIfExists(JsonTestOutputFile);
        JSONEventMarshaller jm = new JSONEventMarshaller();
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
        jm.marshal(e, writer);
        writer.close();

        FileWriter fileWriter = new FileWriter(JsonTestOutputFile);
        fileWriter.write(output.toString());
        fileWriter.close();

        ObjectMapper mapper = null;
        mapper = new ObjectMapper();
        AnnotationIntrospector introspector = new JaxbAnnotationIntrospector();
        mapper.getDeserializationConfig().withAnnotationIntrospector(
                introspector);

        Event event = mapper.readValue(new File(JsonTestOutputFile),
                Event.class);

        Assert.assertEquals("eid1", event.getEventId().toString());
        Assert.assertEquals("http://tenant.1", event.getTenantId().toString());
        deleteIfExists(JsonTestOutputFile);

    }

    @Test
    public void testJsonEventMarshallingForNullEvent() throws URISyntaxException, IOException,
            MarshallingExcetion {

        deleteIfExists(JsonTestOutputFile);
        JSONEventMarshaller jm = new JSONEventMarshaller();
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

        FileWriter fileWriter = new FileWriter(JsonTestOutputFile);
        fileWriter.write(output.toString());
        fileWriter.close();

        ObjectMapper mapper = null;
        mapper = new ObjectMapper();
        AnnotationIntrospector introspector = new JaxbAnnotationIntrospector();
        mapper.getDeserializationConfig().withAnnotationIntrospector(
                introspector);

        try {
            @SuppressWarnings("unused")
            Event event = mapper.readValue(new File(JsonTestOutputFile),
                    Event.class);
        } catch (UnrecognizedPropertyException e) {
            Assert.assertTrue(e.toString().contains("Unrecognized"));
        }

        deleteIfExists(JsonTestOutputFile);

    }

    @Test
    public void testJsonEventMarshallingForIOExceptions() throws URISyntaxException, IOException,
            MarshallingExcetion {

        deleteIfExists(JsonTestOutputFile);
        JSONEventMarshaller jm = new JSONEventMarshaller();
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

        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    output));
            writer.close();
            jm.header(writer);
        } catch (MarshallingExcetion e) {
            Assert.assertTrue(e.toString().contains("JSON head Streaming failed"));
        }

        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    output));
            writer.close();
            jm.marshal(evt, writer);
        } catch (MarshallingExcetion e) {
            Assert.assertTrue(e.toString().contains("JSON streaming failed"));
        }

        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    output));
            writer.close();
            jm.tailer(writer);
        } catch (MarshallingExcetion e) {
            Assert.assertTrue(e.toString().contains("JSON tail Streaming failed"));
        }
        deleteIfExists(JsonTestOutputFile);

    }

    private void deleteIfExists(String fname) {
        File f = new File(fname);

        if (f.exists()) {
            f.delete();
        }
    }
}
