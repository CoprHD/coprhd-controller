/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.VolumeRestRep;
import com.iwave.ext.linux.util.VolumeWWNUtils;

public class GetBlockVolumeByWWN extends ViPRExecutionTask<VolumeRestRep> {
    private String wwn;

    public GetBlockVolumeByWWN(String wwn) {
        this.wwn = wwn;
        provideDetailArgs(wwn);
    }

    @Override
    public VolumeRestRep executeTask() throws Exception {
        List<VolumeRestRep> matches = getClient().blockVolumes().findByWwn(wwn);
        if (!matches.isEmpty()) {
            return matches.get(0);
        }

        // This may be a partial wwn in the ViPR database (like for HDS Volumes).
        // Search for the partial WWN instead of the full WWN
        String partialWwn = VolumeWWNUtils.getPartialWwn(wwn);
        logDebug("block.volume.not.found.rety", wwn, partialWwn);
        matches = getClient().blockVolumes().findByWwn(partialWwn);
        if (!matches.isEmpty()) {
            return matches.get(0);
        }

        // Still not found, try shorter WWN for HUS-VM
        partialWwn = VolumeWWNUtils.getHusVmPartialWwn(wwn);
        logDebug("block.volume.not.found.retry", wwn, partialWwn);
        matches = getClient().blockVolumes().findByWwn(partialWwn);
        if (!matches.isEmpty()) {
            return matches.get(0);
        }

        logWarn("block.volume.not.found", wwn);
        return null;
    }
}