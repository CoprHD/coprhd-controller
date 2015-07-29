/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.audit;

import java.util.Arrays;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.*;
import com.emc.storageos.security.audit.RecordableAuditLog;

/**
 * Utilities class encapsulates auditlog utility methods.
 */
public class AuditLogUtils {

    // Logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(AuditLogUtils.class);

    /**
     * Converts a RecordableAuditLog to an AuditLog Model
     * 
     * @param event
     * @return
     */
    public static AuditLog convertToAuditLog(RecordableAuditLog auditlog) {

        AuditLog dbAuditLog = new AuditLog();

        dbAuditLog.setTimeInMillis(auditlog.getTimestamp());
        dbAuditLog.setProductId(auditlog.getProductId());
        dbAuditLog.setTenantId(auditlog.getTenantId());
        dbAuditLog.setUserId(auditlog.getUserId());
        dbAuditLog.setServiceType(auditlog.getServiceType());
        dbAuditLog.setAuditType(auditlog.getAuditType());
        dbAuditLog.setDescription(auditlog.getDescription());
        dbAuditLog.setOperationalStatus(auditlog.getOperationalStatus());
        dbAuditLog.setAuditlogId(auditlog.getAuditlogId());

        return dbAuditLog;
    }

    /**
     * reset auditlog "description" column in specific language with parameters packed.
     * 
     * @param auditlog
     * @return
     */
    public static void resetDesc(AuditLog auditlog, ResourceBundle resb) {

        // get formatted description from "description" column
        // Formatted description: "<auditlog version>|<description id>|<param1>|<param2>|..."
        String origdesc = auditlog.getDescription();
        try {
            // parse description id and parameters from column
            String[] parameters = origdesc.split("\\|");

            if (parameters[0].equals(AuditLogManager.AUDITLOG_VERSION)) {
                // get specific-language description from resource file
                String format_desc = resb.getString(parameters[1]);

                // changes are that the number of format specifiers (i.e. %s) doesn't
                // match the number of actual paramters, in which case we will try to
                // generate something still readable (though incomplete).
                int paramCount = parameters.length - 2;
                int formatSpecCount = format_desc.split("%s").length - 1;
                if (formatSpecCount != paramCount) {
                    s_logger.warn("Unexpected number of parameters for audit log {}. Expect {}, {} given."
                            + " Filling the gap will nulls.",
                            new Object[] { parameters[1], formatSpecCount, paramCount });
                }

                // set parameters into the description
                String[] formatParams = Arrays.copyOfRange(parameters, 2,
                        formatSpecCount + 2);
                String newdesc = String.format(format_desc, formatParams);

                auditlog.setDescription(newdesc);
            }
        } catch (Exception e) {
            s_logger.error("can not reset description for {}", origdesc, e);
        }
        return;
    }

}
