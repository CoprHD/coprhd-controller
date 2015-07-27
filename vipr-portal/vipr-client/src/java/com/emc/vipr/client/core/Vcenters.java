/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.impl.SearchConstants.VALIDATE_CONNECTION_PARAM;
import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.host.vcenter.VcenterBulkRep;
import com.emc.storageos.model.host.vcenter.VcenterCreateParam;
import com.emc.storageos.model.host.vcenter.VcenterList;
import com.emc.storageos.model.host.vcenter.VcenterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterUpdateParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.client.core.util.ResourceUtils;

/**
 * Vcenters resources.
 * <p>
 * Base URL: <tt>/compute/vcenters</tt>
 */
public class Vcenters extends AbstractCoreBulkResources<VcenterRestRep> implements TenantResources<VcenterRestRep>,
        TaskResources<VcenterRestRep> {
    public Vcenters(ViPRCoreClient parent, RestClient client) {
        super(parent, client, VcenterRestRep.class, PathConstants.VCENTER_URL);
    }

    @Override
    public Vcenters withInactive(boolean inactive) {
        return (Vcenters) super.withInactive(inactive);
    }

    @Override
    public Vcenters withInternal(boolean internal) {
        return (Vcenters) super.withInternal(internal);
    }

    @Override
    protected List<VcenterRestRep> getBulkResources(BulkIdParam input) {
        VcenterBulkRep response = client.post(VcenterBulkRep.class, input, getBulkUrl());
        return defaultList(response.getVcenters());
    }

    @Override
    public Tasks<VcenterRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<VcenterRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }

    /**
     * Gets a list of vCenters from the given path.
     * 
     * @param path
     *        the path to get.
     * @param args
     *        the path arguments.
     * @return the list of vCenter references.
     */
    protected List<NamedRelatedResourceRep> getList(String path, Object... args) {
        VcenterList response = client.get(VcenterList.class, path, args);
        return ResourceUtils.defaultList(response.getVcenters());
    }

    /**
     * Lists the vCenters for the given tenant by ID.
     * <p>
     * API Call: <tt>GET /tenants/{tenantId}/vcenters</tt>
     * 
     * @param tenantId
     *        the ID of the tenant.
     * @return the list of vCenter references.
     */
    @Override
    public List<NamedRelatedResourceRep> listByTenant(URI tenantId) {
        return getList(PathConstants.VCENTER_BY_TENANT_URL, tenantId);
    }

    @Override
    public List<VcenterRestRep> getByTenant(URI tenantId) {
        return getByTenant(tenantId, null);
    }

    @Override
    public List<NamedRelatedResourceRep> listByUserTenant() {
        return listByTenant(parent.getUserTenantId());
    }

    @Override
    public List<VcenterRestRep> getByUserTenant() {
        return getByTenant(parent.getUserTenantId());
    }

    @Override
    public List<VcenterRestRep> getByUserTenant(ResourceFilter<VcenterRestRep> filter) {
        return getByTenant(parent.getUserTenantId(), filter);
    }

    @Override
    public List<VcenterRestRep> getByTenant(URI tenantId, ResourceFilter<VcenterRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByTenant(tenantId);
        return getByRefs(refs, filter);
    }

    /**
     * Begins creating a vCenter for the given tenant by ID.
     * <p>
     * API Call: <tt>POST /tenants/{tenantId}/vcenters</tt>
     * 
     * @param tenantId
     *        the ID of the tenant.
     * @param input
     *        the create configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<VcenterRestRep> create(URI tenantId, VcenterCreateParam input) {
        return create(tenantId, input, false);
    }
    
    public Task<VcenterRestRep> create(URI tenantId, VcenterCreateParam input, Boolean validateConnection) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.VCENTER_BY_TENANT_URL);
        if (validateConnection) {
            uriBuilder.queryParam(VALIDATE_CONNECTION_PARAM, Boolean.TRUE);
        }
        return postTaskURI(input, uriBuilder.build(tenantId));         
    }    
    
    /**
     * Begins updating the given vCenter by ID.
     * <p>
     * API Call: <tt>PUT /compute/vcenter/{id}</tt>
     * 
     * @param id
     *        the ID of the vCenter.
     * @param input
     *        the update configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<VcenterRestRep> update(URI id, VcenterUpdateParam input) {
        return update(id, input, false);
    }
    
    public Task<VcenterRestRep> update(URI id, VcenterUpdateParam input, boolean validateConnection) {
        UriBuilder uriBuilder = client.uriBuilder(getIdUrl());
        if (validateConnection) {
            uriBuilder.queryParam(VALIDATE_CONNECTION_PARAM, Boolean.TRUE);
        }
        return putTaskURI(input, uriBuilder.build(id));        
    }    

    /**
     * Deactivates the given vCenter by ID.
     * <p>
     * API Call: <tt>POST /compute/vcenter/{id}/deactivate</tt>
     * 
     * @param id
     *        the ID of the vCenter to deactivate.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<VcenterRestRep> deactivate(URI id) {
        return doDeactivateWithTask(id);
    }
    
    /**
     * Deactivates a vCenter.
     * <p>
     * API Call: <tt>POST /compute/vcenters/{id}/deactivate?detach-storage={detachStorage}</tt>
     * 
     * @param id
     *        the ID of the vCenter to deactivate.
     * @param detachStorage
     *        if true, will first detach storage.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<VcenterRestRep> deactivate(URI id, boolean detachStorage) {
        URI deactivateUri = client.uriBuilder(getDeactivateUrl()).queryParam("detach-storage", detachStorage).build(id);
        return postTaskURI(deactivateUri);
    }
    
    /**
     * Detaches storage from a vCenter.
     * <p>
     * API Call: <tt>POST /compute/vcenters/{id}/detach-storage</tt>
     * 
     * @param id
     *        the ID of the host.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<VcenterRestRep> detachStorage(URI id) {
        return postTask(PathConstants.VCENTER_DETACH_STORAGE_URL, id);
    }
    
    /**
     * Begins discovery of the given vCenter by ID.
     * <p>
     * API Call: <tt>POST /compute/vcenters/{id}/discover</tt>
     * 
     * @param id
     *        the ID of the vCenter to discover.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<VcenterRestRep> discover(URI id) {
        return postTask(getIdUrl() + "/discover", id);
    }
}