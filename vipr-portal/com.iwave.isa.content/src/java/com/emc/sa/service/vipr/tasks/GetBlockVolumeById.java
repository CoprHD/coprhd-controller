/*
 * Copyright (c) 2017 Dell-EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import java.net.URI;

import javax.inject.Inject;

import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.block.VolumeRestRep;

public class GetBlockVolumeById extends ViPRExecutionTask<Volume> {
    @Inject
    private ModelClient models;
    private URI id;

    public GetBlockVolumeById(URI id) {
        this.id = id;
        provideDetailArgs(id);
    }

    @Override
    public Volume executeTask() throws Exception {
        // Verify that the object exists through the REST api, and that we have permission to access it
        VolumeRestRep rep = getClient().blockVolumes().get(id);
        if (rep == null) {
            return null;
        }
        return models.of(Volume.class).findById(id);
    }

}
