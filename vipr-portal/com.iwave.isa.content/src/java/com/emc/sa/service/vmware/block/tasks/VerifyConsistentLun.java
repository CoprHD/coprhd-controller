/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block.tasks;

import java.util.Map;

import com.emc.sa.engine.ExecutionTask;
import com.iwave.ext.vmware.HostStorageAPI;
import com.vmware.vim25.HostScsiDisk;
import com.vmware.vim25.HostScsiTopologyLun;
import com.vmware.vim25.HostScsiTopologyTarget;
import com.vmware.vim25.mo.HostSystem;

public class VerifyConsistentLun extends ExecutionTask<Void> {
    private Map<HostSystem, HostScsiDisk> host2disks;

    public VerifyConsistentLun(Map<HostSystem, HostScsiDisk> host2disks) {
        this.host2disks = host2disks;
    }

    @Override
    public void execute() throws Exception {
        int lun = -1;
        for (Map.Entry<HostSystem, HostScsiDisk> entry : host2disks.entrySet()) {
            HostSystem host = entry.getKey();
            HostScsiDisk disk = entry.getValue();
            int hlu = getHostLunId(host, disk);

            if (lun < 0) {
                lun = hlu;
            }
            else if (lun != hlu) {
            	throw new IllegalArgumentException(getMessage("VerifyConsistentLun.failure.inconsistendLunId",
            			disk.getDisplayName(), host.getName(), lun, hlu));
            }
        }
    }

    private int getHostLunId(HostSystem host, HostScsiDisk disk) {
        for (HostScsiTopologyTarget target : new HostStorageAPI(host).listScsiTopologyTargets()) {
            HostScsiTopologyLun lun = HostStorageAPI.findLun(target, disk);
            if (lun != null) {
                return lun.getLun();
            }
        }
        throw new IllegalArgumentException(getMessage("VerifyConsistentLun.failure.undetermineLunId", 
        		disk.getDisplayName(), host.getName()));
    }
}
