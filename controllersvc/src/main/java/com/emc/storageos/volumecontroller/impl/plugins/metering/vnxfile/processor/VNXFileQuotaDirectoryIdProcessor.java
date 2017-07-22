/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Severity;
import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.nas.vnxfile.xmlapi.TreeQuota;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;

public class VNXFileQuotaDirectoryIdProcessor extends VNXFileProcessor {
    private final Logger _logger = LoggerFactory.getLogger(VNXFileQuotaDirectoryIdProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        _logger.info("processing quota dir id response" + resultObj);
        final PostMethod result = (PostMethod) resultObj;
        try {
            ResponsePacket responsePacket = (ResponsePacket) _unmarshaller.unmarshal(result
                    .getResponseBodyAsStream());
            if (null != responsePacket.getPacketFault()) {
                Status status = responsePacket.getPacketFault();
                processErrorStatus(status, keyMap);
            } else {
                boolean isQuotaDirFound = false;
                List<Object> quotaDirList = getQueryResponse(responsePacket);
                final String quotaDirName = (String) keyMap.get(VNXFileConstants.QUOTA_DIR_NAME);
                String quotaDirPath = "/" + quotaDirName;
                _logger.info("Quota dir name to match: {} Size of quotadir found {} ", quotaDirName, quotaDirList.size());
                Iterator<Object> quotaDirItr = quotaDirList.iterator();
                if (quotaDirItr.hasNext()) {
                    Status status = (Status) quotaDirItr.next();
                    if (status.getMaxSeverity() == Severity.OK) {
                        while (quotaDirItr.hasNext()) {
                            TreeQuota quotaDir = (TreeQuota) quotaDirItr.next();
                            _logger.debug("searching quota dir: {}", quotaDir.getTree());
                            if (quotaDir.getPath().equals(quotaDirPath)) {
                                String id = quotaDir.getTree();
                                _logger.info("Found matching quota dir: {}", id);
                                keyMap.put(VNXFileConstants.QUOTA_DIR_ID, id);
                                keyMap.put(VNXFileConstants.QUOTA_DIR_PATH, quotaDir.getPath());
                                keyMap.put(VNXFileConstants.CMD_RESULT, VNXFileConstants.CMD_SUCCESS);
                                isQuotaDirFound = true;
                                break;
                            }
                        }
                        if (!isQuotaDirFound) {
                            _logger.error("Error in getting the quota dir information.");
                        }
                    } else {
                        _logger.error("Error in getting the quota dir information.");
                    }
                }
            }

        } catch (final Exception ex) {
            _logger.error(
                    "Exception occurred while processing the quota dir response due to {}",
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
