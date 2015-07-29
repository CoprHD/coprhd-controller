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
