/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.custom.CustomDataObjectRestRep;
import com.emc.storageos.model.custom.CustomServiceInputParam;
import com.emc.storageos.model.object.BucketBulkRep;
import com.emc.storageos.model.object.BucketDeleteParam;
import com.emc.storageos.model.object.BucketParam;
import com.emc.storageos.model.object.BucketRestRep;
import com.emc.storageos.model.object.BucketUpdateParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * For external services
 * <p>
 * Base URL: <tt>/custom/service</tt>
 */
public class CustomServiceClient extends ProjectResources<CustomDataObjectRestRep> implements TaskResources<CustomDataObjectRestRep> {

    public CustomServiceClient(ViPRCoreClient parent, RestClient client) {
        super(parent, client, CustomDataObjectRestRep.class, "/custom/service");
    }

    @Override
    public Tasks<CustomDataObjectRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<CustomDataObjectRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }

   
    public Task<CustomDataObjectRestRep> deactivate(URI id, BucketDeleteParam input) {
        return postTask(input, getDeactivateUrl(), id);
    }

    public Task<CustomDataObjectRestRep> create(String xmlPath) {
    	String url ="http://localhost:8080/activiti-rest/service/repository/deployments";
        TaskResourceRep task = client.postWithXML(TaskResourceRep.class, xmlPath, url);
        return new Task<>(client, task, CustomDataObjectRestRep.class);
    }

	@Override
	protected List<CustomDataObjectRestRep> getBulkResources(BulkIdParam input) {
		// TODO Auto-generated method stub
		return null;
	}

    
}
