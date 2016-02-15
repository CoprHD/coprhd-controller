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
import com.emc.sa.service.vipr.application.tasks.RestoreSnapshotForApplication;
import com.emc.sa.service.vipr.application.tasks.RestoreSnapshotSessionForApplication;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Maps;

@Service("RestoreSnapshotOfApplication")
public class RestoreSnapshotOfApplicationService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.APPLICATION_SNAPSHOT_TYPE)
    private String snapshotType;

    @Param(ServiceParams.APPLICATION_SUB_GROUP)
    protected List<URI> subGroups;

    @Override
    public void execute() throws Exception {

        // get list of volumes in application
        NamedVolumesList volList = getClient().application().getVolumeByApplication(applicationId);

        // group by system type (type -> volume URI map)
        Map<String, URI> volumeTypes = Maps.newHashMap();
        for (NamedRelatedResourceRep vol : volList.getVolumes()) {
            VolumeRestRep volume = getClient().blockVolumes().get(vol);
            if (subGroups.contains(volume.getReplicationGroupInstance())) {
                volumeTypes.put(volume.getStorageController().toString(), volume.getId());
            }
        }

        Tasks<? extends DataObjectRestRep> tasks = null;

        for (String type : volumeTypes.keySet()) {
            // TODO type needs to be system type from volume rest rep
            if (type == "VMAX3") {
                tasks = execute(new RestoreSnapshotSessionForApplication(applicationId, volumeTypes.get(type)));
            } else {
                tasks = execute(new RestoreSnapshotForApplication(applicationId, volumeTypes.get(type)));
            }
            addAffectedResources(tasks);
        }
    }
}
