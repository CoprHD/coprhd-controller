/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.resource;

import com.emc.storageos.services.ServicesMetadata;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.logsvc.LogSvcConstants;
import com.emc.storageos.systemservices.impl.logsvc.LogSvcPropertiesLoader;
import com.emc.vipr.model.sys.logging.LogSeverity;

import java.io.StringReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Base class for all resources
 */
public abstract class BaseLogSvcResource {
    // Constant defines the date/time format for a request parameter.
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd_HH:mm:ss";

    // non-service logs
    private final static List<String> nonServiceLogFileNames = new ArrayList<String>() {{
        add("systemevents");
        add("messages");
        add("nginx_access");
        add("nginx_error");
        add("bkutils");
    }};
        
    // used when no media type is specified
    public final static MediaType DEFAULT_MEDIA_TYPE = MediaType.APPLICATION_XML_TYPE;
    
    // used as xml tag or json attribute name for error message 
    public final static String ERROR_MESSAGE_TAG = "error_message";
    
    // A reference to the log service configurable properties loader.
    @Autowired
    protected LogSvcPropertiesLoader _logSvcPropertiesLoader;

    @Context
    HttpHeaders header;

    protected MediaType getMediaType() {
        MediaType mediaType = DEFAULT_MEDIA_TYPE;
        if(header != null) {
            List<MediaType> mTypes = header.getAcceptableMediaTypes();
            if (mTypes != null) {
                for (MediaType media : mTypes) {
                    if (LogSvcConstants.ACCEPTED_MEDIA_TYPES.contains(media)) {
                        mediaType = media;
                        break;
                    }
                }
            }
        }
        return mediaType;
    }

    /**
     * Converts the passed timestamp to a Date reference, thereby validating the
     * request timestamp is a valid formatted date/time string.
     *
     * @param timestampStr The request timestamp as a string.
     * @return The passed timestamp as a Date reference. A null is returned if
     *         the passed timestamp string is null or blank.
     */
    protected Date getDateTimestamp(String timestampStr) {
        Date timestamp = null;

        // This is OK. Just means no timestamp was passed in the request.
        if ((timestampStr == null) || (timestampStr.length() == 0)) {
            return timestamp;
        }

        try {
            // Ensure the passed timestamp can be parsed.
            SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_TIME_FORMAT);
            timestamp = dateFormat.parse(timestampStr);
        } catch (ParseException pe) {
            timestamp = getDateFromLong(timestampStr);
        }
        return timestamp;
    }

    private Date getDateFromLong(String timestampStr) {
        Date timestamp = null;

        try {
            long timeInMs = Long.parseLong(timestampStr);
            timestamp = new Date(timeInMs);
        } catch (NumberFormatException n) {
            throw APIException.badRequests.invalidDate(timestampStr);
        }
        return timestamp;
    }

    /**
     * Validates that the specified end time comes after the specified start
     * time. Note that it is OK for the start/end times to be null. It just
     * means they were not specified in the request.
     *
     * @param startTime The requested start time or null.
     * @param endTime   The requested end time or null.
     * @throws APIException When the passed end time comes before the
     *                                  passed start time.
     */
    protected void validateTimestamps(Date startTime, Date endTime) {
        if ((startTime != null) && (endTime != null)) {
            if (endTime.before(startTime)) {
                throw APIException.badRequests.endTimeBeforeStartTime(startTime.toString(), endTime.toString());
            }
        }
    }

    /**
     * Verifies a valid severity level is passed in the request and returns the
     * appropriate LogSeverity enumeration.
     *
     * @param severity The severity passed in the request.
     * @return The corresponding LogSeverity.
     * @throws APIException for an invalid severity.
     */
    protected LogSeverity validateLogSeverity(int severity) {
        if ((severity >= 0) && (severity < LogSeverity.values().length)) {
            return LogSeverity.values()[severity];
        } else {
            throw APIException.badRequests.parameterIsNotValid("severity");
        }
    }

    protected void validateMsgRegex(String msgRegex) {
        // Validate regular message
        if (msgRegex != null && !msgRegex.equals("")) {
            try {
                Pattern.compile(msgRegex);
            } catch (PatternSyntaxException e) {
                throw APIException.badRequests.parameterIsNotValid("regex", e);
            }
        }
    }

    /**
     * Returns list of actual log names.
     * If there are any alias names present in the inputted list they will be replaced
     * with their actual names.
     */
    protected List<String> getLogNamesFromAlias(List<String> logNames) {
        if (logNames == null || logNames.size() == 0) {
            return logNames;
        }

        List<String> validLogNames = new ArrayList<String>();
        for (String name : logNames) {
            if (LogSvcConstants.logAliasNames.containsKey(name)) {
                validLogNames.add(LogSvcConstants.logAliasNames.get(name));
            } else {
                validLogNames.add(name);
            }
        }
        return validLogNames;
    }
         
     /**
      * Get set of log names supported by vipr
      */
     protected Set<String> getValidLogNames() {
         Set<String> logNames = new HashSet<String>();        
         logNames.addAll(ServicesMetadata.getControlNodeServiceNames());
         logNames.addAll(ServicesMetadata.getExtraNodeServiceNames());
         logNames.addAll(nonServiceLogFileNames);
         return logNames;
     }     
}
