/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc.merger;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MediaType;

import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.io.output.CountingOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.logsvc.LogConstants;
import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.storageos.systemservices.impl.logsvc.LogSvcPropertiesLoader;
import com.emc.storageos.systemservices.impl.logsvc.marshaller.Marshaller;
import com.emc.storageos.systemservices.impl.logsvc.marshaller.MarshallerFactory;
import com.emc.storageos.systemservices.impl.logsvc.stream.LogNetworkReader;
import com.emc.storageos.systemservices.impl.logsvc.util.LogUtil;
import com.emc.storageos.systemservices.impl.resource.util.ClusterNodesUtil;
import com.emc.storageos.systemservices.impl.resource.util.NodeInfo;
import com.emc.vipr.model.sys.logging.LogRequest;

public class LogNetworkStreamMerger extends AbstractLogStreamMerger {
    // Logger reference.
    private static final Logger logger = LoggerFactory.getLogger(LogNetworkStreamMerger.class);

    public long streamedBytes;

    private MediaType mediaType;
    private LogSvcPropertiesLoader propertiesLoader;
    private boolean isOverMaxByte = false;

    /**
     * Merges all logs on each node based on time stamp
     * 
     * @param req
     * @param mediaType
     */
    public LogNetworkStreamMerger(LogRequest req, MediaType mediaType,
            LogSvcPropertiesLoader propertiesLoader) {
        logger.trace("In LogNetworkStreamMerger's constructor");
        this.request = req;
        this.mediaType = mediaType;
        this.propertiesLoader = propertiesLoader;
        List<LogNetworkReader> readers = getLogNetworkStreams();
        int size = readers.size();
        logHeads = new LogMessage[size];
        this.logStreamList = new LogNetworkReader[size];
        int index = 0;
        for (LogNetworkReader reader : readers) {
            logStreamList[index] = reader;
            logHeads[index] = null;
            index++;
        }
    }

    public boolean getIsOverMaxByte() {
        return isOverMaxByte;
    }

    public void streamLogs(OutputStream outputStream) {
        logger.trace("Entering into LogNetworkStreamMerger.streamLogs()");
        CountingOutputStream cos = new CountingOutputStream(new BufferedOutputStream(
                outputStream, LogConstants.BUFFER_SIZE));
        Marshaller marshaller = MarshallerFactory.getLogMarshaller(mediaType, cos);
        int finalCount = 0;
        isOverMaxByte = false;
        try {
            marshaller.head();
            LogMessage msg = readNextMergedLogMessage();
            if (msg != null) {
                do {
                    // Maximum bytes to stream check
                    streamedBytes = cos.getByteCount();
                    if (request.getMaxBytes() > 0 && streamedBytes >= request.getMaxBytes()) {
                        logger.info("Streamed log size {}bytes reached maximum allowed limit {}bytes. So quitting.",
                                streamedBytes, request.getMaxBytes());
                        isOverMaxByte = true;
                        break;
                    }

                    List<LogMessage> currentLogBatch = new ArrayList<>();
                    LogMessage startLogOfNextBatch = readLogBatch(msg, currentLogBatch);
                    if (!LogUtil.permitNextLogBatch(request.getMaxCount(), finalCount,
                            currentLogBatch.size())) {
                        // discard this batch
                        break;
                    }

                    // current log batch has been accepted
                    for (String st : status.getStatus()) {
                        marshaller.marshall(st, msg);     // use previous log message's timeStamp as status's timeStamp
                    }
                    status.clear();

                    for (LogMessage logMessage : currentLogBatch) {
                        marshaller.marshall(logMessage);
                        finalCount++;
                    }

                    msg = startLogOfNextBatch;
                } while (msg != null);
            }
            logger.info("final count={}", finalCount);
            marshaller.tail();
            marshaller.flush();
        } catch (Exception e) {
            logger.error("Exception in streamLogs:", e);
        }
    }

