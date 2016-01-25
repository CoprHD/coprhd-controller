/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.storageos.model.block.SnapshotSessionCreateParam;
import com.emc.storageos.model.block.SnapshotSessionNewTargetsParam;
import com.emc.vipr.client.Tasks;

public class CreateBlockSnapshotSession extends WaitForTasks<BlockSnapshotSessionRestRep> {
    private URI volumeId;
    private String name;
    private SnapshotSessionNewTargetsParam linkedTargetsParam;
    
    public CreateBlockSnapshotSession(String volumeId, String name, String linkedSnapshotName, Integer linkedSnapshotCount, String copyMode) {
        this(uri(volumeId), name, linkedSnapshotName, linkedSnapshotCount, copyMode);
    }

    public CreateBlockSnapshotSession(URI volumeId, String name, String linkedSnapshotName, Integer linkedSnapshotCount, String copyMode) {
        this.volumeId = volumeId;
        this.name = name;
        if (linkedSnapshotName != null && !linkedSnapshotName.isEmpty()) {
            this.linkedTargetsParam = new SnapshotSessionNewTargetsParam(linkedSnapshotCount, linkedSnapshotName, copyMode);
        }
        provideDetailArgs(volumeId, name, linkedSnapshotName, linkedSnapshotCount, copyMode);
    }

    @Override
    protected Tasks<BlockSnapshotSessionRestRep> doExecute() throws Exception {
        SnapshotSessionCreateParam createParam = new SnapshotSessionCreateParam();
        createParam.setName(name);
        if (linkedTargetsParam != null) {
            createParam.setNewLinkedTargets(linkedTargetsParam);
        }
        return getClient().blockSnapshotSessions().createForVolume(volumeId, createParam);
    }
}
