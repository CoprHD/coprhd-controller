/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource.utils;

import java.io.Writer;

import javax.ws.rs.core.MediaType;

import com.emc.storageos.security.audit.AuditLogRequest;
/**
 * Interface to retrieve auditlogs from underlying persistence layer
 * 
 */
public interface AuditLogRetriever {

    /**
     * Retrieve all auditlogs and stream them to the output stream
     * 
     * @param auditLogRequest
     *            - request with query params that auditlogs to be retrieved
     * @param type
     *            - media type to be streamed
     * @param writer
     *            - the output writer to stream retrived auditlogs
     * @throws MarshallingExcetion
     *             - auditlog object marshalling failed
     */
    public void getBulkAuditLogs(AuditLogRequest auditLogRequest, MediaType type, Writer writer) throws MarshallingExcetion;

}
