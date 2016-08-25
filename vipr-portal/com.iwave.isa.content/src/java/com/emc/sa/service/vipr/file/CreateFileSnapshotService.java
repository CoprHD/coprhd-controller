/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.FILESYSTEMS;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResource;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.execute;

import java.util.ArrayList;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.file.tasks.CreateFileSnapshot;
import com.emc.storageos.db.client.model.uimodels.RetainedReplica;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.emc.vipr.client.Task;

@Service("CreateFileSnapshot")
public class CreateFileSnapshotService extends ViPRService {
    @Param(FILESYSTEMS)
    protected List<String> fileSystemIds;
    @Param(NAME)
    protected String name;

    private List<FileShareRestRep> fileSystems;

    @Override
    public void precheck() {
        fileSystems = FileStorageUtils.getFileSystems(uris(fileSystemIds));
    }

    @Override
    public void execute() {
        for (FileShareRestRep fs : fileSystems) {
            String fileSystemId = fs.getId().toString();
            checkAndPurgeObsoleteSnapshot(fileSystemId);
            
            Task<FileSnapshotRestRep> task = ViPRExecutionUtils.execute(new CreateFileSnapshot(fileSystemId, name));
            addAffectedResource(task);
            
            // record file snapshots for retention 
            List< Task<FileSnapshotRestRep> > tasks = new ArrayList< Task<FileSnapshotRestRep> >();
            tasks.add(task);
            addRetainedReplicas(fs.getId(), tasks);
        }
    }
    
    /**
     * Check retention policy and delete obsolete full copies if necessary
     * 
     * @param fileSystemId - file system id 
     */
    private void checkAndPurgeObsoleteSnapshot(String fileSystemId) {
        if (!isRetentionRequired()) {
            return;
        }
        List<RetainedReplica> replicas = findObsoleteReplica(fileSystemId);
        for (RetainedReplica replica : replicas) {
            for (String obsoleteCopyId : replica.getAssociatedReplicaIds()) {
                info("Delete snapshot %s since it exceeds max number of copies allowed", obsoleteCopyId);
                FileStorageUtils.deleteFileSnapshot(uri(obsoleteCopyId));
            }
            getModelClient().delete(replica);
        }
    }
}
