/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.file;

import static com.emc.sa.service.ServiceParams.DATASTORE_NAME;
import static com.emc.sa.service.ServiceParams.PROJECT;
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
import com.emc.sa.service.vmware.file.tasks.LookupDatastoreMountPointOnFilesystem;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.google.common.collect.Lists;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;

/**
 * @author sanjes
 *
 */
@Service("VMware-ExtendSharedNfsDatastore")
public class ReshareNfsDatastoreToHostService extends VMwareHostService {
    @Param(PROJECT)
    protected URI project;
    @Param(DATASTORE_NAME)
    protected String datastoreName;
    @Param(value = STORAGE_IO_CONTROL, required = false)
    protected Boolean storageIOControl;

    protected Datastore datastore;
    protected FileShareRestRep fileSystem;
    protected List<HostSystem> hostsAdded;
    private FileSystemExportParam export;

    @Override
    public void init() throws Exception {
        super.init();
        vmware.connect(vcenterId);
        datacenter = vmware.getDatacenter(datacenterId);
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        fileSystem = vmware.findFileSystemWithDatastore(project, datacenterId, datastoreName);
        datastore = vmware.getDatastore(datacenter.getLabel(), datastoreName);
        hostsAdded = vmware.getTheHostsAdded(cluster, datastore);
        String mountPoint = execute(new LookupDatastoreMountPointOnFilesystem(fileSystem, vcenterId, datacenterId, datastoreName));
        export = FileStorageUtils.findNfsExportByMountPoint(fileSystem.getId(), mountPoint);
    }

    @Override
    public void execute() throws Exception {

        // update the export list
        updateExportHosts();
        // add the NFS datastore to the hosts
        List<Datastore> datastores = Lists.newArrayList();
        for (HostSystem addedHost : hostsAdded) {
            datastores.add(vmware.createNfsDatastore(addedHost, fileSystem, export, datacenterId, datastoreName));
        }
        ExecutionUtils.addAffectedResource(hostId.toString());
        for (Datastore newDatastore : datastores) {
            vmware.setStorageIOControl(newDatastore, storageIOControl, true);
        }

    }

    private void updateExportHosts() {
        Set<String> ipAddresses = vmware.getEndpoints(host, cluster);
        if (export.getEndpoints() != null) {
            ipAddresses.removeAll(export.getEndpoints());
        }

        // Add any missing IPs to the export list
        if (!ipAddresses.isEmpty()) {
            execute(new AddNfsExportEndpoints(fileSystem.getId(), export, ipAddresses));
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        vmware.disconnect();
    }

}
