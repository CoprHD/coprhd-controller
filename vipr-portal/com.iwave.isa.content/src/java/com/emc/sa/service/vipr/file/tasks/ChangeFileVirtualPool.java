package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.block.VolumeVirtualPoolChangeParam;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;

public class ChangeFileVirtualPool  extends WaitForTasks<FileShareRestRep> {
    private List<URI> fileIds;
    private URI targetVirtualPoolId;

    public ChangeFileVirtualPool(URI fileId, URI targetVirtualPoolId) {
        this.fileIds = Lists.newArrayList(fileId);
        this.targetVirtualPoolId = targetVirtualPoolId;
        provideDetailArgs(fileId, targetVirtualPoolId);
    }

    public ChangeFileVirtualPool(List<URI> fileIds, URI targetVirtualPoolId) {
        this.fileIds = fileIds;
        this.targetVirtualPoolId = targetVirtualPoolId;
        provideDetailArgs(fileIds, targetVirtualPoolId);
    }

    @Override
    protected Tasks<FileShareRestRep> doExecute() throws Exception {
       VolumeVirtualPoolChangeParam input = new VolumeVirtualPoolChangeParam();
//        input.setVolumes(volumeIds);
//        input.setVirtualPool(targetVirtualPoolId);
//        if (!NullColumnValueGetter.isNullURI(consistencyGroup)) {
//            input.setConsistencyGroup(consistencyGroup);
//        }
        return getClient().fileSystems().changeFileVirtualPool(input);
    }
}
