/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.model.application.VolumeGroupCreateParam;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.google.common.collect.Sets;

public class CreateApplication extends ViPRExecutionTask<VolumeGroupRestRep> {
    private final String name;
    private final String description;

    public CreateApplication(String name, String description) {
        this.name = name;
        this.description = description;
        provideDetailArgs(name);
    }

    @Override
    public VolumeGroupRestRep executeTask() throws Exception {
        VolumeGroupCreateParam input = new VolumeGroupCreateParam();
        input.setRoles(Sets.newHashSet(VolumeGroup.VolumeGroupRole.COPY.name()));
        if (description != null) {
            input.setDescription(description);
        }
        input.setName(name);

        return getClient().application().createApplication(input);
    }
}
