package com.emc.sa.service.vipr.block.consistency;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResources;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.execute;
import static com.emc.sa.service.vipr.block.BlockStorageUtils.*;

import java.net.URI;
import java.util.Collections;

import com.emc.sa.service.vipr.block.consistency.tasks.ActivateConsistencyGroupFullCopy;
import com.emc.sa.service.vipr.block.consistency.tasks.CreateConsistencyGroupFullCopy;
import com.emc.sa.service.vipr.block.consistency.tasks.DetachConsistencyGroupFullCopy;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.vipr.client.Tasks;

final class ConsistencyUtils {
	
	public static Tasks<BlockConsistencyGroupRestRep> createFullCopy(URI consistencyGroupId, String name, Integer count) {
        int countValue = (count != null) ? count : 1;
        Tasks<BlockConsistencyGroupRestRep> copies = execute(new CreateConsistencyGroupFullCopy(consistencyGroupId, name, countValue));
        addAffectedResources(copies);
        return copies;
    }
	
	public static Tasks<BlockConsistencyGroupRestRep> removeFullCopy(URI consistencyGroupId, URI fullCopyId) {
		Tasks<BlockConsistencyGroupRestRep> tasks = execute( new DetachConsistencyGroupFullCopy(consistencyGroupId, fullCopyId) );
		removeBlockResources(Collections.singletonList(fullCopyId), VolumeDeleteTypeEnum.FULL);
		return tasks;
	}
	
	public static Tasks<BlockConsistencyGroupRestRep> activateConsistencyGroupFullCopy(URI consistencyGroupId, URI fullCopyId){
		Tasks<BlockConsistencyGroupRestRep> copies = execute( new ActivateConsistencyGroupFullCopy(consistencyGroupId, fullCopyId) );
		addAffectedResources(copies);
        return copies;
	}
	
	
}
