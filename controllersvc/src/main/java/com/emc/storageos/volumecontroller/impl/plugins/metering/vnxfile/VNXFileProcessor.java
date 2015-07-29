/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.bind.Unmarshaller;

import com.emc.nas.vnxfile.xmlapi.Response;
import com.emc.nas.vnxfile.xmlapi.ResponseEx;
import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.nas.vnxfile.xmlapi.Status.Problem;

import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processor responsible to do VNXFile related activities.
 */
public abstract class VNXFileProcessor extends Processor {

    private final Logger _logger = LoggerFactory.getLogger(VNXFileProcessor.class);

    /**
     * Unmarshaller instance.
     */
    protected Unmarshaller _unmarshaller = null;

    /**
     * Fetches the QueryStats response from ResponsePacket.
     * 
     * @param responsePacket
     * @return
     */
    protected List<Object> getQueryStatsResponse(ResponsePacket responsePacket) {
        List<Object> responseList = responsePacket.getResponseOrResponseEx();
        Iterator<Object> responseListItr = responseList.iterator();
        List<Object> queryResponse = new ArrayList<Object>();
        while (responseListItr.hasNext()) {
            Response response = (Response) responseListItr.next();
            queryResponse.addAll(response.getQueryStatsResponseChoice());
        }
        return queryResponse;
    }

    /**
     * Fetches the Query response from ResponsePacket.
     * 
     * @param responsePacket
     * @return
     */
    protected List<Object> getQueryResponse(ResponsePacket responsePacket) {
        List<Object> responseList = responsePacket.getResponseOrResponseEx();
        Iterator<Object> responseListItr = responseList.iterator();
        List<Object> queryResponse = new ArrayList<Object>();
        while (responseListItr.hasNext()) {
            Response response = (Response) responseListItr.next();
            queryResponse.addAll(response.getQueryResponseChoice());
        }
        return queryResponse;
    }

    /**
     * Fetches the Query response (ResponseEx in this case) from the ResponsePacket.
     * 
     * @param responsePacket
     * @return
     */
    protected List<Object> getQueryResponseEx(ResponsePacket responsePacket) {
        List<Object> responseList = responsePacket.getResponseOrResponseEx();
        Iterator<Object> responseListItr = responseList.iterator();
        List<Object> queryResponse = new ArrayList<Object>();
        while (responseListItr.hasNext()) {
            ResponseEx response = (ResponseEx) responseListItr.next();
            queryResponse.addAll(response.getQueryResponseChoiceEx());
        }
        return queryResponse;
    }

    protected List<Object> getTaskResponse(ResponsePacket responsePacket) {
        List<Object> responseList = responsePacket.getResponseOrResponseEx();
        Iterator<Object> responseListItr = responseList.iterator();
        List<Object> taskResponse = new ArrayList<Object>();

        while (responseListItr.hasNext()) {
            Response response = (Response) responseListItr.next();
            if (null != response.getFault()) {
                Status status = response.getFault();
                List<Problem> problems = status.getProblem();
                Iterator<Problem> problemsItr = problems.iterator();
                while (problemsItr.hasNext()) {
                    Problem prob = problemsItr.next();
                    _logger.error("Respone fault: {}  cause: {}", prob.getDescription(), prob.getDiagnostics());
                }
            }

            taskResponse.add(response.getTaskResponse());
        }

        return taskResponse;
    }

    /**
     * Retrieve the description and diagnostics information from the error status.
     * 
     * @param status status of the response returned by the XML API Server
     * @param keyMap map used to returned values to upper layers.
     */
    protected void processErrorStatus(Status status, Map<String, Object> keyMap) {
        List<Problem> problems = status.getProblem();

        // Only retrieve the first problem.
        if (null != problems && !problems.isEmpty()) {
            Problem problem = problems.get(0);
            String desc = problem.getDescription();
            String message = problem.getMessage();

            String diags = problem.getDiagnostics();

            keyMap.put(VNXFileConstants.CMD_RESULT, VNXFileConstants.CMD_FAILURE);

            // These fields are optional in the response packet.
            if (null != desc) {
                keyMap.put(VNXFileConstants.FAULT_DESC, desc);
            }

            if (null != diags) {
                keyMap.put(VNXFileConstants.FAULT_DIAG, diags);
            }

            if (null != message) {
                keyMap.put(VNXFileConstants.FAULT_MSG, message);
            }

            if ((desc != null) && (!desc.isEmpty())) {
                _logger.error("Fault response received due to {} possible cause {}", desc, diags);
            } else {
                _logger.error("Fault response received due to {} possible cause {}", message, diags);
            }
        }
    }

    /**
     * Utility to get the FileshareNativeId from NativeGuid.
     * 
     * @param nativeGuid
     * @return
     */
    protected String fetchNativeId(String nativeGuid) {
        String[] token = nativeGuid.split(VNXFileConstants.PLUS_SEPERATOR);
        return token[token.length - 1];
    }

    /**
     * Set unmarshaller instance.
     * 
     * @param unmarshaller
     */
    public void setUnmarshaller(Unmarshaller unmarshaller) {
        _unmarshaller = unmarshaller;
    }

}
