package com.emc.sa.service.vipr.block.consistency;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResources;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.execute;

import java.net.URI;

import com.emc.sa.service.vipr.block.consistency.tasks.CreateConsistencyGroupFullCopy;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.vipr.client.Tasks;

final class ConsistencyUtils {
	
	public static Tasks<BlockConsistencyGroupRestRep> createFullCopy(URI consistencyGroupId, String name, Integer count) {
        int countValue = (count != null) ? count : 1;
        Tasks<BlockConsistencyGroupRestRep> copies = execute(new CreateConsistencyGroupFullCopy(consistencyGroupId, name, countValue));
        addAffectedResources(copies);
        return copies;
    }

}
