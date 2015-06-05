/**
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

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.nas.vnxfile.xmlapi.Checkpoint;
import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Severity;
import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;

public class VNXSnapshotIdProcessor extends VNXFileProcessor {
    private final Logger _logger = LoggerFactory.getLogger(VNXSnapshotIdProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj,
                              Map<String, Object> keyMap) throws BaseCollectionException {
        _logger.info("processing snapshot id response" + resultObj);
        final PostMethod result = (PostMethod) resultObj;
        try {
            ResponsePacket responsePacket = (ResponsePacket) _unmarshaller.unmarshal(result
                    .getResponseBodyAsStream());
            if (null != responsePacket.getPacketFault()) {
                Status status = responsePacket.getPacketFault();
                processErrorStatus(status, keyMap);
            } else {
                boolean isSnapshotFound = false;
                List<Object> snapshotList = getQueryResponse(responsePacket);
                final String snapName = (String) keyMap.get(VNXFileConstants.SNAPSHOT_NAME);
                _logger.info( "Snapshot name to match: {} Size of snaps found {} ", snapName, snapshotList.size());
                Iterator<Object> snapshotItr = snapshotList.iterator();
                if (snapshotItr.hasNext()) {
                    Status status = (Status) snapshotItr.next();
                    if (status.getMaxSeverity() == Severity.OK) {
                        while (snapshotItr.hasNext()) {
                            Checkpoint point = (Checkpoint) snapshotItr.next();
                            _logger.debug("searching snapshot: {}", point.getName());
                            if (point.getName().equalsIgnoreCase(snapName)) {
                                String id = point.getCheckpoint();
                                _logger.info("Found matching snapshot: {}", id);
                                keyMap.put(VNXFileConstants.SNAPSHOT_ID, id);
                                keyMap.put(VNXFileConstants.CMD_RESULT, VNXFileConstants.CMD_SUCCESS);
                                isSnapshotFound = true;
                                break;
                            }
                        }
                        if(!isSnapshotFound)
                        {
                            _logger.error("Error in getting the snapshot information.");
                        }
                    } else {
                        _logger.error("Error in getting the snapshot information.");
                    }
                }
            }

        } catch (final Exception ex) {
            _logger.error(
                    "Exception occurred while processing the snapshot response due to {}",
                    ex.getMessage());
            keyMap.put(VNXFileConstants.FAULT_DESC, ex.getMessage());
            keyMap.put(VNXFileConstants.CMD_RESULT, VNXFileConstants.CMD_FAILURE);
        } finally {
            result.releaseConnection();
        }

    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {

    }
}
