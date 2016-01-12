/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource.utils;

import java.io.Writer;
import java.util.concurrent.atomic.AtomicLong;

import com.emc.storageos.security.audit.AuditLogRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.TimeSeriesQueryResult;
import com.emc.storageos.db.client.model.AuditLog;

/**
 * Implementation of DB time series based query result for audit logs
 */
public class AuditLogQueryResult implements TimeSeriesQueryResult<AuditLog> {

    final private Logger _logger = LoggerFactory
            .getLogger(AuditLogQueryResult.class);

    private final AtomicLong _resultsCount = new AtomicLong(0);

    /**
     * indication of situation when streaming should be stopped
     */
    boolean _stopStreaming = false;

    /**
     * the marshaller to be used for serializing audit logs
     */
    private AuditLogMarshaller _marshaller;

    /**
     * the putput writer for serializing audit logs
     */
    private Writer _out;
    /*
    * request contain the query parameter to filter out audit logs
    * */
    private AuditLogRequest _request;

    /**
     * Create new auditlog query results with query filters
     * @param marshaller
     *            auditlog marshaler
     * @param out
     *            the writer for writing results one by one
     *@param auditLogFilter
     *            the request contain the query filters
     */
    AuditLogQueryResult(AuditLogMarshaller marshaller, Writer out, AuditLogRequest auditLogFilter) {
        _out = out;
        _marshaller = marshaller;
        _request = auditLogFilter;
    }

    @Override
    public void data(AuditLog data, long insertionTimeMs) {

        _logger.debug("AuditLog #{}", _resultsCount.get());
        if (hasFilterOut(data)) {
            _logger.debug("Filter out the audit log {}",data);
            return ;
        }
        try {
            if (!_stopStreaming) {
                if (_marshaller.marshal(data, _out, _request.getKeyword())) {
                    _resultsCount.incrementAndGet();
                }
            }
        } catch (MarshallingExcetion e) {
            _logger.error("Error during auditlog marshaling", e);
            _stopStreaming = true;
        }
    }

    @Override
    public void done() {
        _logger.debug("Query Result Size  = {}", _resultsCount.get());
    }

    @Override
    public void error(Throwable e) {
        _logger.error("Error during query execution", e);
    }

    public void outputCount() { _logger.info("Query Result Size  = {}", _resultsCount.get()); }

    private boolean filterByServiceType(AuditLog auditLog) {
        String sType = _request.getServiceType();
        return (sType != null && !sType.isEmpty() && !sType.equalsIgnoreCase(auditLog.getServiceType()));
    }

    private boolean filterByUser(AuditLog auditLog) {
        String user = _request.getUser();
        return (user != null && !user.isEmpty() && !user.equalsIgnoreCase(auditLog.getUserId().toString()));
    }

    private boolean filterByResult(AuditLog auditLog) {
        String result = _request.getResult();
        return (result != null && !result.isEmpty() && !result.equalsIgnoreCase(auditLog.getOperationalStatus()));
    }


    private boolean hasFilterOut(AuditLog auditLog) {
        return filterByServiceType(auditLog) || filterByUser(auditLog) || filterByResult(auditLog);
    }

}
