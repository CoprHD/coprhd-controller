/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.io.PrintWriter;
import com.emc.storageos.db.client.model.Stat;

/**
 * 
 * @author rvobugar
 * 
 *         Interface to serialize an object to a Writer in a desired format
 */
public interface StatMarshaller {

    /**
     * output a header if needed to the writer
     * 
     * @param writer
     */
    public void header(PrintWriter writer);

    /**
     * output a marshaled Stat to the writer
     * 
     * @param message
     * @param writer
     * @throws Exception
     */
    public void marshall(Stat stat, PrintWriter writer) throws Exception;

    /**
     * output a tailer if needed to the writer
     * 
     * @param writer
     */
    public void tailer(PrintWriter writer);

    /**
     * output a error if needed to the writer
     * 
     * @param writer
     * @param error
     */
    public void error(PrintWriter writer, String error);

}
