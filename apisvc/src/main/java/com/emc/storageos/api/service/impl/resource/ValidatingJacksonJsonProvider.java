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

package com.emc.storageos.api.service.impl.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.xml.bind.*;

import org.codehaus.jackson.jaxrs.JacksonJaxbJsonProvider;

import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Jersey provider that adds validation to the default Jackson Json provider
 */
@Provider
@Consumes({ MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_JSON })
public class ValidatingJacksonJsonProvider implements MessageBodyReader<Object>, 
        MessageBodyWriter<Object> {
    private JacksonJaxbJsonProvider provider = new JacksonJaxbJsonProvider();

    @Override
    public boolean isReadable(Class<?> type, Type genericType,
            Annotation[] annotations, MediaType mediaType) {
        return provider.isReadable(type, genericType, annotations, mediaType);
    }

    @Override
    public Object readFrom(Class<Object> type, Type genericType, 
            Annotation[] annotations, MediaType mediaType, 
            MultivaluedMap<String, String> httpHeaders, InputStream entityStream) 
            throws IOException {
        Object target = provider.readFrom(type, genericType, annotations, mediaType, 
                httpHeaders, entityStream);
        if (target == null) {
            throw APIException.badRequests.parameterIsNullOrEmpty(type.getSimpleName());
        }
 
        InputValidator.getInstance().validate(target);
         
        return target;
    }
     
    @Override
    public boolean isWriteable(Class<?> type, Type genericType,
            Annotation[] annotations, MediaType mediaType) {
        return provider.isWriteable(type, genericType, annotations, mediaType);
    }
     
    @Override
    public long getSize(Object t, Class<?> type, Type genericType,
            Annotation[] annotations, MediaType mediaType) {
        return provider.getSize(t, type, genericType, annotations, mediaType);
    }
     
    @Override
    public void writeTo(Object t, Class<?> type, Type genericType, 
            Annotation[] annotations, MediaType mediaType, 
            MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
            throws IOException {
        provider.writeTo(t, type, genericType, annotations, mediaType, httpHeaders, 
                entityStream);
    }
}
