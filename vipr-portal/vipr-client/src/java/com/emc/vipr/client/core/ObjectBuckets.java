package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.BulkDeleteParam;
import com.emc.storageos.model.block.VolumeBulkRep;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.object.BucketBulkRep;
import com.emc.storageos.model.object.BucketRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;


/**
 * ObjectBuckets resources.
 * <p>
 * Base URL: <tt>/object/buckets</tt>
 */
public class ObjectBuckets extends ProjectResources<BucketRestRep> implements TaskResources<BucketRestRep> {

	public ObjectBuckets(ViPRCoreClient parent, RestClient client) {
        super(parent, client, BucketRestRep.class, PathConstants.OBJECT_BUCKET_URL);
    }

	@Override
	public Tasks<BucketRestRep> getTasks(URI id) {
		return doGetTasks(id);
	}

	@Override
	public Task<BucketRestRep> getTask(URI id, URI taskId) {
		return doGetTask(id, taskId);
	}

	@Override
	protected List<BucketRestRep> getBulkResources(BulkIdParam input) {
		BucketBulkRep response = client.post(BucketBulkRep.class, input, getBulkUrl());
        return defaultList(response.getBuckets());
	}
	
    /**
     * Begins deactivating a object bucket by ID.
     * <p>
     * API Call: <tt>POST /object/buckets/{id}/deactivate?type={deletionType}</tt>
     * 
     * @param id
     *            the ID of the object bucket to deactivate.
     * @param deletionType
     *            {@code FULL} or {@code VIPR_ONLY}
     * @return a task for monitoring the progress of the operation.
     * 
     * @see com.emc.storageos.model.block.VolumeDeleteTypeEnum
     */
    public Task<BucketRestRep> deactivate(URI id, VolumeDeleteTypeEnum deletionType) {
        URI uri = client.uriBuilder(getDeactivateUrl()).queryParam("type", deletionType).build(id);
        TaskResourceRep task = client.postURI(TaskResourceRep.class, uri);
        return new Task<>(client, task, resourceClass);
    }
    
    /**
     * Begins deactivating multiple object bucket by their IDs.
     * <p>
     * API Call: <tt>POST /object/buckets/deactivate?type={deletionType}</tt>
     * 
     * @param ids
     *            The IDs of the object buckets to deactivate.
     * @param deletionType
     *            {@code FULL} or {@code VIPR_ONLY}
     * @return tasks for monitoring the progress of the operations.
     * 
     * @see com.emc.storageos.model.block.VolumeDeleteTypeEnum
     */
    public Tasks<BucketRestRep> deactivate(List<URI> ids, VolumeDeleteTypeEnum deletionType) {
        URI uri = client.uriBuilder(baseUrl + "/deactivate").queryParam("type", deletionType).build();
        TaskList tasks = client.postURI(TaskList.class, new BulkDeleteParam(ids), uri);
        return new Tasks<>(client, tasks.getTaskList(), resourceClass);
    }

}
