/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.models.ReplicationSession;
import com.emc.storageos.vnxe.models.ReplicationSessionParam;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;

public class ReplicationSessionRequest extends KHRequests<ReplicationSession> {

    private static Logger _logger = LoggerFactory.getLogger(ReplicationSessionRequest.class);

    private static final String URL_INSTANCE = "/api/instances/replicationSession/";
    private static final String URL = "/api/types/replicationSession/instances";
    private static final String FIELDS = "name,maxTimeOutOfSync,srcResourceId,dstResourceId,replicationResourceType";
    private static final String ACTION = "/action/";

    public ReplicationSessionRequest(KHClient client) {
        super(client);
        _fields = FIELDS;

    }

    /**
     * Get specific replication session
     *
     * @return ReplicationSession
     */

    public ReplicationSession get(String id) {
        _logger.info("getting replication session for id:" + id);
        _url = URL_INSTANCE + id;
        return getDataForOneObject(ReplicationSession.class);

    }

    /**
     * Get all replication sessions in the array
     *
     * @return List of ReplicationSession
     */

    public List<ReplicationSession> get() {
        _queryParams = null;
        _url = URL;
        return getDataForObjects(ReplicationSession.class);
    }

    public VNXeCommandJob createReplicationSession(ReplicationSessionParam param) {
        _queryParams = null;
        _url = URL;
        return postRequestAsync(param);
    }

    public VNXeCommandJob modifyReplicationSession(String id, ReplicationSessionParam param) {
        _url = URL_INSTANCE + id;
        return postRequestAsync(param);
    }

    public VNXeCommandResult deleteReplicationSession(String id) {
        _url = URL_INSTANCE + id;
        setQueryParameters(null);
        deleteRequest(null);
        VNXeCommandResult result = new VNXeCommandResult();
        result.setSuccess(true);
        return result;
    }

    public VNXeCommandJob resumeReplicationSession(String id, ReplicationSessionParam param) {
        _url = URL_INSTANCE + id + ACTION + "resume";
        return postRequestAsync(param);
    }

    public VNXeCommandJob pauseReplicationSession(String id) {
        _url = URL_INSTANCE + id + ACTION + "pause";
        return postRequestAsync(null);
    }

    public VNXeCommandJob syncReplicationSession(String id) {
        _url = URL_INSTANCE + id + ACTION + "sync";
        return postRequestAsync(null);
    }

    public VNXeCommandJob failoverReplicationSession(String id, ReplicationSessionParam param) {
        _url = URL_INSTANCE + id + ACTION + "failover";
        return postRequestAsync(param);
    }

    public VNXeCommandJob failbackReplicationSession(String id, ReplicationSessionParam param) {
        _url = URL_INSTANCE + id + ACTION + "failback";
        return postRequestAsync(param);
    }

}
