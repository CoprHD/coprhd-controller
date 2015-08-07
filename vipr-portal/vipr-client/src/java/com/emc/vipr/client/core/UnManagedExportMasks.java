/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.block.*;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;
import java.net.URI;
import java.util.List;
import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

/**
 * Unmanaged Export-Mask resources.
 * <p>
 * Base URL: <tt>/vdc/unmanaged/export-masks</tt>
 */
public class UnManagedExportMasks extends AbstractCoreBulkResources<UnManagedExportMaskRestRep> {
    public UnManagedExportMasks(ViPRCoreClient parent, RestClient client) {
        super(parent, client, UnManagedExportMaskRestRep.class, PathConstants.UNMANAGED_EXPORTS_URL);
    }

    @Override
    public UnManagedExportMasks withInactive(boolean inactive) {
        return (UnManagedExportMasks) super.withInactive(inactive);
    }

    @Override
    public UnManagedExportMasks withInternal(boolean internal) {
        return (UnManagedExportMasks) super.withInternal(internal);
    }

    @Override
    protected List<UnManagedExportMaskRestRep> getBulkResources(BulkIdParam input) {
        UnManagedExportMaskBulkRep response = client.post(UnManagedExportMaskBulkRep.class, input, getBulkUrl());
        return defaultList(response.getUnManagedExportMasks());
    }

    /**
     * Gets the list of unmanaged export-masks for the given host by ID.
     * <p>
     * API Call: <tt>GET /compute/hosts/{hostId}/unmanaged-export-masks</tt>
     * 
     * @param hostId
     *            the ID of the host.
     * @return the list of unmanaged export-masks references.
     */
    public List<RelatedResourceRep> listByHost(URI hostId) {
        UnManagedExportMaskList response = client.get(UnManagedExportMaskList.class,
                PathConstants.UNMANAGED_EXPORTS_BY_HOST_URL, hostId);
        return ResourceUtils.defaultList(response.getUnManagedExportMasks());
    }

    /**
     * Gets the list of unmanaged export-masks for the given host by ID. This is a convenience method for:
     * <tt>getByRefs(listByHost(hostId))</tt>
     * 
     * @param hostId
     *            the ID of the host.
     * @return the list of unmanaged export-masks.
     */
    public List<UnManagedExportMaskRestRep> getByHost(URI hostId) {
        return getByHost(hostId, null);
    }

    /**
     * Gets the list of unmanaged export-masks for the given host by ID. This is a convenience method for:
     * <tt>getByRefs(listByHost(hostId), filter)</tt>
     * 
     * @param hostId
     *            the ID of the host.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of unmanaged export-masks.
     */
    public List<UnManagedExportMaskRestRep> getByHost(URI hostId, ResourceFilter<UnManagedExportMaskRestRep> filter) {
        List<RelatedResourceRep> refs = listByHost(hostId);
        return getByRefs(refs, filter);
    }

    /**
     * Gets the list of unmanaged export-masks for the given cluster by ID.
     * <p>
     * API Call: <tt>GET /compute/clusters/{clusterId}/unmanaged-export-masks</tt>
     * 
     * @param clusterId
     *            the ID of the cluster.
     * @return the list of unmanaged export-masks references.
     */
    public List<RelatedResourceRep> listByCluster(URI clusterId) {
        UnManagedExportMaskList response = client.get(UnManagedExportMaskList.class,
                PathConstants.UNMANAGED_EXPORTS_BY_CLUSTER_URL, clusterId);
        return ResourceUtils.defaultList(response.getUnManagedExportMasks());
    }

    /**
     * Gets the list of unmanaged export-masks for the given cluster by ID. This is a convenience method for:
     * <tt>getByRefs(listByCluster(clusterId))</tt>
     * 
     * @param clusterId
     *            the ID of the cluster.
     * @return the list of unmanaged export-masks.
     */
    public List<UnManagedExportMaskRestRep> getByCluster(URI clusterId) {
        return getByCluster(clusterId, null);
    }

    /**
     * Gets the list of unmanaged export-masks for the given cluster by ID. This is a convenience method for:
     * <tt>getByRefs(listByCluster(hostId), filter)</tt>
     * 
     * @param clusterId
     *            the ID of the cluster.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of unmanaged export-masks.
     */
    public List<UnManagedExportMaskRestRep> getByCluster(URI clusterId, ResourceFilter<UnManagedExportMaskRestRep> filter) {
        List<RelatedResourceRep> refs = listByCluster(clusterId);
        return getByRefs(refs, filter);
    }
}
