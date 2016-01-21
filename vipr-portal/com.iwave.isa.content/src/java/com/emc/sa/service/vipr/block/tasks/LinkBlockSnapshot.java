/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.storageos.model.block.SnapshotSessionLinkTargetsParam;
import com.emc.storageos.model.block.SnapshotSessionNewTargetsParam;
import com.emc.storageos.model.block.SnapshotSessionRelinkTargetsParam;
import com.emc.vipr.client.Task;

public class LinkBlockSnapshot extends WaitForTask<BlockSnapshotSessionRestRep> {
    private URI snapshotSessionId;
    private SnapshotSessionLinkTargetsParam newLinkedTargetsParam;
    private SnapshotSessionRelinkTargetsParam relinkExistingLinkedTargetsParam;
    
    public LinkBlockSnapshot(String snapshotSessionId, List<String> existingLinkedSnapshotIds, String linkedSnapshotName, Integer linkedSnapshotCount, String copyMode) {
        this(uri(snapshotSessionId), existingLinkedSnapshotIds, linkedSnapshotName, linkedSnapshotCount, copyMode);
    }

    public LinkBlockSnapshot(URI snapshotSessionId, List<String> existingLinkedSnapshotIds, String linkedSnapshotName, Integer linkedSnapshotCount, String copyMode) {
        this.snapshotSessionId = snapshotSessionId;
        
        if (linkedSnapshotName != null && !linkedSnapshotName.isEmpty()) {
            SnapshotSessionNewTargetsParam param = new SnapshotSessionNewTargetsParam(linkedSnapshotCount, linkedSnapshotName, copyMode);
            this.newLinkedTargetsParam = new SnapshotSessionLinkTargetsParam(param);
        }
        
        StringBuffer existingLinkedSnapshots = new StringBuffer();
        if (existingLinkedSnapshotIds != null && !existingLinkedSnapshotIds.isEmpty()) {
            List<URI> existingLinkedSnapshotURIs = new ArrayList<URI>();
            for (String linkedSnapshotId : existingLinkedSnapshotIds) {
                existingLinkedSnapshotURIs.add(uri(linkedSnapshotId));
                existingLinkedSnapshots.append(linkedSnapshotId + " ");
            }            
            this.relinkExistingLinkedTargetsParam = new SnapshotSessionRelinkTargetsParam(existingLinkedSnapshotURIs);
        }
        
        provideDetailArgs(snapshotSessionId, existingLinkedSnapshots.toString(), linkedSnapshotName, linkedSnapshotCount, copyMode);
    }

    @Override
    protected Task<BlockSnapshotSessionRestRep> doExecute() throws Exception {  
        // Relink trumps linking new targets
        if (relinkExistingLinkedTargetsParam != null) {
            return getClient().blockSnapshotSessions().relinkTargets(snapshotSessionId, relinkExistingLinkedTargetsParam);
        }
        return getClient().blockSnapshotSessions().linkTargets(snapshotSessionId, newLinkedTargetsParam);                 
    }
}
