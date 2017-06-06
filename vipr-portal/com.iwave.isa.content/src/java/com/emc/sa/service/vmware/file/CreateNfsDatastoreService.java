/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.file;

import static com.emc.sa.service.ServiceParams.DATASTORE_NAME;
import static com.emc.sa.service.ServiceParams.EXPORT_NAME;
import static com.emc.sa.service.ServiceParams.FILESYSTEMS;
import static com.emc.sa.service.ServiceParams.STORAGE_IO_CONTROL;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.sa.service.vmware.VMwareHostService;
import com.emc.sa.service.vmware.file.tasks.AddNfsExportEndpoints;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.google.common.collect.Lists;
import com.vmware.vim25.mo.Datastore;

@Service("VMware-CreateNfsDatastore")
public class CreateNfsDatastoreService extends VMwareHostService {
    @Param(FILESYSTEMS)
    protected URI fileSystemId;
    @Param(EXPORT_NAME)
    protected String mountPoint;
    @Param(DATASTORE_NAME)
    protected String datastoreName;
    @Param(value = STORAGE_IO_CONTROL, required = false)
    protected Boolean storageIOControl;

    private FileShareRestRep fileSystem;
    private FileSystemExportParam export;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        acquireHostLock();
        vmware.verifyDatastoreDoesNotExist(datacenter.getLabel(), datastoreName);
        fileSystem = FileStorageUtils.getFileSystem(fileSystemId);
        export = FileStorageUtils.findNfsExportByMountPoint(fileSystemId, mountPoint);
    }

    @Override
    public void execute() throws Exception {
        List<Datastore> datastores = Lists.newArrayList();
        updateExportHosts();
        if (cluster != null) {
            datastores.addAll(vmware.createNfsDatastore(cluster, fileSystem, export, datacenterId, datastoreName));
        }
        else {
            datastores.add(vmware.createNfsDatastore(host, fileSystem, export, datacenterId, datastoreName));
        }

        if (hostId != null) {
            ExecutionUtils.addAffectedResource(hostId.toString());
        }

        for (Datastore datastore : datastores) {
            vmware.setStorageIOControl(datastore, storageIOControl, true);
        }
    }

    private void updateExportHosts() {
        Set<String> ipAddresses = vmware.getEndpoints(host, cluster);
        if (export.getEndpoints() != null) {
            ipAddresses.removeAll(export.getEndpoints());
        }

        // Add any missing IPs to the export list
        if (!ipAddresses.isEmpty()) {
            execute(new AddNfsExportEndpoints(fileSystemId, export, ipAddresses));
        }
    }
}
