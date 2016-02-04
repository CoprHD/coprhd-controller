/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.DataObjectRestRep;
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
        replicationGroup = fieldIsPopulated(newApplicationSubGroup) ? newApplicationSubGroup : existingApplicationSubGroup;
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
