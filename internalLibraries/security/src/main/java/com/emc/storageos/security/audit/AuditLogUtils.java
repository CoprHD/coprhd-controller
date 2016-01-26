/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.audit;

import java.util.Arrays;
import java.util.ResourceBundle;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.*;

/**
 * Utilities class encapsulates auditlog utility methods.
 */
public class AuditLogUtils {

    // Logger reference.
    private static final Logger log = LoggerFactory.getLogger(AuditLogUtils.class);

    /**
     * Converts a RecordableAuditLog to an AuditLog Model
     * 
     * @param auditlog
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
                String formatDesc = resb.getString(parameters[1]);

                // chances are that the number of format specifiers (i.e. %s) doesn't
                // match the number of actual parameters, in which case we will try to
                // generate something still readable (though incomplete).
                int paramCount = parameters.length - 2;
                int formatSpecCount = StringUtils.countMatches(formatDesc, "%s");
                if (formatSpecCount != paramCount) {
                    log.warn("Unexpected number of parameters for audit log {}. Expect {}, {} given."
                                    + " Filling the gap will nulls.",
                            new Object[]{parameters[1], formatSpecCount, paramCount});
                }

                // set parameters into the description
                String[] formatParams = Arrays.copyOfRange(parameters, 2,
                        formatSpecCount + 2);
                String newdesc = String.format(formatDesc, formatParams);

                auditlog.setDescription(newdesc);
            }
        } catch (Exception e) {
            log.error("can not reset description for {}", origdesc, e);
        }
    }

}
