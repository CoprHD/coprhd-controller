/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.FileSystemQuotaConfigParam;
import com.emc.storageos.vnxe.models.FileSystemQuotaCreateParam;
import com.emc.storageos.vnxe.models.FileSystemQuotaModifyParam;
import com.emc.storageos.vnxe.models.VNXUnityTreeQuota;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class FileSystemQuotaRequests extends KHRequests<VNXUnityTreeQuota> {
    private static final Logger _logger = LoggerFactory.getLogger(FileSystemQuotaRequests.class);
    private static final String URL = "/api/types/treeQuota/instances";
    private static final String URL_INSTANCE = "/api/instances/treeQuota/";
    private static final String URL_MODIFY = "/action/modify";
    private static final String FIELDS = "filesystem,quotaConfig,path,description,hardLimit,softLimit,remainingGracePeriod,sizeUsed";

    public FileSystemQuotaRequests(KHClient client) {
        super(client);
        _url = URL;
        _fields = FIELDS;
    }

    /**
     * create file system quota in async mode
     * 
     * @param param:
     *            FileSystemQuotaCreateParam
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob createFileSystemQuotaAsync(FileSystemQuotaCreateParam param) throws VNXeException {
        _logger.info("Async create quota with name: " + param.getName());
        _url = URL;
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.TIMEOUT, "0");
        setQueryParameters(queryParams);
        return postRequestAsync(param);
    }

    /**
     * create file system quota in sync mode
     * 
     * @param param:
     *            FileSystemQuotaCreateParam
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandResult createFileSystemQuotaSync(FileSystemQuotaCreateParam param) throws VNXeException {
        _logger.info("Sync Create quota with name: " + param.getName());
        _url = URL;
        setQueryParameters(null);
        return postRequestSync(param);
    }

    /**
     * Delete file system quota
     * 
     * @param quotaId
     *            Id of the quota
     * @return
     * @throws VNXeException
     */
    public VNXeCommandJob deleteFileSystemQuota(String quotaId) throws VNXeException {
        _url = URL_INSTANCE + quotaId;
        _logger.info("delete quota with ID: " + quotaId);
        setQueryParameters(null);
        if (getDataForOneObject(VNXUnityTreeQuota.class) != null) {
            return deleteRequestAsync(null);
        } else {
            throw VNXeException.exceptions.vnxeCommandFailed(String.format("No filesystem quota with id: %s found",
                    quotaId));
        }

    }

    public VNXeCommandResult updateFileSystemQuotaSync(String quotaId, FileSystemQuotaModifyParam param)
            throws VNXeException {
        _logger.info("Sync update quota with ID: " + quotaId);
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.TIMEOUT, "0");
        setQueryParameters(queryParams);
        VNXUnityTreeQuota quota = getFileSystemQuota(quotaId);
        _url = URL_INSTANCE + quota.getId() + URL_MODIFY;
        return postRequestSync(param);

    }

    public VNXeCommandJob updateFileSystemQuotaAsync(String quotaId, FileSystemQuotaModifyParam param)
            throws VNXeException {
        _logger.info("Async update quota with ID: " + quotaId);
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.TIMEOUT, "0");
        setQueryParameters(queryParams);
        VNXUnityTreeQuota quota = getFileSystemQuota(quotaId);
        _url = URL_INSTANCE + quota.getId() + URL_MODIFY;
        return postRequestAsync(param);

    }

    public VNXeCommandJob updateFileSystemQuotaConfig(String quotaId, FileSystemQuotaConfigParam param) throws VNXeException {
        VNXUnityTreeQuota quotaObj = null;
        if (quotaId != null) {
            quotaObj = getFileSystemQuota(quotaId);
        }
        FileSystemQuotaConfigRequests req = new FileSystemQuotaConfigRequests(_client);
        _logger.info("updating quota config for quota ID: " + quotaId);
        return req.updateFileSystemQuotaConfig(quotaObj.getQuotaConfigId(), param);
    }

    /**
     * Get quota details by it's name
     * 
     * @param Id
     *            id
     * @return the quota object
     */
    public VNXUnityTreeQuota getByName(String fsId, String quotaName) {
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.FILTER, VNXeConstants.PATH_FILTER + "/" + "\"" + quotaName + "\"");
        queryParams.add(VNXeConstants.FILTER, VNXeConstants.FILE_SYSTEM_FILTER_V31 + "\"" + fsId + "\"");
        setQueryParameters(queryParams);
        VNXUnityTreeQuota result = null;
        _url = URL;
        List<VNXUnityTreeQuota> quotaList = getDataForObjects(VNXUnityTreeQuota.class);
        // it should just return 1
        if (quotaList != null && !quotaList.isEmpty()) {
            result = quotaList.get(0);
            _logger.info("file system tree quota found using the name: " + quotaName);
        } else {
            _logger.info("No file system tree quota found using the name: " + quotaName);
        }
        return result;
    }

    /**
     * Get the specific file system quota's details
     * 
     * @return
     */
    public VNXUnityTreeQuota getFileSystemQuota(String quotaId) {
        _url = URL_INSTANCE + quotaId;
        setQueryParameters(null);
        _logger.info("getting data for quota: " + quotaId);
        return getDataForOneObject(VNXUnityTreeQuota.class);

    }
}