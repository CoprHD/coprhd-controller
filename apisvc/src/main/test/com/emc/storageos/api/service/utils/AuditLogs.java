/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.utils;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.db.client.model.AuditLog;

@XmlRootElement(name = "auditlogs")
public class AuditLogs {

    @XmlElements(@XmlElement(name = "log"))
    @JsonProperty("auditlogs")
    public List<AuditLog> auditLogs;

}
