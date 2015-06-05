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

import com.emc.nas.vnxfile.xmlapi.Mover;
import com.emc.nas.vnxfile.xmlapi.MoverRole;
import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Severity;
import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.plugins.metering.vnxfile.VNXFilePluginException;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class VNXDataMoverNameProcessor extends VNXFileProcessor {
    private final Logger _logger = LoggerFactory.getLogger(VNXDataMoverNameProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj,
                              Map<String, Object> keyMap) throws BaseCollectionException {
        final PostMethod result = (PostMethod) resultObj;
        try {
            ResponsePacket responsePacket = (ResponsePacket) _unmarshaller.unmarshal(result
                    .getResponseBodyAsStream());
            // Extract session information from the response header.
            Header[] headers = result
                    .getResponseHeaders(VNXFileConstants.CELERRA_SESSION);
            if (null != headers && headers.length > 0) {
                keyMap.put(VNXFileConstants.CELERRA_SESSION,
                        headers[0].getValue());
                _logger.info("Received celerra session info from the Server.");
            }
            if (null != responsePacket.getPacketFault()) {
                Status status = responsePacket.getPacketFault();
                processErrorStatus(status, keyMap);
            } else {
                List<Object> dataMoverList = getQueryResponse(responsePacket);
                processDataMoverList(dataMoverList, keyMap);
                keyMap.put(VNXFileConstants.CMD_RESULT, VNXFileConstants.CMD_SUCCESS);
            }
        } catch (final Exception ex) {
            _logger.error(
                    "Exception occurred while processing the vnx data mover response due to {}",
                    ex.getMessage());
            keyMap.put(VNXFileConstants.FAULT_DESC, ex.getMessage());
            keyMap.put(VNXFileConstants.CMD_RESULT, VNXFileConstants.CMD_FAILURE);
        } finally {
            result.releaseConnection();
        }

    }

    private void processDataMoverList( List<Object> dmList, Map<String, Object> keyMap ) throws VNXFilePluginException {

        Iterator<Object> iterator = dmList.iterator();
        String dmId = (String)keyMap.get(VNXFileConstants.DATAMOVER_ID);

        if (iterator.hasNext()) {
            Status status = (Status) iterator.next();

            if (status.getMaxSeverity() == Severity.OK || status.getMaxSeverity() == Severity.WARNING) {
                while (iterator.hasNext()) {
                    Mover dm = (Mover) iterator.next();
                    if (dm.getRole() == MoverRole.STANDBY) {
                        continue;
                    }

                    if ( dmId.equals( dm.getMover() ) ) {
                        keyMap.put( VNXFileConstants.DATAMOVER_NAME, dm.getName() );
                        break;
                    }
                }
            } else {
                throw new VNXFilePluginException(
                        "Fault response received from XMLAPI Server.",
                        VNXFilePluginException.ERRORCODE_INVALID_RESPONSE);
            }
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {

    }

}

