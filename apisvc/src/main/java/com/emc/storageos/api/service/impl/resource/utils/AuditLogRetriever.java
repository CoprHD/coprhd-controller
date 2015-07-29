/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource.utils;

import java.io.Writer;

import javax.ws.rs.core.MediaType;

import org.joda.time.DateTime;

import com.emc.storageos.db.client.TimeSeriesMetadata;

/**
 * Interface to retrieve auditlogs from underlying persistence layer
 * 
 */
public interface AuditLogRetriever {

    /**
     * Retrieve all auditlogs and stream them to the output stream
     * 
     * @param time
     *            - time of the bucket to be retrieved
     * @param bucket
     *            - granularity of the time, eg. HOUR, MINUTE, etc.
     * @param type
     *            - media type to be streamed
     * @param writer
     *            - the output writer to stream retrived auditlogs
     * @throws MarshallingExcetion
     *             - auditlog object marshalling failed
     */
    public void getBulkAuditLogs(DateTime time, TimeSeriesMetadata.TimeBucket bucket,
            MediaType type, String lang, Writer writer) throws MarshallingExcetion;
}
