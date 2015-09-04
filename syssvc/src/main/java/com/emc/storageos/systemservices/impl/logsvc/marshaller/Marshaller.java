/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc.marshaller;

import java.io.IOException;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.systemservices.impl.logsvc.LogMessage;

public abstract class Marshaller {
    private static final Logger logger = LoggerFactory.getLogger(Marshaller.class);
    protected OutputStream outputStream;

    protected static final byte[] SPACE = " ".getBytes();
    protected static final byte[] RETURN = "\n".getBytes();
    protected static final byte[] LEFT_BRACKET = "[".getBytes();
    protected static final byte[] RIGHT_BRACKET = "]".getBytes();

    protected Marshaller(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    abstract public void marshall(LogMessage logMessage) throws IOException;

    abstract public void marshall(String statusEntry, LogMessage preMsg) throws IOException;

    abstract public void head() throws IOException;

    abstract public void tail() throws IOException;

    public void flush() {
        try {
            outputStream.flush();
        } catch (IOException e) {
            logger.error("Failed to flush output stream", e);
        }
    }
}
