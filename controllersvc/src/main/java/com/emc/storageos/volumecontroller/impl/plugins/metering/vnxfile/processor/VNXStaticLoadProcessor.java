package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.nas.vnxfile.xmlapi.Mount;
import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Severity;
import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;

public class VNXStaticLoadProcessor extends VNXFileProcessor {
    private final Logger _logger = LoggerFactory
            .getLogger(VNXFileSystemUsageProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap) throws BaseCollectionException {
        // TODO Auto-generated method stub
        final PostMethod result = (PostMethod) resultObj;
        try {
            ResponsePacket responsePacket = (ResponsePacket) _unmarshaller.unmarshal(result
                    .getResponseBodyAsStream());
            // Extract session information from the response header.
            Header[] headers = result.getResponseHeaders(VNXFileConstants.CELERRA_SESSION);
            if (null != headers && headers.length > 0) {
                keyMap.put(VNXFileConstants.CELERRA_SESSION, headers[0].getValue());
                _logger.info("Received celerra session info from the Server.");
            }

            Status status = null;
            if (null != responsePacket.getPacketFault()) {
                status = responsePacket.getPacketFault();
                processErrorStatus(status, keyMap);
            } else {
                List<Object> objeList = getQueryResponse(responsePacket);
                Iterator<Object> iterator = objeList.iterator();

                Map<String, List<String>> fsMoverList = new HashMap<String, List<String>>();
                Map<String, List<String>> fsVirMoverList = new HashMap<String, List<String>>();

                List<String> fsList = null;
                if (iterator.hasNext()) {
                    status = (Status) iterator.next();
                    if (status.getMaxSeverity() == Severity.OK) {
                        // process mover list from response
                        while (iterator.hasNext()) {
                            Mount mount = (Mount) iterator.next();
                            if (mount.isMoverIdIsVdm() == true) { // get vMover -> fs List
                                fsList = fsVirMoverList.get(mount.getMover());
                                if (null == fsList) {
                                    fsList = new ArrayList<String>();
                                }
                                fsList.add(mount.getFileSystem());
                                fsVirMoverList.put(mount.getMover(), fsList);

                            } else {// get Mover->fs list
                                fsList = fsMoverList.get(mount.getMover());
                                if (null == fsList) {
                                    fsList = new ArrayList<String>();
                                }
                                fsList.add(mount.getFileSystem());
                                fsMoverList.put(mount.getMover(), fsList);
                            }
                        }
                        // process info

                    } else {
                        _logger.error("Error in getting the Mover information.");
                    }
                }

                processStaticLoadMetrics();

                keyMap.put(VNXFileConstants.CMD_RESULT, VNXFileConstants.CMD_SUCCESS);
            }
        } catch (final Exception ex) {
            _logger.error(
                    "Exception occurred while processing the vnx fileShare response due to {}",
                    ex.getMessage());
            keyMap.put(com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants.FAULT_DESC, ex.getMessage());
            keyMap.put(com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants.CMD_RESULT,
                    com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants.CMD_FAILURE);
        } finally {
            result.releaseConnection();
        }

    }

    private void processStaticLoadMetrics() {
        return;
    }

}
