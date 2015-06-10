/*
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
import com.emc.storageos.db.client.model.AuditLog;

/**
 *         Interface to serialize an object to a Writer in a desired format
 */
public interface AuditLogMarshaller {

    /**
     * output a header if needed to the writer
     * 
     * @param writer
     */
    public void header(Writer writer) throws MarshallingExcetion;

    /**
     * output a marshaled AuditLog to the writer
     * 
     * @param message
     * @param writer
     * @throws Exception
     */
    public void marshal(AuditLog auditlog, Writer writer) throws MarshallingExcetion;

    /**
     * output a tailer if needed to the writer
     * 
     * @param writer
     */
    public void tailer(Writer writer) throws MarshallingExcetion;

    /**
     * set language for localization
     * 
     * @param lang (e.g. en_US) 
     */
    public void setLang(String lang);
}
