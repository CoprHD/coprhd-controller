/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.io.InputStream;
import java.util.Date;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.TimeBucketUtils;
import com.emc.vipr.client.impl.RestClient;
import com.sun.jersey.api.client.ClientResponse;

public class Audit {
    protected final RestClient client;

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
     * 
     * @see #getLogsForHourAsStream(Date, String)
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
     * 
     * @see #getLogsForTimeBucketAsStream(String)
     * @see TimeBucketUtils#forHour(Date)
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
     * 
     * @see #getLogsForMinuteAsStream(Date, String)
     * @see TimeBucketUtils#forMinute(Date)
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
     * @see #getLogsForTimeBucketAsStream(String)
     * @see TimeBucketUtils#forMinute(Date)
     */
    public InputStream getLogsForMinuteAsStream(Date date, String language) {
        String timeBucket = TimeBucketUtils.forMinute(date);
        return getLogsForTimeBucketAsStream(timeBucket, language);
    }

    /**
     * Gets the logs for the given time bucket, as a stream. A time bucket can be either an hour or a minute of any day,
     * in the form of <tt>yyyy-MM-dd'T'HH</tt> for hour or <tt>yy-MM-dd'T'HH:mm for minute.
     * <p>
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
     * in the form of <tt>yyyy-MM-dd'T'HH</tt> for hour or <tt>yy-MM-dd'T'HH:mm for minute.
     * <p>
     * API Call: <tt>GET /audit/logs?time_bucket={timeBucket}&language={language}</tt>
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
        UriBuilder builder = client.uriBuilder(PathConstants.AUDIT_LOGS_URL).queryParam("time_bucket", timeBucket);
        if ((language != null) && (language.length() > 0)) {
            builder.queryParam("language", language);
        }
        return client.resource(builder.build()).type(MediaType.APPLICATION_XML).get(responseType);
    }
}
