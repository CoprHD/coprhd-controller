/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;
import com.emc.storageos.vnxe.models.RemoteSystem;
import com.emc.storageos.vnxe.models.RemoteSystemCreateParam;

public class RemoteSystemRequest extends KHRequests<RemoteSystem> {
    private static final String URL = "/api/instances/remoteSystem/";
    private static final String FIELDS = "name,model,serialNumber,managementAddress";

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
        _url = URL + id;
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

    public void createRemoteSystem(RemoteSystemCreateParam createParam) {
        //TODO
    }
}
