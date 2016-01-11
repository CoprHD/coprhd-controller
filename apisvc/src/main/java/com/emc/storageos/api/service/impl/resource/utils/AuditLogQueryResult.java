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
     *@param auditLogRequest
     *            the request contain the query filters
     */
    AuditLogQueryResult(AuditLogMarshaller marshaller, Writer out, AuditLogRequest auditLogRequest) {
        _out = out;
        _marshaller = marshaller;
        _request = auditLogRequest;
    }

    @Override
    public void data(AuditLog data, long insertionTimeMs) {

        _logger.debug("AuditLog #{}", _resultsCount.get());
        if (!filterOut(data)) {
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

    private boolean filterOut(AuditLog auditLog){
        if(_request.getServiceType() != null && _request.getServiceType().length() != 0
                && !_request.getServiceType().equalsIgnoreCase(auditLog.getServiceType())){
            _logger.debug("{} filter out by service type {}",auditLog.getDescription(),_request.getServiceType());
            return true;
        }
        if(_request.getUser() != null && _request.getUser().length() != 0
                && (auditLog.getUserId() != null) && !_request.getUser().equalsIgnoreCase(auditLog.getUserId().toString())){
            _logger.debug("{} filter out by user  {}",auditLog.getDescription(),_request.getUser());
            return true;
        }
        if(_request.getResult() != null && _request.getResult().length() != 0
                && !_request.getResult().equalsIgnoreCase(auditLog.getOperationalStatus())){
            _logger.debug("{} filter out by result {}",auditLog.getDescription(),_request.getResult());
            return true;
        }
        return false;
    }

}
