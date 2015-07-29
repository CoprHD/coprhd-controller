/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import org.junit.Assert;
import org.junit.Test;
import com.emc.storageos.api.service.impl.resource.utils.MarshallingExcetion;
import com.emc.storageos.api.service.impl.resource.utils.XMLEventMarshaller;
import com.emc.storageos.api.service.impl.resource.utils.XMLStatMarshaller;
import com.emc.storageos.db.client.model.Event;
import com.emc.storageos.db.client.model.Stat;

public class XMLStatMarshallerTest {

    private static final String XmlTestOutputFile = "XMLStatMarshallerTest.xml";

    @Test
    public void testXmlStatMarshalling() throws MarshallingExcetion, IOException,
            JAXBException {
        deleteIfExists(XmlTestOutputFile);
        XMLStatMarshaller xmlMarshaller = new XMLStatMarshaller();
        Stat st = new Stat();
        String svcType = "block";
        st.setServiceType(svcType);
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

        PrintWriter writer = new PrintWriter(output);
        xmlMarshaller.marshall(st, writer);
        writer.close();

        FileWriter fileWriter = new FileWriter(XmlTestOutputFile);
        fileWriter.write(output.toString());
        fileWriter.close();

        JAXBContext context = null;
        Unmarshaller unmarshaller = null;
        context = JAXBContext.newInstance(Stat.class);
        unmarshaller = context.createUnmarshaller();

        File f = new File(XmlTestOutputFile);

        Stat stat = (Stat) unmarshaller.unmarshal(f);
        Assert.assertEquals("block", stat.getServiceType().toString());
        deleteIfExists(XmlTestOutputFile);

    }

    @Test
    public void testXmlStatMarshallingForNullEvent() throws URISyntaxException, IOException,
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
        try {
            @SuppressWarnings("unused")
            Event event = (Event) unmarshaller.unmarshal(f);
        } catch (Exception e) {
            Assert.assertTrue(e.toString().contains("java.io.FileNotFoundException"));
        }

        deleteIfExists(XmlTestOutputFile);

    }

    @Test
    public void testXmlStatMarshallingForError() throws URISyntaxException, IOException,
            MarshallingExcetion {

        deleteIfExists(XmlTestOutputFile);
        XMLStatMarshaller xm = new XMLStatMarshaller();
        Stat stat = new Stat();

        stat.setTenant(new URI("http://tenant.1"));
        stat.setTenant(new URI("http://project.1"));

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

        PrintWriter writer = new PrintWriter(output);
        String error = "someerror";
        xm.error(writer, error);
        writer.close();
        Assert.assertTrue(output.toString().contains("<stats>" + "someerror" + "</stats>"));
        deleteIfExists(XmlTestOutputFile);

    }

    private void deleteIfExists(String fname) {
        File f = new File(fname);

        if (f.exists()) {
            f.delete();
        }
    }

}
