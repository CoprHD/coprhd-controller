package com.emc.sa.service.vipr.block.consistency;

import static com.emc.sa.service.ServiceParams.CONSISTENCY_GROUP;
import static com.emc.sa.service.ServiceParams.COPIES;
import static com.emc.sa.service.vipr.block.consistency.ConsistencyUtils.activateFullCopy;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("ConsistencyGroupActivateFullCopy")
public class ActivateFullCopyService extends ViPRService {

	@Param(CONSISTENCY_GROUP)
	protected URI consistencyGroupId;

	@Param(COPIES)
	protected List<String> copyIds;

	@Override
	public void execute() throws Exception {
		for (URI copyId : uris(copyIds)) {
			activateFullCopy(consistencyGroupId, copyId);
		}
	}
}
