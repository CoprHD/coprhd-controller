/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.host.cluster.ClusterBulkRep;
import com.emc.storageos.model.host.cluster.ClusterCreateParam;
import com.emc.storageos.model.host.cluster.ClusterList;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.host.cluster.ClusterUpdateParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.search.ClusterSearchBuilder;
import com.emc.vipr.client.impl.RestClient;

/**
 * Cluster resources.
 * <p>
 * Base URL: <tt>/compute/clusters</tt>
 */
public class Clusters extends AbstractCoreBulkResources<ClusterRestRep> implements TenantResources<ClusterRestRep> {
    public Clusters(ViPRCoreClient parent, RestClient client) {
        super(parent, client, ClusterRestRep.class, PathConstants.CLUSTER_URL);
    }

    @Override
    public Clusters withInactive(boolean inactive) {
        return (Clusters) super.withInactive(inactive);
    }

    @Override
    public Clusters withInternal(boolean internal) {
        return (Clusters) super.withInternal(internal);
    }

    @Override
    protected List<ClusterRestRep> getBulkResources(BulkIdParam input) {
        ClusterBulkRep response = client.post(ClusterBulkRep.class, input, getBulkUrl());
        return defaultList(response.getClusters());
    }

    /**
     * Gets a list of cluster references from the given path.
     * 
     * @param path
     *            the URL path.
     * @param args
     *            the path arguments.
     * @return the list of cluster references.
     */
    protected List<NamedRelatedResourceRep> getList(String path, Object... args) {
        ClusterList response = client.get(ClusterList.class, path, args);
        return defaultList(response.getClusters());
    }

    /**
     * Lists the clusters for the given tenant.
     * <p>
     * API Call: <tt>GET /tenants/{tenantId}/clusters</tt>
     * 
     * @param tenantId
     *            the ID of the tenant.
     * @return the list of cluster references.
     */
    @Override
    public List<NamedRelatedResourceRep> listByTenant(URI tenantId) {
        return getList(PathConstants.CLUSTER_BY_TENANT_URL, tenantId);
    }

    @Override
    public List<NamedRelatedResourceRep> listByUserTenant() {
        return listByTenant(parent.getUserTenantId());
    }

    @Override
    public List<ClusterRestRep> getByUserTenant() {
        return getByTenant(parent.getUserTenantId(), null);
    }

    @Override
    public List<ClusterRestRep> getByUserTenant(ResourceFilter<ClusterRestRep> filter) {
        return getByTenant(parent.getUserTenantId(), filter);
    }

    @Override
    public List<ClusterRestRep> getByTenant(URI tenantId) {
        return getByTenant(tenantId, null);
    }

    @Override
    public List<ClusterRestRep> getByTenant(URI tenantId, ResourceFilter<ClusterRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByTenant(tenantId);
        return getByRefs(refs, filter);
    }

    /**
     * Lists the clusters for the given datacenter by ID.
     * <p>
     * API Call: <tt>GET /compute/vcenter-data-centers/{dataCenterId}/clusters</tt>
     * 
     * @param dataCenterId
     *            the ID of the datacenter.
     * @return the list of cluster references.
     */
    public List<NamedRelatedResourceRep> listByDataCenter(URI dataCenterId) {
        return getList(PathConstants.CLUSTER_BY_DATACENTER_URL, dataCenterId);
    }

    /**
     * Gets the list of clusters for the given datacenter by ID.
     * <p>
     * Convenience method for <tt>getByRefs(listByDataCenter(dataCenterId))</tt>.
     * 
     * @param dataCenterId
     *            the ID of the datacenter.
     * @return the list of clusters.
     * 
     * @see #listByDataCenter(URI)
     * @see #getByRefs(java.util.Collection)
     */
    public List<ClusterRestRep> getByDataCenter(URI dataCenterId) {
        List<NamedRelatedResourceRep> refs = listByDataCenter(dataCenterId);
        return getByRefs(refs);
    }

