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
package com.emc.storageos.api.service.impl.resource;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
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

import com.emc.storageos.api.service.impl.resource.utils.StatRetriever;
import com.emc.storageos.db.client.TimeSeriesMetadata;
import com.emc.storageos.db.client.TimeSeriesMetadata.TimeBucket;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

@Path("/metering")
public class MeteringService extends ResourceService {

    private StatRetriever _statRetriever;

    final private Logger _logger = LoggerFactory.getLogger(MeteringService.class);
    /**
     * formats to be used to parse supported time bucket strings
     */

    public static final String HOUR_BUCKET_TIME_FORMAT = "yyyy-MM-dd'T'HH";
    public static final String MINUTE_BUCKET_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm";


    public static final String BAD_TIMEBUCKET_MSG = "Error: time_bucket parameter format supplied is not valid.\n"
            + "Acceptable formats: yyyy-MM-dd'T'HH , yyyy-MM-dd'T'HH:mm";

    public StatRetriever getStatRetriever() {
        return _statRetriever;
    }

    public void setStatRetriever(StatRetriever _statRetriever) {
        this._statRetriever = _statRetriever;
    }

    /**     
     * Retrieves the bulk metering stats for all resources in a specified time bucket (minute or hour).
     *
     * @param time_bucket required Time bucket for retrieval of stats. Acceptable formats are: yyyy-MM-dd'T'HH for hour bucket, yyyy-MM-dd'T'HH:mm for minute bucket
     * @brief List metering statistics for time period
     * @return - Output stream of stats or an error status.
     */
    @GET
    @Path("/stats")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = {Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN})
    public Response getStats(
            @QueryParam("time_bucket") final String timeBucketParam,
            @Context HttpHeaders header) {

        _logger.debug("getStats: timebucket: {}", timeBucketParam);
        MediaType mediaType = MediaType.APPLICATION_XML_TYPE;
        List<MediaType> mTypes = header.getAcceptableMediaTypes();
        if (mTypes != null) {
            for (MediaType media : mTypes) {
                if (media.equals(MediaType.APPLICATION_JSON_TYPE)
                        || media.equals(MediaType.APPLICATION_XML_TYPE)) {
                    mediaType = media;
                    break;
                }
            }
        }

        DateTimeFormatter dfHourFormat = DateTimeFormat.forPattern(
                HOUR_BUCKET_TIME_FORMAT).withZoneUTC();
        DateTimeFormatter dfMinuteFormat = DateTimeFormat.forPattern(
                MINUTE_BUCKET_TIME_FORMAT).withZoneUTC();
        DateTime timeBucket = null;
        TimeBucket granularity = TimeBucket.HOUR;

        try {
            //we reduce the length by 2 here to account for single quote in yyyy-MM-dd'T'HH format
            if ((null != timeBucketParam ) && (timeBucketParam.length() == HOUR_BUCKET_TIME_FORMAT.length() - 2)) {
                timeBucket = dfHourFormat.parseDateTime(timeBucketParam);
                granularity = TimeSeriesMetadata.TimeBucket.HOUR;
            } else if ((null != timeBucketParam ) && (timeBucketParam.length() == MINUTE_BUCKET_TIME_FORMAT
                    .length() - 2)) {
                timeBucket = dfMinuteFormat.parseDateTime(timeBucketParam);
                granularity = TimeSeriesMetadata.TimeBucket.MINUTE;
            } else {
            	throw APIException.badRequests.invalidTimeBucket(timeBucketParam );
            }
        } catch (final IllegalArgumentException e) {
        	throw APIException.badRequests.invalidTimeBucket(timeBucketParam, e);
        }

        if (timeBucket == null) {
        	throw APIException.badRequests.invalidTimeBucket(timeBucketParam );
        }

        return Response.ok(
                    getStreamData(timeBucket, granularity, mediaType),
                    mediaType).build();
    }

    /**
     * Retrieves the bulk metering statistics for the given query params.
     *
     * @param timeBucket
     *            - time_bucket for retrieval of stats
     * @param granularity
     *            - granularity can be HOUR or MINUTE
     * @param mediaType
     *            - mediaType application/xml (default) or application/json
     * @return StreamingOuput() - produces StreamingOutput of Stats
     */
    private StreamingOutput getStreamData(final DateTime timeBucket,
            final TimeBucket granularity, final MediaType mediaType) {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream outputStream) {
                PrintWriter out = new PrintWriter(new BufferedOutputStream(
                        outputStream));
                try {
                    if (_statRetriever == null) {
                        throw APIException.internalServerErrors.noMeteringStats();
                    }
                    _statRetriever.getBulkStats(timeBucket, granularity,
                            mediaType, out);
                } catch (final Exception e) {
                   throw APIException.internalServerErrors.meteringStatsError(e.getMessage(),e);
                } finally {
                    out.close();
                }
            }
        };
    }
}
