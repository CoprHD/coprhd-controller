/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.datadomain.restapi;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;

import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.common.http.RestClientItf;
import com.emc.storageos.datadomain.restapi.errorhandling.DataDomainApiException;
import com.emc.storageos.datadomain.restapi.errorhandling.DataDomainResourceNotFoundException;
import com.emc.storageos.datadomain.restapi.model.DDAuthInfo;
import com.emc.storageos.datadomain.restapi.model.DDExportClient;
import com.emc.storageos.datadomain.restapi.model.DDExportClientModify;
import com.emc.storageos.datadomain.restapi.model.DDExportCreate;
import com.emc.storageos.datadomain.restapi.model.DDExportInfo;
import com.emc.storageos.datadomain.restapi.model.DDExportInfoDetail;
import com.emc.storageos.datadomain.restapi.model.DDExportList;
import com.emc.storageos.datadomain.restapi.model.DDExportModify;
import com.emc.storageos.datadomain.restapi.model.DDMCInfoDetail;
import com.emc.storageos.datadomain.restapi.model.DDMTreeCreate;
import com.emc.storageos.datadomain.restapi.model.DDMTreeInfo;
import com.emc.storageos.datadomain.restapi.model.DDMTreeInfoDetail;
import com.emc.storageos.datadomain.restapi.model.DDMTreeList;
import com.emc.storageos.datadomain.restapi.model.DDMTreeModify;
import com.emc.storageos.datadomain.restapi.model.DDMtreeCapacityInfos;
import com.emc.storageos.datadomain.restapi.model.DDNetworkDetails;
import com.emc.storageos.datadomain.restapi.model.DDNetworkList;
import com.emc.storageos.datadomain.restapi.model.DDQuotaConfig;
import com.emc.storageos.datadomain.restapi.model.DDRetentionLockSet;
import com.emc.storageos.datadomain.restapi.model.DDServiceStatus;
import com.emc.storageos.datadomain.restapi.model.DDShareCreate;
import com.emc.storageos.datadomain.restapi.model.DDShareInfo;
import com.emc.storageos.datadomain.restapi.model.DDShareInfoDetail;
import com.emc.storageos.datadomain.restapi.model.DDShareList;
import com.emc.storageos.datadomain.restapi.model.DDShareModify;
import com.emc.storageos.datadomain.restapi.model.DDSnapshot;
import com.emc.storageos.datadomain.restapi.model.DDSnapshotCreate;
import com.emc.storageos.datadomain.restapi.model.DDStatsCapacityInfos;
import com.emc.storageos.datadomain.restapi.model.DDStatsDataViewQuery;
import com.emc.storageos.datadomain.restapi.model.DDStatsInfos;
import com.emc.storageos.datadomain.restapi.model.DDStatsIntervalQuery;
import com.emc.storageos.datadomain.restapi.model.DDSystem;
import com.emc.storageos.datadomain.restapi.model.DDSystemList;
import com.emc.storageos.services.util.SecurityUtils;
import com.google.gson.Gson;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class DataDomainClient {

    private RestClientItf _client;

    private String _username;

    private String _password;

    private URI _base;

    private String _authToken;

    private static final int LOWEST_ERROR_STATUS = 300;

    private static Logger log = LoggerFactory.getLogger(DataDomainClient.class);

    /**
     * Constructor
     * 
     * @param client A reference to a Jersey Apache HTTP client.
     * @param username The user to be authenticated.
     * @param password The user password for authentication.
     */
    public DataDomainClient(URI baseURI, String username, String password, RestClientItf client) {
        _client = client;
        _base = baseURI;
        _username = username;
        _password = password;
        authenticate();
    }

    private void authenticate() throws DataDomainApiException {
        DDAuthInfo authInfo = new DDAuthInfo();
        authInfo.setPassword(_password);
        authInfo.setUsername(_username);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        String body = getJsonForEntity(authInfo);
        URI requestURI = _base.resolve(DataDomainApiConstants.URI_DATADOMAIN_AUTH);

        ClientResponse response = _client.post(requestURI, headers, body);

        if (response.getClientResponseStatus() != ClientResponse.Status.OK &&
                response.getClientResponseStatus() != ClientResponse.Status.CREATED) {
            throw DataDomainApiException.exceptions.authenticationFailure(_base.toString());
        }

        _authToken = response.getHeaders().getFirst(DataDomainApiConstants.AUTH_TOKEN);
    }

    private boolean authenticationFailed(ClientResponse response) {
        return response.getClientResponseStatus() == com.sun.jersey.api.client.ClientResponse.Status.UNAUTHORIZED;
    }

    private int checkResponse(URI uri, ClientResponse response) throws DataDomainApiException {
        int errorCode = response.getStatus();
        if (errorCode >= LOWEST_ERROR_STATUS) {
            JSONObject obj = null;
            String msg;
            int ddCode;
            try {
                obj = response.getEntity(JSONObject.class);
                msg = obj.getString(DataDomainApiConstants.DETAILS);
                ddCode = obj.getInt(DataDomainApiConstants.CODE);
            } catch (Exception e) {
                throw DataDomainApiException.exceptions.jsonWriterReaderException(e);
            }
            log.error(String.format("DataDomain Rest API failed, DDCode: %d, Msg : %s", ddCode, msg));

            if (ddCode == 404 || ddCode == 410) {
                throw DataDomainResourceNotFoundException.notFound.resourceNotFound(uri.toString(), msg);
            } else {
                throw DataDomainApiException.exceptions.failedResponseFromDataDomainMsg(uri, errorCode, msg, ddCode);
            }
        } else {
            return errorCode;
        }
    }

    private <T> String getJsonForEntity(T model) throws DataDomainApiException {
        try {
            return new Gson().toJson(model);
        } catch (Exception e) {
            throw DataDomainApiException.exceptions.jsonWriterReaderException(e);
        }
    }

    private <T> T getResponseObject(Class<T> clazz, ClientResponse response) throws DataDomainApiException {
        try {
            JSONObject resp = response.getEntity(JSONObject.class);
            T respObject = new Gson().fromJson(SecurityUtils.sanitizeJsonString(resp.toString()), clazz);
            return respObject;
        } catch (Exception e) {
            throw DataDomainApiException.exceptions.jsonWriterReaderException(e);
        }
    }

    private ClientResponse doCreateMTree(String ddSystem, String mtreeName, long size)
            throws DataDomainApiException {
        DDMTreeCreate createParam = new DDMTreeCreate();
        
        createParam.setName(mtreeName);
        createParam.setQuota(new DDQuotaConfig());
        createParam.getQuota().setHardLimit(size);
        createParam.getQuota().setSoftLimit((long) (size * DataDomainApiConstants.DD_MTREE_SOFT_LIMIT));
        
        ClientResponse response = _client.post(DataDomainApiConstants.uriDataDomainMtrees(ddSystem), getAuthAndJsonHeader(), getJsonForEntity(createParam));
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.post(DataDomainApiConstants.uriDataDomainMtrees(ddSystem), getAuthAndJsonHeader(), getJsonForEntity(createParam));
        }
        return response;
    }

    protected Map<String, String> getAuthAndJsonHeader() {
        Map<String, String> headers = getAuthHeader();
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private ClientResponse modifyMTreeRetentionlock(String ddSystem, String mtreeId,
            Boolean enable, String mode) throws DataDomainApiException {
        DDQuotaConfig quotaConfig = null;
        DDRetentionLockSet retentionLockSet = new DDRetentionLockSet(enable, mode);
        DDMTreeModify modifyParam = new DDMTreeModify(quotaConfig, retentionLockSet);
        
        ClientResponse response = _client.post(DataDomainApiConstants.uriDataDomainMtree(ddSystem, mtreeId), getAuthAndJsonHeader(), getJsonForEntity(modifyParam));
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.post(DataDomainApiConstants.uriDataDomainMtree(ddSystem, mtreeId), getAuthAndJsonHeader(), getJsonForEntity(modifyParam));
        }
        return response;
    }

    private ClientResponse doCreateExport(String ddSystem, DDExportCreate ddExport) throws DataDomainApiException {
        
        ClientResponse response = _client.post(DataDomainApiConstants.uriDataDomainExports(ddSystem), getAuthAndJsonHeader(), getJsonForEntity(ddExport));
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.post(DataDomainApiConstants.uriDataDomainExports(ddSystem), getAuthAndJsonHeader(), getJsonForEntity(ddExport));
        }
        return response;
    }

    private ClientResponse doModifyExport(String ddSystem, String ddExportId,
            DDExportModify ddExportModify) throws DataDomainApiException {
        
        ClientResponse response = _client.post(DataDomainApiConstants.uriDataDomainExport(ddSystem, ddExportId), getAuthAndJsonHeader(), getJsonForEntity(ddExportModify));
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.post(DataDomainApiConstants.uriDataDomainExport(ddSystem, ddExportId), getAuthAndJsonHeader(), getJsonForEntity(ddExportModify));
        }
        return response;
    }

    private ClientResponse doCreateShare(String ddSystem, DDShareCreate ddShare) throws DataDomainApiException {
        
        ClientResponse response = _client.post(DataDomainApiConstants.uriDataDomainShares(ddSystem), getAuthAndJsonHeader(), getJsonForEntity(ddShare));
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.post(DataDomainApiConstants.uriDataDomainShares(ddSystem), getAuthAndJsonHeader(), getJsonForEntity(ddShare));
        }
        return response;
    }

    public DDMCInfoDetail getManagementSystemInfo() throws DataDomainApiException {
        
        ClientResponse response = _client.get(DataDomainApiConstants.URI_DATADOMAIN_SERVICE, getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.get(DataDomainApiConstants.URI_DATADOMAIN_SERVICE, getAuthHeader());
        }
        return getResponseObject(DDMCInfoDetail.class, response);
    }

    protected Map<String, String> getAuthHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put(DataDomainApiConstants.AUTH_TOKEN, _authToken);
        return headers;
    }

    public DDSystemList getManagedSystemList() throws DataDomainApiException {
        
        ClientResponse response = _client.get(DataDomainApiConstants.URI_DATADOMAIN_SYSTEM_LIST, getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.get(DataDomainApiConstants.URI_DATADOMAIN_SYSTEM_LIST, getAuthHeader());
        }
        return getResponseObject(DDSystemList.class, response);
    }

    public DDSystem getDDSystem(String system) throws DataDomainApiException {
        ClientResponse response = _client.get(DataDomainApiConstants.uriDataDomainSystem(system), getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.get(DataDomainApiConstants.uriDataDomainSystem(system), getAuthHeader());
        }
        return getResponseObject(DDSystem.class, response);
    }

    public DDMCInfoDetail getDDSystemInfoDetail(String system) throws DataDomainApiException {
        ClientResponse response = _client.get(DataDomainApiConstants.uriDataDomainSystem(system), getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.get(DataDomainApiConstants.uriDataDomainSystem(system), getAuthHeader());
        }
        return getResponseObject(DDMCInfoDetail.class, response);
    }

    public DDMTreeList getMTreeList(String system) throws DataDomainApiException {

        // As per DD team, the maximum mtrees that DDOS supports is 100. Hence we need
        // to request for page with size 100 so we get all MTrees.
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add("size", String.valueOf(DataDomainApiConstants.DD_MAX_MTREE_LIMIT));

        ClientResponse response = _client.get(DataDomainApiConstants.uriDataDomainMtrees(system), queryParams, getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.get(DataDomainApiConstants.uriDataDomainMtrees(system), queryParams, getAuthHeader());
        }
        return getResponseObject(DDMTreeList.class, response);
    }

    public DDMTreeInfoDetail getMTree(String system, String mtree) throws DataDomainApiException {
        ClientResponse response = _client.get(DataDomainApiConstants.uriDataDomainMtree(system, mtree), getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.get(DataDomainApiConstants.uriDataDomainMtree(system, mtree), getAuthHeader());
        }
        return getResponseObject(DDMTreeInfoDetail.class, response);
    }

    public DDMTreeInfo createMTree(String ddSystem, String mtreeName, long size,
            Boolean enableRetention, String retentionMode) throws DataDomainApiException {
        ClientResponse response = doCreateMTree(ddSystem, mtreeName, size);
        // The following lines are commented out for now because enabling/disabling
        // retention lock requires a special license
        // if (response.getStatus() < LOWEST_ERROR_STATUS) {
        // DDMTreeInfo ddMtree = getResponseObject(DDMTreeInfo.class, response);
        // response = modifyMTreeRetentionlock(ddSystem, ddMtree.getId(),
        // enableRetention, retentionMode);
        // }
        return getResponseObject(DDMTreeInfo.class, response);
    }

    public DDServiceStatus deleteMTree(String ddSystem, String mtreeId) throws DataDomainApiException {
        ClientResponse response = _client.delete(DataDomainApiConstants.uriDataDomainMtree(ddSystem, mtreeId), getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.delete(DataDomainApiConstants.uriDataDomainMtree(ddSystem, mtreeId), getAuthHeader());
        }
        return getResponseObject(DDServiceStatus.class, response);
    }

    public DDMTreeInfo expandMTree(String ddSystem, String mtreeId, long newSize) throws DataDomainApiException {
        DDQuotaConfig quotaConfig = new DDQuotaConfig();
        quotaConfig.setHardLimit(newSize);
        quotaConfig.setSoftLimit((long) (newSize * DataDomainApiConstants.DD_MTREE_SOFT_LIMIT));
        DDRetentionLockSet retentionLockSet = null;
        DDMTreeModify modifyParam = new DDMTreeModify(quotaConfig, retentionLockSet);
        
        ClientResponse response = _client.put(DataDomainApiConstants.uriDataDomainMtree(ddSystem, mtreeId),
                getAuthAndJsonHeader(), getJsonForEntity(modifyParam));
        if(authenticationFailed(response)) {
            authenticate();
            _client.put(DataDomainApiConstants.uriDataDomainMtree(ddSystem, mtreeId),
                    getAuthAndJsonHeader(), getJsonForEntity(modifyParam));
        }
        return getResponseObject(DDMTreeInfo.class, response);
    }

    public DDExportInfoDetail getExport(String ddSystem, String exportId) throws DataDomainApiException {
        ClientResponse response = _client.get(DataDomainApiConstants.uriDataDomainExport(ddSystem, exportId), getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.get(DataDomainApiConstants.uriDataDomainExport(ddSystem, exportId), getAuthHeader());
        }
        return getResponseObject(DDExportInfoDetail.class, response);
    }

    public DDExportInfo createExport(String ddSystem, String exportName,
            List<DDExportClient> exportClients) throws DataDomainApiException {
        DDExportCreate ddExportCreate = new DDExportCreate(exportName, exportClients);
        ClientResponse response = doCreateExport(ddSystem, ddExportCreate);
        return getResponseObject(DDExportInfo.class, response);
    }

    public DDExportInfo modifyExport(String ddSystem, String ddExportId,
            List<DDExportClientModify> ddExportClients) throws DataDomainApiException {
        DDExportModify ddExportModify = new DDExportModify(ddExportClients);
        ClientResponse response = doModifyExport(ddSystem, ddExportId, ddExportModify);
        return getResponseObject(DDExportInfo.class, response);
    }

    public DDServiceStatus deleteExport(String ddSystem, String ddExportId) throws DataDomainApiException {
        ClientResponse response = _client.delete(DataDomainApiConstants.uriDataDomainExport(ddSystem, ddExportId), getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.delete(DataDomainApiConstants.uriDataDomainExport(ddSystem, ddExportId), getAuthHeader());
        }
        return getResponseObject(DDServiceStatus.class, response);
    }

    public DDShareInfo createShare(String ddSystem, String shareName, String sharePath,
            int maxUsers, String description, String permissionType, String permission)
                    throws DataDomainApiException {
        DDShareCreate ddShareCreate = new DDShareCreate(shareName, sharePath,
                maxUsers, description, permissionType, permission);
        ClientResponse response = doCreateShare(ddSystem, ddShareCreate);
        return getResponseObject(DDShareInfo.class, response);
    }

    public DDShareInfo modifyShare(String ddSystem, String ddShareId, String description)
            throws DataDomainApiException {
        DDShareModify ddShareModify = new DDShareModify(description);
        
        ClientResponse response = _client.put(DataDomainApiConstants.uriDataDomainShare(ddSystem, ddShareId),
                getAuthAndJsonHeader(), getJsonForEntity(ddShareModify));
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.put(DataDomainApiConstants.uriDataDomainShare(ddSystem, ddShareId),
                    getAuthAndJsonHeader(), getJsonForEntity(ddShareModify));
        }
        return getResponseObject(DDShareInfo.class, response);
    }

    public DDServiceStatus deleteShare(String ddSystem, String ddShareId) throws DataDomainApiException {
        ClientResponse response = _client.delete(DataDomainApiConstants.uriDataDomainShare(ddSystem, ddShareId), getAuthHeader());
        
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.delete(DataDomainApiConstants.uriDataDomainShare(ddSystem, ddShareId), getAuthHeader());
        }
        return getResponseObject(DDServiceStatus.class, response);
    }

    public DDShareInfoDetail getShare(String ddSystem, String shareId) throws DataDomainApiException {
        ClientResponse response = _client.get(DataDomainApiConstants.uriDataDomainShare(ddSystem, shareId), getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.get(DataDomainApiConstants.uriDataDomainShare(ddSystem, shareId), getAuthHeader());
        }
        return getResponseObject(DDShareInfoDetail.class, response);
    }

    public DDShareList getShares(String ddSystem) throws DataDomainApiException {
        ClientResponse response = _client.get(DataDomainApiConstants.uriDataDomainShares(ddSystem), getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.get(DataDomainApiConstants.uriDataDomainShares(ddSystem), getAuthHeader());
        }
        return getResponseObject(DDShareList.class, response);
    }

    public DDNetworkList getNetworks(String ddSystem) throws DataDomainApiException {
        ClientResponse response = _client.get(DataDomainApiConstants.uriDataDomainNetworks(ddSystem), getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.get(DataDomainApiConstants.uriDataDomainNetworks(ddSystem), getAuthHeader());
        }
        return getResponseObject(DDNetworkList.class, response);
    }

    public DDNetworkDetails getNetwork(String ddSystem, String network) throws DataDomainApiException {
        ClientResponse response = _client.get(DataDomainApiConstants.uriDataDomainNetwork(ddSystem, network), getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.get(DataDomainApiConstants.uriDataDomainNetwork(ddSystem, network), getAuthHeader());
        }
        return getResponseObject(DDNetworkDetails.class, response);
    }

    public DDExportList getExports(String ddSystem) throws DataDomainApiException {
        ClientResponse response = _client.get(DataDomainApiConstants.uriDataDomainExports(ddSystem), getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.get(DataDomainApiConstants.uriDataDomainExports(ddSystem), getAuthHeader());
        }
        return getResponseObject(DDExportList.class, response);
    }

    public DDSnapshot createSnapshot(String ddSystem, DDSnapshotCreate ddSnapshotCreate) throws DataDomainApiException {
        ClientResponse response = _client.post(DataDomainApiConstants.uriDataDomainSnapshots(ddSystem), getAuthAndJsonHeader(), getJsonForEntity(ddSnapshotCreate));
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.post(DataDomainApiConstants.uriDataDomainSnapshots(ddSystem), getAuthAndJsonHeader(), getJsonForEntity(ddSnapshotCreate));
        }
        return getResponseObject(DDSnapshot.class, response);
    }

    public DDServiceStatus deleteSnapshot(String ddSystem, String ddSnapshotId) throws DataDomainApiException {
        ClientResponse response = _client.delete(DataDomainApiConstants.uriDataDomainSnapshot(ddSystem, ddSnapshotId), getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.delete(DataDomainApiConstants.uriDataDomainSnapshot(ddSystem, ddSnapshotId), getAuthHeader());
        }
        return getResponseObject(DDServiceStatus.class, response);
    }

    public DDStatsCapacityInfos getSystemCapacityInfo(String ddSystem, int page,
            int size, DDStatsIntervalQuery interval, boolean requestedIntervalOnly,
            String sort, String queryFilter) throws DataDomainApiException {

        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        if (page >= 0) {
            queryParams.add("page", String.valueOf(page));
        }
        if (size > 0) {
            queryParams.add("size", String.valueOf(size));
        }
        if (DDStatsIntervalQuery.isMember(interval)) {
            queryParams.add("interval", interval.toString());
        }
        queryParams.add("requested_interval_only", String.valueOf(requestedIntervalOnly));
        if ((sort != null) && (sort.endsWith(DataDomainApiConstants.COLLECTION_EPOCH))) {
            queryParams.add("sort", sort);
        }
        if ((queryFilter != null) && (queryFilter.startsWith(DataDomainApiConstants.COLLECTION_EPOCH))) {
            queryParams.add("filter", queryFilter);
        }
        
        ClientResponse response = _client.get(DataDomainApiConstants.uriDataDomainSystemStatsCapacity(ddSystem),
                (MultivaluedMap<String, String>) queryParams, getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.get(DataDomainApiConstants.uriDataDomainSystemStatsCapacity(ddSystem),
                    (MultivaluedMap<String, String>) queryParams, getAuthHeader());
        }
        return getResponseObject(DDStatsCapacityInfos.class, response);

    }

    public DDStatsInfos getMTreeStatsInfos(String ddSystem, String mtreeId) throws DataDomainApiException {
        ClientResponse response = _client.get(DataDomainApiConstants.uriDataDomainMtreeStats(ddSystem, mtreeId), getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            _client.get(DataDomainApiConstants.uriDataDomainMtreeStats(ddSystem, mtreeId), getAuthHeader());
        }
        return getResponseObject(DDStatsInfos.class, response);
    }

    public DDMtreeCapacityInfos getMTreeCapacityInfo(String ddSystem, String mtreeId,
            int page, int pgSize, DDStatsDataViewQuery dataView, DDStatsIntervalQuery interval,
            boolean requestedIntervalOnly, String sort) throws DataDomainApiException {

        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        if (page >= 0) {
            queryParams.add(DataDomainApiConstants.PAGE, String.valueOf(page));
        }
        if (pgSize > 0) {
            queryParams.add(DataDomainApiConstants.PAGE_SIZE, String.valueOf(pgSize));
        }
        if (DDStatsDataViewQuery.isMember(dataView)) {
            queryParams.add(DataDomainApiConstants.DATA_VIEW, dataView.toString());
        }
        // interval is not supported now, but leaving the following lines here in case DD API evolves
        // if ((DDStatsIntervalQuery.isMember(interval)) && (dataView.equals(DDStatsDataViewQuery.delta))) {
        // queryParams.add(DataDomainApiConstants.INTERVAL, interval.toString());
        // }
        if (dataView.equals(DDStatsDataViewQuery.delta)) {
            queryParams.add(DataDomainApiConstants.REQUESTED_INTERVAL_ONLY,
                    String.valueOf(requestedIntervalOnly));
        }
        if ((sort != null) && (sort.endsWith(DataDomainApiConstants.COLLECTION_EPOCH))) {
            queryParams.add(DataDomainApiConstants.SORT, sort);
        }
        
        ClientResponse response = _client.get(DataDomainApiConstants.uriDataDomainMtreeStatsCapacity(ddSystem, mtreeId),
                (MultivaluedMap<String, String>) queryParams, getAuthHeader());
        if(authenticationFailed(response)) {
            authenticate();
            response = _client.get(DataDomainApiConstants.uriDataDomainMtreeStatsCapacity(ddSystem, mtreeId),
                    (MultivaluedMap<String, String>) queryParams, getAuthHeader());
        }
        return getResponseObject(DDMtreeCapacityInfos.class, response);
    }

}
