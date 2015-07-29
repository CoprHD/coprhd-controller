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

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.*;

import com.emc.storageos.svcs.errorhandling.resources.APIException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * JAXB context provider with validation bits
 */
@Provider
public class ContextProvider implements ContextResolver<JAXBContext> {
    private ConcurrentMap<Class, ValidatingContext> _contextMap =
            new ConcurrentHashMap<Class, ValidatingContext>();

    /**
     * Wraps default JAXB provider with a couple of validation bits
     */
    @SuppressWarnings("deprecation")
    public static class ValidatingContext extends JAXBContext {
        private JAXBContext _context;

        /**
         * Throws error on any unexpected element, etc.
         */
        private static final ValidationEventHandler _eventHandler = new ValidationEventHandler() {
            @Override
            public boolean handleEvent(ValidationEvent event) {
                throw APIException.badRequests.invalidInput(event.getLocator().getLineNumber(), event.getLocator().getColumnNumber());
            }
        };

        /**
         * Throws error on any missing parameters
         */
        private static final Unmarshaller.Listener _unmarshallListener = new Unmarshaller.Listener() {
            @Override
            public void afterUnmarshal(Object target, Object parent) {
                InputValidator.getInstance().validate(target);
            }
        };

        public ValidatingContext(JAXBContext context) {
            _context = context;
        }

        @Override
        public Unmarshaller createUnmarshaller() throws JAXBException {
            Unmarshaller unmarshaller = _context.createUnmarshaller();
            unmarshaller.setEventHandler(_eventHandler);
            unmarshaller.setListener(_unmarshallListener);
            return unmarshaller;
        }

        @Override
        public Marshaller createMarshaller() throws JAXBException {
            return _context.createMarshaller();
        }

        @Override
        public Validator createValidator() throws JAXBException {
            return _context.createValidator();
        }
    }

    @Override
    public JAXBContext getContext(Class<?> clazz) {
        try {
            ValidatingContext ctx = _contextMap.get(clazz);
            if (ctx != null) {
                return ctx;
            }
            JAXBContext context = JAXBContext.newInstance(clazz);
            ctx = new ValidatingContext(context);
            _contextMap.putIfAbsent(clazz, ctx);
            return ctx;
        } catch (JAXBException e) {
            throw APIException.internalServerErrors.jaxbContextError(e.getMessage(), e);
        }
    }
}
