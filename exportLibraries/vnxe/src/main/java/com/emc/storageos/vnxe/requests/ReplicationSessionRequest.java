/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;
import com.emc.storageos.vnxe.models.ReplicationSession;
import com.emc.storageos.vnxe.models.ReplicationSessionCreateParam;

public class ReplicationSessionRequest extends KHRequests<ReplicationSession> {
    private static final String URL = "/api/instances/replicationSession/";
    private static final String FIELDS = "name,maxTimeOutOfSync,srcResourceId,dstResourceId";

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
        return getDataForObjects(ReplicationSession.class);
    }

    public void createReplicationSession(ReplicationSessionCreateParam createParam){
        //TODO
    }


}
