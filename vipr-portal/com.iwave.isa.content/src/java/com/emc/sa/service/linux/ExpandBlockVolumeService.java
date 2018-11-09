/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux;

import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VOLUME;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.block.BlockObjectRestRep;

@Service("Linux-ExpandBlockVolume")
public class ExpandBlockVolumeService extends LinuxService {

    @Param(VOLUME)
    protected String volumeId;

    @Param(SIZE_IN_GB)
    protected Double newSizeInGB;

    protected ExpandBlockVolumeHelper expandBlockVolumeHelper;

    private BlockObjectRestRep volume;

    @Override
    public void init() throws Exception {
        super.init();
        expandBlockVolumeHelper = ExpandBlockVolumeHelper.createHelper(linuxSystem, hostPorts);
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        acquireHostsLock();
        volume = BlockStorageUtils.getVolume(uri(volumeId));

        expandBlockVolumeHelper.precheck(volume, newSizeInGB);
    }

    @Override
    public void execute() throws Exception {
        expandBlockVolumeHelper.expandVolume(volume, newSizeInGB);

        if (hostId != null) {
            ExecutionUtils.addAffectedResource(hostId.toString());
        }
    }
}
