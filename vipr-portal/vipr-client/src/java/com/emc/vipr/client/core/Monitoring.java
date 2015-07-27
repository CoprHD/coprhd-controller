/*
 * Copyright 2015 EMC Corporation
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

public class Monitoring {
    protected final RestClient client;

    public Monitoring(RestClient client) {
        this.client = client;
    }

    /**
     * Gets the events for the hour of the given date. This is a convenience method for:
     * <tt>getEventsForTimeBucketAsStream(TimeBucketUtils.forHour(date))</tt>
     * 
     * @param date
     *        the date to format as an hour time bucket.
     * @return the stream containing the events as XML.
     * 
     * @see #getEventsForTimeBucketAsStream(String)
     * @see TimeBucketUtils#forHour(Date)
     */
    public InputStream getEventsForHourAsStream(Date date) {
        String timeBucket = TimeBucketUtils.forHour(date);
        ClientResponse response = getEvents(ClientResponse.class, timeBucket);
        return response.getEntityInputStream();
    }

    /**
     * Gets the events for the hour of the given date. This is a convenience method for:
     * <tt>getEventsForTimeBucketAsStream(TimeBucketUtils.forHour(date))</tt>
     * 
     * @param date
     *        the date to format as an hour time bucket.
     * @return the stream containing the events as XML.
     * 
     * @see #getEventsForTimeBucketAsStream(String)
     * @see TimeBucketUtils#forHour(Date)
     */
    public InputStream getEventsForMinuteAsStream(Date date) {
        String timeBucket = TimeBucketUtils.forMinute(date);
        ClientResponse response = getEvents(ClientResponse.class, timeBucket);
        return response.getEntityInputStream();
    }

    /**
     * Gets the event for the given time bucket, as a stream. A time bucket can be either an hour or a minute of any
     * day, in the form of <tt>yyyy-MM-dd'T'HH</tt> for hour or <tt>yy-MM-dd'T'HH:mm for minute.
     * <p>
     * API Call: <tt>GET /monitoring/events?time_bucket={timeBucket}</tt>
     * 
     * @param timeBucket
     *        the time bucket for which to retrieve the events.
     * @return the stream containing the events as XML. This must be closed by the client in order to release the
     *         connection.
     */
    public InputStream getEventsForTimeBucketAsStream(Date date) {
        String timeBucket = TimeBucketUtils.forMinute(date);
        ClientResponse response = getEvents(ClientResponse.class, timeBucket);
        return response.getEntityInputStream();
    }

    protected <T> T getEvents(Class<T> responseType, String timeBucket) {
        UriBuilder builder = client.uriBuilder(PathConstants.MONITORING_EVENTS_URL).queryParam("time_bucket",
                timeBucket);
        return client.resource(builder.build()).type(MediaType.APPLICATION_XML).get(responseType);
    }
}
