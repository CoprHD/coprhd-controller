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
    private final URI varrayId;
    private final URI projectId;
    private final URI vpoolId;
    private final Double softQuota;
    private final Double hardQuota;
    private final String retention;
    private final String owner;

    public CreateBucket(String name, String varrayId, String vpoolId, String projectId, Double softQuota, Double hardQuota,
            String retention,
            String ownerId) {
        this(name, uri(varrayId), uri(vpoolId), uri(projectId), softQuota, hardQuota, retention, ownerId);
    }

    public CreateBucket(String name, URI varrayId, URI vpoolId, URI projectId, Double softQuota, Double hardQuota, String retention,
            String ownerId) {
        this.name = name;
        this.projectId = projectId;
        this.varrayId = varrayId;
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
        create.setVarray(varrayId);
        create.setVpool(vpoolId);
        create.setSoftQuota(softQuota.toString());
        create.setHardQuota(hardQuota.toString());
        if (retention != null) {
            create.setRetention(retention.toString());
        }
        if (owner != null) {
            create.setOwner(owner);
        }

        return getClient().objectBuckets().create(create, projectId);
    }
}
