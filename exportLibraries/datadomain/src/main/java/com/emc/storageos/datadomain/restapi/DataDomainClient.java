/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.datadomain.restapi;

import com.emc.storageos.datadomain.restapi.errorhandling.DataDomainApiException;
import com.emc.storageos.datadomain.restapi.errorhandling.DataDomainResourceNotFoundException;
import com.emc.storageos.services.restutil.RestClientItf;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import com.emc.storageos.datadomain.restapi.model.*;

import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import java.net.URI;
import java.util.List;

public class DataDomainClient implements RestClientItf {

    private Client _client;
    private String _username;
    private String _password;
    private String _authToken;
    private URI _base;
    
    private static final int LOWEST_ERROR_STATUS = 300;


    private static Logger log = LoggerFactory.getLogger(DataDomainClient.class);

    /**
     * Constructor
     *
     * @param client A reference to a Jersey Apache HTTP client.
     * @param username The user to be authenticated.
     * @param password The user password for authentication.
     */
    public DataDomainClient( URI baseURI, String username, String password, Client client) {
        _client = client;
        _base = baseURI;
        _username = username;
        _password = password;
        _authToken = "";
    }

    @Override
    public ClientResponse get(URI uri) throws DataDomainApiException {
        URI requestURI = _base.resolve(uri);
        ClientResponse response = setResourceHeaders(_client.resource(requestURI)).get(ClientResponse.class);
        if ( authenticationFailed(response) ){
            authenticate();
            response = setResourceHeaders(_client.resource(requestURI)).get(ClientResponse.class);
        }
        checkResponse(uri,response);
        return response;
    }
    
    public ClientResponse get(URI uri, MultivaluedMap<String,String> params) throws DataDomainApiException {
        URI requestURI = _base.resolve(uri);
        ClientResponse response = setResourceHeaders(_client.resource(requestURI).queryParams(params)).get(ClientResponse.class);
        if ( authenticationFailed(response) ){
            authenticate();
            response = setResourceHeaders(_client.resource(requestURI).queryParams(params)).get(ClientResponse.class);
        }
        checkResponse(uri,response);
        return response;
    }

    @Override
    public ClientResponse put(URI uri, String body) throws DataDomainApiException {
        URI requestURI = _base.resolve(uri);
        ClientResponse response = setResourceHeaders(_client.resource(requestURI)).put(ClientResponse.class, body);
        if ( authenticationFailed(response) ){
            authenticate();
            response = setResourceHeaders(_client.resource(requestURI)).put(ClientResponse.class,body);
        }
        checkResponse(uri,response);
        return response;
    }

    @Override
    public ClientResponse post(URI uri, String body) throws DataDomainApiException{
        URI requestURI = _base.resolve(uri);
        ClientResponse response = setResourceHeaders(_client.resource(requestURI)).type(MediaType.APPLICATION_JSON)
                                           .post(ClientResponse.class, body);
        if ( authenticationFailed(response) ){
            authenticate();
            response = setResourceHeaders(_client.resource(requestURI)).type(MediaType.APPLICATION_JSON)
                                            .post(ClientResponse.class, body);
        }
        checkResponse(uri,response);
        return response;
    }

