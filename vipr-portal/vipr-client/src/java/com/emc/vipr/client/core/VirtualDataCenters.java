/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.vdc.*;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.sun.jersey.api.client.ClientResponse;

import java.net.URI;
import java.util.List;
import java.util.Properties;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

/**
 * VDC resources.
 * <p>
 * Base URL: <tt>/vdc</tt>
 */
public class VirtualDataCenters extends AbstractCoreResources<VirtualDataCenterRestRep> implements TopLevelResources<VirtualDataCenterRestRep> {
    private static final String EXPECTED_VERSION_QUERY_PARAM = "expect_version";
    public VirtualDataCenters(ViPRCoreClient parent, RestClient client) {
        super(parent, client, VirtualDataCenterRestRep.class, PathConstants.VDC_URL);
    }

    @Override
    public VirtualDataCenters withInactive(boolean inactive) {
        return (VirtualDataCenters) super.withInactive(inactive);
    }

    public List<NamedRelatedResourceRep> list() {
    	VirtualDataCenterList response = 
    			client.get(VirtualDataCenterList.class, PathConstants.VDC_URL);
    	return defaultList(response.getVirtualDataCenters());
    }

    /**
     * Creates a vdc.
     * <p>
     * API Call: <tt>POST /vdc/</tt>
     * 
     * @param input
     *        the vdc configuration.
     * @return Task<VirtualDataCenterRestRep> the task to create the vdc.
     */
    public Task<VirtualDataCenterRestRep> create(VirtualDataCenterAddParam input) {
    	return postTask(input, PathConstants.VDC_URL);
    }
    
    /**
     * Updates a vdc.
     * <p>
     * API Call: <tt>PUT /vdc/{id}</tt>
     *
     * @param id
     *        the ID of the vdc to update.
     * @param input
     *        the vdc configuration.
     * @return Task<VirtualDataCenterRestRep> the task to update the vdc.
     */
    public Task<VirtualDataCenterRestRep>  update(URI id, VirtualDataCenterModifyParam input) {
    	return putTask(input, getIdUrl(), id);
    }

    /**
     * Deletes the given vdc by ID.
     * <p>
     * API Call: <tt>DELETE /vdc/{id}</tt>
     * 
     * @param id
     *        the ID of the vdc to delete.
     * @return Task<VirtualDataCenterRestRep> the task to delete the vdc.
     */
    public Task<VirtualDataCenterRestRep>  delete(URI id) {
        TaskResourceRep task = client.delete(TaskResourceRep.class, getIdUrl(), id);
        return new Task<VirtualDataCenterRestRep>(client, task, resourceClass);
    }
    
    /**
     * Reconnects the given vdc by ID.
     * <p>
     * API Call: <tt>POST /vdc/{id}/reconnect</tt>
     * 
     * @param id
     *        the ID of the vdc to reconnect.
     * @return Task<VirtualDataCenterRestRep> the task to reconnect the vdc.
     */
    public Task<VirtualDataCenterRestRep> reconnect(URI id) {
    	return postTask(getReconnectUrl(), id);
    }
    
    /**
     * Disconnects the given vdc by ID.
     * <p>
     * API Call: <tt>POST /vdc/{id}/disconnect</tt>
     * 
     * @param id
     *        the ID of the vdc to disconnect.
     * @return Task<VirtualDataCenterRestRep> the task to disconnect the vdc.
     */
    public Task<VirtualDataCenterRestRep> disconnect(URI id) {
    	return postTask(getDisconnectUrl(), id);
    }
    
    /**
     * gets the secret key.
     * <p>
     * API Call: <tt>POST /vdc/secret-key</tt>
     * 
     * @return VirtualDataCenterSecretKeyRestRep the vdc secret
     * 				key response.
     */
    public VirtualDataCenterSecretKeyRestRep getSecretKey() {
        return client.get(VirtualDataCenterSecretKeyRestRep.class, 
        		PathConstants.VDC_SECRET_KEY_URL);
    }
    
    /**
     * Gets the URL for disconnecting a vdc.
     * 
     * @return the disconnect URL.
     */
    protected String getDisconnectUrl() {
        return String.format(PathConstants.DISCONNECT_URL_FORMAT, baseUrl);
    }
    
    /**
     * Gets the URL for reconnecting a vdc.
     * 
     * @return the reconnect URL.
     */
    protected String getReconnectUrl() {
        return String.format(PathConstants.RECONNECT_URL_FORMAT, baseUrl);
    }

    @Override
    public List<VirtualDataCenterRestRep> getAll() {
        return getByRefs(list());
    }

    @Override
    public List<VirtualDataCenterRestRep> getAll(ResourceFilter<VirtualDataCenterRestRep> filter) {
        return getByRefs(list(), filter);
    }
    
    public Tasks<VirtualDataCenterRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    /**
     * A VDC compatibility check to see of all the VDCs in the federation are in the
     * minimum expected version or not. This can be used in the UI to restrict a
     * view of a feature.
     * *
     * API Call: <tt>GET /vdc/check-compatibility?expect_version={expectedVersion}
     *
     * @param expectedVersion minimum expected version of all the VDCs in the federation.
     *
     * @return true if the all the VDCs are in equal or higher version of the expectedVersion
     * otherwise false.
     */
    public boolean isCompatibleVDCVersion (String expectedVersion) {
        Properties queryParams = new Properties();
        queryParams.put(EXPECTED_VERSION_QUERY_PARAM, expectedVersion);

        ClientResponse resp = client.get(ClientResponse.class, PathConstants.CHECK_COMPATIBLE_VDC_URL, queryParams);
        return Boolean.parseBoolean(resp.getEntity(String.class));
    }
    
    /**
     * A check to see if the setup is geo-distributed multi-vdc setup.
     * This can be used in the UI to restrict a view of a feature.
     * *
     * API Call: <tt>GET /vdc/check-geo-distributed
     *
     * @return true if the setup is geo-distributed/multi-vdc setup
     * otherwise false.
     */
    public boolean isGeoSetup () {
        ClientResponse resp = client.get(ClientResponse.class, PathConstants.CHECK_IS_GEO_DISTRIBUTED_VDC_URL);
        return Boolean.parseBoolean(resp.getEntity(String.class));
    }
}