/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.MediaType;

import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.services.restutil.StandardRestClient;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.vmax.VMAXConstants;
import com.emc.storageos.vmax.restapi.errorhandling.VMAXException;
import com.emc.storageos.vmax.restapi.model.AsyncJob;
import com.emc.storageos.vmax.restapi.model.ErrorResponse;
import com.emc.storageos.vmax.restapi.model.ForceModel;
import com.emc.storageos.vmax.restapi.model.Symmetrix;
import com.emc.storageos.vmax.restapi.model.SyncModel;
import com.emc.storageos.vmax.restapi.model.VMAXAuthInfo;
import com.emc.storageos.vmax.restapi.model.request.migration.CreateMigrationEnvironmentRequest;
import com.emc.storageos.vmax.restapi.model.request.migration.CreateMigrationRequest;
import com.emc.storageos.vmax.restapi.model.request.migration.MigrationRequest;
import com.emc.storageos.vmax.restapi.model.response.migration.CreateMigrationEnvironmentResponse;
import com.emc.storageos.vmax.restapi.model.response.migration.MigrationEnvironmentListResponse;
import com.emc.storageos.vmax.restapi.model.response.migration.MigrationEnvironmentResponse;
import com.emc.storageos.vmax.restapi.model.response.migration.MigrationStorageGroupListResponse;
import com.emc.storageos.vmax.restapi.model.response.migration.MigrationStorageGroupResponse;
import com.emc.storageos.vmax.restapi.model.response.system.GetSymmetrixResponse;
import com.emc.storageos.vmax.restapi.model.response.system.ListSymmetrixResponse;
import com.emc.storageos.vmax.restapi.model.response.system.SystemVersionResponse;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;

public class VMAXApiClient extends StandardRestClient {
    private static Logger log = LoggerFactory.getLogger(VMAXApiClient.class);

    public VMAXApiClient(URI baseURI, String username, String password, Client client) {
        _client = client;
        _base = baseURI;
        _username = username;
        _password = password;
        _authToken = "";
    }

    /**
     * Returns unisphere ip address
     * 
     * @return {@link String}
     */
    public String getIpAddress() {
        return _base.getHost();
    }

    /**
     * Returns port number
     * 
     * @return {@link String}
     */
    public int getPortNumber() {
        return _base.getPort();
    }

    @Override
    protected Builder setResourceHeaders(WebResource resource) {
        return resource.header(VMAXConstants.AUTH_TOKEN, _authToken).header(VMAXConstants.APPLICATION_TYPE,
                VMAXConstants.VIPR_APPLICATION_TYPE);
    }

