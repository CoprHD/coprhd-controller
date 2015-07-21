/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.vnx.xmlapi.VNXControlStation;
import com.emc.storageos.vnx.xmlapi.VNXVdm;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;
import com.emc.nas.vnxfile.xmlapi.CelerraSystem;
import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.nas.vnxfile.xmlapi.Vdm;
import com.emc.nas.vnxfile.xmlapi.VdmState;

/**
 * VNXVDMProcessor is responsible to process the result received from XML API
 * Server after getting the VNX VDM Information. This is extracts the session
 * information from response packet and uses the session id in the subsequent
 * requests.
 */
public class VNXVDMProcessor extends VNXFileProcessor {

    /**
     * Logger instance.
     */
    private final Logger _logger = LoggerFactory
            .getLogger(VNXVDMProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj,
                              Map<String, Object> keyMap) throws BaseCollectionException {
        final PostMethod result = (PostMethod) resultObj;
        _logger.info("processing vnx info response" + resultObj);
        try {
            ResponsePacket responsePacket = (ResponsePacket) _unmarshaller
                    .unmarshal(result.getResponseBodyAsStream());
            if (null != responsePacket.getPacketFault()) {
                Status status = responsePacket.getPacketFault();
                processErrorStatus(status, keyMap);
            } else {
                List<Object> queryResponse = getQueryResponse(responsePacket);
                Iterator<Object> queryRespItr = queryResponse.iterator();
                List<VNXVdm> vdms = new ArrayList<VNXVdm>();
                while (queryRespItr.hasNext()) {
                    Object responseObj = queryRespItr.next();
                    if (responseObj instanceof Vdm) {
                        Vdm system = (Vdm) responseObj;
                        //if((system.getInterfaces().getLi().size() > 0) &&
                        //        system.getState() == VdmState.LOADED) {
                        if(system.getState() == VdmState.LOADED) {
                            VNXVdm vdm = new VNXVdm(
                                    system.getName(), system.getMover(), system.getVdm());
                            vdm.setInterfaces(system.getInterfaces().getLi());
                            vdms.add(vdm);
                        } else {
                            _logger.info("Ignoring VDM {} because not LOADED {}",
                                    system.getName(), system.getState().value());
                        }
                    }
                }
                keyMap.put(VNXFileConstants.VDM_INFO, vdms);

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
