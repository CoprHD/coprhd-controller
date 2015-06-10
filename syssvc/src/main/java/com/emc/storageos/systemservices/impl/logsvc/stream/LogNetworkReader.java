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
package com.emc.storageos.systemservices.impl.logsvc.stream;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.systemservices.impl.logsvc.LogACKCode;
import com.emc.storageos.systemservices.impl.logsvc.LogConstants;
import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.storageos.systemservices.impl.logsvc.LogStatusInfo;
import com.emc.storageos.systemservices.impl.logsvc.util.LogUtil;

public class LogNetworkReader implements LogStream {
    // Logger reference.
    private static final Logger logger = LoggerFactory.getLogger(LogNetworkReader.class);

    private DataInputStream dis;
    private LogStatusInfo status;
    private boolean isFinished;  // record is the logs and status chucks are all finished
    private String nodeId;
    private int logMessageCount = 0;
    
    public LogNetworkReader(String nodeId, InputStream inputStream, LogStatusInfo status) {
        this.nodeId = nodeId;
        this.dis = new DataInputStream(inputStream);
        this.status = status;
        this.isFinished = false;
    }

    @Override
    public LogMessage readNextLogMessage() {
        try {
            while(true) {
                byte flag = (byte) dis.read();
                if (flag == LogACKCode.ACK_FIN) {
                    logger.debug("received FIN");
                    logger.debug("logMessageCount_Reader={}", logMessageCount);
                    isFinished = true;
                    return null;
                } else if (flag == LogACKCode.ACK_STATUS) {
                    status.readAndAppend(dis);
                } else if (flag == LogACKCode.ACK_LOG_ENTRY) {
                    LogMessage log = LogMessage.read(dis);
                    logMessageCount++;
                    log.setNodeId(LogUtil.nodeIdToBytes(nodeId));
                    if (logMessageCount % 100000 == 0) {
                        logger.debug("processing the {}th log messages", logMessageCount);
                    }
                    return log;
                } else {
                    logger.info("receive {} -- ERROR", flag);
                }
            }
        } catch (IOException e) {
            //TODO: generate a dynamic error log message
            logger.error("IOException:", e);
            return null;
        }
    }
    public String getNodeId() {
        return this.nodeId;
    }
    
    public LogStatusInfo getStatus() {
        return this.status;
    }
    
    public boolean isFinished() {
        return this.isFinished;
    }
}
