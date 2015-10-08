/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.consistency.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.vipr.client.Tasks;

public class ResynchronizeConsistencyGroupFullCopy extends
        WaitForTasks<BlockConsistencyGroupRestRep> {

    private URI consistencyGroup;

    public ResynchronizeConsistencyGroupFullCopy(URI consistencyGroup) {
        this.consistencyGroup = consistencyGroup;
    }

    @Override
    protected Tasks<BlockConsistencyGroupRestRep> doExecute() throws Exception {
        NamedRelatedResourceRep item = getClient().blockConsistencyGroups().getFullCopies(consistencyGroup).get(0);
        return getClient().blockConsistencyGroups().resynchronizeFullCopy(consistencyGroup, item.getId());
    }
}
