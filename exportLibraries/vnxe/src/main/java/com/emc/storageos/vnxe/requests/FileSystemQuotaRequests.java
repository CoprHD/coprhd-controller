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
import com.emc.storageos.vnxe.models.FilesystemTreeQuota;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class FileSystemQuotaRequests extends KHRequests<FilesystemTreeQuota> {
    private static final Logger _logger = LoggerFactory.getLogger(FileSystemQuotaRequests.class);
    private static final String URL = "/api/types/treeQuota/instances";
    private static final String URL_INSTANCE = "/api/instances/treeQuota/";
    private static final String URL_MODIFY = "/action/modify";
    private static final String URL_INSTANCE_DETAILS = "?fields=filesystem,quotaConfig,path,description,hardLimit,softLimit,remainingGracePeriod,sizeUsed";
    private static final String URL_QUOTACONFIG_INSTANCE = "/api/instances/quotaConfig/";

    public FileSystemQuotaRequests(KHClient client) {
        super(client);
        _url = URL;
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
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.TIMEOUT, "0");
        setQueryParameters(queryParams);
        return postRequestSync(param);
    }

    /**
     * Get quota details by it's id
     * 
     * @param Id
     *            id
     * @return the quota object
     */
    public FilesystemTreeQuota getByName(String fsName, String quotaName) {
        FileSystemListRequest req = new FileSystemListRequest(_client);
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.FILTER, VNXeConstants.PATH_FILTER + "/" + "\"" + quotaName + "\"");
        queryParams.add(VNXeConstants.FILTER, VNXeConstants.FILE_SYSTEM_FILTER_V31 + "\"" + req.getByFSName(fsName).getName() + "\"");
        setQueryParameters(queryParams);
        FilesystemTreeQuota result = null;
        List<FilesystemTreeQuota> quotaList = getDataForObjects(FilesystemTreeQuota.class);
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
     * Delete file system quota
     * 
     * @param quotaId
     *            Id of the quota
     * @return
     * @throws VNXeException
     */
    public VNXeCommandJob deleteFileSystemQuota(String fsName, String quotaName) throws VNXeException {
        FileSystemListRequest req = new FileSystemListRequest(_client);
        FilesystemTreeQuota quota = getByName(req.getByFSName(fsName).getName(), quotaName);
        _url = URL_INSTANCE + quota.getId();
        setQueryParameters(null);
        if (getDataForOneObject(FilesystemTreeQuota.class) != null) {
            return deleteRequestAsync(null);
        } else {
            throw VNXeException.exceptions.vnxeCommandFailed(String.format("No filesystem quota %s found",
                    quota.getId()));
        }

    }

    public VNXeCommandJob modifyFileSystemQuota(String fsName, String quotaName, FileSystemQuotaModifyParam param) throws VNXeException {
        FileSystemListRequest req = new FileSystemListRequest(_client);
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.TIMEOUT, "0");
        setQueryParameters(queryParams);
        FilesystemTreeQuota quota = getByName(req.getByFSName(fsName).getName(), quotaName);
        _url = URL_INSTANCE + quota.getId() + URL_MODIFY;
        return postRequestAsync(param);

    }

    /**
     * Get the specific file system quota's details
     * 
     * @return
     */
    public FilesystemTreeQuota getFileSystemQuota(String quotaId) throws VNXeException {
        _url = URL_INSTANCE + quotaId + URL_INSTANCE_DETAILS;
        setQueryParameters(null);
        return getDataForOneObject(FilesystemTreeQuota.class);

    }

    public VNXeCommandJob modifyFileSystemQuotaConfig(String fsName, String quotaName, FileSystemQuotaConfigParam param) {
        FileSystemListRequest fsReq = new FileSystemListRequest(_client);
        FilesystemTreeQuota quota = getByName(fsReq.getByFSName(fsName).getName(), quotaName);
        _url = URL_QUOTACONFIG_INSTANCE + getFileSystemQuota(quota.getId()).getQuotaConfig().getId() + URL_MODIFY;
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.TIMEOUT, "0");
        setQueryParameters(queryParams);
        return postRequestAsync(param);
    }
}
