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
import com.emc.sa.service.vipr.application.tasks.CreateSnapshotForApplication;
import com.emc.sa.service.vipr.application.tasks.CreateSnapshotSessionForApplication;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Maps;

@Service("CreateSnapshotOfApplication")
public class CreateSnapshotOfApplicationService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.NAME)
    protected String name;

    @Param(ServiceParams.COUNT)
    protected Integer count;

    @Param(ServiceParams.READ_ONLY)
    protected Boolean readOnly;

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
                tasks = execute(new CreateSnapshotSessionForApplication(applicationId, volumeTypes.get(type), name, count));
            } else {
                tasks = execute(new CreateSnapshotForApplication(applicationId, volumeTypes.get(type), name, readOnly, count));
            }
            addAffectedResources(tasks);
        }
    }
}
