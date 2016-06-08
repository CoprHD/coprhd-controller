/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.requests;

import java.util.List;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.models.ConsistencyGroupCreateParam;
import com.emc.storageos.vnxe.models.LunGroupModifyParam;
import com.emc.storageos.vnxe.models.StorageResource;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.VNXeLun;

public class ConsistencyGroupRequests extends KHRequests<StorageResource> {
    private static String URL_CREATE = "/api/types/storageResource/action/createConsistencyGroup";
    private static String URL_MODIFY1 = "/api/instances/storageResource/";
    private static String URL_MODIFY2 = "/action/modifyConsistencyGroup";
    private static String URL_RESOURCES = "/api/types/storageResource/instances";
    private static String FIELDS = "name";

    public ConsistencyGroupRequests(KHClient client) {
        super(client);
        _fields = FIELDS;
    }

    /**
     * Create consistency group
     * @param createParam
     * @return
     */
    public VNXeCommandResult createConsistencyGroup(ConsistencyGroupCreateParam createParam) {
        _url = URL_CREATE;
        return postRequestSync(createParam);
    }

    /**
     * Modify consistency group in Async mode
     * @param id consistency group id
     * @param param
     * @return
     */
    public VNXeCommandJob modifyConsistencyGroupAsync(String id, LunGroupModifyParam param) {
        StringBuilder urlBld = new StringBuilder(URL_MODIFY1);
        urlBld.append(id);
        urlBld.append(URL_MODIFY2);
        _url = urlBld.toString();

        return postRequestAsync(param);

    }

    /**
     * Modify consistency group in Sync mode
     * @param id consistency group id
     * @param param
     * @return
     */
    public VNXeCommandResult modifyConsistencyGroupSync(String id, LunGroupModifyParam param) {
        StringBuilder urlBld = new StringBuilder(URL_MODIFY1);
        urlBld.append(id);
        urlBld.append(URL_MODIFY2);
        _url = urlBld.toString();

        return postRequestSync(param);

    }
    
    /**
     * Get Consistency group Id by its name
     * @param cgName consistency group name
     * @return cg id
     */
    public String getConsistencyGroupIdByName(String cgName) {
        String result = null;
        _url = URL_RESOURCES;
        setFilter(VNXeConstants.NAME_FILTER + "\"" + cgName + "\"");
        List<StorageResource> res = getDataForObjects(StorageResource.class);
        if (res != null && !res.isEmpty()) {
            result = res.get(0).getId();
        }
        return result;
    }

}
