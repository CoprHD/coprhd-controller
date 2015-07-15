/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block.tasks;

import com.emc.sa.engine.ExecutionTask;
import com.iwave.ext.vmware.HostStorageAPI;
import com.vmware.vim25.HostFileSystemMountInfo;
import com.vmware.vim25.HostVmfsVolume;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;

public class UnmountVmfsDatastore extends ExecutionTask<Void> {
    private final HostSystem host;
    private final Datastore datastore;

    public UnmountVmfsDatastore(HostSystem host, Datastore datastore) {
        this.host = host;
        this.datastore = datastore;
        provideDetailArgs(host.getName(), datastore.getName());
    }


    @Override
    public void execute() throws Exception {
    	
    	String vmfsUuid = findVmfsVolumeUuid(host, datastore);
		info("Found unmount volume id : " + vmfsUuid);
    	new HostStorageAPI(host).getStorageSystem().unmountVmfsVolume(vmfsUuid);
    }
    
    private String findVmfsVolumeUuid(HostSystem host, Datastore datastore) {
		for (HostFileSystemMountInfo mount : new HostStorageAPI(host)
				.getStorageSystem().getFileSystemVolumeInfo().getMountInfo()) {

			if (mount.getVolume() instanceof HostVmfsVolume && datastore.getName().equals(mount.getVolume().getName())) {
				HostVmfsVolume volume = (HostVmfsVolume) mount.getVolume();
				return volume.getUuid();
			}

		}
		return null;
	}
}
