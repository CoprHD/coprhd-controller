/*
 * Copyright (c) 2012-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.CopiesParam;
import com.emc.storageos.model.block.Copy;
import com.emc.vipr.client.Tasks;

public class UpdateBlockConsistencyGroupAccessMode extends WaitForTasks<BlockConsistencyGroupRestRep> {
    // The consistency group
    private final URI consistencyGroupId;
    // The target virtual array
    private final URI failoverTarget;
    private final String type;
    private final String accessMode;

    public UpdateBlockConsistencyGroupAccessMode(URI consistencyGroupId, URI failoverTarget, String accessMode) {
        this(consistencyGroupId, failoverTarget, "rp", accessMode);
    }

    public UpdateBlockConsistencyGroupAccessMode(URI consistencyGroupId, URI failoverTarget, String type, String accessMode) {
        this.consistencyGroupId = consistencyGroupId;
        this.failoverTarget = failoverTarget;
        this.type = type;
        this.accessMode = accessMode;
        provideDetailArgs(consistencyGroupId, failoverTarget, type, accessMode);
    }

    @Override
    protected Tasks<BlockConsistencyGroupRestRep> doExecute() throws Exception {
        Copy copy = new Copy();
        copy.setType(type);
        copy.setCopyID(failoverTarget);
        copy.setAccessMode(accessMode);

        CopiesParam param = new CopiesParam();
        param.getCopies().add(copy);
        return getClient().blockConsistencyGroups().updateCopyAccessMode(consistencyGroupId, param);
    }
}
