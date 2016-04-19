/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.consistency.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.SnapshotSessionCreateParam;
import com.emc.storageos.model.block.SnapshotSessionNewTargetsParam;
import com.emc.vipr.client.Tasks;

public class CreateConsistencyGroupSnapshotSession extends
        WaitForTasks<BlockConsistencyGroupRestRep> {

    private URI consistencyGroupId;
    private String name;
    private SnapshotSessionNewTargetsParam linkedTargetsParam;
    
    public CreateConsistencyGroupSnapshotSession(String consistencyGroupId, String name, String linkedSnapshotName, Integer linkedSnapshotCount, String copyMode) {
        this(uri(consistencyGroupId), name, linkedSnapshotName, linkedSnapshotCount, copyMode);
    }

    public CreateConsistencyGroupSnapshotSession(URI consistencyGroupId, String name, String linkedSnapshotName, Integer linkedSnapshotCount, String copyMode) {
        this.consistencyGroupId = consistencyGroupId;
        this.name = name;
        if (linkedSnapshotName != null && !linkedSnapshotName.isEmpty()) {
            this.linkedTargetsParam = new SnapshotSessionNewTargetsParam(linkedSnapshotCount, linkedSnapshotName, copyMode);
        }
        provideDetailArgs(consistencyGroupId, name, linkedSnapshotName, linkedSnapshotCount, copyMode);
    }

    @Override
    protected Tasks<BlockConsistencyGroupRestRep> doExecute() throws Exception {
        SnapshotSessionCreateParam createParam = new SnapshotSessionCreateParam();
        createParam.setName(name);
        if (linkedTargetsParam != null) {
            createParam.setNewLinkedTargets(linkedTargetsParam);
        }
        return getClient().blockConsistencyGroups().createSnapshotSession(consistencyGroupId, createParam);
    }
}
