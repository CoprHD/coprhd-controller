/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import com.emc.storageos.vnxe.models.ReplicationSession;
import com.emc.storageos.vnxe.models.ReplicationSessionCreateParam;
import com.emc.storageos.vnxe.models.VNXeCommandJob;

public class ReplicationSessionRequest extends KHRequests<ReplicationSession> {
    private static final String URL = "/api/instances/replicationSession/";
    private static final String URL_INSTANCES = "/api/types/replicationSession/instances";
    private static final String FIELDS = "name,maxTimeOutOfSync,srcResourceId,dstResourceId,replicationResourceType";

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
        _url = URL + id;
        return getDataForOneObject(ReplicationSession.class);

    }

    /**
     * Get all replication sessions in the array
     *
     * @return List of ReplicationSession
     */

    public List<ReplicationSession> get() {
        _queryParams = null;
        _url = URL_INSTANCES;
        return getDataForObjects(ReplicationSession.class);
    }

    public VNXeCommandJob createReplicationSession(ReplicationSessionCreateParam param) {
        return postRequestAsync(param);
    }

}
