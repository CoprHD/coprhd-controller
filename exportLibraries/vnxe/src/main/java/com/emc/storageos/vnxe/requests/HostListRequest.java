/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.models.HostCreateParam;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.VNXeHost;


public class HostListRequest extends KHRequests<VNXeHost>{
	private static final Logger _logger = LoggerFactory.getLogger(HostListRequest.class);
	private static final String URL = "/api/types/host/instances";

    public HostListRequest(KHClient client) {
        super(client);
        _url = URL;
    }

    /**
     * Get all hosts in the array
     * @return
     */
    public List<VNXeHost> get(){
        return getDataForObjects(VNXeHost.class);

    }
    
    /**
     * Create a host
     * @param createParam
     * @return
     */
    public VNXeCommandResult createHost(HostCreateParam createParam) {
    	_logger.info("Creating host:" + createParam.getName());
    	return postRequestSync(createParam);
    	
    }

}
