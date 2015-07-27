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
import com.emc.nas.vnxfile.xmlapi.TreeQuota;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;

public class VNXFSQuotaDirsProcessor extends VNXFileProcessor {
    private final Logger _logger = LoggerFactory.getLogger(VNXFSQuotaDirsProcessor.class);

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
                List<TreeQuota> quotaDirs = new ArrayList<TreeQuota>();
                final String fsId = (String) keyMap.get(VNXFileConstants.FILESYSTEM_ID);
                _logger.info( "Quota dirs for parent file systems to match: {} Size of quotadir found {} ", fsId, quotaDirList.size());
                Iterator<Object> quotaDirItr = quotaDirList.iterator();
                if (quotaDirItr.hasNext()) {
                    Status status = (Status) quotaDirItr.next();
                    if (status.getMaxSeverity() == Severity.OK) {
                        while (quotaDirItr.hasNext()) {
                            TreeQuota quotaDir = (TreeQuota) quotaDirItr.next();
                            _logger.debug("searching quota dir: {}", quotaDir.getTree());
                            if (quotaDir.getFileSystem().equalsIgnoreCase(fsId)) {
                                String id = quotaDir.getTree();
                                quotaDirs.add(quotaDir);
                                _logger.info("Found matching quota dir: {}", id);
                                
                                isQuotaDirFound = true;
                            }
                        }
                        if(isQuotaDirFound){
                        	_logger.info("Number of Quota dirs found for FS : {} are : {}", fsId, quotaDirs.size()) ;
                            keyMap.put(VNXFileConstants.QUOTA_DIR_LIST, quotaDirs);
                            keyMap.put(VNXFileConstants.CMD_RESULT, VNXFileConstants.CMD_SUCCESS);
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
