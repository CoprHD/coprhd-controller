/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.requests;

import com.emc.storageos.vnxe.models.ConsistencyGroupCreateParam;
import com.emc.storageos.vnxe.models.LunGroupModifyParam;
import com.emc.storageos.vnxe.models.StorageResource;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;

public class ConsistencyGroupRequests extends KHRequests<StorageResource> {
    private static String URL_CREATE = "/api/types/storageResource/action/createConsistencyGroup";
    private static String URL_MODIFY1 = "/api/instances/storageResource/";
    private static String URL_MODIFY2 = "/action/modifyConsistencyGroup";

    public ConsistencyGroupRequests(KHClient client) {
        super(client);
    }

    public VNXeCommandResult createConsistencyGroup(ConsistencyGroupCreateParam createParam) {
        _url = URL_CREATE;
        return postRequestSync(createParam);
    }

    public VNXeCommandJob modifyConsistencyGroupAsync(String id, LunGroupModifyParam param) {
        StringBuilder urlBld = new StringBuilder(URL_MODIFY1);
        urlBld.append(id);
        urlBld.append(URL_MODIFY2);
        _url = urlBld.toString();

        return postRequestAsync(param);

    }

    public VNXeCommandResult modifyConsistencyGroupSync(String id, LunGroupModifyParam param) {
        StringBuilder urlBld = new StringBuilder(URL_MODIFY1);
        urlBld.append(id);
        urlBld.append(URL_MODIFY2);
        _url = urlBld.toString();

        return postRequestSync(param);

    }

}
