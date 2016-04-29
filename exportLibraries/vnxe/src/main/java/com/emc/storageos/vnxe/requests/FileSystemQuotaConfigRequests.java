/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import javax.ws.rs.core.MultivaluedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.FileSystemQuotaConfigParam;
import com.emc.storageos.vnxe.models.VNXUnityQuotaConfig;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class FileSystemQuotaConfigRequests extends KHRequests<VNXUnityQuotaConfig> {
    private static final Logger _logger = LoggerFactory.getLogger(FileSystemQuotaConfigRequests.class);
    private static final String URL = "/api/types/quotaConfig/instances";
    private static final String URL_INSTANCE = "/api/instances/quotaConfig/";
    private static final String URL_MODIFY = "/action/modify";
    private static final String FIELDS = "filesystem,treeQuota,defaultHardLimit,defaultSoftLimit,gracePeriod";

    public FileSystemQuotaConfigRequests(KHClient client) {
        super(client);
        _url = URL;
        _fields = FIELDS;
    }

    /**
     * update the specific file system quotaConfig
     * 
     * @param quotaConfigId
     *            Id of the quota config to be updated
     * @param param
     *            FileSystemQuotaConfigParam
     * @return VNXeCommandJob
     */
    public VNXeCommandJob updateFileSystemQuotaConfig(String quotaConfigId, FileSystemQuotaConfigParam param) {
        _url = URL_INSTANCE + quotaConfigId + URL_MODIFY;
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.TIMEOUT, "0");
        setQueryParameters(queryParams);
        _logger.info("Post request async for: " + quotaConfigId);
        return postRequestAsync(param);

    }

    /**
     * Get the specific file system quotaConfig's details
     * 
     * @param quotaConfigId
     *            Id of the quota config to be updated
     * @return VNXUnityQuotaConfig - quotaConfig Object
     */
    public VNXUnityQuotaConfig getFileSystemQuotaConfig(String quotaConfigId) throws VNXeException {
        _url = URL_INSTANCE + quotaConfigId;
        setQueryParameters(null);
        _logger.info("getting data for quota config: " + quotaConfigId);
        return getDataForOneObject(VNXUnityQuotaConfig.class);

    }
}