/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import java.net.URI;

import javax.inject.Inject;

import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.model.host.cluster.ClusterRestRep;

public class GetCluster extends ViPRExecutionTask<Cluster> {
    @Inject
    private ModelClient models;
    private URI id;

    public GetCluster(URI id) {
        this.id = id;
        this.provideDetailArgs(id);
    }

    @Override
    public Cluster executeTask() throws Exception {
        // Verify that the object exists through the REST api, and that we have permission to access it
        ClusterRestRep rep = getClient().clusters().get(id);
        if (rep == null) {
            return null;
        }
        return models.of(Cluster.class).findById(id);
    }
}
