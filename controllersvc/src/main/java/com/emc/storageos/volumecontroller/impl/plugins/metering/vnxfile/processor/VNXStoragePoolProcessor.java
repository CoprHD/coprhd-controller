/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.nas.vnxfile.xmlapi.StoragePool;
import com.emc.nas.vnxfile.xmlapi.SystemStoragePoolData;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.vnx.xmlapi.VNXStoragePool;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;

/**
 * VNXStoragePoolProcessor is responsible to process the result received from XML API
 * Server after getting the VNX Information. This is extracts the session
 * information from response packet and uses the session id in the subsequent
 * requests.
 */
public class VNXStoragePoolProcessor extends VNXFileProcessor {

    /**
     * Logger instance.
     */
    private final Logger _logger = LoggerFactory
            .getLogger(VNXStoragePoolProcessor.class);
    
    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        final PostMethod result = (PostMethod) resultObj;
        _logger.info("processing vnx storagepool response" + resultObj);
        try {
            ResponsePacket responsePacket = (ResponsePacket) _unmarshaller
                    .unmarshal(result.getResponseBodyAsStream());
            if (null != responsePacket.getPacketFault()) {
                Status status = responsePacket.getPacketFault();
                processErrorStatus(status, keyMap);
            } else {
                List<Object> queryResponse = getQueryResponse(responsePacket);
                Iterator<Object> queryRespItr = queryResponse.iterator();
                List<VNXStoragePool> storagePools = new ArrayList<VNXStoragePool>();
                while (queryRespItr.hasNext()) {
                    Object responseObj = queryRespItr.next();
                    _logger.info("{}",responseObj);
                    if (responseObj instanceof StoragePool) {
                        
                        VNXStoragePool vnxPool = new VNXStoragePool();
                        StoragePool storagePool = (StoragePool) responseObj;
                        
                        vnxPool.setName(storagePool.getName());
                        vnxPool.setPoolId(storagePool.getPool());
                        vnxPool.setSize(String.valueOf(storagePool.getSize()));
                        vnxPool.setAutoSize(String.valueOf(storagePool.getAutoSize()));
                        vnxPool.setVirtualProv(String.valueOf(storagePool.isVirtualProvisioning()));
                        vnxPool.setUsedSize(String.valueOf(storagePool.getUsedSize()));

                        SystemStoragePoolData data = storagePool.getSystemStoragePoolData();
                        if (null != data) {
                            String dynamic = String.valueOf(data.isDynamic());
                            vnxPool.setDynamic(dynamic);
                        }
                
                        _logger.info("VNX Pool Information {}",vnxPool.toString());
                
                        storagePools.add(vnxPool);
                    }
                }
                _logger.info("Number of StorgePools found  : {}", storagePools.size());
                keyMap.put(VNXFileConstants.STORAGEPOOLS, storagePools);
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

    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {

    }

}
