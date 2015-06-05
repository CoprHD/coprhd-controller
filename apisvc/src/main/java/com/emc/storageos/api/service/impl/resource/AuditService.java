/**
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

import java.io.*;
import java.util.List;

import javax.ws.rs.*;
import javax.ws.rs.core.*;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.AuditLogRetriever;
import com.emc.storageos.api.service.impl.resource.utils.MarshallingExcetion;
import com.emc.storageos.db.client.TimeSeriesMetadata;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Audit log resource implementation
 */
@Path("/audit")
public class AuditService extends ResourceService {

    final private Logger _logger = LoggerFactory.getLogger(AuditService.class);

    private AuditLogRetriever _auditlogRetriever;

    /**
     * formats to be used to parse supported time bucket strings
     */
    public static final String HOUR_BUCKET_TIME_FORMAT = "yyyy-MM-dd'T'HH";
    public static final String MINUTE_BUCKET_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm";

    public static final String BAD_TIMEBUCKET_MSG = "Error: time_bucket parameter format supplied is not valid.\n"
            + "Acceptable formats: yyyy-MM-dd'T'HH , yyyy-MM-dd'T'HH:mm";

    /**
     * getter
     */
    public AuditLogRetriever getAuditLogRetriever() {
        return _auditlogRetriever;
    }

    /**
     * setter
     */
    public void setAuditLogRetriever(AuditLogRetriever auditlogRetriever) {
        _auditlogRetriever = auditlogRetriever;
    }

    /**
     * Retrieves the bulk auditlogs and alerts in a specified time bucket (minute
     * or hour).
     *     
     * @param timeBucket Time bucket for retrieval of auditlogs. Acceptable
     *            formats are: yyyy-MM-dd'T'HH for hour bucket,
     *            yyyy-MM-dd'T'HH:mm for minute bucket
     * @param language   Lanuage for the auditlog description. "en_US" by default
     * @brief Show audit logs for time period
     * @return Output stream of auditlogs or an error status.
     */
    @GET
    @Path("/logs")
    @Produces( { MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_AUDITOR })
    public Response getAuditLogs(@QueryParam("time_bucket") final String timeBucket,
            @QueryParam("language") @DefaultValue("en_US") final String language, @Context HttpHeaders header) {
        ArgValidator.checkFieldNotNull(timeBucket, "time_bucket");

        _logger.debug("getAuditLogs: timebucket: {}", timeBucket);

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

        return Response.ok(getStreamOutput(timeBucket, mType, language), mType).build();
    }

    /**
     * Return an output stream object as http response entity so that the client
     * could stream potentially large response.
     *
     * @param time - the time bucket to retrieve auditlogs.
     * @param type - media type of the response.
     * @param lang - language of output. style: en_US
     * @return - the stream object from which client retrieves response message
     *         body.
     */
    private StreamingOutput getStreamOutput(final String time, final MediaType type,
            final String lang) {

        return new StreamingOutput() {
            @Override
            public void write(OutputStream outputStream) {

                // try two time formats which are supported
                DateTimeFormatter hourBucketFormat = DateTimeFormat.forPattern(
                        HOUR_BUCKET_TIME_FORMAT).withZoneUTC();
                DateTimeFormatter minuteBucketFormat = DateTimeFormat.forPattern(
                        MINUTE_BUCKET_TIME_FORMAT).withZoneUTC();

                DateTime timeBucket = null;
                TimeSeriesMetadata.TimeBucket timeBucketGran = TimeSeriesMetadata.TimeBucket.HOUR;

                try {
                    if ((null != time) && (time.length() == HOUR_BUCKET_TIME_FORMAT.length() - 2)) {
                        timeBucket = hourBucketFormat.parseDateTime(time);
                        timeBucketGran = TimeSeriesMetadata.TimeBucket.HOUR;
                    } else if ((null != time)
                            && (time.length() == MINUTE_BUCKET_TIME_FORMAT.length() - 2)) {
                        timeBucket = minuteBucketFormat.parseDateTime(time);
                        timeBucketGran = TimeSeriesMetadata.TimeBucket.MINUTE;
                    } else {
                        throw APIException.badRequests.invalidTimeBucket(time);
                    }
                } catch (final IllegalArgumentException e) {
                	throw APIException.badRequests.invalidTimeBucket(time, e);
                }

                if (timeBucket == null) {
                	throw APIException.badRequests.invalidTimeBucket(time);
                }

                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream));
                try {
                    getAuditLogs(timeBucket, timeBucketGran, type, lang, out);
                } catch (MarshallingExcetion e) {
                    _logger.error("retrieving event error", e);
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

    /**
     * Retrieve auditlogs from underlying source through a streamed writer
     *
     * @param time - the time bucket within which to retrieve auditlogs from.
     * @param bucket - granularity of the time bucket, can be hour or minute.
     * @param type - media type of the auditlogs to be streamed.
     * @param writer - the writer into which the auditlogs to be streamed.
     * @throws MarshallingExcetion - internal marshalling error on auditlog
     *             object.
     */
    private void getAuditLogs(DateTime time, TimeSeriesMetadata.TimeBucket bucket, MediaType type,
            String lang, Writer writer) throws MarshallingExcetion {
        if (_auditlogRetriever == null) {
            throw APIException.internalServerErrors.noAuditLogRetriever();
        }
        _auditlogRetriever.getBulkAuditLogs(time, bucket, type, lang, writer);
    }
}
