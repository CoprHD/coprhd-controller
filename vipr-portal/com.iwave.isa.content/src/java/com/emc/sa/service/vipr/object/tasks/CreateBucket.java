package com.emc.sa.service.vipr.object.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.object.BucketParam;
import com.emc.storageos.model.object.BucketRestRep;
import com.emc.vipr.client.Task;

public class CreateBucket extends WaitForTask<BucketRestRep> {
    private String name;
    private URI projectId;
    private URI vpoolId;
    private Double softQuota;
    private Double hardQuota;
    private Double retention;
    private URI namespace;
    private URI tenant;
    private URI owner;
    
    public CreateBucket(String name, String projectId, String vpoolId, Double softQuota, Double hardQuota, Double retention,
            String namespaceId, String tenantId, String ownerId) {
        this(name, uri(projectId), uri(vpoolId), softQuota, hardQuota, retention, uri(namespaceId), uri(tenantId), uri(ownerId));
    }

    public CreateBucket(String name, URI projectId, URI vpoolId, Double softQuota, Double hardQuota, Double retention,
            URI namespaceId, URI tenantId, URI ownerId) {
        this.name = name;
        this.projectId = projectId;
        this.vpoolId = vpoolId;
        this.softQuota = softQuota;
        this.hardQuota = hardQuota;
        this.retention = retention;
        this.namespace = namespaceId;
        this.tenant = tenantId;
        this.owner = ownerId;
        //provideDetailArgs(name, size, vpoolId, varrayId, projectId);
    }

    @Override
    public Task<BucketRestRep> doExecute() throws Exception {
        BucketParam create = new BucketParam();
        create.setLabel(name);
        create.setVpool(vpoolId);
        create.setSoftQuota(softQuota.toString());
        create.setHardQuota(hardQuota.toString());
        create.setRetention(retention.toString());
        create.setNamespace(namespace.toString());
        create.setOwner(owner.toString());

        return getClient().objectBuckets().create(create);
    }
}
