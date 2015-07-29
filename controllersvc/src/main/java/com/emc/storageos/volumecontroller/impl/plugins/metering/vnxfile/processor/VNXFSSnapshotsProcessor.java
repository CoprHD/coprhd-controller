/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import java.util.ArrayList;
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

public class VNXFSSnapshotsProcessor extends VNXFileProcessor {
    private final Logger _logger = LoggerFactory.getLogger(VNXSnapshotIdProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        _logger.info("processing snapshot id response" + resultObj);
        final PostMethod result = (PostMethod) resultObj;
        try {
            ResponsePacket responsePacket = (ResponsePacket) _unmarshaller.unmarshal(result
                    .getResponseBodyAsStream());
            Status status = null;
            if (null != responsePacket.getPacketFault()) {
                status = responsePacket.getPacketFault();
                processErrorStatus(status, keyMap);
            } else {

                List<Object> snapshotList = getQueryResponse(responsePacket);
                List<Checkpoint> snapsList = new ArrayList<Checkpoint>();

                final String fsId = (String) keyMap.get(VNXFileConstants.FILESYSTEM_ID);
                _logger.info("Looking for all snapshots of filesystem {}", fsId);

                Iterator<Object> snapshotItr = snapshotList.iterator();
                if (snapshotItr.hasNext()) {
                    status = (Status) snapshotItr.next();
                    if (status.getMaxSeverity() == Severity.OK) {
                        while (snapshotItr.hasNext()) {
                            Checkpoint point = (Checkpoint) snapshotItr.next();
                            _logger.debug("searching snapshot: {} - {}", point.getName(), point.getCheckpointOf());
                            if (point.getCheckpointOf().equalsIgnoreCase(fsId)) {
                                String id = point.getCheckpoint();
                                String localFSId = point.getCheckpointOf();
                                _logger.info("Found matching snapshot: {} and checkpoint of {}", id, localFSId);
                                snapsList.add(point);
                            }
                        }
                        _logger.info("Number of Snapshots found for FS : {} are : {}", fsId, snapsList.size());
                        keyMap.put(VNXFileConstants.SNAPSHOTS_LIST, snapsList);
                        keyMap.put(VNXFileConstants.CMD_RESULT, VNXFileConstants.CMD_SUCCESS);
                    } else {
                        _logger.error("Error in getting the snapshot information.");
                        processErrorStatus(status, keyMap);
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
