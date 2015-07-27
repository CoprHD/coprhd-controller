/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.svcs.errorhandling.mappers;

import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.API_BAD_REQUEST;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.API_PARAMETER_INVALID;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.UNFORSEEN_ERROR;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceErrorFactory.DEFAULT_LOCALE;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceErrorFactory.toServiceErrorRestRep;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.StatusType;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.StatusCoded;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.core.provider.jaxb.AbstractRootElementProvider;

/**
 * Class ControllerExceptionMapper will map
 * 
 */
@Provider
public class ServiceCodeExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger _log = LoggerFactory.getLogger(ServiceCodeExceptionMapper.class);

    @Context
    protected UriInfo info;

    @Context
    protected HttpHeaders headers;

    @Override
    public Response toResponse(final Exception t) {

        final Throwable e = getException(t);
        final Locale preferedLocale = getPreferedLocale(headers);
        final ServiceErrorRestRep serviceError = toServiceError(e, preferedLocale);
        final StatusType status = getHTTPStatus(e, info);

        // Just get the ServiceCode from the ServiceError
        final int code = serviceError.getCode();

        // CQs 603808, 603811
        // Check for those WebApplicationExceptions which result from the XML parser
        // correctly processing its input.  Stack traces are not necessary and should
        // be filtered out in order not to raise undue concerns
        String prefix = "Responding to internal " + code + " with HTTP " + status;
        if (isStackTracePrinted(e)) {
            _log.warn(prefix + "; Caused by", e);
        } else {
            _log.warn(prefix + "; Caused by " + e.getMessage());
        }

        final ResponseBuilder builder = Response.status(status);
        if (status.getStatusCode() == SERVICE_UNAVAILABLE.getStatusCode()) {
            // recommend how many seconds to wait before retrying
            builder.header("Retry-After", "30");
        }

        builder.type(getPreferredMediaType(headers));
        builder.entity(serviceError);
        return builder.build();
    }

    /**
     * Test if the stack trace should be suppressed for the exception. Currently,
     * exceptions thrown by Jersey from class AbstractRootElementProvider are 
     * selected for suppression.
     *
     * @param e the exception to be tested
     * @returns true if trace if to be printed, false otherwise.
     */
    public static boolean isStackTracePrinted(Throwable e) {
        if (e instanceof WebApplicationException) {
            Class<?> cl2check = AbstractRootElementProvider.class;
            StackTraceElement[] ste = e.getStackTrace();
            
            for (int i = 0; i < ste.length; i++) {
                // use classes so process is not brittle
                if (ste[i].getClassName().equals(cl2check.getName())) {
                    return false;
                }
            }
        }
        
        return true;
    }

    /**
     * Get the HTTP Status for the specified Exception
     * 
     * @param e
     * @param info
     * @return
     */
    public static StatusType getHTTPStatus(final Throwable e, final UriInfo info) {
        if (e instanceof ServiceCoded) {
            // e is ServiceCoded so defer to next method
            return getHTTPStatus((ServiceCoded) e);
        } else if (e instanceof WebApplicationException) {
            // use the status from the WebApplicationException
            return getHTTPStatus((WebApplicationException) e);
        } else if (e instanceof IllegalArgumentException) {
            return API_PARAMETER_INVALID.getHTTPStatus();
        } else if (e instanceof JsonMappingException
                || e instanceof JsonParseException) {
            return API_BAD_REQUEST.getHTTPStatus();
        }

        // Others will be ServiceCodeExceptions
        return UNFORSEEN_ERROR.getHTTPStatus();
    }

    public static StatusType getHTTPStatus(final WebApplicationException e) {
        return ClientResponse.Status.fromStatusCode(e.getResponse().getStatus());
    }

    public static StatusType getHTTPStatus(final ServiceCoded e) {
        if (e instanceof StatusCoded) {
            // If e is StatusCoded then just get the status from it
            return ((StatusCoded) e).getStatus();
        }
        // e is ServiceCoded so use the retryable field for the status
        return e.isRetryable() ? SERVICE_UNAVAILABLE : INTERNAL_SERVER_ERROR;
    }

    /**
     * Convert the Exception to a ServiceError
     * 
     * @param e
     * @return
     */
    public static ServiceErrorRestRep toServiceError(final Throwable e, final Locale locale) {
        if (e instanceof ServiceCoded) {
            // e should now be ServiceCoded
            return toServiceErrorRestRep((ServiceCoded) e, locale);
        } else if (e instanceof WebApplicationException) {
            // convert WebApplicationExceptions to ServiceErrors
            return toServiceErrorRestRep((WebApplicationException) e, locale);
        } else if (e instanceof IllegalArgumentException) {
            return toServiceErrorRestRep(API_PARAMETER_INVALID, e.getMessage(), locale);
        } else if (e instanceof JsonMappingException
                || e instanceof JsonParseException) {
            return toServiceErrorRestRep(API_BAD_REQUEST, e.getMessage(), locale);
        }

        // Others will be ServiceCodeExceptions
        return toServiceErrorRestRep(UNFORSEEN_ERROR, e.getMessage(), locale);
    }

    /**
     * If the Exception wraps a {@link ServiceCoded} then return the cause
     * otherwise return the exception
     * 
     * @param e
     * @return
     */
    private Throwable getException(final Exception e) {
        if (e.getCause() instanceof ServiceCoded) {
            // Catch some runtime exceptions that wrap ServiceCoded Exceptions
            _log.info("Unwrapping the Exception from:", e);
            return e.getCause();
        }
        // return all others
        return e;
    }

    public static Locale getPreferedLocale(final HttpHeaders headers) {
        try {
            List<Locale> locales = headers.getAcceptableLanguages();
            if (headers.getAcceptableLanguages() != null && !locales.isEmpty()) {
                return locales.get(0);
            }
        } catch (Exception e) {
            _log.debug("Unable to determine prefered locale. " + e.getMessage());
        }

        return DEFAULT_LOCALE;
    }

    /**
     * Iterate over the acceptable media type from the request and returns
     * XML or JSON type on first occurrence. If not found, return XML type.
     *
     * @param headers request headers
     * @return preferred media type, XML by default.
     */
    private MediaType getPreferredMediaType(final HttpHeaders headers) {
        try {
            List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
            if (mediaTypes != null && !mediaTypes.isEmpty()) {
                for (MediaType mediaType : mediaTypes) {
                    // ServiceErrorRestRep can only be serialized in JSON or XML (by default)
                    if (mediaType.equals(MediaType.APPLICATION_JSON_TYPE) ||
                            mediaType.equals(MediaType.APPLICATION_XML_TYPE))
                        return mediaType;
                }
            }
        } catch (Exception e) {
            _log.debug("Unable to determine prefered media types. ", e);
        }

        return MediaType.APPLICATION_XML_TYPE;
    }

    public static void main(final String[] args) throws SecurityException, NoSuchFieldException {
        for (final ServiceCode code : ServiceCode.values()) {
            System.out.println("Service Code: " + code.getCode());
            System.out.println("Name:         " + code.name());
            System.out.println("Description:  " + code.getSummary(Locale.ENGLISH));
            System.out.println("Retryable:    " + code.isRetryable());
            System.out.println("Fatal:        " + code.isFatal());

            final Field field = ServiceCode.class.getField(code.name());
            final boolean deprecated = field.isAnnotationPresent(Deprecated.class);
            System.out.println("Deprecated:   " + deprecated);

            final StatusType status = code.getHTTPStatus();
            System.out.println("HTTP Status:  " + status.getStatusCode() + " "
                    + status.getReasonPhrase());
            System.out.println();
        }
    }
}
