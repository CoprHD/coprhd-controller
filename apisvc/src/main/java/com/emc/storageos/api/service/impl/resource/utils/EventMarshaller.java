/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource.utils;

import java.io.Writer;

import com.emc.storageos.db.client.model.Event;

/**
 * Interface to serialize an Event object to a Writer
 */
public interface EventMarshaller {

    /**
     * output a header if needed to the writer
     * 
     * @param writer
     * @throws MarshallingExcetion
     */
    public void header(Writer writer) throws MarshallingExcetion;

    /**
     * output a marshaled event to the writer
     * 
     * @param event
     * @param writer
     * @throws MarshallingExcetion
     */
    public void marshal(Event event, Writer writer) throws MarshallingExcetion;

    /**
     * output a tailer if needed to the writer
     * 
     * @param writer
     * @throws MarshallingExcetion
     */
    public void tailer(Writer writer) throws MarshallingExcetion;
}
