/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.util.Properties;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.compute.VcenterClusterParam;
import com.emc.storageos.model.host.vcenter.*;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;
import com.sun.jersey.api.client.ClientResponse;
import org.codehaus.jackson.map.ser.std.StaticListSerializerBase;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;
import java.util.Properties;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

/**
 * Virtual Data Centers resources.
 * <p>
 * Base URL: <tt>/compute/vcenter-data-centers</tt>
 */
public class VcenterDataCenters extends AbstractCoreBulkResources<VcenterDataCenterRestRep> {
    private static final String EXPECTED_VERSION_QUERY_PARAM = "expect_version";

    public VcenterDataCenters(ViPRCoreClient parent, RestClient client) {
        super(parent, client, VcenterDataCenterRestRep.class, PathConstants.DATACENTER_URL);
    }

    @Override
    public VcenterDataCenters withInactive(boolean inactive) {
        return (VcenterDataCenters) super.withInactive(inactive);
    }

    @Override
    public VcenterDataCenters withInternal(boolean internal) {
        return (VcenterDataCenters) super.withInternal(internal);
    }

    @Override
    protected List<VcenterDataCenterRestRep> getBulkResources(BulkIdParam input) {
        VcenterDataCenterBulkRep response = client.post(VcenterDataCenterBulkRep.class, input, getBulkUrl());
        return defaultList(response.getVcenterDataCenters());
    }

    /**
     * Lists the datacenters for the given vCenter by ID.
     * <p>
     * API Call: <tt>GET /compute/vcenters/{vcenterId}/vcenter-data-centers</tt>
     * 
     * @param vcenterId
     *        the ID of the vCenter.
     * @return the list of datacenter references.
     */
    public List<NamedRelatedResourceRep> listByVcenter(URI vcenterId) {
        VcenterDataCenterList response = client.get(VcenterDataCenterList.class, PathConstants.DATACENTER_BY_VCENTER,
                vcenterId);
        return ResourceUtils.defaultList(response.getDataCenters());
    }

    /**
     * Gets the list of datacenters for the given vCenter by ID. This is a convenience method for:
     * <tt>getByRefs(listByVcenter(vcenterId))</tt>
     * 
     * @param vcenterId
     *        the ID of the vCenter.
     * @return the list of datacenters.
     */
    public List<VcenterDataCenterRestRep> getByVcenter(URI vcenterId) {
        List<NamedRelatedResourceRep> refs = listByVcenter(vcenterId);
        return getByRefs(refs, null);
    }

    /**
     * Creates a datacenter within the given vCenter by ID.
     * <p>
     * API Call: <tt>POST /compute/vcenters/{vcenterId}/vcenter-data-centers</tt>
     * 
     * @param vcenterId
     *        the ID of the vCenter.
     * @param input
     *        the create configuration.
     * @return the newly created datacenter.
     */
    public VcenterDataCenterRestRep create(URI vcenterId, VcenterDataCenterCreate input) {
        return client.post(VcenterDataCenterRestRep.class, input, PathConstants.DATACENTER_BY_VCENTER, vcenterId);
    }

    /**
     * Updates the given datacenter by ID.
     * <p>
     * API Call: <tt>PUT /compute/vcenter-data-centers/{id}</tt>
     * 
     * @param id
     *        the ID of the datacenter to update.
     * @param input
     *        the update configuration.
     * @return the updated datacenter.
     */
    public VcenterDataCenterRestRep update(URI id, VcenterDataCenterUpdate input) {
        return client.put(VcenterDataCenterRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Deactivates the given datacenter by ID.
     * <p>
     * API Call: <tt>POST /compute/vcenter-data-centers/{id}/deactivate</tt>
     * 
     * @param id
     *        the ID of the datacenter to deactivate.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<VcenterDataCenterRestRep> deactivate(URI id) {
        return doDeactivateWithTask(id);
    }
    
    /**
     * Deactivates a data center.
     * <p>
     * API Call: <tt>POST /compute/vcenter-data-centers/{id}/deactivate?detach-storage={detachStorage}</tt>
     * 
     * @param id
     *        the ID of the data center to deactivate.
     * @param detachStorage
     *        if true, will first detach storage.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<VcenterDataCenterRestRep> deactivate(URI id, boolean detachStorage) {
        URI deactivateUri = client.uriBuilder(getDeactivateUrl()).queryParam("detach-storage", detachStorage).build(id);
        return postTaskURI(deactivateUri);
    }
    
    /**
     * Detaches storage from a data center.
     * <p>
     * API Call: <tt>POST /compute/vcenter-data-centers/{id}/detach-storage</tt>
     * 
     * @param id
     *        the ID of the data center.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<VcenterDataCenterRestRep> detachStorage(URI id) {
        return postTask(PathConstants.DATACENTER_DETACH_STORAGE_URL, id);
    }
    
    /**
     * Create a vCenter cluster in a datacenter.
     * <p>
     * API Call: <tt>POST /compute/vcenter-data-centers/{id}/create-vcenter-cluster</tt>
     * 
     * @param id
     *        the id of the data center.
     * @param clusterParam
     *        VcenterClusterParam id of the selected cluster       
     * @return a task for monitoring the progress of the operation.
     */
    public Task<VcenterDataCenterRestRep> createVcenterCluster(URI dataCenterId, VcenterClusterParam clusterParam) {
        TaskResourceRep response = client.post(TaskResourceRep.class, clusterParam, PathConstants.DATACENTER_CREATE_CLUSTER_URL, dataCenterId);
        return new Task<VcenterDataCenterRestRep>(client, response, resourceClass);
    }

    /**
     * Update and existing vCenter cluster.
     * <p>
     * API Call: <tt>PUT /compute/vcenter-data-centers/{id}/update-vcenter-cluster</tt>
     * 
     * @param id
     *        the id of the data center.
     * @param clusterParam
     *        VcenterClusterParam id of the selected cluster       
     * @return a task for monitoring the progress of the operation.
     */
    public Task<VcenterDataCenterRestRep> updateVcenterCluster(URI dataCenterId, VcenterClusterParam clusterParam) {
        TaskResourceRep response = client.post(TaskResourceRep.class, clusterParam, PathConstants.DATACENTER_UPDATE_CLUSTER_URL, dataCenterId);
        return new Task<VcenterDataCenterRestRep>(client, response, resourceClass);
    }
}
