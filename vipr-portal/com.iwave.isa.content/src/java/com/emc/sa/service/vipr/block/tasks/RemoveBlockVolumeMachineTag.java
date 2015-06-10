/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.machinetags.MachineTagUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;

public class RemoveBlockVolumeMachineTag extends ViPRExecutionTask<Void> {
    private URI volumeId;
    private String tag;

    public RemoveBlockVolumeMachineTag(String volumeId, String tag) {
        this(uri(volumeId), tag);
    }

    public RemoveBlockVolumeMachineTag(URI volumeId, String tag) {
        this.volumeId = volumeId;
        this.tag = tag;
        provideDetailArgs(volumeId, tag);
    }

    @Override
    public void execute() throws Exception {
        MachineTagUtils.removeBlockVolumeTag(getClient(), volumeId, tag);
    }
}
