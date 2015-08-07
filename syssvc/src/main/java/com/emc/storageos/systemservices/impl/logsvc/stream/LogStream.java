/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc.stream;

import com.emc.storageos.systemservices.impl.logsvc.LogMessage;

public interface LogStream {
    public LogMessage readNextLogMessage();
}
