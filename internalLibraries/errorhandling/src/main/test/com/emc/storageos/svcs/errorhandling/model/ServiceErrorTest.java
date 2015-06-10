/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.svcs.errorhandling.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.StringWriter;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCodeException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceErrorFactory;

public class ServiceErrorTest {

    @Test
    public void testXML_unauthorized() throws JAXBException {
        final ServiceCode code = ServiceCode.SECURITY_UNAUTHORIZED_OPERATION;
        final String message = "No credentials were specified";
        final String xml = xml(error(code, message, null));

        // useful for debugging
        // System.out.println(xml);

        assertXmlContains(xml, "code", "4000");
        assertXmlContains(xml, "retryable", "false");
        assertXmlContains(xml, "details", message);
    }

    @Test
    public void testJSON_unauthorized() throws Exception {
        final ServiceCode code = ServiceCode.SECURITY_UNAUTHORIZED_OPERATION;
        final String message = "No credentials were specified";
        final String json = json(error(code, message, null));

        // useful for debugging
        // System.out.println(json);

        assertJSONContains(json, "code", "4000");
        assertJSONContains(json, "retryable", "false");
        assertJSONContains(json, "details", message);
    }

    private void assertJSONContains(final String json, final String element, final String expected)
            throws JSONException {
        final JSONObject jsonParser = new JSONObject(json);

        final String actual = jsonParser.getString(element);

        assertEquals("Unexpected JSON value for element " + element, expected, actual);
    }

    private void assertXmlContains(final String xml, final String element, final String expected) {
        // this is really bad and ought to be done in a more structured way some
        // time

        final String open = "<" + element + ">";
        final String close = "</" + element + ">";

        int openIndex = xml.indexOf(open);
        int closeIndex = xml.indexOf(close, openIndex);

        if (openIndex < 0 || closeIndex < 0) {
            if (expected != null) {
                fail("Element not found: " + element);
            }
        } else {
            final String actual = xml.substring(openIndex + open.length(), closeIndex);
            if (expected == null) {
                fail(open + ": " + actual);
            } else {
                assertEquals(open, expected, actual);
            }
        }
    }

    private ServiceErrorRestRep error(final ServiceCode code, final String pattern,
            final Object[] parameters) {
        return error(new ServiceCodeException(code, pattern, parameters));
    }

    @SuppressWarnings("deprecation")
    private ServiceErrorRestRep error(final ServiceCodeException e) {
        return ServiceErrorFactory.toServiceErrorRestRep(e);
    }

    /**
     * Marshals the given ServiceError instance into XML
     *
     * @param error
     * @return
     * @throws JAXBException
     * @throws PropertyException
     */
    private String xml(final ServiceErrorRestRep error) throws JAXBException, PropertyException {
        final JAXBContext context = JAXBContext.newInstance(ServiceErrorRestRep.class);
        final StringWriter out = new StringWriter();

        final Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

        marshaller.marshal(error, out);

        String xml = out.toString();
        return xml;
    }

    /**
     * Transform the given ServiceError instance into JSON
     *
     * @param error
     * @return
     * @throws IOException
     * @throws JsonMappingException
     * @throws JsonGenerationException
     */
    private String json(final ServiceErrorRestRep error) throws JsonGenerationException,
            JsonMappingException, IOException {
        final ObjectMapper mapper = new ObjectMapper();
        final AnnotationIntrospector introspector = new JaxbAnnotationIntrospector();
        mapper.getSerializationConfig().withAnnotationIntrospector(introspector);
        mapper.getDeserializationConfig().withAnnotationIntrospector(introspector);

        return mapper.writeValueAsString(error);
    }
}
