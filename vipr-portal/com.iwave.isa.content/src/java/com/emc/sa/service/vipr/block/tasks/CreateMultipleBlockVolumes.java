/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.util.List;

import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.block.CreateBlockVolumeHelper;
import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

/**
 * Task that will create multiple block volumes in parallel. Executes a single create volume API
 * call for each CreateBlockVolumeHelper instance and returns tasks for all volumes that are created.
 */
public class CreateMultipleBlockVolumes extends WaitForTasks<VolumeRestRep> {
    private final List<? extends CreateBlockVolumeHelper> helpers;

    public CreateMultipleBlockVolumes(List<? extends CreateBlockVolumeHelper> helpers) {
        this.helpers = helpers;
        if (!helpers.isEmpty()) {
            CreateBlockVolumeHelper helper = helpers.get(0);
            provideDetailArgs(helper.getVirtualPool(), helper.getVirtualArray(), helper.getProject(),
                    getDetails(helpers));
        }
    }

    @Override
    public Tasks<VolumeRestRep> doExecute() throws Exception {
        Tasks<VolumeRestRep> tasks = null;
        for (CreateBlockVolumeHelper param : helpers) {
            String volumeSize = BlockStorageUtils.gbToVolumeSize(param.getSizeInGb());
            VolumeCreate create = new VolumeCreate();
            create.setVpool(param.getVirtualPool());
            create.setVarray(param.getVirtualArray());
            create.setProject(param.getProject());
            create.setName(param.getName());
            create.setSize(volumeSize);
            int numberOfVolumes = 1;
            if ((param.getCount() != null) && (param.getCount() > 1)) {
                numberOfVolumes = param.getCount();
            }
            create.setCount(numberOfVolumes);
            create.setConsistencyGroup(param.getConsistencyGroup());

            if (tasks == null) {
                tasks = getClient().blockVolumes().create(create);
            } else {
                tasks.getTasks().addAll(getClient().blockVolumes().create(create).getTasks());
            }
        }
        return tasks;
    }

    private String getDetails(List<? extends CreateBlockVolumeHelper> helpers) {
        String result = "";
        for (CreateBlockVolumeHelper helper : helpers) {
            result += String.format("[Name: %s, Size: %s, Count: %s] ", helper.getName(), helper.getSizeInGb(), helper.getCount());
        }
        result = result.trim();
        return result;
    }
}