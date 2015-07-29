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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.*;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.EventRetriever;
import com.emc.storageos.api.service.impl.resource.utils.MarshallingExcetion;
import com.emc.storageos.db.client.TimeSeriesMetadata;
import com.emc.storageos.db.client.TimeSeriesMetadata.TimeBucket;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Monitoring event resource implementation
 */
@Path("/monitoring")
public class MonitoringService extends ResourceService {

    final private Logger _logger = LoggerFactory.getLogger(MonitoringService.class);

    private EventRetriever _eventRetriever;

    /**
     * formats to be used to parse supported time bucket strings
     */
    public static final String HOUR_BUCKET_TIME_FORMAT = "yyyy-MM-dd'T'HH";
    public static final String MINUTE_BUCKET_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm";

    public static final String BAD_TIMEBUCKET_MSG =
            "Error: time_bucket parameter format supplied is not valid.\n" +
                    "Acceptable formats: yyyy-MM-dd'T'HH , yyyy-MM-dd'T'HH:mm";

    /**
     * getter
     */
    public EventRetriever getEventRetriever() {
        return _eventRetriever;
    }

    /**
     * setter
     */
    public void setEventRetriever(EventRetriever eventRetriever) {
        _eventRetriever = eventRetriever;
    }

    /**
     * Retrieves the bulk events and alerts in a specified time bucket (minute or hour).
     * 
     * @param time_bucket required Time bucket for retrieval of events. Acceptable formats are: yyyy-MM-dd'T'HH for hour bucket,
     *            yyyy-MM-dd'T'HH:mm for minute bucket
     * @brief List events and alerts for time period
     * @return Output stream of events or an error status.
     */
    @GET
    @Path("/events")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN })
    public Response getEvents(
            @QueryParam("time_bucket") final String timeBucketParam,
            @Context HttpHeaders header) {

        _logger.debug("getEvents: timebucket: {}", timeBucketParam);

        MediaType mType = MediaType.APPLICATION_XML_TYPE;
        List<MediaType> mTypes = header.getAcceptableMediaTypes();
        if (mTypes != null) {
            for (MediaType media : mTypes) {
                if (media.equals(MediaType.APPLICATION_JSON_TYPE)
                        || media.equals(MediaType.APPLICATION_XML_TYPE)) {
                    mType = media;
                    break;
                }
            }
        }

        // try two time formats which are supported
        DateTimeFormatter hourBucketFormat = DateTimeFormat.forPattern(HOUR_BUCKET_TIME_FORMAT)
                .withZoneUTC();
        DateTimeFormatter minuteBucketFormat = DateTimeFormat.forPattern(MINUTE_BUCKET_TIME_FORMAT)
                .withZoneUTC();

        DateTime timeBucket = null;
        TimeSeriesMetadata.TimeBucket timeBucketGran = TimeSeriesMetadata.TimeBucket.HOUR;

        try {
            // we reduce the length by 2 here to account for single quote in yyyy-MM-dd'T'HH format
            if ((null != timeBucketParam) && (timeBucketParam.length() == HOUR_BUCKET_TIME_FORMAT.length() - 2)) {
                timeBucket = hourBucketFormat.parseDateTime(timeBucketParam);
                timeBucketGran = TimeSeriesMetadata.TimeBucket.HOUR;
            } else if ((null != timeBucketParam) && (timeBucketParam.length() == MINUTE_BUCKET_TIME_FORMAT.length() - 2)) {
                timeBucket = minuteBucketFormat.parseDateTime(timeBucketParam);
                timeBucketGran = TimeSeriesMetadata.TimeBucket.MINUTE;
            } else {
                throw APIException.badRequests.invalidTimeBucket(timeBucketParam);
            }
        } catch (final IllegalArgumentException e) {
            throw APIException.badRequests.invalidTimeBucket(timeBucketParam, e);
        }

        if (timeBucket == null) {
            throw APIException.badRequests.invalidTimeBucket(timeBucketParam);
        }

        return Response.ok(
                getStreamOutput(timeBucket, timeBucketGran, mType),
                mType).build();
    }

    /**
     * Return an output stream object as http response entity so that the client could stream potentially large response.
     * 
     * @param timeBucket
     *            - the time bucket to retrieve events.
     * @param timeBucketGran
     *            - granularity of the time bucket, can be hour or minute.
     * @param mediaType
     *            - media type of the response.
     * @return
     *         - the stream object from which client retrieves response message body.
     */
    private StreamingOutput getStreamOutput(final DateTime timeBucket,
            final TimeBucket timeBucketGran, final MediaType mediaType) {

        return new StreamingOutput() {
            @Override
            public void write(OutputStream outputStream) {

                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream));
                try {
                    if (_eventRetriever == null) {
                        throw APIException.internalServerErrors.noEventRetriever();
                    }
                    _eventRetriever.getBulkEvents(timeBucket, timeBucketGran, mediaType, out);
                } catch (MarshallingExcetion e) {
                    _logger.error("retrieving event error", e);
                } catch (final Exception e) {
                    throw APIException.internalServerErrors.eventRetrieverError(e.getMessage(), e);
                } finally {
                    try {
                        out.close();
                    } catch (IOException e) {
                        _logger.error("stream close error", e);
                    }
                }
            }
        };
    }
}
