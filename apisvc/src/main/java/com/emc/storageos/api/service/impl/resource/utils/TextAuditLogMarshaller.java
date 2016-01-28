/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource.utils;

import com.emc.storageos.db.client.model.AuditLog;
import com.emc.storageos.security.audit.AuditLogUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * auditlog text marshaler
 */
public class TextAuditLogMarshaller implements AuditLogMarshaller {
    final private Logger _logger = LoggerFactory.getLogger(TextAuditLogMarshaller.class);

    private static volatile Locale locale = null;
    private static volatile ResourceBundle resb = null;
    private static final String SPACE = " ";
    private static final String RETURN = "\n";

    @Override
    public void header(Writer writer) throws MarshallingExcetion {
    }

    @Override
    public void marshal(AuditLog auditlog, Writer writer) throws MarshallingExcetion {
        marshal(auditlog, writer, null);
    }

    public boolean marshal(AuditLog auditlog, Writer writer, String keyword) throws MarshallingExcetion {
        if (auditlog == null) {
            _logger.warn("null auditlog dropped");
            return false;
        }
        AuditLogUtils.resetDesc(auditlog, resb);
        if (!AuditLogUtils.isKeywordContained(auditlog, keyword)) {
            _logger.debug("{} filter out by description keyword {}", auditlog.getDescription(), keyword);
            return false;
        }

        try {
            BufferedWriter ow = ((BufferedWriter) writer);
            ow.write(new DateTime(auditlog.getTimeInMillis(), DateTimeZone.UTC).toString());
            ow.write(SPACE);
            ow.write(auditlog.getServiceType());
            ow.write(SPACE);
            ow.write(auditlog.getUserId().toString());
            ow.write(SPACE);
            ow.write(auditlog.getOperationalStatus());
            ow.write(SPACE);
            ow.write(auditlog.getDescription());
            ow.write(RETURN);
        } catch (IOException e) {
            throw new MarshallingExcetion("JSON streaming failed: ", e);
        }
        return true;
    }

    @Override
    public void tailer(Writer writer) throws MarshallingExcetion {
    }

    @Override
    public void setLang(String lang) {
        String language, country;
        String[] array = lang.split("_");
        if (array.length != 2) {
            language = "en";
            country = "US";
        } else {
            language = array[0];
            country = array[1];
        }

        locale = new Locale(language, country);
        resb = ResourceBundle.getBundle("SDSAuditlogRes", locale);
    }

}
