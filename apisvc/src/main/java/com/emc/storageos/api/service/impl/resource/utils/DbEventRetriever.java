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

package com.emc.storageos.api.service.impl.resource.utils;

import java.io.Writer;
import javax.ws.rs.core.MediaType;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.TimeSeriesMetadata;
import com.emc.storageos.db.client.model.EventTimeSeries;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.api.service.impl.resource.utils.JSONEventMarshaller;
import com.emc.storageos.api.service.impl.resource.utils.MonitoringEventQueryResult;
import com.emc.storageos.api.service.impl.resource.utils.XMLEventMarshaller;

/**
 * 
 * An implementation of event retriever from a dbClient
 * 
 */
public class DbEventRetriever extends AbstractDbRetriever implements EventRetriever {

    private static final Logger log = LoggerFactory.getLogger(DbEventRetriever.class);

    @Override
    public void getBulkEvents(DateTime time, TimeSeriesMetadata.TimeBucket bucket,
            MediaType type, Writer writer) throws MarshallingExcetion {

        if (dbClient == null) {
            throw APIException.internalServerErrors.noDBClient();
        }

        EventMarshaller marshaller = null;

        if (type.equals(MediaType.APPLICATION_XML_TYPE)) {
            marshaller = new XMLEventMarshaller();
            log.debug("Parser type: {}", type.toString());
        } else if (type.equals(MediaType.APPLICATION_JSON_TYPE)) {
            marshaller = new JSONEventMarshaller();
            log.debug("Parser type: {}", type.toString());
        } else {
            log.warn("unsupported type: {}, use XML", type.toString());
            marshaller = new XMLEventMarshaller();
        }

        MonitoringEventQueryResult result = new MonitoringEventQueryResult(marshaller,
                writer);

        marshaller.header(writer);

        log.info("Query time bucket {}", time.toString());

        dbClient.queryTimeSeries(EventTimeSeries.class, time, bucket, result,
                getThreadPool());

        marshaller.tailer(writer);
    }
}
