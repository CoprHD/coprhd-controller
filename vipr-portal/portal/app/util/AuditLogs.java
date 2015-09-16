/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.db.client.model.AuditLog;

/**
 */
@XmlRootElement(name = "auditlogs")
public class AuditLogs {
    private List<AuditLog> auditLogs;

    @XmlElement(name = "log")
    @JsonProperty("auditlogs")
    public List<AuditLog> getAuditLogs() {
        return auditLogs;
    }

    public void setAuditLogs(List<AuditLog> auditLogs) {
        this.auditLogs = auditLogs;
    }

}