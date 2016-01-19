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
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.block.UnManagedVolumeList;
import com.emc.storageos.model.block.UnManagedVolumeRestRep;
import com.emc.storageos.model.block.UnManagedVolumesBulkRep;
import com.emc.storageos.model.block.VolumeExportIngestParam;
import com.emc.storageos.model.block.VolumeIngest;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

/**
 * Unmanaged Volumes resources.
 * <p>
 * Base URL: <tt>/vdc/unmanaged/volumes</tt>
 */
public class UnManagedVolumes extends AbstractCoreBulkResources<UnManagedVolumeRestRep> {
    public UnManagedVolumes(ViPRCoreClient parent, RestClient client) {
        super(parent, client, UnManagedVolumeRestRep.class, PathConstants.UNMANAGED_VOLUMES_URL);
    }

    @Override
    public UnManagedVolumes withInactive(boolean inactive) {
        return (UnManagedVolumes) super.withInactive(inactive);
    }

    @Override
    public UnManagedVolumes withInternal(boolean internal) {
        return (UnManagedVolumes) super.withInternal(internal);
    }

    @Override
    protected List<UnManagedVolumeRestRep> getBulkResources(BulkIdParam input) {
        UnManagedVolumesBulkRep response = client.post(UnManagedVolumesBulkRep.class, input, getBulkUrl());
        return defaultList(response.getUnManagedVolumes());
    }

    /**
     * Gets the list of unmanaged volumes for the given storage system by ID.
     * <p>
     * API Call: <tt>GET /vdc/storage-systems/{storageSystemId}/unmanaged/volumes</tt>
     * 
     * @param storageSystemId
     *            the ID of the storage system.
     * @return the list of unmanaged volume references.
     */
    public List<RelatedResourceRep> listByStorageSystem(URI storageSystemId) {
        UnManagedVolumeList response = client.get(UnManagedVolumeList.class,
                PathConstants.UNMANAGED_VOLUME_BY_STORAGE_SYSTEM_URL, storageSystemId);
        return ResourceUtils.defaultList(response.getUnManagedVolumes());
    }

    /**
     * Gets the list of unmanaged volumes for the given storage system and virtual pool by ID.
     * <p>
     * API Call: <tt>GET /vdc/storage-systems/{storageSystemId}/unmanaged/{virtualPoolId}/volumes</tt>
     * 
     * @param storageSystemId
     *            the ID of the storage system.
     * @param virtualPoolId
     *            the ID of the virtual pool.
     * @return the list of unmanaged volume references.
     */
    public List<NamedRelatedResourceRep> listByStorageSystemVirtualPool(URI storageSystemId, URI virtualPoolId) {
        UnManagedVolumeList response = client.get(UnManagedVolumeList.class,
                PathConstants.UNMANAGED_VOLUME_BY_STORAGE_SYSTEM_AND_VIRTUAL_POOL_URL, storageSystemId, virtualPoolId);
        return ResourceUtils.defaultList(response.getNamedUnManagedVolumes());
    }

    /**
     * Gets the list of unmanaged volumes for the given storage system and virtual pool by ID. This is a convenience method for:
     * <tt>getByRefs(listByStorageSystemVirtualPool(storageSystemId, virtualPoolId))</tt>
     * 
     * @param storageSystemId
     *            the ID of the storage system.
     * @param virtualPoolId
     *            the ID of the virtual pool.
     * @return the list of unmanaged volumes.
     */
    public List<UnManagedVolumeRestRep> getByStorageSystemVirtualPool(URI storageSystemId, URI virtualPoolId) {
        return getByStorageSystemVirtualPool(storageSystemId, virtualPoolId, null);
    }

    /**
     * Gets the list of unmanaged volumes for the given storage system by ID. This is a convenience method for:
     * <tt>getByRefs(listByStorageSystem(storageSystemId))</tt>
     * 
     * @param storageSystemId
     *            the ID of the storage system.
     * @return the list of unmanaged volumes.
     */
    public List<UnManagedVolumeRestRep> getByStorageSystem(URI storageSystemId) {
        return getByStorageSystem(storageSystemId, null);
    }

    /**
     * Gets the list of unmanaged volumes for the given storage system and virtual pool by ID. This is a convenience method for:
     * <tt>getByRefs(listByStorageSystemVirtualPool(storageSystemId, virtualPoolId), filter)</tt>
     * 
     * @param storageSystemId
     *            the ID of the storage system.
     * @param virtualPoolId
     *            the ID of the virtual pool.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of unmanaged volumes.
     */
    public List<UnManagedVolumeRestRep> getByStorageSystemVirtualPool(URI storageSystemId, URI virtualPoolId,
            ResourceFilter<UnManagedVolumeRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByStorageSystemVirtualPool(storageSystemId, virtualPoolId);
        return getByRefs(refs, filter);
    }

