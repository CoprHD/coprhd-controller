/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc.marshaller;

import java.io.IOException;
import java.io.OutputStream;

import com.emc.storageos.systemservices.impl.logsvc.LogMessage;

public class TextMarshaller extends Marshaller {

    TextMarshaller(OutputStream outputStream) {
        super(outputStream);
    }

    @Override
    public void head() {
    }

    @Override
    public void tail() {
    }

    @Override
    public void marshall(LogMessage logMessage) throws IOException {
        outputStream.write(logMessage.getNodeId());
        outputStream.write(SPACE);
        outputStream.write(logMessage.getNodeName());
        outputStream.write(SPACE);
        outputStream.write(logMessage.getService());
        outputStream.write(SPACE);
        outputStream.write(logMessage.getRawLogContent());
        outputStream.write(RETURN);
    }

    @Override
    public void marshall(String status, LogMessage preMsg) throws IOException {
        outputStream.write("internal|".getBytes());
        outputStream.write(status.getBytes());
        outputStream.write(RETURN);
    }
}
