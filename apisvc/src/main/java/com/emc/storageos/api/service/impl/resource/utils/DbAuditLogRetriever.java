/**
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.ws.rs.core.MediaType;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.TimeSeriesMetadata;
import com.emc.storageos.db.client.model.AuditLogTimeSeries;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.api.service.impl.resource.utils.JSONAuditLogMarshaller;
import com.emc.storageos.api.service.impl.resource.utils.XMLAuditLogMarshaller;
import com.emc.storageos.api.service.impl.resource.utils.AuditLogQueryResult;
import com.emc.storageos.services.util.NamedThreadPoolExecutor;


/**
 * 
 * An implementation of auditlog retriever from a dbClient
 *
 */
public class DbAuditLogRetriever extends AbstractDbRetriever implements AuditLogRetriever {

    private static final Logger log = LoggerFactory.getLogger(DbAuditLogRetriever.class);

    @Override
    public void getBulkAuditLogs(DateTime time, TimeSeriesMetadata.TimeBucket bucket,
            MediaType type, String lang, Writer writer) throws MarshallingExcetion {

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
        } else {
            log.warn("unsupported type: {}, use XML", type.toString());
            marshaller = new XMLAuditLogMarshaller();
        }
        marshaller.setLang(lang);

        AuditLogQueryResult result = new AuditLogQueryResult(marshaller,
                writer);

        marshaller.header(writer);

        log.info("Query time bucket {}", time.toString());
            
        dbClient.queryTimeSeries(AuditLogTimeSeries.class, time, bucket, result,
                getThreadPool());

        marshaller.tailer(writer);
    }
}
