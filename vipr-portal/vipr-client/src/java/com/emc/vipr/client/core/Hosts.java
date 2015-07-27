/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.impl.SearchConstants.TENANT_PARAM;
import static com.emc.vipr.client.core.impl.SearchConstants.VALIDATE_CONNECTION_PARAM;
import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.compute.OsInstallParam;
import com.emc.storageos.model.host.HostBulkRep;
import com.emc.storageos.model.host.HostCreateParam;
import com.emc.storageos.model.host.HostList;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.HostUpdateParam;
import com.emc.storageos.model.host.ProvisionBareMetalHostsParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

/**
 * Hosts resources.
 * <p>
 * Base URL: <tt>/compute/hosts</tt>
 */
public class Hosts extends AbstractCoreBulkResources<HostRestRep> implements TenantResources<HostRestRep>,
        TaskResources<HostRestRep> {

    public Hosts(ViPRCoreClient parent, RestClient client) {
        super(parent, client, HostRestRep.class, PathConstants.HOST_URL);
    }

    @Override
    public Hosts withInactive(boolean inactive) {
        return (Hosts) super.withInactive(inactive);
    }

    @Override
    public Hosts withInternal(boolean internal) {
        return (Hosts) super.withInternal(internal);
    }

    @Override
    protected List<HostRestRep> getBulkResources(BulkIdParam input) {
        HostBulkRep response = client.post(HostBulkRep.class, input, getBulkUrl());
        return defaultList(response.getHosts());
    }

    @Override
    public Tasks<HostRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<HostRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }

    /**
     * Gets a list of host references from the given path.
     * 
     * @param path
     *        the path to get.
     * @param args
     *        the path arguments.
     * @return the list of host references.
     */
    protected List<NamedRelatedResourceRep> getList(String path, Object... args) {
        HostList response = client.get(HostList.class, path, args);
        return ResourceUtils.defaultList(response.getHosts());
    }

    /**
     * Lists the hosts by tenant.
     * <p>
     * API Call: <tt>GET /compute/hosts?tenant={tenantId}</tt>
     * 
     * @param tenantId
     *        the ID of the tenant.
     * @return the list of host references.
     */
	@Override
	public List<NamedRelatedResourceRep> listByTenant(URI tenantId) {
		UriBuilder uriBuilder = client.uriBuilder(baseUrl);
		if (tenantId != null) {
			uriBuilder.queryParam(TENANT_PARAM, tenantId);
		}
		HostList response = client.getURI(HostList.class, uriBuilder.build());
		return defaultList(response.getHosts());
	}

    @Override
    public List<NamedRelatedResourceRep> listByUserTenant() {
        return listByTenant(null);
    }

    @Override
    public List<HostRestRep> getByTenant(URI tenantId) {
        return getByTenant(tenantId, null);
    }

    @Override
    public List<HostRestRep> getByUserTenant() {
        return getByTenant(parent.getUserTenantId(), null);
    }

    @Override
    public List<HostRestRep> getByUserTenant(ResourceFilter<HostRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByTenant(parent.getUserTenantId());
        return getByRefs(refs, filter);
    }

    @Override
    public List<HostRestRep> getByTenant(URI tenantId, ResourceFilter<HostRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByTenant(tenantId);
        return getByRefs(refs, filter);
    }

    /**
     * Lists the hosts in the given datacenter.
     * <p>
     * API Call: <tt>GET /compute/vcenter-data-centers/{dataCenterId}/hosts</tt>
     * 
     * @param dataCenterId
     *        the ID of the datacenter.
     * @return the list of host references.
     */
    public List<NamedRelatedResourceRep> listByDataCenter(URI dataCenterId) {
        return getList(PathConstants.HOST_BY_DATACENTER_URL, dataCenterId);
    }

    /**
     * Gets the list of hosts in the given datacenter.
     * 
     * @param dataCenterId
     *        the ID of the datacenter.
     * @return the list of hosts.
     */
    public List<HostRestRep> getByDataCenter(URI dataCenterId) {
        return getByDataCenter(dataCenterId, null);
    }

    /**
     * Gets the list of hosts in a given datacenter, optionally filtering the results.
     * 
     * @param dataCenterId
     *        the ID of the datacenter.
     * @param filter
     *        the resource filter to apply to the results as they are returned (optional).
     * @return the list of hosts.
     */
    public List<HostRestRep> getByDataCenter(URI dataCenterId, ResourceFilter<HostRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByDataCenter(dataCenterId);
        return getByRefs(refs, filter);
    }

    /**
     * Lists the hosts in the given cluster.
     * <p>
     * API Call: <tt>GET /compute/clusters/{clusterId}/hosts</tt>
     * 
     * @param clusterId
     *        the ID of the cluster.
     * @return the list of host references.
     */
    public List<NamedRelatedResourceRep> listByCluster(URI clusterId) {
        return getList(PathConstants.HOST_BY_CLUSTER_URL, clusterId);
    }

    /**
     * Gets the list of hosts in the given cluster.
     * 
     * @param clusterId
     *        the ID of the cluster.
     * @return the list of hosts.
     */
    public List<HostRestRep> getByCluster(URI clusterId) {
        List<NamedRelatedResourceRep> refs = listByCluster(clusterId);
        return getByRefs(refs);
    }

    /**
     * Begins creating a host in the given tenant.
     * <p>
     * API Call: <tt>POST /compute/hosts</tt>
     * 
     * @param input
     *        the create configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<HostRestRep> create(HostCreateParam input) {
        return create(input, false);
    }
    
    public Task<HostRestRep> create(HostCreateParam input, boolean validateConnection) {
        UriBuilder uriBuilder = client.uriBuilder(baseUrl);
        if (validateConnection) {
            uriBuilder.queryParam(VALIDATE_CONNECTION_PARAM, Boolean.TRUE);
        }
        return postTaskURI(input, uriBuilder.build());
    }    

    /**
     * Begins updating a host.
     * <p>
     * API Call: <tt>PUT /compute/hosts/{id}</tt>
     * 
     * @param id
     *        the ID of the host.
     * @param input
     *        the update configuration.
     * @return a task for monitoing the progress of the operation.
     */
    public Task<HostRestRep> update(URI id, HostUpdateParam input) {
        return update(id, input, Boolean.FALSE);
    }
    
    public Task<HostRestRep> update(URI id, HostUpdateParam input, boolean validateConnection) {
        UriBuilder uriBuilder = client.uriBuilder(getIdUrl());
        if (validateConnection) {
            uriBuilder.queryParam(VALIDATE_CONNECTION_PARAM, Boolean.TRUE);
        }
        return putTaskURI(input, uriBuilder.build(id));
    }    

    /**
     * Deactivates a host.
     * <p>
     * API Call: <tt>POST /compute/hosts/{id}/deactivate</tt>
     * 
     * @param id
     *        the ID of the host.
     * @return 
     */
    public Task<HostRestRep> deactivate(URI id) {
        return deactivate(id, false);
    }
    
    /**
     * Deactivates a host.
     * <p>
     * API Call: <tt>POST /compute/hosts/{id}/deactivate?detach_storage={detachStorage}</tt>
     * 
     * @param id
     *        the ID of the host to deactivate.
     * @param detachStorage
     *        if true, will first detach storage.
     */
    public Task<HostRestRep> deactivate(URI id, boolean detachStorage) {
        URI deactivateUri = client.uriBuilder(getDeactivateUrl()).queryParam("detach_storage", detachStorage).build(id);
        return postTaskURI(deactivateUri);
    }
    
    
    /**
     * Deactivates a host.
     * <p>
     * API Call: <tt>POST /compute/hosts/{id}/deactivate?detach_storage={detachStorage}&deactivate_boot_volume={deactivateBootVolume}</tt>
     * 
     * @param id
     *        the ID of the host to deactivate.
     * @param detachStorage
     *        if true, will first detach storage.
     * @param deactivateBootVolume
     * 		  if true, and if the host was provisioned by ViPR the associated boot volume (if exists) will be deactivated 
     */
    public Task<HostRestRep> deactivate(URI id, boolean detachStorage,boolean deactivateBootVolume) {
        URI deactivateUri = client.uriBuilder(getDeactivateUrl()).queryParam("detach_storage", detachStorage).queryParam("deactivate_boot_volume", deactivateBootVolume).build(id);
        return postTaskURI(deactivateUri);
    }
    
    /**
     * Detaches storage from a host.
     * <p>
     * API Call: <tt>POST /compute/hosts/{id}/detach-storage</tt>
     * 
     * @param id
     *        the ID of the host.
     */
    public Task<HostRestRep> detachStorage(URI id) {
        return postTask(PathConstants.HOST_DETACH_STORAGE_URL, id);
    }

    /**
     * Begins discovery of the given host by ID.
     * <p>
     * API Call: <tt>POST /compute/hosts/{id}/discover</tt>
     * 
     * @param id
     *        the ID of the host to discover.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<HostRestRep> discover(URI id) {
        return postTask(getIdUrl() + "/discover", id);
    }

    /**
     * Provision bare metal hosts.
     * <p>
     * API Call: <tt>POST /compute/hosts/provision-bare-metal</tt>
     *
     * @return Tasks for monitoring the progress of the operation(s).
     */
    public Tasks<HostRestRep> provisionBareMetalHosts(ProvisionBareMetalHostsParam param) {
        return postTasks(param, baseUrl + "/provision-bare-metal");
    }

	/**
	 * Install OS on the given host.
	 * <p>
	 * API Call: <tt> PUT /compute/hosts/{id}/os-install</tt>
	 *
	 * @param id
	 *            the ID of the host to install OS on.
	 * @param input
	 *            the OS install information.
	 * @return a task for monitoring the progress of the operation.
	 */
	public Task<HostRestRep> osInstall(URI id, OsInstallParam input) {
		return putTask(input, getIdUrl() + "/os-install", id);
	}
}
