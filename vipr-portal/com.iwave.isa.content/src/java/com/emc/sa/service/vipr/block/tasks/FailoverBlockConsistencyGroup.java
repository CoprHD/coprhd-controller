/*
 * Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.CopiesParam;
import com.emc.storageos.model.block.Copy;
import com.emc.vipr.client.Tasks;

public class FailoverBlockConsistencyGroup extends WaitForTasks<BlockConsistencyGroupRestRep> {
    // The consistency group
    private final URI consistencyGroupId;
    // The target virtual array
    private final URI failoverTarget;
    private final String type;

    public FailoverBlockConsistencyGroup(URI consistencyGroupId, URI failoverTarget) {
        this(consistencyGroupId, failoverTarget, "rp");
    }

    public FailoverBlockConsistencyGroup(URI consistencyGroupId, URI failoverTarget, String type) {
        this.consistencyGroupId = consistencyGroupId;
        this.failoverTarget = failoverTarget;
        this.type = type;
        provideDetailArgs(consistencyGroupId, failoverTarget, type);
    }

    @Override
    protected Tasks<BlockConsistencyGroupRestRep> doExecute() throws Exception {
        Copy copy = new Copy();
        copy.setType(type);
        copy.setCopyID(failoverTarget);

        CopiesParam param = new CopiesParam();
        param.getCopies().add(copy);
        return getClient().blockConsistencyGroups().failover(consistencyGroupId, param);
    }
}
