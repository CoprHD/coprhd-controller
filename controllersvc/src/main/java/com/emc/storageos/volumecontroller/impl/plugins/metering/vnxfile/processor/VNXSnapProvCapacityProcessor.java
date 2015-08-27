package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.nas.vnxfile.xmlapi.Checkpoint;
import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;

public class VNXSnapProvCapacityProcessor extends VNXFileProcessor {
    private final Logger _logger = LoggerFactory
            .getLogger(VNXSnapProvCapacityProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap) throws BaseCollectionException {
        // TODO Auto-generated method stub
        final PostMethod result = (PostMethod) resultObj;

        _logger.info("processing filesystems of a mover response" + resultObj);
        try {
            ResponsePacket responsePacket = (ResponsePacket) _unmarshaller
                    .unmarshal(result.getResponseBodyAsStream());
            if (null != responsePacket.getPacketFault()) {
                Status status = responsePacket.getPacketFault();
                processErrorStatus(status, keyMap);
            } else {
                String moverId = (String) keyMap.get(VNXFileConstants.MOVER_ID);

                Map<String, Map<String, Long>> fsSnapshotCapacityMap = new HashMap<String, Map<String, Long>>();
                int snapCount = 0;
                List<Object> queryResponse = getQueryResponse(responsePacket);
                Iterator<Object> queryRespItr = queryResponse.iterator();
                Map<String, Long> snapshotCapacityMap = null;
                while (queryRespItr.hasNext()) {
                    Object responseObj = queryRespItr.next();
                    if (responseObj instanceof Checkpoint) {
                        Checkpoint checkpoint = (Checkpoint) responseObj;
                        snapshotCapacityMap = fsSnapshotCapacityMap.get(checkpoint.getCheckpointOf());
                        if (snapshotCapacityMap == null) {
                            snapshotCapacityMap = new HashMap<String, Long>();
                        }
                        snapshotCapacityMap.put(checkpoint.getCheckpoint(), checkpoint.getFileSystemSize());
                        snapCount++;
                        fsSnapshotCapacityMap.put(checkpoint.getCheckpointOf(), snapshotCapacityMap);
                    }
                }
                _logger.info("Number of Snapshot found {} on Data mover : {}", String.valueOf(snapCount), moverId);
                keyMap.put(VNXFileConstants.SNAP_CAPACITY_MAP, fsSnapshotCapacityMap);
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
                    "Exception occurred while processing the vnx info response due to ",
                    ex);
        } finally {
            result.releaseConnection();
        }
        return;
    }
}
