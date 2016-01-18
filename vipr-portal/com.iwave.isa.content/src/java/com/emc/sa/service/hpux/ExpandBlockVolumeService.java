/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux;

import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VOLUME;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.block.BlockObjectRestRep;

@Service("Hpux-ExpandBlockVolume")
public class ExpandBlockVolumeService extends HpuxService {

    @Param(VOLUME)
    protected String volumeId;

    @Param(SIZE_IN_GB)
    protected Double newSizeInGB;

    protected ExpandBlockVolumeHelper expandBlockVolumeHelper;

    private BlockObjectRestRep volume;

    @Override
    public void init() throws Exception {
        super.init();
        expandBlockVolumeHelper = ExpandBlockVolumeHelper.createHelper(hpuxSystem, hostPorts);
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        volume = BlockStorageUtils.getVolume(uri(volumeId));
        acquireHostsLock();

        expandBlockVolumeHelper.precheck(volume);
    }

    @Override
    public void execute() throws Exception {
        volume = BlockStorageUtils.getVolume(uri(volumeId));
        expandBlockVolumeHelper.expandVolume(volume, newSizeInGB);
        if (hostId != null) {
            ExecutionUtils.addAffectedResource(hostId.toString());
        }
    }
}