    @Override
    public ClientResponse delete(URI uri) throws DataDomainApiException{
        URI requestURI = _base.resolve(uri);
        ClientResponse response = setResourceHeaders(_client.resource(requestURI)).type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class);
        if ( authenticationFailed(response) ){
            authenticate();
            response = setResourceHeaders(_client.resource(requestURI)).type(MediaType.APPLICATION_JSON)
                    .delete(ClientResponse.class);
        }
        checkResponse(uri,response);
        return response;
    }


    /**
     * Close the client
     */
    @Override
    public void close() {
        _client.destroy();
    }

    private void authenticate() throws DataDomainApiException{
        DDAuthInfo authInfo = new DDAuthInfo();
        authInfo.setPassword(_password);
        authInfo.setUsername(_username);

        String body = getJsonForEntity(authInfo);

        URI requestURI = _base.resolve(DataDomainApiConstants.URI_DATADOMAIN_AUTH);
        ClientResponse response = _client.resource(requestURI).
                                       type(MediaType.APPLICATION_JSON)
                                       .post(ClientResponse.class, body);

        if (response.getClientResponseStatus() != ClientResponse.Status.OK &&
            response.getClientResponseStatus() != ClientResponse.Status.CREATED    )  {
            throw DataDomainApiException.exceptions.authenticationFailure(_base.toString());
        }
        _authToken = response.getHeaders().getFirst(DataDomainApiConstants.AUTH_TOKEN);
    }

    private WebResource.Builder setResourceHeaders(WebResource resource) {
        return resource.header(DataDomainApiConstants.AUTH_TOKEN, _authToken);
    }

    private boolean authenticationFailed(ClientResponse response){
        return response.getClientResponseStatus() ==
                com.sun.jersey.api.client.ClientResponse.Status.UNAUTHORIZED;
    }

    private int checkResponse(URI uri, ClientResponse response) throws DataDomainApiException{
    	int errorCode = response.getStatus();
        if ( errorCode >= LOWEST_ERROR_STATUS  ) {
            JSONObject obj = null;
            String msg;
            int ddCode;
            try  {
                obj = response.getEntity(JSONObject.class);
                msg = obj.getString(DataDomainApiConstants.DETAILS);
                ddCode = obj.getInt(DataDomainApiConstants.CODE);
            }
            catch( Exception e) {
                throw DataDomainApiException.exceptions.jsonWriterReaderException(e);
            }
            log.error(String.format("DataDomain Rest API failed, DDCode: %d, Msg : %s",ddCode, msg));

            if( ddCode == 404 || ddCode == 410 )   {
                throw DataDomainResourceNotFoundException.notFound.ResourceNotFound(uri.toString(),msg);
            }
            else {
                throw DataDomainApiException.exceptions.failedResponseFromDataDomainMsg(uri,errorCode,msg, ddCode);
            }
        }
        else {
            return errorCode;
        }
    }

    private <T> String getJsonForEntity(T model) throws  DataDomainApiException {
        try {
            return new Gson().toJson(model);
            /*ObjectMapper mapper = new ObjectMapper();
            mapper.configure(SerializationConfig.Feature.WRAP_ROOT_VALUE, true);
            return mapper.writeValueAsString(model);         */
        }
        catch( Exception e) {
            throw DataDomainApiException.exceptions.jsonWriterReaderException(e);
        }
    }

    private <T> T getResponseObject(Class<T> clazz, ClientResponse response ) throws  DataDomainApiException {
        try {
            JSONObject resp = response.getEntity(JSONObject.class);
            T respObject = new Gson().fromJson(resp.toString(), clazz);
            /*ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationConfig.Feature.UNWRAP_ROOT_VALUE, true);
            mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            T respObject = mapper.readValue(response.getEntity(String.class),clazz);  */
            return respObject;
        }
        catch( Exception e) {
            throw DataDomainApiException.exceptions.jsonWriterReaderException(e);
        }
    }
    
    private ClientResponse doCreateMTree(String ddSystem, String mtreeName, long size) 
    		throws DataDomainApiException {
    	DDMTreeCreate createParam = new DDMTreeCreate();
    	createParam.setName(mtreeName);
    	createParam.setQuota(new DDQuotaConfig());
    	createParam.getQuota().setHardLimit(size);
    	createParam.getQuota().setSoftLimit((long)(size * DataDomainApiConstants.DD_MTREE_SOFT_LIMIT));
    	ClientResponse response = post(DataDomainApiConstants.uriDataDomainMtrees(ddSystem),
    			getJsonForEntity(createParam));
    	return response;
    }
    
    private ClientResponse modifyMTreeRetentionlock(String ddSystem, String mtreeId,
    		Boolean enable, String mode) throws DataDomainApiException{
    	DDQuotaConfig quotaConfig = null;
    	DDRetentionLockSet retentionLockSet = new DDRetentionLockSet(enable, mode);
    	DDMTreeModify modifyParam = new DDMTreeModify(quotaConfig, retentionLockSet);
    	ClientResponse response =  put(DataDomainApiConstants.uriDataDomainMtree(ddSystem, mtreeId),
    			getJsonForEntity(modifyParam));
    	return response;
    }
    
    private ClientResponse doCreateExport(String ddSystem, DDExportCreate ddExport) throws DataDomainApiException{
    	ClientResponse response = post(DataDomainApiConstants.uriDataDomainExports(ddSystem),
    			getJsonForEntity(ddExport));
    	return response;
    }

    private ClientResponse doModifyExport(String ddSystem, String ddExportId,
    		DDExportModify ddExportModify) throws DataDomainApiException{
    	ClientResponse response = put(DataDomainApiConstants.uriDataDomainExport(ddSystem, ddExportId),
    			getJsonForEntity(ddExportModify));
    	return response;
    }

    private ClientResponse doCreateShare(String ddSystem, DDShareCreate ddShare) throws DataDomainApiException{
    	ClientResponse response = post(DataDomainApiConstants.uriDataDomainShares(ddSystem),
    			getJsonForEntity(ddShare));
    	return response;
    }

    public DDMCInfoDetail getManagementSystemInfo() throws DataDomainApiException {
        ClientResponse response  = get(DataDomainApiConstants.URI_DATADOMAIN_SERVICE);
        return getResponseObject(DDMCInfoDetail.class, response);
    }

    public DDSystemList getManagedSystemList() throws DataDomainApiException  {
        ClientResponse response = get(DataDomainApiConstants.URI_DATADOMAIN_SYSTEM_LIST);
        return getResponseObject(DDSystemList.class, response);
    }

    public DDSystem getDDSystem(String system) throws DataDomainApiException {
        ClientResponse response = get(DataDomainApiConstants.uriDataDomainSystem(system));
        return getResponseObject(DDSystem.class, response);
    }
    
    public DDMCInfoDetail getDDSystemInfoDetail(String system) throws DataDomainApiException {
        ClientResponse response = get(DataDomainApiConstants.uriDataDomainSystem(system));
        return getResponseObject(DDMCInfoDetail.class, response);
    }

    public DDMTreeList getMTreeList(String system) throws DataDomainApiException {
    	
    	// As per DD team, the maximum mtrees that DDOS supports is 100. Hence we need 
    	// to request for page with size 100 so we get all MTrees.
    	MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
    	queryParams.add("size", String.valueOf(DataDomainApiConstants.DD_MAX_MTREE_LIMIT));
    	
        ClientResponse response = get(DataDomainApiConstants.uriDataDomainMtrees(system),
        		(MultivaluedMap<String, String>) queryParams);
        return getResponseObject(DDMTreeList.class, response);
    }

    public DDMTreeInfoDetail getMTree(String system,String mtree) throws DataDomainApiException {
        ClientResponse response = get(DataDomainApiConstants.uriDataDomainMtree(system, mtree));
        return getResponseObject(DDMTreeInfoDetail.class, response);
    }
    
    public DDMTreeInfo createMTree(String ddSystem, String mtreeName, long size,
    		Boolean enableRetention, String retentionMode) throws DataDomainApiException{
    	ClientResponse response = doCreateMTree(ddSystem, mtreeName, size);
    	// The following lines are commented out for now because enabling/disabling
    	// retention lock requires a special license
//    	if (response.getStatus() < LOWEST_ERROR_STATUS) {
//    		DDMTreeInfo ddMtree = getResponseObject(DDMTreeInfo.class, response);
//    		response = modifyMTreeRetentionlock(ddSystem, ddMtree.getId(),
//    				enableRetention, retentionMode);
//    	}
    	return getResponseObject(DDMTreeInfo.class, response);
    }

    public DDServiceStatus deleteMTree(String ddSystem, String mtreeId) throws DataDomainApiException{
    	ClientResponse response =  delete(DataDomainApiConstants.uriDataDomainMtree(ddSystem, mtreeId));
    	return getResponseObject(DDServiceStatus.class, response);
    }
    
    public DDMTreeInfo expandMTree(String ddSystem, String mtreeId, long newSize) throws DataDomainApiException{
    	DDQuotaConfig quotaConfig = new DDQuotaConfig();
    	quotaConfig.setHardLimit(newSize);
    	quotaConfig.setSoftLimit((long) (newSize * DataDomainApiConstants.DD_MTREE_SOFT_LIMIT));
    	DDRetentionLockSet retentionLockSet = null;
    	DDMTreeModify modifyParam = new DDMTreeModify(quotaConfig, retentionLockSet);
    	ClientResponse response =  put(DataDomainApiConstants.uriDataDomainMtree(ddSystem, mtreeId), 
    			getJsonForEntity(modifyParam));
    	return getResponseObject(DDMTreeInfo.class, response);
    }

    public DDExportInfoDetail getExport(String ddSystem, String exportId) throws DataDomainApiException{
    	ClientResponse response = get(DataDomainApiConstants.uriDataDomainExport(ddSystem, exportId));
    	return getResponseObject(DDExportInfoDetail.class, response);
    }
    
    public DDExportInfo createExport(String ddSystem, String exportName,
    		List<DDExportClient> exportClients) throws DataDomainApiException{
    	DDExportCreate ddExportCreate = new DDExportCreate(exportName, exportClients);
    	ClientResponse response = doCreateExport(ddSystem, ddExportCreate);
    	return getResponseObject(DDExportInfo.class, response);
    }
    
    public DDExportInfo modifyExport(String ddSystem, String ddExportId,
    		List <DDExportClientModify> ddExportClients) throws DataDomainApiException{
    	DDExportModify ddExportModify = new DDExportModify(ddExportClients);
    	ClientResponse response = doModifyExport(ddSystem, ddExportId, ddExportModify);
    	return getResponseObject(DDExportInfo.class, response);
    }
    
    public DDServiceStatus deleteExport(String ddSystem, String ddExportId) throws DataDomainApiException{
    	ClientResponse response = delete(DataDomainApiConstants.uriDataDomainExport(ddSystem, ddExportId));
    	return getResponseObject(DDServiceStatus.class, response);
    }
    
    public DDShareInfo createShare(String ddSystem, String shareName, String sharePath, 
    		int maxUsers, String description, String permissionType, String permission) 
    				throws DataDomainApiException{
    	DDShareCreate ddShareCreate = new DDShareCreate(shareName, sharePath,
    			maxUsers, description, permissionType, permission);
    	ClientResponse response = doCreateShare(ddSystem, ddShareCreate);
    	return getResponseObject(DDShareInfo.class, response);
    }

    public DDShareInfo modifyShare(String ddSystem, String ddShareId, String description) 
    		throws DataDomainApiException{
		DDShareModify ddShareModify = new DDShareModify(description);
    	ClientResponse response = put(DataDomainApiConstants.uriDataDomainShare(ddSystem, ddShareId),
    			getJsonForEntity(ddShareModify));
    	return getResponseObject(DDShareInfo.class, response);
    }
    
    public DDServiceStatus deleteShare(String ddSystem, String ddShareId) throws DataDomainApiException{
    	ClientResponse response = delete(DataDomainApiConstants.uriDataDomainShare(ddSystem, ddShareId));
    	return getResponseObject(DDServiceStatus.class, response);
    }
    
    public DDShareInfoDetail getShare(String ddSystem, String shareId) throws DataDomainApiException{
    	ClientResponse response = get(DataDomainApiConstants.uriDataDomainShare(ddSystem, shareId));
    	return getResponseObject(DDShareInfoDetail.class, response);
    }
    
    
    public DDShareList getShares(String ddSystem) throws DataDomainApiException{
    	ClientResponse response = get(DataDomainApiConstants.uriDataDomainShares(ddSystem));
    	return getResponseObject(DDShareList.class, response);
    }

    public DDNetworkList getNetworks(String ddSystem)  throws DataDomainApiException{
        ClientResponse response = get(DataDomainApiConstants.uriDataDomainNetworks(ddSystem));
        return getResponseObject(DDNetworkList.class, response);
    }

    public DDNetworkDetails getNetwork(String ddSystem, String network)  throws DataDomainApiException{
        ClientResponse response = get(DataDomainApiConstants.uriDataDomainNetwork(ddSystem, network));
        return getResponseObject(DDNetworkDetails.class, response);
    }

    public DDExportList getExports(String ddSystem )throws DataDomainApiException{
        ClientResponse response = get(DataDomainApiConstants.uriDataDomainExports(ddSystem));
        return getResponseObject(DDExportList.class, response);
    }

    public DDSnapshot createSnapshot(String ddSystem, DDSnapshotCreate ddSnapshotCreate) throws DataDomainApiException{
    	ClientResponse response = post(DataDomainApiConstants.uriDataDomainSnapshots(ddSystem),getJsonForEntity(ddSnapshotCreate));
    	return getResponseObject(DDSnapshot.class, response);
    }

    public DDServiceStatus deleteSnapshot(String ddSystem, String ddSnapshotId) throws DataDomainApiException{
    	ClientResponse response = delete(DataDomainApiConstants.uriDataDomainSnapshot(ddSystem, ddSnapshotId));
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
        ClientResponse response = get(
                DataDomainApiConstants.uriDataDomainSystemStatsCapacity(ddSystem),
                (MultivaluedMap<String, String>) queryParams);
        return getResponseObject(DDStatsCapacityInfos.class, response);
   
    }
    
    public DDStatsInfos getMTreeStatsInfos(String ddSystem, String mtreeId) throws DataDomainApiException{
        ClientResponse response = get(DataDomainApiConstants.uriDataDomainMtreeStats(ddSystem, mtreeId));
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
//        if ((DDStatsIntervalQuery.isMember(interval)) && (dataView.equals(DDStatsDataViewQuery.delta))) {
//            queryParams.add(DataDomainApiConstants.INTERVAL, interval.toString());
//        }
        if (dataView.equals(DDStatsDataViewQuery.delta)) {
            queryParams.add(DataDomainApiConstants.REQUESTED_INTERVAL_ONLY, 
                    String.valueOf(requestedIntervalOnly));
        }
        if ((sort != null) && (sort.endsWith(DataDomainApiConstants.COLLECTION_EPOCH))) {
            queryParams.add(DataDomainApiConstants.SORT, sort);
        }
        ClientResponse response = get(
                DataDomainApiConstants.uriDataDomainMtreeStatsCapacity(ddSystem, mtreeId),
                (MultivaluedMap<String, String>) queryParams);
        return getResponseObject(DDMtreeCapacityInfos.class, response);
    }

}
