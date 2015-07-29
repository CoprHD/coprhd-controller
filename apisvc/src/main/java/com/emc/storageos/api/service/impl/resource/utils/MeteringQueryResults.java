/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
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
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.TimeSeriesQueryResult;
import com.emc.storageos.db.client.model.Stat;

public class MeteringQueryResults implements TimeSeriesQueryResult<Stat> {
    private final AtomicLong _resultsCount = new AtomicLong(0);
    private PrintWriter _out;
    private StatMarshaller _marshaller;
    final private Logger _logger = LoggerFactory
            .getLogger(MeteringQueryResults.class);

    /**
     * Create new metering query results with max count
     * 
     * @param StatMarshaller
     *            an instance of StatMarshaller
     * @param out
     *            the print writer for writing results one by one
     * @param limit
     *            the maximum number of records to return, 0 for no limit
     */
    MeteringQueryResults(StatMarshaller marshaller, PrintWriter out) {
        _out = out;
        _marshaller = marshaller;
    }

    @Override
    public void done() {
    }

    @Override
    public void error(Throwable e) {
        _marshaller.error(_out, e.toString());
    }

    @Override
    public void data(Stat data, long insertionTimeMs) {
        if (data != null) {
            try {
                _marshaller.marshall(data, _out);
                _resultsCount.incrementAndGet();
            } catch (Exception e) {
                _logger.error("Exception during marshalling:", e);
            }
        }
    }
}
