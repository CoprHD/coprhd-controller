/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.svcs.errorhandling.resources;

import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.fromHTTPStatus;

import java.io.StringWriter;
import java.util.Locale;
import java.util.Arrays;

import javax.ws.rs.WebApplicationException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class ServiceErrorFactory {
    public static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    private static JAXBContext context;
    private static final int STATUS_NOT_FOUND = 404;
    private static final String ABSTRACT_AUTHENTICATION_FILTER = "AbstractAuthenticationFilter";

    @Deprecated
    public static ServiceErrorRestRep toServiceErrorRestRep(final WebApplicationException e) {
        return toServiceErrorRestRep(e, DEFAULT_LOCALE);
    }

    public static ServiceErrorRestRep toServiceErrorRestRep(final WebApplicationException e,
            final Locale locale) {
        // COP-28153 : We do not want the details to get printed in the XML when the 
        // HTTP 404 error is hit. We check for AbstractAuthenticationFilter
        // in the stacktrace to judge that the control went to authsvc as a safety check.
        if ((Arrays.toString(e.getStackTrace()).contains(ABSTRACT_AUTHENTICATION_FILTER))
             && (e.getResponse().getStatus() == STATUS_NOT_FOUND)) {
            return toServiceErrorRestRep(fromHTTPStatus(e.getResponse().getStatus()), null,
                locale);
        } else {
            return toServiceErrorRestRep(fromHTTPStatus(e.getResponse().getStatus()), e.getMessage(),
                locale);
        }
    }

    @Deprecated
    public static ServiceErrorRestRep toServiceErrorRestRep(final ServiceCode code, final String details) {
        return toServiceErrorRestRep(code, details, DEFAULT_LOCALE);
    }

    @Deprecated
    public static ServiceErrorRestRep toServiceErrorRestRep(final ServiceCode code, final String details,
            final Locale locale) {
        final ServiceErrorRestRep error = new ServiceErrorRestRep();
        error.setCode(code.getCode());
        error.setRetryable(code.isRetryable());
        error.setCodeDescription(code.getSummary(locale));
        error.setDetailedMessage(details);
        return error;
    }

    public static ServiceErrorRestRep toServiceErrorRestRep(final ServiceCoded coded) {
        return toServiceErrorRestRep(coded, DEFAULT_LOCALE);
    }

    public static ServiceErrorRestRep toServiceErrorRestRep(final ServiceCoded coded, final Locale locale) {
        final ServiceErrorRestRep error = new ServiceErrorRestRep();
        error.setCode(coded.getServiceCode().getCode());
        error.setRetryable(coded.isRetryable());
        error.setCodeDescription(coded.getServiceCode().getSummary(locale));
        error.setDetailedMessage(coded.getMessage(locale));
        return error;
    }

    public static String toXml(final ServiceErrorRestRep error) {
        try {
            initContext();
            // Create a stringWriter to hold the XML
            final StringWriter stringWriter = new StringWriter();
            final Marshaller jaxbMarshaller = context.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            jaxbMarshaller.marshal(error, stringWriter);
            return stringWriter.toString();
        } catch (final JAXBException e) {
            return error.toString();
        }
    }

    private static synchronized void initContext() throws JAXBException {
        if (context == null) {
            context = JAXBContext.newInstance(ServiceErrorRestRep.class);
        }
    }
}
