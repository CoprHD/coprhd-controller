/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionUnlinkTargetsParam;
import com.emc.storageos.model.block.SnapshotSessionUnlinkTargetParam;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;

public class UnlinkSnapshotSessionForApplication extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final List<URI> snapshotSessions;
    private final List<String> existingLinkedSnapshotIds;

    public UnlinkSnapshotSessionForApplication(URI applicationId, List<URI> snapshotSessions, List<String> existingLinkedSnapshotIds) {
        this.applicationId = applicationId;
        this.snapshotSessions = snapshotSessions;
        this.existingLinkedSnapshotIds = existingLinkedSnapshotIds;
        provideDetailArgs(applicationId, snapshotSessions);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupSnapshotSessionUnlinkTargetsParam input = new VolumeGroupSnapshotSessionUnlinkTargetsParam();
        input.setSnapshotSessions(snapshotSessions);
        input.setPartial(true);
        List<SnapshotSessionUnlinkTargetParam> linkedTargets = Lists.newArrayList();
        if (existingLinkedSnapshotIds != null) {
            for (String linkedSnapshot : existingLinkedSnapshotIds) {
                SnapshotSessionUnlinkTargetParam param = new SnapshotSessionUnlinkTargetParam();
                param.setId(uri(linkedSnapshot));
                // TODO should user have option to delete or not?
                param.setDeleteTarget(Boolean.FALSE);
                linkedTargets.add(param);
            }
        }
        input.setLinkedTargets(linkedTargets);

        TaskList taskList = getClient().application().unlinkApplicationSnapshotSession(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