    @Override
    protected void authenticate() {
        ClientResponse response = null;
        try {
            VMAXAuthInfo authInfo = new VMAXAuthInfo();
            authInfo.setPassword(_password);
            authInfo.setUsername(_username);

            String body = getJsonForEntity(authInfo);

            URI requestURI = _base.resolve(URI.create(VMAXConstants.UNIVMAX_BASE_URI));
            response = _client.resource(requestURI).type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, body);

            if (response.getClientResponseStatus() != ClientResponse.Status.OK
                    && response.getClientResponseStatus() != ClientResponse.Status.CREATED) {
                throw VMAXException.exceptions.authenticationFailure(_base.toString());
            }
            _authToken = response.getHeaders().getFirst(VMAXConstants.AUTH_TOKEN_HEADER);
        } catch (Exception e) {
            throw VMAXException.exceptions.authenticationFailure(_base.toString());
        } finally {
            closeResponse(response);
        }
    }

    @Override
    protected int checkResponse(URI uri, ClientResponse response) {
        ClientResponse.Status status = response.getClientResponseStatus();
        int errorCode = status.getStatusCode();
        if (errorCode >= 300) {

            JSONObject obj = null;
            String extraExceptionInfo = null;

            try {
                ErrorResponse errorResponse = getResponseObject(ErrorResponse.class, response);
                log.error("Error Response received from Unisphere :{}", errorResponse);
                extraExceptionInfo = errorResponse.getMessage();
            } catch (Exception e) {
                extraExceptionInfo = e.getMessage();
                log.error("Parsing the failure response object failed", e);
            }

            if (errorCode == 404 || errorCode == 410) {
                throw VMAXException.exceptions.resourceNotFound(uri.toString(), extraExceptionInfo);
            } else if (errorCode == 401) {
                throw VMAXException.exceptions.authenticationFailure(uri.toString());
            } else {
                // Sometimes the response object can be null, just set it to empty when it is null.
                String objStr = (obj == null) ? "" : obj.toString();
                // Append extra exception info if present
                if (extraExceptionInfo != null) {
                    objStr = String.format("%s%s",
                            (objStr.isEmpty()) ? objStr : objStr + " | ", extraExceptionInfo);
                }
                throw VMAXException.exceptions.internalError(uri.toString(), objStr);
            }
        } else {
            return errorCode;
        }
    }

    @Override
    public ClientResponse post(URI uri, String body) throws InternalException {
        ClientResponse response = null;
        log.info(String.format("Calling POST %s with data %s", uri.toString(), body));
        response = super.post(uri, body);
        return response;
    }

    @Override
    public ClientResponse get(URI uri) throws InternalException {
        ClientResponse response = null;
        log.info("Calling GET {}", uri.toString());
        response = super.get(uri);
        return response;
    }

    /**
     * Wrapper of post method to ignore the response
     *
     * @param uri URI
     * @param body request body string
     * @throws InternalException
     */
    public void postIgnoreResponse(URI uri, String body) throws InternalException {
        ClientResponse response = null;
        try {
            log.info(String.format("Calling POST %s with data %s", uri.toString(), body));
            response = super.post(uri, body);
        } finally {
            closeResponse(response);
        }
    }

    @Override
    public ClientResponse put(URI uri, String body) throws InternalException {
        ClientResponse response = null;
        log.info(String.format("Calling PUT %s with data %s", uri.toString(), body));
        response = super.put(uri, body);
        return response;
    }

    public void putIgnoreResponse(URI uri, String body) throws InternalException {
        ClientResponse response = null;
        try {
            log.info(String.format("Calling PUT %s with data %s", uri.toString(), body));
            response = super.put(uri, body);
        } finally {
            closeResponse(response);
        }
    }

    @Override
    public ClientResponse delete(URI uri) throws InternalException {
        ClientResponse response = null;
        try {
            log.info("Calling DELETE {}", uri.toString());
            response = super.delete(uri);
        } finally {
            closeResponse(response);
        }
        return null;
    }

    @Override
    public ClientResponse delete(URI uri, String body) throws InternalException {
        ClientResponse response = null;
        try {
            log.info(String.format("Calling DELETE %s with data %s", uri.toString(), body));
            response = super.delete(uri, body);
        } finally {
            closeResponse(response);
        }
        return null;
    }

    /**
     * Returns migration environment status if environment is available between source and target.
     * 
     * @param sourceArraySerialNumber Source Array Serial number
     * @param targetArraySerialNumber Target Array Serial number
     * @return
     * @throws Exception
     */
    public MigrationEnvironmentResponse getMigrationEnvironment(String sourceArraySerialNumber, String targetArraySerialNumber)
            throws Exception {
        ClientResponse clientResponse = get(
                VMAXConstants.getValidateEnvironmentURI(sourceArraySerialNumber, targetArraySerialNumber));
        MigrationEnvironmentResponse environmentResponse = getResponseObject(MigrationEnvironmentResponse.class, clientResponse);
        log.info("Response -> :{}", environmentResponse);
        return environmentResponse;
    }

    /**
     * Get Unisphere REST API version
     *
     * @return API version
     * @throws Exception
     */
    public String getApiVersion() throws Exception {
        ClientResponse clientResponse = get(VMAXConstants.getVersionURI());
        SystemVersionResponse response = getResponseObject(SystemVersionResponse.class, clientResponse);
        return response.getVersion().replaceFirst("[^\\d-]", "");
    }

    /**
     * Get local storage systems
     *
     * @return set of storage system IDs
     * @throws Exception
     */
    public Set<String> getLocalSystems() throws Exception {
        Set<String> localSystems = new HashSet<>();
        ClientResponse clientResponse = get(VMAXConstants.getSystemListURI());
        ListSymmetrixResponse response = getResponseObject(ListSymmetrixResponse.class, clientResponse);

        List<String> systems = response.getSymmetrixId();
        if (!CollectionUtils.isEmpty(systems)) {
            for (String system : systems) {
                clientResponse = get(VMAXConstants.getSystemGetURI(system));
                GetSymmetrixResponse symmResponse = getResponseObject(GetSymmetrixResponse.class, clientResponse);
                List<Symmetrix> symmSystems = symmResponse.getSymmetrix();
                if (!CollectionUtils.isEmpty(symmSystems)) {
                    Symmetrix symmSystem = symmSystems.get(0);
                    if (symmSystem != null && symmSystem.isLocal()) {
                        localSystems.add(system);
                    }
                }
            }
        }

        return localSystems;
    }

    /**
     * Returns list of available migration environments for the given array
     * 
     * @param sourceArraySerialNumber
     * @return {@link MigrationEnvironmentListResponse}.getArrayList() will returns the other arrays serial number
     * @throws Exception
     */
    public MigrationEnvironmentListResponse getMigrationEnvironmentList(String sourceArraySerialNumber) throws Exception {
        ClientResponse clientResponse = get(
                VMAXConstants.getMigrationEnvironmentURI(sourceArraySerialNumber));
        MigrationEnvironmentListResponse environmentResponse = getResponseObject(MigrationEnvironmentListResponse.class, clientResponse);
        log.info("Response -> :{}", environmentResponse);
        return environmentResponse;
    }

    /**
     * Deletes the existing migration environment for the given source and target array
     * 
     * @param sourceArraySerialNumber
     * @param targetArraySerialNumber
     * @throws Exception
     */
    public void deleteMigrationEnvironment(String sourceArraySerialNumber, String targetArraySerialNumber) throws Exception {
        delete(VMAXConstants.getValidateEnvironmentURI(sourceArraySerialNumber, targetArraySerialNumber));
        log.info("Deleted migration environment between {} and {}", sourceArraySerialNumber, targetArraySerialNumber);
    }

    /**
     * Creates new migration environment for the given source and target arrays
     * 
     * @param sourceArraySerialNumber
     * @param targetArraySerialNumber
     * @return {@link CreateMigrationEnvironmentResponse}
     * @throws InternalException
     * @throws Exception
     */
    public CreateMigrationEnvironmentResponse createMigrationEnvironment(String sourceArraySerialNumber, String targetArraySerialNumber)
            throws InternalException, Exception {
        log.info("Started Create Migration environment call between {} and {}", sourceArraySerialNumber, targetArraySerialNumber);
        CreateMigrationEnvironmentRequest createMigrationEnvironmentRequest = new CreateMigrationEnvironmentRequest();
        createMigrationEnvironmentRequest.setOtherArrayId(targetArraySerialNumber);
        ClientResponse clientResponse = post(VMAXConstants.createMigrationEnvornmentURI(sourceArraySerialNumber),
                getJsonForEntity(createMigrationEnvironmentRequest));
        CreateMigrationEnvironmentResponse response = getResponseObject(CreateMigrationEnvironmentResponse.class, clientResponse);
        log.info("Response -> :{}", response);
        log.info("Successfully created migration environment between {} and {}", sourceArraySerialNumber, targetArraySerialNumber);
        return response;
    }

    /**
     * Get all migration storage group names for the given array
     * 
     * @param sourceArraySerialNumber
     * @return {@link MigrationStorageGroupListResponse}
     * @throws Exception
     */
    public MigrationStorageGroupListResponse getMigrationStorageGroups(String sourceArraySerialNumber) throws Exception {
        log.info("Get all migration storage groups available for the array {}", sourceArraySerialNumber);
        ClientResponse clientResponse = get(VMAXConstants.getMigrationStorageGroupsURI(sourceArraySerialNumber));
        MigrationStorageGroupListResponse migrationStorageGroupListResponse = getResponseObject(
                MigrationStorageGroupListResponse.class, clientResponse);
        log.info("Response -> :{}", migrationStorageGroupListResponse);
        return migrationStorageGroupListResponse;
    }

    /**
     * Get migration storage group for the given array
     * 
     * @param sourceArraySerialNumber
     * @param storageGroupName
     * @return {@link MigrationStorageGroupResponse}
     * @throws Exception
     */
    public MigrationStorageGroupResponse getMigrationStorageGroup(String sourceArraySerialNumber, String storageGroupName)
            throws Exception {
        log.info("Get migration storage group {} from array {}", storageGroupName, sourceArraySerialNumber);
        ClientResponse clientResponse = get(VMAXConstants.migrationStorageGroupURI(sourceArraySerialNumber, storageGroupName));
        MigrationStorageGroupResponse migrationStorageGroupResponse = getResponseObject(MigrationStorageGroupResponse.class,
                clientResponse);
        log.info("Response -> :{}", migrationStorageGroupResponse);
        return migrationStorageGroupResponse;
    }

    /**
     * Create migration for the given SG from array sourceArraySerialNumber to targetArraySerialNumber
     * 
     * @param sourceArraySerialNumber
     * @param targetArraySerialNumber
     * @param storageGroupName
     * @param noCompression
     * @param srpId
     * @return {@link AsyncJob}
     * @throws Exception
     */
    public AsyncJob createMigration(String sourceArraySerialNumber, String targetArraySerialNumber, String storageGroupName,
            boolean noCompression, String srpId) throws Exception {
        log.info("Create migration for the storage group {} on source array {} to target array{}", storageGroupName,
                sourceArraySerialNumber, targetArraySerialNumber);
        CreateMigrationRequest request = new CreateMigrationRequest();
        request.setOtherArrayId(targetArraySerialNumber);
        request.setExecutionOption(VMAXConstants.ASYNCHRONOUS_API_CALL);
        if (noCompression) {
            request.setNoCompression(noCompression);
        }
        request.setSrpId(srpId);
        log.info("Request -> {}", request);
        ClientResponse response = post(VMAXConstants.migrationStorageGroupURI(sourceArraySerialNumber, storageGroupName),
                getJsonForEntity(request));
        AsyncJob asyncJob = getResponseObject(AsyncJob.class, response);
        log.info("Async Job Response -> :{} ", asyncJob);
        log.info("Successfully initiated create migration");
        return asyncJob;
    }

    /**
     * Recover Migration for the given SG
     * 
     * @param sourceArraySerialNumber Source Array Serial Number
     * @param storageGroupName Storage Group Name
     * @param forceOperation
     * @return {@link AsyncJob}
     * @throws Exception
     */
    public AsyncJob recoverMigration(String sourceArraySerialNumber, String storageGroupName, boolean forceOperation) throws Exception {
        log.info("Recover migration for the SG {} for the array {}", storageGroupName, sourceArraySerialNumber);
        MigrationRequest request = new MigrationRequest();
        request.setAction(VMAXConstants.MigrationActionTypes.Recover.name());
        request.setExecutionOption(VMAXConstants.ASYNCHRONOUS_API_CALL);
        if (forceOperation) {
            log.info("Adding force flag in the json payload");
            ForceModel forceModel = new ForceModel();
            forceModel.setForce(forceOperation);
            request.setRecover(forceModel);
        }
        ClientResponse response = put(VMAXConstants.migrationStorageGroupURI(sourceArraySerialNumber, storageGroupName),
                getJsonForEntity(request));
        AsyncJob asyncJob = getResponseObject(AsyncJob.class, response);
        log.info("Successfully initiated receover migration");
        return asyncJob;
    }

    /**
     * Cutover Migration for the given SG
     * 
     * @param sourceArraySerialNumber Source Array Serial Number
     * @param storageGroupName Storage Group Name
     * @return {@link AsyncJob}
     * @throws Exception
     */
    public AsyncJob cutoverMigration(String sourceArraySerialNumber, String storageGroupName) throws Exception {
        log.info("Cutover migration for the SG {} for the array {}", storageGroupName, sourceArraySerialNumber);
        MigrationRequest request = new MigrationRequest();
        request.setAction(VMAXConstants.MigrationActionTypes.Cutover.name());
        request.setExecutionOption(VMAXConstants.ASYNCHRONOUS_API_CALL);
        ClientResponse response = put(VMAXConstants.migrationStorageGroupURI(sourceArraySerialNumber, storageGroupName),
                getJsonForEntity(request));
        AsyncJob asyncJob = getResponseObject(AsyncJob.class, response);
        log.info("Successfully initiated cutover migration");
        return asyncJob;
    }

    /**
     * Cancel Migration for the given SG
     * 
     * @param sourceArraySerialNumber Source Array Serial Number
     * @param storageGroupName Storage Group Name
     * @throws Exception
     */
    public void cancelMigration(String sourceArraySerialNumber, String storageGroupName) throws Exception {
        log.info("Cancel migration for the SG {} for the array {}", storageGroupName, sourceArraySerialNumber);
        delete(VMAXConstants.cancelMigrationURI(sourceArraySerialNumber, storageGroupName));
        log.info("Successfully initiated cancel migration");
    }

    /**
     * Cancel Migration for the given SG (with revert)
     * 
     * @param sourceArraySerialNumber Source Array Serial Number
     * @param storageGroupName Storage Group Name
     * @throws Exception
     */
    public void cancelMigrationWithRevert(String sourceArraySerialNumber, String storageGroupName) throws Exception {
        log.info("Cancel migration for the SG {} for the array {}", storageGroupName, sourceArraySerialNumber);
        delete(VMAXConstants.cancelMigrationWithRevertURI(sourceArraySerialNumber, storageGroupName));
        log.info("Successfully initiated cancel migration");
    }

    /**
     * Commit Migration for the given SG
     * 
     * @param sourceArraySerialNumber Source Array Serial Number
     * @param storageGroupName Storage Group Name
     * @return {@link AsyncJob}
     * @throws Exception
     */
    public AsyncJob commitMigration(String sourceArraySerialNumber, String storageGroupName) throws Exception {
        log.info("Commit migration for the SG {} for the array {}", storageGroupName, sourceArraySerialNumber);
        MigrationRequest request = new MigrationRequest();
        request.setAction(VMAXConstants.MigrationActionTypes.Commit.name());
        request.setExecutionOption(VMAXConstants.ASYNCHRONOUS_API_CALL);
        ClientResponse response = put(VMAXConstants.migrationStorageGroupURI(sourceArraySerialNumber, storageGroupName),
                getJsonForEntity(request));
        AsyncJob asyncJob = getResponseObject(AsyncJob.class, response);
        log.info("Successfully initiated commit migration");
        return asyncJob;
    }

    /**
     * Delete Migration for the given SG
     * 
     * @param sourceArraySerialNumber Source Array Serial Number
     * @param storageGroupName Storage Group Name
     * @throws Exception
     */
    public void deleteMigration(String sourceArraySerialNumber, String storageGroupName) throws Exception {
        log.info("Delete migration for the SG {} for the array {}", storageGroupName, sourceArraySerialNumber);
        delete(VMAXConstants.migrationStorageGroupURI(sourceArraySerialNumber, storageGroupName));
        log.info("Successfully initiated delete migration");
    }

    /**
     * Start migration sync for the given SG
     * 
     * @param sourceArraySerialNumber Source Array Serial Number
     * @param storageGroupName Storage Group Name
     * @return {@link AsyncJob}AsyncJob
     * @throws Exception
     */
    public AsyncJob startMigrationSync(String sourceArraySerialNumber, String storageGroupName) throws Exception {
        log.info("Start migration sync for the SG {} for the array {}", storageGroupName, sourceArraySerialNumber);
        SyncModel syncModel = new SyncModel();
        syncModel.setStart(true);
        MigrationRequest request = new MigrationRequest();
        request.setAction(VMAXConstants.MigrationActionTypes.Sync.name());
        request.setExecutionOption(VMAXConstants.ASYNCHRONOUS_API_CALL);
        request.setSync(syncModel);
        ClientResponse response = put(VMAXConstants.migrationStorageGroupURI(sourceArraySerialNumber, storageGroupName),
                getJsonForEntity(request));
        AsyncJob asyncJob = getResponseObject(AsyncJob.class, response);
        log.info("Successfully initiated start sync operation");
        return asyncJob;
    }

    /**
     * Stop migration sync for the given SG
     * 
     * @param sourceArraySerialNumber Source Array Serial Number
     * @param storageGroupName Storage Group Name
     * @return {@link AsyncJob}AsyncJob
     * @throws Exception
     */
    public AsyncJob stopMigrationSync(String sourceArraySerialNumber, String storageGroupName) throws Exception {
        log.info("Stop migration sync for the SG {} for the array {}", storageGroupName, sourceArraySerialNumber);
        SyncModel syncModel = new SyncModel();
        syncModel.setStop(true);
        MigrationRequest request = new MigrationRequest();
        request.setAction(VMAXConstants.MigrationActionTypes.Sync.name());
        request.setExecutionOption(VMAXConstants.ASYNCHRONOUS_API_CALL);
        request.setSync(syncModel);
        ClientResponse response = put(VMAXConstants.migrationStorageGroupURI(sourceArraySerialNumber, storageGroupName),
                getJsonForEntity(request));
        AsyncJob asyncJob = getResponseObject(AsyncJob.class, response);
        log.info("Successfully initiated stop sync operation");
        return asyncJob;
    }

    /**
     * Get async job instance from unisphere for the given jobId
     * 
     * @param jobId
     * @return
     * @throws Exception
     */
    public AsyncJob getAsyncJob(String jobId) throws Exception {
        log.info("Get job {} status", jobId);
        ClientResponse clientResponse = get(
                VMAXConstants.getAsyncJobURI(jobId));
        AsyncJob asyncJobResponse = getResponseObject(AsyncJob.class, clientResponse);
        log.info("Successfully collected async job object");
        return asyncJobResponse;
    }

}
