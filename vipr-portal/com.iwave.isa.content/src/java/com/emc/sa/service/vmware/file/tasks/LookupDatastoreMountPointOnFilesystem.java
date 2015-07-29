/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.file.tasks;

import java.net.URI;

import com.emc.sa.machinetags.vmware.VMwareDatastoreTagger;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.FileShareRestRep;

public class LookupDatastoreMountPointOnFilesystem extends ViPRExecutionTask<String> {

    private final FileShareRestRep filesystem;
    private final URI vcenterId;
    private final URI datacenterId;
    private final String datastoreName;

    public LookupDatastoreMountPointOnFilesystem(FileShareRestRep filesystem, URI vcenterId, URI datacenterId, String datastoreName) {
        this.filesystem = filesystem;
        this.vcenterId = vcenterId;
        this.datacenterId = datacenterId;
        this.datastoreName = datastoreName;
        provideDetailArgs(datastoreName, filesystem.getId(), vcenterId, datacenterId);
    }

    @Override
    public String executeTask() throws Exception {
        // final String mountPoint = MachineTagUtils.getDatastoreMountPoint(filesystem, vcenterName, datacenterName, datastoreName);
        final String mountPoint = VMwareDatastoreTagger.getDatastoreMountPoint(filesystem, vcenterId, datacenterId, datastoreName);
        if (mountPoint != null) {
            return mountPoint;
        }

        // we didn't find the datastore and so we didn't find the mount point. Raise an exception.
        throw stateException("LookupDatastoreMountPointOnFilesystem.illegalState.noMountPoint",
                filesystem.getId(), vcenterId, datacenterId, datastoreName);
    }
}
