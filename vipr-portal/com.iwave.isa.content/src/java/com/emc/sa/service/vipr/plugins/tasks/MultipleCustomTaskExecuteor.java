package com.emc.sa.service.vipr.plugins.tasks;

import java.util.List;


import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.exceptions.ServiceErrorException;

public class MultipleCustomTaskExecuteor  extends WaitForTasks<VolumeRestRep> {
    private final List<? extends CustomSampleHelper> helpers;

    public MultipleCustomTaskExecuteor(List<? extends CustomSampleHelper> helpers) {
        this.helpers = helpers;
        if (!helpers.isEmpty()) {
        	CustomSampleHelper helper = helpers.get(0);
            provideDetailArgs(helper.getVirtualPool(), helper.getVirtualArray(), helper.getProject(),
                    getDetails(helpers));
        }
    }

    @Override
    public Tasks<VolumeRestRep> doExecute() throws Exception {
        Tasks<VolumeRestRep> tasks = null;
        for (CustomSampleHelper param : helpers) {
            String volumeSize = CustomUtils.gbToVolumeSize(param.getSizeInGb());
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
            
            try {
                if (tasks == null) {
                    tasks = getClient().blockVolumes().create(create);
                } else {
                    tasks.getTasks().addAll(getClient().blockVolumes().create(create).getTasks());
                }
            } catch (ServiceErrorException ex) {
                logError(getMessage("CreateMultipleBlockVolumes.getTask.error", create.getName(), ex.getDetailedMessage()));
            }
        }
        
        if (tasks == null) {
            throw stateException("CreateMultipleBlockVolumes.illegalState.invalid");
        }
        return tasks;
    }

    private String getDetails(List<? extends CustomSampleHelper> helpers) {
        String result = "";
        for (CustomSampleHelper helper : helpers) {
            result += String.format("[Name: %s, Size: %s, Count: %s] ", helper.getName(), helper.getSizeInGb(), helper.getCount());
        }
        result = result.trim();
        return result;
    }
}