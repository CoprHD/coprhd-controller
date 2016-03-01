/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.migration;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.migration.tasks.RemoveClustersFromMobilityGroup;
import com.emc.sa.service.vipr.migration.tasks.RemoveHostsFromMobilityGroup;
import com.emc.sa.service.vipr.migration.tasks.RemoveVolumesFromMobilityGroup;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.vipr.client.Tasks;

@Service("RemoveResourcesFromMobilityGroup")
public class RemoveResourcesFromMobilityGroupService extends ViPRService {

    @Param(ServiceParams.MOBILITY_GROUP)
    private URI mobilityGroupId;

    @Param(ServiceParams.MOBILITY_GROUP_RESOURCES)
    private List<String> resourceIds;

    private VolumeGroupRestRep mobilityGroup;

    @Override
    public void precheck() throws Exception {
        mobilityGroup = getClient().application().get(mobilityGroupId);
    }

    @Override
    public void execute() throws Exception {
        Tasks<? extends DataObjectRestRep> tasks = null;
        if (mobilityGroup.getMigrationGroupBy().equals(VolumeGroup.MigrationGroupBy.VOLUMES.name())) {
            tasks = execute(new RemoveVolumesFromMobilityGroup(mobilityGroupId, uris(resourceIds)));
        } else if (mobilityGroup.getMigrationGroupBy().equals(VolumeGroup.MigrationGroupBy.HOSTS.name())) {
            tasks = execute(new RemoveHostsFromMobilityGroup(mobilityGroupId, uris(resourceIds)));
        } else if (mobilityGroup.getMigrationGroupBy().equals(VolumeGroup.MigrationGroupBy.CLUSTERS.name())) {
            tasks = execute(new RemoveClustersFromMobilityGroup(mobilityGroupId, uris(resourceIds)));
        }
        if (tasks != null) {
            addAffectedResources(tasks);
        }
    }
}
