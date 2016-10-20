/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.models.HostInitiatorCreateParam;
import com.emc.storageos.vnxe.models.HostInitiatorModifyParam;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.VNXeHostInitiator;

public class HostInitiatorRequest extends KHRequests<VNXeHostInitiator> {

    private static final Logger _logger = LoggerFactory.getLogger(HostInitiatorRequest.class);
    private static final String URL = "/api/instances/hostInitiator/";
    private static final String URL_ALL = "/api/types/hostInitiator/instances";
    private static final String MODIFY = "/action/modify";
    private static final String FIELDS = "parentHost,nodeWWN,portWWN,type";

    public HostInitiatorRequest(KHClient client) {
        super(client);
        _fields = FIELDS;

    }

    public VNXeHostInitiator get(String id) {
        _url = URL + id;
        return getDataForOneObject(VNXeHostInitiator.class);

    }

    public VNXeHostInitiator getByIQNorWWN(String initiatorId) {
        _url = URL_ALL;
        String filter = null;
        if (_client.isUnity()) {
            filter = VNXeConstants.INITIATORID_FILTER + "\"" + initiatorId + "\"";
        } else {
            filter = VNXeConstants.INITIATORID_FILTER + initiatorId;
        }
        setFilter(filter);
        VNXeHostInitiator result = null;
        List<VNXeHostInitiator> initList = getDataForObjects(VNXeHostInitiator.class);
        // it should just return 1
        if (initList != null && !initList.isEmpty()) {
            result = initList.get(0);
        } else {
            _logger.info("No HostInitiator found using iqn: {}", initiatorId);
        }
        return result;
    }

    public VNXeCommandResult createHostInitiator(HostInitiatorCreateParam param) {
        _url = URL_ALL;
        return postRequestSync(param);
    }

    /**
     * Modify the host initiator
     * 
     * @param param The parameters to modify the host initiator. 
     * @param id The host initiator Id
     * @return VNXeCommandResult, indicating if the command is successful
     */
    public VNXeCommandResult modifyHostInitiator(HostInitiatorModifyParam param, String id) {
        _url = URL + id + MODIFY;
        return postRequestSync(param);

    }
}
