/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.VNXeHostInitiator;

public class DeleteHostInitiatorRequest extends KHRequests<VNXeHostInitiator> {

    private static final Logger logger = LoggerFactory.getLogger(DeleteHostInitiatorRequest.class);
    private static final String URL = "/api/instances/hostInitiator/";

    public DeleteHostInitiatorRequest(KHClient client) {
        super(client);
    }

    public VNXeCommandResult deleteInitiator(String initiatorId) throws VNXeException {
        logger.info("deleting initiator: {}", initiatorId);
        HostInitiatorRequest hostInitiatorRequest = new HostInitiatorRequest(_client);
        VNXeHostInitiator initiator = hostInitiatorRequest.getByIQNorWWN(initiatorId);
        if (initiator == null) {
            logger.info("Could not find initiator: {}", initiatorId);
            return null;
        }
        return deleteHostInitiatorSync(initiator.getId());

    }

    private VNXeCommandResult deleteHostInitiatorSync(String id) {
        _url = URL + id;
        deleteRequest(null);
        VNXeCommandResult result = new VNXeCommandResult();
        result.setSuccess(true);
        return result;
    }
}
