/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.consistency.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.vipr.client.Tasks;

public class DeactivateConsistencyGroupSnapshotSession extends
        WaitForTasks<BlockConsistencyGroupRestRep> {
    private URI consistencyGroupId;    
    private URI snapshotSessionId;

    public DeactivateConsistencyGroupSnapshotSession(String consistencyGroupId, String snapshotSessionId) {
        this(uri(consistencyGroupId), uri(snapshotSessionId));
    }

    public DeactivateConsistencyGroupSnapshotSession(URI consistencyGroupId, URI snapshotSessionId) {
        super();
        this.consistencyGroupId = consistencyGroupId;
        this.snapshotSessionId = snapshotSessionId;        
        provideDetailArgs(consistencyGroupId, snapshotSessionId);
    }

    @Override
    protected Tasks<BlockConsistencyGroupRestRep> doExecute() throws Exception {
        return getClient().blockConsistencyGroups().deactivateSnapshotSession(consistencyGroupId, snapshotSessionId);
    }
}
