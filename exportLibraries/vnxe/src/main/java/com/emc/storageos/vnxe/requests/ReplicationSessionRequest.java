/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.ReplicationSession;
import com.emc.storageos.vnxe.models.ReplicationSession.ReplicationEndpointResourceTypeEnum;
import com.emc.storageos.vnxe.models.ReplicationSessionParam;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class ReplicationSessionRequest extends KHRequests<ReplicationSession> {

    private static Logger _logger = LoggerFactory.getLogger(ReplicationSessionRequest.class);

    private static final String URL_INSTANCE = "/api/instances/replicationSession/";
    private static final String URL = "/api/types/replicationSession/instances";
    private static final String FIELDS = "name,maxTimeOutOfSync,srcResourceId,dstResourceId,replicationResourceType,localRole,remoteSystem";
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

    public List<ReplicationSession> getByType(ReplicationEndpointResourceTypeEnum resourceEnum) {
        _queryParams = null;
        _url = URL;
        List<ReplicationSession> sessions = getDataForObjects(ReplicationSession.class);
        List<ReplicationSession> fileSessions = new ArrayList<ReplicationSession>();
        if (!sessions.isEmpty()) {
            for (ReplicationSession session : sessions) {
                if (session.getReplicationResourceType() == resourceEnum) {
                    fileSessions.add(session);
                }
            }
        }
        return fileSessions;
    }

    public VNXeCommandJob createReplicationSession(ReplicationSessionParam param) {
        _queryParams = null;
        _url = URL;
        return postRequestAsync(param);
    }

    public VNXeCommandJob modifyReplicationSession(String id, ReplicationSessionParam param) {
        _url = URL_INSTANCE + id + ACTION + "modify";
        return postRequestAsync(param);
    }

    public VNXeCommandResult modifyReplicationSessionSync(String id, ReplicationSessionParam param) {
        _url = URL_INSTANCE + id + ACTION + "modify";
        return postRequestSync(param);
    }

    public VNXeCommandJob deleteReplicationSession(String id) {
        _url = URL_INSTANCE + id;
        if (get(id) != null) {
            unsetQueryParameters();
            return deleteRequestAsync(null);
        }
        throw VNXeException.exceptions.vnxeCommandFailed("The replication session not found: " + id);
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

    public ReplicationSession get(String sourceId, String targetId) {
        _queryParams = null;
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.FILTER,
                VNXeConstants.SOURCE_RESOURCE + "\"" + sourceId + "\"" + " and " + VNXeConstants.TARGET_RESOURCE + "\"" + targetId + "\"");
        setQueryParameters(queryParams);
        _url = URL;
        List<ReplicationSession> sessions = getDataForObjects(ReplicationSession.class);
        if (!sessions.isEmpty()) {
            return sessions.get(0);
        }
        return null;
    }
}
