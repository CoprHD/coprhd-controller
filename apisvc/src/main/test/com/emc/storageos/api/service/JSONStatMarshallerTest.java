/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.Assert;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.exc.UnrecognizedPropertyException;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.junit.Test;
import com.emc.storageos.api.service.impl.resource.utils.JSONStatMarshaller;
import com.emc.storageos.api.service.impl.resource.utils.MarshallingExcetion;
import com.emc.storageos.db.client.model.Stat;

public class JSONStatMarshallerTest {

    private static final String JsonTestOutputFile = "JSONStatMarshallerTest.json";

    @Test
    public void testJsonStatMarshalling() throws JsonParseException,
            JsonMappingException, IOException {
        deleteIfExists(JsonTestOutputFile);
        JSONStatMarshaller jMarshaller = new JSONStatMarshaller();
        Stat st = new Stat();
        long smbSize = 10000000;
        st.setSmdSize(smbSize);
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
        jMarshaller.marshall(st, writer);
        writer.close();

        FileWriter fileWriter = new FileWriter(JsonTestOutputFile);
        fileWriter.write(output.toString());
        fileWriter.close();

        ObjectMapper mapper = null;
        mapper = new ObjectMapper();
        AnnotationIntrospector introspector = new JaxbAnnotationIntrospector();
        mapper.getDeserializationConfig().withAnnotationIntrospector(
                introspector);

        Stat stat = mapper.readValue(new File(JsonTestOutputFile), Stat.class);

        Assert.assertEquals("10000000", stat.getSmdSize().toString());
        deleteIfExists(JsonTestOutputFile);

    }

    
    @Test
    public void testJsonStatMarshallingForNullEvent() throws URISyntaxException, IOException,
            MarshallingExcetion {

        deleteIfExists(JsonTestOutputFile);
        JSONStatMarshaller jm = new JSONStatMarshaller();
        Stat st = null;
        
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
                
        jm.header(writer);
        jm.marshall(st, writer);
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
        
        try{
            @SuppressWarnings("unused")
            Stat stat = mapper.readValue(new File(JsonTestOutputFile),
                    Stat.class);
        }
        catch(UnrecognizedPropertyException e){
            Assert.assertTrue(e.toString().contains("Unrecognized"));
        }

      
        deleteIfExists(JsonTestOutputFile);

    }
    
    
    
    @Test
    public void testJsonStatMarshallingForError() throws URISyntaxException, IOException,
            MarshallingExcetion {

        deleteIfExists(JsonTestOutputFile);
        JSONStatMarshaller jm = new JSONStatMarshaller();
        Stat st = new Stat();
        
        st.setTenant(new URI("http://tenant.1"));
        st.setProject(new URI("http://project.1"));
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
        jm.error(writer, error); 
        writer.close();
        Assert.assertTrue(output.toString().contains("{ \"error\": [" + "someerror" + "] }"));
        deleteIfExists(JsonTestOutputFile);
    }
    
    
    private void deleteIfExists(String fname) {
        File f = new File(fname);

        if (f.exists()) {
            f.delete();
        }
    }
}
