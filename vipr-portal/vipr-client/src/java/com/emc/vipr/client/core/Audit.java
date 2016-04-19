/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.io.InputStream;
import java.net.URI;
import java.util.Date;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.TimeBucketUtils;
import com.emc.vipr.client.impl.DateUtils;
import com.emc.vipr.client.impl.RestClient;
import com.sun.jersey.api.client.ClientResponse;

public class Audit {
    protected final RestClient client;

    private static final String TIME_BUCKET = "time_bucket";
    private static final String START = "start";
    private static final String END = "end";
    private static final String SERVICE_TYPE = "service_type";
    private static final String USER = "user";
    private static final String RESULT = "result";
    private static final String KEYWORD = "keyword";
    private static final String LANGUAGE = "language";

    public Audit(RestClient client) {
        this.client = client;
    }

    /**
     * Gets the logs for the hour of the given date, as a stream. This is a convenience method for
     * <tt>getLogsForHourAsStream(date, null)</tt>.
     * 
     * @param date
     *            the date, which will be formatted as an hour time bucket.
     * @return the stream containing the logs as XML. This must be closed by the client in order to release the
     *         connection.
     * @see #getLogsForHourAsStream(Date,String)
     * @see TimeBucketUtils#forHour(Date)  
     */
    public InputStream getLogsForHourAsStream(Date date) {
        return getLogsForHourAsStream(date, null);
    }

    /**
     * Gets the logs for the hour of the given date, as a stream. This formats the date as <tt>yyyy-MM-dd'T'HH</tt> in
     * UTC time and supplies that as the time bucket for {@link #getLogsForTimeBucketAsStream(String)}.
     * 
     * @param date
     *            the date, which will be formatted as an hour time bucket.
     * @return the stream containing the logs as XML. This must be closed by the client in order to release the
     *         connection.
     * @see #getLogsForTimeBucketAsStream(String)
     * @see TimeBucketUtils#forHour(Date)
     * 
     */
    public InputStream getLogsForHourAsStream(Date date, String language) {
        String timeBucket = TimeBucketUtils.forHour(date);
        return getLogsForTimeBucketAsStream(timeBucket, language);
    }

    /**
     * Gets the logs for the minute of the given date, as a stream. This is a convenience method for
     * <tt>getLogsForMinuteAsStream(date, null)</tt>.
     * 
     * @param date
     *            the date, which will be formatted as a minute time bucket.
     * @return the stream containing the logs as XML. This must be closed by the client in order to release the
     *         connection.
     * @see #getLogsForTimeBucketAsStream(String)
     * @see TimeBucketUtils#forHour(Date) 
     */
    public InputStream getLogsForMinuteAsStream(Date date) {
        return getLogsForMinuteAsStream(date, null);
    }

    /**
     * Gets the logs for the minute of the given date, as a stream. This formats the date as <tt>yyyy-MM-dd'T'HH:mm</tt> in UTC time and
     * supplies that as the time bucket for {@link #getLogsForTimeBucketAsStream(String)}.
     * 
     * @param date
     *            the date, which will be formatted as a minute time bucket.
     * @return the stream containing the logs as XML. This must be closed by the client in order to release the
     *         connection.
     * 
     */
    public InputStream getLogsForMinuteAsStream(Date date, String language) {
        String timeBucket = TimeBucketUtils.forMinute(date);
        return getLogsForTimeBucketAsStream(timeBucket, language);
    }

    /**
     * Gets the logs for the given time bucket, as a stream. A time bucket can be either an hour or a minute of any day,
     * in the form of <tt>yyyy-MM-dd'T'HH</tt> for hour or <tt>yy-MM-dd'T'HH:mm for minute.</tt>
     * API Call: <tt>GET /audit/logs?time_bucket={timeBucket}</tt>
     * 
     * @param timeBucket
     *            the time bucket for which to retrieve the logs.
     * @return the stream containing the logs as XML. This must be closed by the client in order to release the
     *         connection.
     */
    public InputStream getLogsForTimeBucketAsStream(String timeBucket) {
        return getLogsForTimeBucketAsStream(timeBucket, null);
    }

