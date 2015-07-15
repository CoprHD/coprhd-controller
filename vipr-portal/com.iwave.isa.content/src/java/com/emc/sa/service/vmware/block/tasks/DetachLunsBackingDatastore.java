/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block.tasks;

import java.util.List;

import com.emc.sa.engine.ExecutionTask;
import com.iwave.ext.vmware.HostStorageAPI;
import com.vmware.vim25.HostScsiDisk;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;

public class DetachLunsBackingDatastore extends ExecutionTask<Void> {
    private final HostSystem host;
    private final Datastore datastore;

    public DetachLunsBackingDatastore(HostSystem host, Datastore datastore) {
        this.host = host;
        this.datastore = datastore;
        provideDetailArgs(host.getName(), datastore.getName());
    }


    @Override
    public void execute() throws Exception {
    	
        List<HostScsiDisk> disks = new HostStorageAPI(host).listDisks(datastore);
                
        for (HostScsiDisk disk : disks) {
        	debug("Detaching disk %s", disk.getCanonicalName());
        	host.getHostStorageSystem().detachScsiLun(disk.getUuid());
        }
    }
}
