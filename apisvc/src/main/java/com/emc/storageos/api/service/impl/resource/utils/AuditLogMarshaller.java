/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.io.Writer;
import com.emc.storageos.db.client.model.AuditLog;

/**
 * Interface to serialize an object to a Writer in a desired format
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
     * @param auditlog
     * @param writer
     * @throws MarshallingExcetion
     */
    public void marshal(AuditLog auditlog, Writer writer) throws MarshallingExcetion;
    /**
     * output a marshaled AuditLog with description containing keyword to the writer
     *
     * @param auditlog
     * @param writer
     * @param keyword
     * @return True if the Auditlog outputted to the writer,else False
     * @throws MarshallingExcetion
     */
    public boolean marshal(AuditLog auditlog, Writer writer,String keyword) throws MarshallingExcetion;

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
