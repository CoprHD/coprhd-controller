/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import com.emc.nas.vnxfile.xmlapi.CifsServer;
import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.vnx.xmlapi.VNXCifsServer;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * VNXCifsConfigProcessor is responsible for processing the result received
 * from the XML API Server for a CifsConfig query.
 */
public class VNXCifsConfigProcessor extends VNXFileProcessor {
    private final Logger _logger = LoggerFactory.getLogger(VNXCifsConfigProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        final PostMethod result = (PostMethod) resultObj;
        _logger.info("processing vnx cifs config response" + resultObj);
        try {
            ResponsePacket responsePacket = (ResponsePacket) _unmarshaller
                    .unmarshal(result.getResponseBodyAsStream());
            // Extract session information from the response header.
            Header[] headers = result.getResponseHeaders(VNXFileConstants.CELERRA_SESSION);
            if (null != headers && headers.length > 0) {
                keyMap.put(VNXFileConstants.CELERRA_SESSION,
                        headers[0].getValue());
                _logger.info("Received celerra session info from the Server.");
            }

            if (null != responsePacket.getPacketFault()) {
                Status status = responsePacket.getPacketFault();
                processErrorStatus(status, keyMap);
            } else {
                List<Object> queryResponse = getQueryResponse(responsePacket);
                Iterator<Object> queryRespItr = queryResponse.iterator();
                Boolean enabled = false;
                List<VNXCifsServer> cifsServers = new ArrayList<>();

                while (queryRespItr.hasNext()) {
                    Object responseObj = queryRespItr.next();
                    if (responseObj instanceof CifsServer) {

                        CifsServer config = (CifsServer) responseObj;
                        _logger.info("CIFS Interfaces: {}, for VDM: {}", config.getInterfaces(), config.getMover());
                        
                        enabled = true;
                        // Get the domain of cifs server!!!
                        String domain = new String();
                        if(config.getNT40ServerData() != null )
                        {
                        	domain = config.getNT40ServerData().getDomain();
                        }else if (config.getW2KServerData() != null){
                        	domain = config.getW2KServerData().getDomain();
                        }else if(config.getStandaloneServerData() != null){
                        	domain = config.getStandaloneServerData().getWorkgroup();
                        }
                        if(!domain.isEmpty()){
                        	_logger.info("domain cofigured for cifs : {}", domain);
                        }
                        
                        VNXCifsServer server = new VNXCifsServer(config.getName(), config.getMover(),
                                config.getType().toString(), config.isMoverIdIsVdm(), config.getInterfaces(), domain);
                        cifsServers.add(server);
                        _logger.info("Add : {}", server.toString());
                    }
                }

                keyMap.put(VNXFileConstants.CIFS_SUPPORTED, enabled);
                keyMap.put(VNXFileConstants.CIFS_SERVERS, cifsServers);
            }
        } catch (final Exception ex) {
            _logger.error("Exception occurred while processing the vnx cifs config response due to ", ex);

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
