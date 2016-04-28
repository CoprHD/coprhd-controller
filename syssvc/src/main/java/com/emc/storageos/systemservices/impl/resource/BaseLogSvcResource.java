/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.services.ServicesMetadata;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.logsvc.LogSvcConstants;
import com.emc.storageos.systemservices.impl.logsvc.LogSvcPropertiesLoader;
import com.emc.vipr.model.sys.logging.LogSeverity;

/**
 * Base class for all resources
 */
public abstract class BaseLogSvcResource {
    // Constant defines the date/time format for a request parameter.
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd_HH:mm:ss";

    // non-service logs
    private final static List<String> nonServiceLogFileNames = new ArrayList<String>() {
        {
            add("systemevents");
            add("messages");
            add("nginx_access");
            add("nginx_error");
            add("bkutils");
        }
    };

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
        if (header != null) {
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
     * Validates that the specified end time comes after the specified start
     * time. Note that it is OK for the start/end times to be null. It just
     * means they were not specified in the request.
     *
     * @param startTime The requested start time or null.
     * @param endTime The requested end time or null.
     * @throws APIException When the passed end time comes before the
     *             passed start time.
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
        if (logNames == null || logNames.isEmpty()) {
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
