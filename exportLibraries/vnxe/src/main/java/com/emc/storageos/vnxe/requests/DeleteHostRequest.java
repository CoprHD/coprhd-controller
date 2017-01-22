/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.VNXeHost;

public class DeleteHostRequest extends KHRequests<VNXeHost> {
    private static final Logger logger = LoggerFactory.getLogger(DeleteHostRequest.class);
    private static final String URL = "/api/instances/host/";
    private String hostId = null;

    public DeleteHostRequest(KHClient client, String id) {
        super(client);
        hostId = id;
        _url = URL + id;
    }

    public VNXeCommandResult deleteHost() throws VNXeException {
        logger.info("deleting host: {}", hostId);
        HostRequest hostRequest = new HostRequest(_client, hostId);
        VNXeHost host = hostRequest.get();
        if (host == null) {
            logger.info("Could not find host: ", hostId);
            return null;
        }
        return deleteHostInitiatorSync();
    }

    private VNXeCommandResult deleteHostInitiatorSync() {
        deleteRequest(null);
        VNXeCommandResult result = new VNXeCommandResult();
        result.setSuccess(true);
        return result;
    }
}
