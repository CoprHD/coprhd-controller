/*
 * Copyright (c) 2012-2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.object.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.object.BucketParam;
import com.emc.storageos.model.object.BucketRestRep;
import com.emc.vipr.client.Task;

public class CreateBucket extends WaitForTask<BucketRestRep> {
    private final String name;
    private final URI projectId;
    private final URI vpoolId;
    private final Double softQuota;
    private final Double hardQuota;
    private final Double retention;
    private final String owner;

    public CreateBucket(String name, String vpoolId, String projectId, Double softQuota, Double hardQuota, Double retention,
            String ownerId) {
        this(name, uri(vpoolId), uri(projectId), softQuota, hardQuota, retention, ownerId);
    }

    public CreateBucket(String name, URI vpoolId, URI projectId, Double softQuota, Double hardQuota, Double retention,
            String ownerId) {
        this.name = name;
        this.projectId = projectId;
        this.vpoolId = vpoolId;
        this.softQuota = softQuota;
        this.hardQuota = hardQuota;
        this.retention = retention;
        this.owner = ownerId;
        provideDetailArgs(name, vpoolId, projectId);
    }

    @Override
    public Task<BucketRestRep> doExecute() throws Exception {
        BucketParam create = new BucketParam();
        create.setLabel(name);
        create.setVpool(vpoolId);
        create.setSoftQuota(softQuota.toString());
        create.setHardQuota(hardQuota.toString());
        create.setRetention(retention.toString());
        create.setOwner(owner);

        return getClient().objectBuckets().create(create, projectId);
    }
}