    /**
     * Gets the logs for the given time bucket, as a stream. A time bucket can be either an hour or a minute of any day,
     * in the form of <tt>yyyy-MM-dd'T'HH</tt> for hour or <tt>yy-MM-dd'T'HH:mm for minute. </tt>
     * API Call: <tt>GET /audit/logs?time_bucket={timeBucket} and language={language}</tt>
     * 
     * @param timeBucket
     *            the time bucket for which to retrieve the logs.
     * @param language
     *            the language for the logs (optional).
     * @return the stream containing the logs as XML. This must be closed by the client in order to release the
     *         connection.
     */
    public InputStream getLogsForTimeBucketAsStream(String timeBucket, String language) {
        ClientResponse response = getLogs(ClientResponse.class, timeBucket, language);
        return response.getEntityInputStream();
    }

    protected <T> T getLogs(Class<T> responseType, String timeBucket, String language) {
        UriBuilder builder = client.uriBuilder(PathConstants.AUDIT_LOGS_URL).queryParam(TIME_BUCKET, timeBucket);
        if ((language != null) && (language.length() > 0)) {
            builder.queryParam(LANGUAGE, language);
        }
        return client.resource(builder.build()).accept(MediaType.APPLICATION_XML).get(responseType);
    }

    public InputStream getAsStream(Date start, Date end, String serviceType, String user, String result,
            String keyword, String language) {
        return getAsStream(formatDate(start), formatDate(end), serviceType, user, result, keyword, language);
    }

    public InputStream getAsStream(String startTime, String endTime, String serviceType, String user, String result, String keyword,
            String language) {
        URI uri = getURI(startTime, endTime, serviceType, user, result, keyword, language);
        ClientResponse response = client.resource(uri).accept(MediaType.APPLICATION_XML).get(ClientResponse.class);
        return response.getEntityInputStream();
    }

    public InputStream getAsText(Date start, Date end, String serviceType, String user, String result,
            String keyword, String language) {
        return getAsText(formatDate(start), formatDate(end), serviceType, user, result, keyword, language);
    }

    public InputStream getAsText(String startTime, String endTime, String serviceType, String user, String result, String keyword,
            String language) {
        URI uri = getURI(startTime, endTime, serviceType, user, result, keyword, language);
        ClientResponse response = client.resource(uri).accept(MediaType.TEXT_PLAIN).get(ClientResponse.class);
        return response.getEntityInputStream();
    }

    private URI getURI(String startTime, String endTime, String serviceType, String user, String result, String keyword,
            String language) {
        UriBuilder builder = client.uriBuilder(PathConstants.AUDIT_LOGS_URL);

        if ((startTime != null) && (startTime.length() > 0)) {
            builder.queryParam(START, startTime);
        }
        if ((endTime != null) && (endTime.length() > 0)) {
            builder.queryParam(END, endTime);
        }
        if ((serviceType != null) && (serviceType.length() > 0)) {
            builder.queryParam(SERVICE_TYPE, serviceType);
        }
        if ((language != null) && (language.length() > 0)) {
            builder.queryParam(LANGUAGE, language);
        }
        buildUserResultKeyword(builder, user, result, keyword);
        return builder.build();
    }

    private void buildUserResultKeyword(UriBuilder builder, String user, String result, String keyword) {
        if ((user != null) && (user.length() > 0)) {
            builder.queryParam(USER, user);
        }
        if ((result != null) && (result.length() > 0)) {
            builder.queryParam(RESULT, result);
        }
        if ((keyword != null) && (keyword.length() > 0)) {
            builder.queryParam(KEYWORD, keyword);
        }
    }

    private String formatDate(Date date) {
        return date != null ? DateUtils.formatUTC(date, "yyyy-MM-dd'T'HH") : null;
    }
}
