/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import java.io.*;
import java.util.List;

import javax.ws.rs.*;
import javax.ws.rs.core.*;

import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.audit.AuditLogRequest;
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
     * @param language Lanuage for the auditlog description. "en_US" by default
     * @brief Show audit logs for time period
     * @return Output stream of auditlogs or an error status.
     */
    public Response getAuditLogs( final String timeBucket, final String language, HttpHeaders header) {
        return getAuditLogs(timeBucket, language, null, null, null, null, null, null, header);
    }
    /**
     * Retrieves the bulk auditlogs and alerts based on passed request paramters
     * or hour).
     *
     * @param timeBucket Time bucket for retrieval of auditlogs. Acceptable
     *            formats are: yyyy-MM-dd'T'HH for hour bucket,
     *            yyyy-MM-dd'T'HH:mm for minute bucket
     * @param startTimeStr Overrided if timeBucket specified .start time for retieval of auditlogs.
     *                     formats are: yyyy-MM-dd'T'HH
     * @param endTimeStr  Override if timeBucket specified .end time for retieval of auditlogs.
     *                   formats are: yyyy-MM-dd'T'HH
     * @param svcType service type for retrieval of auditlogs
     * @param user the user of auditlogs to retrieve
     * @param result the result of auditlogs to retrieve
     * @param keyword the containing keyword of auditlog to retrive
     * @param language Lanuage for the auditlog description. "en_US" by default
     * @brief Show audit logs for time period with specified paramters
     * @return Output stream of auditlogs or an error status.
     */
    @GET
    @Path("/logs")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
    @CheckPermission(roles = { Role.SYSTEM_AUDITOR })
    public Response getAuditLogs(
            @QueryParam("time_bucket") final String timeBucket,
            @QueryParam("start") final String startTimeStr,
            @QueryParam("end") final String endTimeStr,
            @QueryParam("service_type") final String svcType,
            @QueryParam("user") final String user,
            @QueryParam("result") final String result,
            @QueryParam("keyword") final String keyword,
            @QueryParam("language") @DefaultValue("en_US") final String language,
            @Context HttpHeaders header) {

        MediaType mType = MediaType.APPLICATION_XML_TYPE;
        List<MediaType> mTypes = header.getAcceptableMediaTypes();

        if (mTypes != null) {
            for (MediaType media : mTypes) {
                if (media.equals(MediaType.APPLICATION_JSON_TYPE)
                        || media.equals(MediaType.TEXT_PLAIN_TYPE)) {
                    mType = media;
                    break;
                }
            }
        }
        _logger.info("mtype is :{}",mType);
        
        DateTime startTime, endTime;
        if (timeBucket != null && !timeBucket.isEmpty()) {
            startTime = getDataTime(timeBucket,HOUR_BUCKET_TIME_FORMAT);
            if (startTime != null ) {
                endTime = startTime.plusMinutes(59);
            }else {
                startTime = getDataTime(timeBucket,MINUTE_BUCKET_TIME_FORMAT);
                if (startTime != null) {
                    endTime = startTime.plusSeconds(59);
                }else {
                    throw APIException.badRequests.invalidTimeBucket(timeBucket);
                }
            }
        }else {
            startTime = getDataTime(startTimeStr, HOUR_BUCKET_TIME_FORMAT);
            if (startTime == null) {
                throw APIException.badRequests.invalidDate(startTimeStr);
            }
            endTime = getDataTime(endTimeStr, HOUR_BUCKET_TIME_FORMAT);
            if (endTime == null) {
                throw APIException.badRequests.invalidDate(endTimeStr);
            }
            validateDataTimePair(startTime,endTime);
        }
        validateResultValue(result);
        String auditResult = null;
        if (result != null){
            auditResult = (result.equalsIgnoreCase("S") ? AuditLogManager.AUDITLOG_SUCCESS : AuditLogManager.AUDITLOG_FAILURE);
        }

        AuditLogRequest auditLogRequest = new AuditLogRequest.Builder().serviceType(svcType)
                .user(user).result(auditResult).keyword(keyword).lang(language).timeBucket(timeBucket)
                .start(startTime).end(endTime).build();

        return Response.ok(getStreamOutput(auditLogRequest, mType), mType).build();
    }

    /**
     * Return an output stream object as http response entity so that the client
     * could stream potentially large response.
     *
     * @param auditLogRequest - the auditLogRequest containing query paramter to filter out when retrieve auditlogs.
     * @param type - media type of the response.
     * @return - the stream object from which client retrieves response message
     *         body.
     */
    private StreamingOutput getStreamOutput(final AuditLogRequest auditLogRequest,final MediaType type) {

        return new StreamingOutput() {
            @Override
            public void write(OutputStream outputStream) {

                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream));
                try {
                    if (_auditlogRetriever == null) {
                        throw APIException.internalServerErrors.noAuditLogRetriever();
                    }
                    _auditlogRetriever.getBulkAuditLogs(auditLogRequest, type, out);
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

    private DateTime getDataTime(String timeStr,String timeFormatStr) {
        DateTimeFormatter timeFormatter = DateTimeFormat.forPattern(
                timeFormatStr).withZoneUTC();
        if ((timeStr == null) || timeStr.length() != timeFormatStr.length() - 2) {
            return null;
        }
        try{
            return timeFormatter.parseDateTime(timeStr);
        }catch (IllegalArgumentException e){
            throw APIException.badRequests.invalidTimeBucket(timeStr, e);
        }
    }

    private void validateDataTimePair(DateTime start,DateTime end) {
        if ((start != null) && (end != null)) {
            if (end.isBefore(start.toInstant())) {
                throw APIException.badRequests.endTimeBeforeStartTime(start.toString(), end.toString());
            }
        }
    }

    private void validateResultValue( String result) {
        if (result != null) {
            if (!result.equalsIgnoreCase("S") &&  !result.equalsIgnoreCase("F")){
                throw APIException.badRequests.parameterIsNotValid("result");
            }
        }
    }
}
