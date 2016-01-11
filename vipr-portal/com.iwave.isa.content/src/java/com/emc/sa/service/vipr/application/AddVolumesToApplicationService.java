package com.emc.sa.service.vipr.application;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
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

    private String replicationGroup;

    @Override
    public void precheck() throws Exception {
        if ((replicationGroup == null || replicationGroup.isEmpty()) || (newReplicationGroup == null || newReplicationGroup.isEmpty())) {
            ExecutionUtils.fail("failTask.AddVolumesToApplicationService.replicationGroup.precheck", new Object[] {});
        } else {
            replicationGroup = existingReplicationGroup != null && !existingReplicationGroup.isEmpty() ?
                    existingReplicationGroup : newReplicationGroup;
        }
    }

    @Override
    public void execute() throws Exception {
        Tasks<? extends DataObjectRestRep> tasks = execute(new AddVolumesToApplication(applicationId, uris(volumeIds), replicationGroup));
        addAffectedResources(tasks);
    }
}
