/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import com.emc.storageos.vnxe.models.RemoteSystem;
import com.emc.storageos.vnxe.models.RemoteSystemParam;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;

public class RemoteSystemRequest extends KHRequests<RemoteSystem> {
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
}
