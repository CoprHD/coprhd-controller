/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.application.tasks.UnlinkSnapshotForApplication;
import com.emc.sa.service.vipr.application.tasks.UnlinkSnapshotSessionForApplication;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.vipr.client.Tasks;

@Service("UnlinkSnapshotOfApplication")
public class UnlinkSnapshotOfApplicationService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.APPLICATION_SUB_GROUP)
    protected List<URI> subGroups;

    @Override
    public void execute() throws Exception {

        // get list of volumes in application
        NamedVolumesList volList = getClient().application().getVolumeByApplication(applicationId);

        Map<String, URI> volumeTypes = BlockStorageUtils.getVolumeSystemTypes(volList, subGroups);

        Tasks<? extends DataObjectRestRep> tasks = null;

        for (String type : volumeTypes.keySet()) {
            if (type.equalsIgnoreCase("VMAX3")) {
                tasks = execute(new UnlinkSnapshotSessionForApplication(applicationId, volumeTypes.get(type)));
            } else {
                tasks = execute(new UnlinkSnapshotForApplication(applicationId, volumeTypes.get(type)));
            }
            addAffectedResources(tasks);
        }
    }
}
