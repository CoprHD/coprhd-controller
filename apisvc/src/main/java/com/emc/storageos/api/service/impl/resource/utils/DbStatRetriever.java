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

import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import javax.ws.rs.core.MediaType;
import org.joda.time.DateTime;

import com.emc.storageos.db.client.TimeSeriesMetadata.TimeBucket;
import com.emc.storageos.db.client.model.StatTimeSeries;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class DbStatRetriever extends AbstractDbRetriever implements StatRetriever {

    @Override
    public void getBulkStats(final DateTime timeBucket, TimeBucket granularity,
            final MediaType mediaType, final PrintWriter out)
            throws MarshallingExcetion {
        StatMarshaller marshaller = StatMarshallerFactory.getMarshaller(mediaType);
        if (marshaller != null) {
            marshaller.header(out);
        } else {
            throw APIException.badRequests.unableToCreateMarshallerForMediaType(mediaType.toString());
        }

        MeteringQueryResults result = new MeteringQueryResults(marshaller, out);
        dbClient.queryTimeSeries(StatTimeSeries.class, timeBucket,
                granularity, result, getThreadPool());
        marshaller.tailer(out);
    }
}