    /**
     * Lists the clusters for the given vCenter by ID.
     * <p>
     * API Call: <tt>GET /compute/vcenters/{vCenterId}/clusters</tt>
     * 
     * @param vCenterId
     *            the ID of the vCenter.
     * @return the list of cluster references.
     */
    public List<NamedRelatedResourceRep> listByVCenter(URI vCenterId) {
        return getList(PathConstants.CLUSTER_BY_VCENTER_URL, vCenterId);
    }

    /**
     * Gets the list of clusters for the given vCenter by ID.
     * <p>
     * Convenience method for <tt>getByRefs(listByVCenter(vCenterId))</tt>.
     * 
     * @param vCenterId
     *            the ID of the vCenter.
     * @return the list of clusters.
     * 
     * @see #listByDataCenter(URI)
     * @see #getByRefs(java.util.Collection)
     */
    public List<ClusterRestRep> getByVCenter(URI vCenterId) {
        List<NamedRelatedResourceRep> refs = listByDataCenter(vCenterId);
        return getByRefs(refs);
    }

    /**
     * Creates a cluster for the given tenant.
     * <p>
     * API Call: <tt>POST /tenants/{tenantId}/clusters</tt>
     * 
     * @param tenantId
     *            the ID of the tenant.
     * @param input
     *            the create configuration.
     * @return the newly created cluster.
     */
    public ClusterRestRep create(URI tenantId, ClusterCreateParam input) {
        return client.post(ClusterRestRep.class, input, PathConstants.CLUSTER_BY_TENANT_URL, tenantId);
    }

    /**
     * Updates a cluster by ID.
     * <p>
     * API Call: <tt>PUT /compute/clusters/{id}</tt>
     * 
     * @param id
     *            the ID of the cluster to update.
     * @param input
     *            the update configuration.
     * @return the updated cluster.
     */
    public ClusterRestRep update(URI id, ClusterUpdateParam input) {
        return client.put(ClusterRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Deactivates a cluster by ID if cluster hosts do not have block or file exports.
     * 
     * <p>
     * API Call: <tt>POST /compute/clusters/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of the cluster to deactivate.
     * @prereq The cluster hosts must not have block or file exports
     */
    public Task<ClusterRestRep> deactivate(URI id) {
        return deactivate(id, false);
    }

    /**
     * Deactivates a cluster.
     * <p>
     * API Call: <tt>POST /compute/clusters/{id}/deactivate?detach-storage={detachStorage}</tt>
     * 
     * @param id
     *            the ID of the cluster to deactivate.
     * @param detachStorage
     *            if true, will first detach storage.
     */
    public Task<ClusterRestRep> deactivate(URI id, boolean detachStorage) {
        URI deactivateUri = client.uriBuilder(getDeactivateUrl()).queryParam("detach-storage", detachStorage).build(id);
        return postTaskURI(deactivateUri);
    }

    /**
     * Detaches storage from a cluster.
     * <p>
     * API Call: <tt>POST /compute/clusters/{id}/detach-storage</tt>
     * 
     * @param id
     *            the ID of the cluster.
     */
    public Task<ClusterRestRep> detachStorage(URI id) {
        return postTask(PathConstants.CLUSTER_DETACH_STORAGE_URL, id);
    }

    /**
     * Lists the clusters for the given name.
     * <p>
     * API Call: <tt>GET /compute/clusters/search?name={name}</tt>
     * 
     * @param clusterName
     *            the name of the cluster.
     * @return the list of cluster references.
     */
    public List<ClusterRestRep> searchByName(String clusterName) {
        return search().byName(clusterName).run();
    }

    /**
     * Creates a search builder specifically for creating cluster search queries.
     * 
     * @return a cluster search builder.
     */
    @Override
    public ClusterSearchBuilder search() {
        return new ClusterSearchBuilder(this);
    }

}