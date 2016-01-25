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

    @Param(value = ServiceParams.REPLICATION_GROUP, required = false)
    private String existingReplicationGroup;

    @Param(value = ServiceParams.NEW_REPLICATION_GROUP, required = false)
    private String newReplicationGroup;

    @Param(value = ServiceParams.NEW_CONSISTENCY_GROUP, required = false)
    private URI newConsistencyGroupId;

    private String replicationGroup;

    @Override
    public void precheck() throws Exception {
//        if (fieldIsPopulated(existingReplicationGroup) || fieldIsPopulated(newReplicationGroup)) {
            // if both fields are populated, we'll take the new replication group because they would have
            // to have actually typed that in, so they probably want it
            // we could throw an exception if they choose both to make it clear
            replicationGroup = fieldIsPopulated(newReplicationGroup) ?
                    newReplicationGroup : existingReplicationGroup;
//        } else {
//            ExecutionUtils.fail("failTask.AddVolumesToApplicationService.replicationGroup.precheck", new Object[] {});
//        }
    }

    @Override
    public void execute() throws Exception {
        Tasks<? extends DataObjectRestRep> tasks = execute(new AddVolumesToApplication(applicationId, uris(volumeIds), replicationGroup, newConsistencyGroupId));
        addAffectedResources(tasks);
    }
    
    private boolean fieldIsPopulated(String field) {
        return (field != null && !field.isEmpty());
    }
}
