/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import java.net.URI;
import java.util.List;

import com.emc.sa.asset.providers.BlockProviderUtils;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.application.tasks.AddVolumesToApplication;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

@Service("AddVolumesToApplication")
public class AddVolumesToApplicationService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.VOLUME)
    private List<String> volumeIds;

    @Param(value = ServiceParams.APPLICATION_SUB_GROUP, required = false)
    private String existingApplicationSubGroup;

    @Param(value = ServiceParams.NEW_APPLICATION_SUB_GROUP, required = false)
    private String newApplicationSubGroup;

    private String replicationGroup;

    @Override
    public void precheck() throws Exception {
        // if a new sub group is selected, make sure it doesn't already exist
        if (fieldIsPopulated(newApplicationSubGroup)
                && BlockProviderUtils.getApplicationReplicationGroupNames(getClient(), applicationId).contains(newApplicationSubGroup)) {
            ExecutionUtils.fail("failTask.AddVolumesToApplicationService.subGroupUnique.precheck", new Object[] {});
        }
        // if volumes in application are either vplex or RP, application sub group is mandatory
        if (volumeIds != null && !volumeIds.isEmpty()) {
            VolumeRestRep vol = getClient().blockVolumes().get(URI.create(volumeIds.iterator().next()));
            if (BlockProviderUtils.isVolumeRP(vol) || BlockProviderUtils.isVolumeVPLEX(vol)) {
                replicationGroup = fieldIsPopulated(newApplicationSubGroup) ? newApplicationSubGroup : existingApplicationSubGroup;
                if (replicationGroup == null || replicationGroup.isEmpty()) {
                    ExecutionUtils.fail("failTask.AddVolumesToApplicationService.subGroupRequired.precheck", new Object[] {});
                }
            }
        } else {
            ExecutionUtils.fail("failTask.AddVolumesToApplicationService.volumes.precheck", new Object[] {});
        }
    }

    @Override
    public void execute() throws Exception {
        Tasks<? extends DataObjectRestRep> tasks = execute(new AddVolumesToApplication(applicationId, uris(volumeIds), replicationGroup));
        addAffectedResources(tasks);
    }
    
    private boolean fieldIsPopulated(String field) {
        return (field != null && !field.isEmpty());
    }
}
