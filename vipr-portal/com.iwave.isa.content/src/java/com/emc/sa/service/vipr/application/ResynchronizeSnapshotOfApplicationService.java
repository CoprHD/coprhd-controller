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
import com.emc.sa.service.vipr.application.tasks.GetBlockSnapshotSet;
import com.emc.sa.service.vipr.application.tasks.ResynchronizeSnapshotForApplication;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.SnapshotList;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

@Service("ResynchronizeSnapshotOfApplication")
public class ResynchronizeSnapshotOfApplicationService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.APPLICATION_SNAPSHOT_TYPE)
    private String snapshotType;

    @Param(ServiceParams.APPLICATION_COPY_SETS)
    private String applicationCopySet;

    @Param(ServiceParams.APPLICATION_SUB_GROUP)
    protected List<URI> subGroups;

    @Override
    public void execute() throws Exception {

        // get list of volumes in application
        NamedVolumesList volList = getClient().application().getVolumeByApplication(applicationId);

        Map<String, VolumeRestRep> volumeTypes = BlockStorageUtils.getVolumeSystemTypes(volList, subGroups);

        Tasks<? extends DataObjectRestRep> tasks = null;

        for (String type : volumeTypes.keySet()) {
            if (type.equalsIgnoreCase("vmax3")) {
                // TODO fail, resynchronize not supported for snapshot session
            } else {
                SnapshotList snapshotList = execute(new GetBlockSnapshotSet(applicationId, applicationCopySet));
                // TODO fail if empty
                tasks = execute(new ResynchronizeSnapshotForApplication(applicationId, snapshotList.getSnapList().get(0).getId()));
            }
            addAffectedResources(tasks);
        }
    }
}
