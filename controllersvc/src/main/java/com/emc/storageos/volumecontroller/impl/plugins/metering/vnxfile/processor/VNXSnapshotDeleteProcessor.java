/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Severity;
import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.nas.vnxfile.xmlapi.TaskResponse;

public class VNXSnapshotDeleteProcessor extends VNXFileProcessor {

    private final Logger _logger = LoggerFactory.getLogger(VNXSnapshotDeleteProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        _logger.info("Processing VNX Snapshot Delete response: {}", resultObj);
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
                        TaskResponse system = (TaskResponse) responseObj;
                        status = system.getStatus();
                        _logger.info("Snapshot Delete task response status: {}", status.getMaxSeverity().name());

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
                    _logger.info("Recieved celerra session information from the Server.");
                }
            }
        } catch (final Exception ex) {
            _logger.error(
                    "Exception occurred while processing the vnx delete file sys response due to ",
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
