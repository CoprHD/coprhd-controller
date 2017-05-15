/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.machinetags.MachineTagUtils;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;

public class SetBlockVolumeMachineTag extends ViPRExecutionTask<Void> {
    private URI volumeId;
    private String tagName;
    private String tagValue;

    public SetBlockVolumeMachineTag(String volumeId, String tagName, String tagValue) {
        this(uri(volumeId), tagName, tagValue);
    }

    public SetBlockVolumeMachineTag(URI volumeId, String tagName, String tagValue) {
        this.volumeId = volumeId;
        this.tagName = tagName;
        this.tagValue = tagValue;
        provideDetailArgs(volumeId, tagName, tagValue);
    }

    @Override
    public void execute() throws Exception {
        try {
            MachineTagUtils.setBlockVolumeTag(getClient(), volumeId, tagName, tagValue);
        } catch (Exception ex) {
            String command = BlockStorageUtils.getVolumeTagCommand(volumeId, tagName, tagValue);
            ExecutionUtils.fail("failTask.SetBlockVolumeMachineTag", new Object [] { volumeId, tagName, tagValue }, command);
        }
    }
}
