/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.utils;

import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MediaType;

import com.emc.storageos.security.audit.AuditLogRequest;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.AuditLogMarshaller;
import com.emc.storageos.api.service.impl.resource.utils.AuditLogRetriever;
import com.emc.storageos.api.service.impl.resource.utils.JSONAuditLogMarshaller;
import com.emc.storageos.api.service.impl.resource.utils.MarshallingExcetion;
import com.emc.storageos.api.service.impl.resource.utils.XMLAuditLogMarshaller;
import com.emc.storageos.db.client.TimeSeriesMetadata;
import com.emc.storageos.db.client.model.AuditLog;

/**
 * Implemation of AuditLogRetriever to retrieve logs from underlying Cassandra DB
 * 
 * 
 */
public class DummyAuditLogRetriever implements AuditLogRetriever {

    // @TODO - dummy logs for test.

    final private Logger _logger = LoggerFactory.getLogger(DummyAuditLogRetriever.class);

    @Override
    public void getBulkAuditLogs(AuditLogRequest auditLogRequest,
            MediaType type, Writer writer) throws MarshallingExcetion {

        AuditLogMarshaller marshaller = null;

        if (type == MediaType.APPLICATION_XML_TYPE) {
            marshaller = new XMLAuditLogMarshaller();
        } else if (type == MediaType.APPLICATION_JSON_TYPE) {
            marshaller = new JSONAuditLogMarshaller();
        }

        marshaller.header(writer);

        List<AuditLog> auditLogs = null;
        try {
            auditLogs = getDummyAuditLogs();
        } catch (URISyntaxException e) {
            _logger.error("Error getting logs", e);
        }

        for (AuditLog log : auditLogs) {
            if (type == MediaType.APPLICATION_XML_TYPE) {
                marshaller.marshal(log, writer);
            } else if (type == MediaType.APPLICATION_JSON_TYPE) {
                marshaller.marshal(log, writer);
            }
        }

        marshaller.tailer(writer);
    }

    private List<AuditLog> getDummyAuditLogs() throws URISyntaxException {

        // @TODO - dummy logs at the moment.
        List<AuditLog> loglist = new ArrayList<AuditLog>();

        for (int i = 0; i < 100; i++) {
            AuditLog log = new AuditLog();
            log.setProductId("productId." + String.valueOf(i));
            log.setTenantId(new URI("http://tenant." + String.valueOf(i)));
            log.setUserId(new URI("http://user." + String.valueOf(i)));
            log.setServiceType("serviceType." + String.valueOf(i));
            log.setAuditType("auditType." + String.valueOf(i));
            log.setDescription("description." + String.valueOf(i));
            log.setOperationalStatus("operationalStatus." + String.valueOf(i));

            loglist.add(log);
        }

        return loglist;
    }

}
