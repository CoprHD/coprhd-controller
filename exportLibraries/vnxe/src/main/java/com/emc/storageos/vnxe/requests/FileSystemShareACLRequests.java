/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.models.CifsShareACE;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.aclUserLookupSIDParam;

public class FileSystemShareACLRequests extends KHRequests<CifsShareACE> {
    private static final Logger _logger = LoggerFactory.getLogger(FileSystemShareACLRequests.class);
    private static final String URL_INSTANCE = "/api/instances/cifsShare/";
    private static final String URL_ACEs = "/action/getACEs";
    private static final String URL_ACLUSER = "/api/types/aclUser/action/lookupSIDByDomainUser";

    public FileSystemShareACLRequests(KHClient client) {
        super(client);
        _url = URL_INSTANCE;
    }

    public List<CifsShareACE> get(String shareId) {
        _queryParams = null;
        _url = URL_INSTANCE + shareId + URL_ACEs;
        VNXeCommandResult result = postRequestSync(null);
        return result.getCifsShareACEs();
    }

    public String getSIDForUser(aclUserLookupSIDParam param) {
        unsetQueryParameters();
        _url = URL_ACLUSER;
        VNXeCommandResult result = postRequestSync(param);
        return result.getSid();
    }

}