/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource.utils;

import java.io.Writer;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.security.audit.AuditLogRequest;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.TimeSeriesMetadata;
import com.emc.storageos.db.client.model.AuditLogTimeSeries;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.api.service.impl.resource.utils.JSONAuditLogMarshaller;
import com.emc.storageos.api.service.impl.resource.utils.XMLAuditLogMarshaller;
import com.emc.storageos.api.service.impl.resource.utils.AuditLogQueryResult;

/**
 * 
 * An implementation of auditlog retriever from a dbClient
 * 
 */
public class DbAuditLogRetriever extends AbstractDbRetriever implements AuditLogRetriever {

    private static final Logger log = LoggerFactory.getLogger(DbAuditLogRetriever.class);

    @Override
    public void getBulkAuditLogs(AuditLogRequest auditLogRequest,
                                 MediaType type, Writer writer) throws MarshallingExcetion {

        if (dbClient == null) {
            throw APIException.internalServerErrors.auditLogNoDb();
        }

        AuditLogMarshaller marshaller = null;

        if (type.equals(MediaType.APPLICATION_XML_TYPE)) {
            marshaller = new XMLAuditLogMarshaller();
            log.debug("Parser type: {}", type.toString());
        } else if (type.equals(MediaType.APPLICATION_JSON_TYPE)) {
            marshaller = new JSONAuditLogMarshaller();
            log.debug("Parser type: {}", type.toString());
        } else if (type.equals(MediaType.TEXT_PLAIN)) {
            marshaller = new TextAuditLogMarshaller();
            log.debug("parser type: {}", type.toString());
        } else {
            log.warn("unsupported type: {}, use XML", type.toString());
            marshaller = new XMLAuditLogMarshaller();
        }
        marshaller.setLang(auditLogRequest.getLanguage());

        DateTime start = auditLogRequest.getStartTime();
        DateTime end = auditLogRequest.getEndTime();

        TimeSeriesMetadata.TimeBucket bucket = TimeSeriesMetadata.TimeBucket.HOUR;
        if (start.plusHours(1).isAfter(end.toInstant())){
            bucket = TimeSeriesMetadata.TimeBucket.MINUTE;
        }

        AuditLogQueryResult result = new AuditLogQueryResult(marshaller,
                    writer,auditLogRequest);

        marshaller.header(writer);

        log.info("Query time bucket  {} to {}", start,end);
        for ( ; !start.isAfter(end.toInstant());start = start.plusHours(1)){
            dbClient.queryTimeSeries(AuditLogTimeSeries.class, start, bucket, result,
                    getThreadPool());
        }
        result.outputCount();

        marshaller.tailer(writer);
    }
}
