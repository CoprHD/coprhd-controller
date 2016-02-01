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

public class Metering {
    protected final RestClient client;

    public Metering(RestClient client) {
        this.client = client;
    }

    /**
     * Gets the statistics for the hour of the given date. This is a convenience method for:
     * <tt>getStatisticsForTimeBucketAsStream(TimeBucketUtils.forHour(date))</tt>
     * 
     * @param date
     *            the date to format as an hour time bucket.
     * @return the stream containing the statistics as XML.
     * 
     * @see #getStatisticsForTimeBucketAsStream(String)
     * @see TimeBucketUtils#forHour(Date)
     */
    public InputStream getStatisticsForHourAsStream(Date date) {
        String timeBucket = TimeBucketUtils.forHour(date);
        return getStatisticsForTimeBucketAsStream(timeBucket);
    }

    /**
     * Gets the statistics for the minute of the given date. This is a convenience method for:
     * <tt>getStatisticsForTimeBucketAsStream(TimeBucketUtils.forMinute(date))</tt>
     * 
     * @param date
     *            the date to format as a minute time bucket.
     * @return the stream containing the statistics as XML.
     * @see #getStatisticsForTimeBucketAsStream(String)
     * @see TimeBucketUtils#forMinute(Date)
     */
    public InputStream getStatisticsForMinuteAsStream(Date date) {
        String timeBucket = TimeBucketUtils.forMinute(date);
        return getStatisticsForTimeBucketAsStream(timeBucket);
    }

    /**
     * Gets the statistics for the given time bucket, as a stream. A time bucket can be either an hour or a minute of
     * any day, in the form of <tt>yyyy-MM-dd'T'HH</tt> for hour or <tt>yy-MM-dd'T'HH:mm for minute.</tt>
     * API Call: <tt>GET /metering/stats?time_bucket={timeBucket}</tt>
     * 
     * @param timeBucket
     *            the time bucket for which to retrieve the stats.
     * @return the stream containing the statistics as XML. This must be closed by the client in order to release the
     *         connection.
     */
    public InputStream getStatisticsForTimeBucketAsStream(String timeBucket) {
        ClientResponse response = getStatistics(ClientResponse.class, timeBucket);
        return response.getEntityInputStream();
    }

    protected <T> T getStatistics(Class<T> responseType, String timeBucket) {
        UriBuilder builder = client.uriBuilder(PathConstants.METERING_STATS_URL).queryParam("time_bucket", timeBucket);
        return client.resource(builder.build()).type(MediaType.APPLICATION_XML).get(responseType);
    }
}
