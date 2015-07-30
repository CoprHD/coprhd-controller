package com.emc.sa.service.vipr.block.consistency;

import static com.emc.sa.service.ServiceParams.CONSISTENCY_GROUP;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.vipr.block.consistency.ConsistencyUtils.createSnapshot;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;

@Service("ConsistencyGroupCreateSnapshot")
public class CreateSnapshotService extends ViPRService {
	
    @Param(CONSISTENCY_GROUP)
    protected URI consistencyGroupId;

    @Param(NAME)
    protected String name;

    @Override
    public void execute() throws Exception {
        Tasks<BlockConsistencyGroupRestRep> snapshot = createSnapshot(consistencyGroupId, name);
        for (Task<BlockConsistencyGroupRestRep> task : snapshot.getTasks()) {
            logInfo("create.full.copy.service", task.getResource().getName(), task.getResource().getId());
        }
    }
}