    /**
     * Read a batch of logs that share the same timestamp and return the first subsequent
     * log that has a different timestamp.
     * 
     * @param startLog the starting log of the current log batch
     * @param logBatch the current log batch to fill in
     * @return the starting log of the next log batch
     * @throws IOException
     * @throws CompressorException
     */
    public LogMessage readLogBatch(LogMessage startLog, List<LogMessage> logBatch)
            throws IOException, CompressorException {
        long batchTime = startLog.getTime();
        logBatch.add(startLog);

        LogMessage msg;
        while ((msg = readNextMergedLogMessage()) != null) {
            if (msg.getTime() == batchTime) {
                logBatch.add(msg);
            } else {
                return msg;
            }
        }
        // msg == null, i.e., no more logs to read
        return null;
    }

    private List<LogNetworkReader> getLogNetworkStreams() {
        List<NodeInfo> nodeInfo;
        List<LogNetworkReader> logNetworkStreams = new ArrayList<>();
        // Getting all nodes information
        if (request.getNodeIds().isEmpty()) {
            logger.info("No nodes specified, getting all nodes");
            nodeInfo = ClusterNodesUtil.getClusterNodeInfo();
        } else {
            nodeInfo = getClusterNodesWithIds(request.getNodeIds());
        }
        if (nodeInfo.isEmpty()) {
            throw APIException.internalServerErrors.noNodeAvailableError("collect logs info");
        }
        List<String> failedNodes = ClusterNodesUtil.getUnavailableControllerNodes();
        if (!request.getNodeIds().isEmpty()) {
            failedNodes.retainAll(request.getNodeIds());
        }
        if (!failedNodes.isEmpty()) {
            logger.error("Cannot collect logs from unavailable nodes: {}", failedNodes.toString());
        }

        for (final NodeInfo node : nodeInfo) {
            LogRequest req;
            SysClientFactory.SysClient sysClient;
            String baseNodeURL = String.format(SysClientFactory.BASE_URL_FORMAT,
                    node.getIpAddress(), node.getPort());
            logger.debug("getting stream from node: " + baseNodeURL);
            logger.debug("connectTimeout=" + propertiesLoader.getNodeLogConnectionTimeout() * 1000);
            logger.debug("readTimeout=" + propertiesLoader.getNodeLogCollectorTimeout() * 1000);
            sysClient = SysClientFactory.getSysClient(URI.create(baseNodeURL),
                    propertiesLoader.getNodeLogCollectorTimeout() * 1000,
                    propertiesLoader.getNodeLogConnectionTimeout() * 1000);
            logger.debug("sysclient=" + sysClient + " uri=" + URI.create(baseNodeURL));
            try {
                req = request;
                req.setNodeIds(new ArrayList<String>() {
                    {
                        add(node.getId());
                    }
                });
                InputStream nodeResponseStream = sysClient.post(SysClientFactory
                        .URI_NODE_LOGS, InputStream.class, req);
                if (nodeResponseStream != null && nodeResponseStream.available() > 0) {
                    LogNetworkReader reader = new LogNetworkReader(node.getId(),node.getName(),nodeResponseStream,status);
                    logNetworkStreams.add(reader);
                }
            } catch (Exception e) {
                logger.error("Exception accessing node {}:", baseNodeURL, e);
                //socketTimeoutException wrapped in ClientHandlerException
                if (e.getCause() != null && e.getCause().getCause() instanceof SocketTimeoutException) {
                    throw InternalServerErrorException.internalServerErrors.logCollectionTimeout();
                }
            }
        }
        return logNetworkStreams;
    }

    private List<NodeInfo> getClusterNodesWithIds(List<String> nodeIds) {
        List<NodeInfo> matchingNodes = new ArrayList<>();
        List<NodeInfo> nodeInfoList = ClusterNodesUtil.getClusterNodeInfo();
        for (NodeInfo node : nodeInfoList) {
            if (nodeIds.contains(node.getId())) {
                matchingNodes.add(node);
            }
        }
        return matchingNodes;
    }
}
