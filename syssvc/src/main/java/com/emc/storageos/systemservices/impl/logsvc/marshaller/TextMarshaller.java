/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.logsvc.marshaller;

import java.io.IOException;
import java.io.OutputStream;

import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.storageos.systemservices.impl.logsvc.util.LogUtil;

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
