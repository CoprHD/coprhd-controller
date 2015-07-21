/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.compress.compressors.CompressorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.systemservices.impl.logsvc.merger.LogStreamMerger;
import com.emc.vipr.model.sys.logging.LogRequest;

public class LogNetworkWriter {
    private LogStreamMerger merger;
    
    // Logger reference.
    private static final Logger logger = LoggerFactory.getLogger(LogNetworkWriter.class);
    
    public LogNetworkWriter(LogRequest req,LogSvcPropertiesLoader propertiesLoader){
        this.merger = new LogStreamMerger(req, propertiesLoader);
    }
    
    public void write(OutputStream outputStream) throws IOException {
        logger.trace("LogNetworkWriter.write()");
        LogMessage log;
        int testLogCount = 0;
        try (BufferedOutputStream bos = new BufferedOutputStream(outputStream,
                LogConstants.BUFFER_SIZE);
                DataOutputStream dos = new DataOutputStream(bos)) {
            while(true) {
                if(!merger.getStatus().isEmpty()) {
                    LogStatusInfo status = merger.getStatus();
                    status.write(dos);
                }
                if((log = merger.readNextMergedLogMessage()) != null) {
                    dos.write(LogACKCode.ACK_LOG_ENTRY);
                    log.write(dos);
                    testLogCount++;
                } else {
                    break;
                }
            }
            logger.debug("testLogCount={}", testLogCount);
            dos.write(LogACKCode.ACK_FIN);
            dos.flush();
        } catch (CompressorException e) {
            logger.error("Exception in write:", e);
        }
    }
}
