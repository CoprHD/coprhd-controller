/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.ModifyHostLUNsParam;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.VNXeHost;

public class HostRequest extends KHRequests<VNXeHost> {
    private static final Logger _logger = LoggerFactory.getLogger(HostRequest.class);
    private static final String URL = "/api/instances/host/";
    private static final String FIELDS = "name,fcHostInitiators,iscsiHostInitiators,hostLUNs,type";
    private static final String URL_MODIFY ="/action/modifyHostLUNs";
    
    public HostRequest(KHClient client, String id) {
        super(client);
        _url = URL + id;
        _fields = FIELDS;
    }

    public VNXeHost get() {
        return getDataForOneObject(VNXeHost.class);

    }
    
    public VNXeCommandResult modifyHostLun( ModifyHostLUNsParam param) {
        _url += URL_MODIFY;
        return postRequestSync(param);
    }

    public VNXeCommandResult deleteHost() throws VNXeException {
        _logger.info("deleting host");
        VNXeHost host = get();
        if (host == null) {
            _logger.info("Could not find host");
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