    /**
     * Gets the list of unmanaged volumes for the given storage system by ID. This is a convenience method for:
     * <tt>getByRefs(listByStorageSystem(storageSystemId), filter)</tt>
     * 
     * @param storageSystemId
     *            the ID of the storage system.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of unmanaged volumes.
     */
    public List<UnManagedVolumeRestRep> getByStorageSystem(URI storageSystemId,
            ResourceFilter<UnManagedVolumeRestRep> filter) {
        List<RelatedResourceRep> refs = listByStorageSystem(storageSystemId);
        return getByRefs(refs, filter);
    }

    /**
     * Gets the list of unmanaged volumes for the given host by ID.
     * <p>
     * API Call: <tt>GET /compute/hosts/{hostId}/unmanaged-volumes</tt>
     * 
     * @param hostId
     *            the ID of the host.
     * @return the list of unmanaged volume references.
     */
    public List<RelatedResourceRep> listByHost(URI hostId) {
        UnManagedVolumeList response = client.get(UnManagedVolumeList.class,
                PathConstants.UNMANAGED_VOLUME_BY_HOST_URL, hostId);
        return ResourceUtils.defaultList(response.getUnManagedVolumes());
    }

    /**
     * Gets the list of unmanaged volumes for the given host by ID. This is a convenience method for: <tt>getByRefs(listByHost(hostId))</tt>
     * 
     * @param hostId
     *            the ID of the host.
     * @return the list of unmanaged volumes.
     */
    public List<UnManagedVolumeRestRep> getByHost(URI hostId) {
        return getByHost(hostId, null);
    }

    /**
     * Gets the list of unmanaged volumes for the given host by ID. This is a convenience method for:
     * <tt>getByRefs(listByHost(hostId), filter)</tt>
     * 
     * @param hostId
     *            the ID of the host.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of unmanaged volumes.
     */
    public List<UnManagedVolumeRestRep> getByHost(URI hostId, ResourceFilter<UnManagedVolumeRestRep> filter) {
        List<RelatedResourceRep> refs = listByHost(hostId);
        return getByRefs(refs, filter);
    }

    /**
     * Gets the list of unmanaged volumes for the given cluster by ID.
     * <p>
     * API Call: <tt>GET /compute/clusters/{clusterId}/unmanaged-volumes</tt>
     * 
     * @param clusterId
     *            the ID of the cluster.
     * @return the list of unmanaged volume references.
     */
    public List<RelatedResourceRep> listByCluster(URI clusterId) {
        UnManagedVolumeList response = client.get(UnManagedVolumeList.class,
                PathConstants.UNMANAGED_VOLUME_BY_CLUSTER_URL, clusterId);
        return ResourceUtils.defaultList(response.getUnManagedVolumes());
    }

    /**
     * Gets the list of unmanaged volumes for the given cluster by ID. This is a convenience method for:
     * <tt>getByRefs(listByCluster(clusterId))</tt>
     * 
     * @param clusterId
     *            the ID of the cluster.
     * @return the list of unmanaged volumes.
     */
    public List<UnManagedVolumeRestRep> getByCluster(URI clusterId) {
        return getByCluster(clusterId, null);
    }

    /**
     * Gets the list of unmanaged volumes for the given cluster by ID. This is a convenience method for:
     * <tt>getByRefs(listByCluster(hostId), filter)</tt>
     * 
     * @param clusterId
     *            the ID of the cluster.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of unmanaged volumes.
     */
    public List<UnManagedVolumeRestRep> getByCluster(URI clusterId, ResourceFilter<UnManagedVolumeRestRep> filter) {
        List<RelatedResourceRep> refs = listByCluster(clusterId);
        return getByRefs(refs, filter);
    }

    /**
     * Ingests unmanaged volumes.
     * <p>
     * API Call: <tt>POST /vdc/unmanaged/volumes/ingest</tt>
     * 
     * @param input
     *            the ingest configuration.
     * @return the list of ingested volumes.
     */
    public Tasks<UnManagedVolumeRestRep> ingest(VolumeIngest input) {
        TaskList tasks = client.post(TaskList.class, input, baseUrl + "/ingest");
        return new Tasks<>(client, tasks.getTaskList(), resourceClass);
    }

    /**
     * Ingests unmanaged volumes.
     * <p>
     * API Call: <tt>POST /vdc/unmanaged/volumes/ingest-exported</tt>
     * 
     * @param input
     *            the ingest configuration.
     * @return the list of ingested volumes.
     */
    public Tasks<UnManagedVolumeRestRep> ingestExported(VolumeExportIngestParam input) {
        TaskList tasks = client.post(TaskList.class, input, "/vdc/unmanaged/volumes/ingest-exported");
        return new Tasks<>(client, tasks.getTaskList(), resourceClass);
    }
}
