/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.models.HostIpPortCreateParam;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.VNXeHostIpPort;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class HostIpPortRequests extends KHRequests<VNXeHostIpPort> {
    private static final Logger _logger = LoggerFactory.getLogger(FileSystemListRequest.class);
    private static final String URL = "/api/types/hostIPPort/instances";

    public HostIpPortRequests(KHClient client) {
        super(client);
        _url = URL;
    }

    /**
     * Get all HostIpPort
     * 
     * @return
     */
    public List<VNXeHostIpPort> get() {
        return getDataForObjects(VNXeHostIpPort.class);

    }

    /**
     * Get HostIpPort by ipaddress
     * 
     * @param address
     * @return
     */
    public VNXeHostIpPort getIpPortByIpAddress(String address) {
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.FILTER, VNXeConstants.IPADDRESS_FILTER + "\"" + address + "\"");
        setQueryParameters(queryParams);
        VNXeHostIpPort result = null;
        List<VNXeHostIpPort> ipPortList = getDataForObjects(VNXeHostIpPort.class);
        // it should just return 1
        if (ipPortList != null && !ipPortList.isEmpty()) {
            result = ipPortList.get(0);
        } else {
            _logger.info("No hostIpPort found using the address: " + address);
        }
        return result;
    }

    /**
     * Create HostIpPort
     * 
     * @param param
     * @return
     */
    public VNXeCommandResult createHostIpPort(HostIpPortCreateParam param) {
        _logger.info("Creating hostIpPort: " + param.getAddress());
        return postRequestSync(param);
    }

}
