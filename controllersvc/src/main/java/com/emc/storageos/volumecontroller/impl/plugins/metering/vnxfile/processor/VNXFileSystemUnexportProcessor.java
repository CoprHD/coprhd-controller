/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Severity;
import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.nas.vnxfile.xmlapi.TaskResponse;

/**
 * VNXFileSystemUnexportProcessor is responsible for unexport file systems
 * on the VNX.
 */
public class VNXFileSystemUnexportProcessor extends VNXFileProcessor {

    private final Logger _logger = LoggerFactory.getLogger(VNXFileSystemUnexportProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        _logger.info("Processing VNX File System Unexport response: {}", resultObj);
        final PostMethod result = (PostMethod) resultObj;

        try {
            ResponsePacket responsePacket = (ResponsePacket) _unmarshaller
                    .unmarshal(result.getResponseBodyAsStream());

            Status status = null;
            if (null != responsePacket.getPacketFault()) {
                status = responsePacket.getPacketFault();
                processErrorStatus(status, keyMap);
            } else {
                List<Object> queryResponse = getTaskResponse(responsePacket);
                Iterator<Object> queryRespItr = queryResponse.iterator();
                while (queryRespItr.hasNext()) {
                    Object responseObj = queryRespItr.next();
                    if (responseObj instanceof TaskResponse) {
                        TaskResponse taskResp = (TaskResponse) responseObj;
                        status = taskResp.getStatus();
                        _logger.info("FileSystem Unexport task response status: {}", status.getMaxSeverity().name());

                        if (status.getMaxSeverity() == Severity.OK) {
                            keyMap.put(VNXFileConstants.CMD_RESULT, VNXFileConstants.CMD_SUCCESS);
                        } else {
                            processErrorStatus(status, keyMap);
                        }

                        break;
                    } else {
                        _logger.info("Response not TaskResponse: {}", responseObj.getClass().getName());
                    }
                }
                // Extract session information from the response header.
                Header[] headers = result
                        .getResponseHeaders(VNXFileConstants.CELERRA_SESSION);
                if (null != headers && headers.length > 0) {
                    keyMap.put(VNXFileConstants.CELERRA_SESSION,
                            headers[0].getValue());
                    _logger.info("Received celerra session information from the Server.");
                }
            }
        } catch (final Exception ex) {
            _logger.error(
                    "Exception occurred while processing the vnx create file sys response due to ",
                    ex);
            keyMap.put(VNXFileConstants.FAULT_DESC, ex.getMessage());
            keyMap.put(VNXFileConstants.CMD_RESULT, VNXFileConstants.CMD_FAILURE);
        } finally {
            result.releaseConnection();
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs) throws BaseCollectionException {
        // TODO Is this method needed? Not used in other processors.
    }
}
