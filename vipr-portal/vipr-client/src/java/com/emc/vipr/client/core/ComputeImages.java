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
import com.emc.storageos.model.compute.ComputeImageBulkRep;
import com.emc.storageos.model.compute.ComputeImageCreate;
import com.emc.storageos.model.compute.ComputeImageList;
import com.emc.storageos.model.compute.ComputeImageRestRep;
import com.emc.storageos.model.compute.ComputeImageUpdate;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

/**
 * Compute Image resource.
 * <p>
 * Base URL: <tt>/compute/images</tt>
 */
public class ComputeImages extends AbstractCoreBulkResources<ComputeImageRestRep> implements
        TopLevelResources<ComputeImageRestRep>, TaskResources<ComputeImageRestRep> {

    public ComputeImages(ViPRCoreClient parent, RestClient client) {
        super(parent, client, ComputeImageRestRep.class, PathConstants.COMPUTE_IMAGE_URL);
    }

    @Override
    public ComputeImages withInactive(boolean inactive) {
        return (ComputeImages) super.withInactive(inactive);
    }

    @Override
    public ComputeImages withInternal(boolean internal) {
        return (ComputeImages) super.withInternal(internal);
    }

    @Override
    protected List<ComputeImageRestRep> getBulkResources(BulkIdParam input) {
        ComputeImageBulkRep response = client.post(ComputeImageBulkRep.class, input, getBulkUrl());
        return defaultList(response.getComputeImages());
    }

    @Override
    public Tasks<ComputeImageRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<ComputeImageRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }

    /**
     * Lists all compute images.
     * <p>
     * API Call: <tt>GET /compute/images</tt>
     * 
     * @return the list of compute image references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        ComputeImageList response = client.get(ComputeImageList.class, baseUrl);
        return ResourceUtils.defaultList(response.getComputeImages());
    }

    /**
     * Gets the list of all compute images. This is a convenience for <tt>getByRefs(list())</tt>.
     * 
     * @return the list of compute images.
     */
    @Override
    public List<ComputeImageRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Gets the list of all compute images, optionally filtering the results. This is a convenience for <tt>getByRefs(list(), filter)</tt>.
     * 
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of compute images.
     */
    @Override
    public List<ComputeImageRestRep> getAll(ResourceFilter<ComputeImageRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Begins creating a compute image.
     * <p>
     * API Call: <tt>POST /compute/images</tt>
     * 
     * @param input
     *            the create configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<ComputeImageRestRep> create(ComputeImageCreate input) {
        return postTask(input, baseUrl);
    }

    /**
     * Begins updating a compute image by ID.
     * <p>
     * API Call: <tt>PUT /compute/images/{id}</tt>
     * 
     * @param id
     *            the ID of the compute image to update.
     * @param input
     *            the update configuration.
     * @return the compute image reference.
     */
    public Task<ComputeImageRestRep> update(URI id, ComputeImageUpdate input) {
        return putTask(input, getIdUrl(), id);
    }

    /**
     * Begins deactivating a compute image by ID.
     * <p>
     * API Call: <tt>POST /compute/images/{id}</tt>
     * 
     * @param id
     *            the ID of the compute image to deactivate.
     */
    public void deactivate(URI id) {
        doDeactivate(id);
    }

    /**
     * Filter to show only ESX/i compute images.
     * 
     * @param imageType
     *            the type of the image (e.g. esx, linux)
     * @return List of ESX/i compute images.
     */
    public List<NamedRelatedResourceRep> listByImageType(String imageType) {
        URI getUri = client.uriBuilder(baseUrl).queryParam("imageType", imageType).build();
        ComputeImageList response = client.getURI(ComputeImageList.class, getUri);
        return ResourceUtils.defaultList(response.getComputeImages());
    }

    /**
     * Begins creating a compute image as a clone.
     * <p>
     * API Call: <tt>POST /compute/images</tt>
     * 
     * @param input
     *            the clone configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<ComputeImageRestRep> cloneImage(ComputeImageCreate input) {
        return postTask(input, baseUrl);
    }

}
