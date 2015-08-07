/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.CopiesParam;
import com.emc.storageos.model.block.Copy;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

public class ResumeContinuousCopy extends WaitForTasks<VolumeRestRep> {

    private URI volumeId;
    private URI continuousCopyId;
    private String type;

    public ResumeContinuousCopy(URI volumeId, URI continuousCopyId, String type) {
        this.continuousCopyId = continuousCopyId;
        this.volumeId = volumeId;
        this.type = type;
        provideDetailArgs(volumeId, continuousCopyId, type);
    }

    @Override
    protected Tasks<VolumeRestRep> doExecute() throws Exception {
        Copy copy = new Copy();
        copy.setCopyID(continuousCopyId);
        copy.setType(type);

        CopiesParam param = new CopiesParam();
        param.getCopies().add(copy);
        return getClient().blockVolumes().resumeContinuousCopies(volumeId, param);
    }
}
