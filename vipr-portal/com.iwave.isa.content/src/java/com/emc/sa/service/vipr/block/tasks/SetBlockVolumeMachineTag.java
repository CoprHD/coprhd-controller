/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.machinetags.MachineTagUtils;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.VirtualArrayRelatedResourceRep;
import com.emc.storageos.model.block.VolumeRestRep;

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
            // Tag this volume and all of its copies (R2's, RP targets)
            // This is important when we tag filesystems, datastores, and other data we want to protect
            // from accidental overwrite through controller.
            VolumeRestRep volume = (VolumeRestRep)BlockStorageUtils.getVolume(volumeId);
            Set<URI> volumesToTag = new HashSet<>(Arrays.asList(volumeId));
            if (volume.getProtection() != null) {
                if (volume.getProtection().getRpRep() != null && volume.getProtection().getRpRep().getRpTargets() != null) {
                    for (VirtualArrayRelatedResourceRep copy : volume.getProtection().getRpRep().getRpTargets()) {
                        volumesToTag.add(copy.getId());
                    }
                }
                
                if (volume.getProtection().getSrdfRep() != null) {
                    for (VirtualArrayRelatedResourceRep copy : volume.getProtection().getSrdfRep().getSRDFTargetVolumes()) {
                        volumesToTag.add(copy.getId());
                    }
                }
            }

            for (URI volumeId : volumesToTag) {
                MachineTagUtils.setBlockVolumeTag(getClient(), volumeId, tagName, tagValue);
            }
        } catch (Exception ex) {
            String command = BlockStorageUtils.getVolumeTagCommand(volumeId, tagName, tagValue);
            ExecutionUtils.fail("failTask.SetBlockVolumeMachineTag", new Object [] { volumeId, tagName, tagValue }, command);
        }
    }
}
