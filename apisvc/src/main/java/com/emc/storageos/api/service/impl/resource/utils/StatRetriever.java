/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.io.PrintWriter;
import javax.ws.rs.core.MediaType;
import org.joda.time.DateTime;
import com.emc.storageos.db.client.TimeSeriesMetadata.TimeBucket;

public interface StatRetriever {

    /**
     * Retrieves the bulk metering statistics for the given query params.
     *
     * @param timeBucket
     *            - time-bucket for retrieval of stats
     * @param granularity
     *            - granularity can be HOUR or MINUTE
     * @param MediaType
     *            - mediaType application/xml (default) or application/json
     * @param PrintWriter
     *            - PrintWriter object
     * @param dbClient
     *            - dbClient client API
     * @throws MarshallingExcetion
     * @returns void
     */
    public void getBulkStats(final DateTime timeBucket, TimeBucket granularity,
            final MediaType mediaType, final PrintWriter out)
            throws MarshallingExcetion;
}
