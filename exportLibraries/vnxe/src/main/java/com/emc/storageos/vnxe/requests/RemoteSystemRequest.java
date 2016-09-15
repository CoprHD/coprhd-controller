/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.models.RemoteSystem;
import com.emc.storageos.vnxe.models.RemoteSystemParam;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class RemoteSystemRequest extends KHRequests<RemoteSystem> {
    private static final Logger _logger = LoggerFactory.getLogger(RemoteSystemRequest.class);
    private static final String URL_INSTANCE = "/api/instances/remoteSystem/";
    private static final String URL = "/api/types/remoteSystem/instances";
    private static final String FIELDS = "name,model,serialNumber,managementAddress,connectionType";
    private static final String ACTION_VERIFY = "/action/verify";

    public RemoteSystemRequest(KHClient client) {
        super(client);
        _fields = FIELDS;

    }

    /**
     * Get specific remote system
     *
     * @return RemoteSystem
     */

    public RemoteSystem get(String id) {
        _url = URL_INSTANCE + id;
        return getDataForOneObject(RemoteSystem.class);

    }

    /**
     * Get all remote systems in the array
     *
     * @return List of RemoteSystem
     */

    public List<RemoteSystem> get() {
        _queryParams = null;
        return getDataForObjects(RemoteSystem.class);
    }

    /**
     * Create a remote system
     * 
     * @param param
     *            RemoteSystemParam
     * @return VNXeCommandJob
     */

    public VNXeCommandJob createRemoteSystem(RemoteSystemParam param) {
        _url = URL;
        return postRequestAsync(param);
    }

    /**
     * modify a remote system
     * 
     * @param id
     *            id of the remote system
     * @param param
     *            RemoteSystemParam
     * @return VNXeCommandJob
     */
    public VNXeCommandJob modifyRemoteSystem(String id, RemoteSystemParam param) {
        _url = URL_INSTANCE + id;
        return postRequestAsync(param);
    }

    /**
     * delete a remote system
     * 
     * @param id
     *            id of the remote system
     * @return VNXeCommandJob
     */
    public VNXeCommandResult deleteRemoteSystem(String id) {
        _url = URL_INSTANCE + id;
        setQueryParameters(null);
        deleteRequest(null);
        VNXeCommandResult result = new VNXeCommandResult();
        result.setSuccess(true);
        return result;
    }

    /**
     * verify a remote system
     * 
     * @param id
     *            id of the remote system
     * @return VNXeCommandJob
     */
    public VNXeCommandJob verifyRemoteSystem(String id, RemoteSystemParam param) {
        _url = URL_INSTANCE + id + ACTION_VERIFY;
        return postRequestAsync(param);
    }

    /**
     * Get remote system details by it's name
     * 
     * @param serial
     *            serial of the remotesystem
     * @return RemoteSystem
     */
    public RemoteSystem getBySerial(String serial) {
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.FILTER, VNXeConstants.SERIAL_FILTER + "\"" + serial + "\"");
        setQueryParameters(queryParams);
        _url = URL;
        List<RemoteSystem> remoteSystemList = getDataForObjects(RemoteSystem.class);
        // it should just return 1
        if (remoteSystemList != null && !remoteSystemList.isEmpty()) {
            _logger.info("Remote System found using the Serial: " + serial);
            return remoteSystemList.get(0);
        }
        _logger.info("No Remote System found using the serial: " + serial);
        return null;
    }
}
