/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.file;

import static com.emc.sa.service.ServiceParams.DATASTORE_NAME;
import static com.emc.sa.service.ServiceParams.PROJECT;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.sa.service.vmware.VMwareHostService;
import com.emc.sa.service.vmware.file.tasks.LookupDatastoreMountPointOnFilesystem;
import com.emc.sa.service.vmware.file.tasks.RemoveNfsExportEndPoints;
import com.emc.sa.service.vmware.tasks.DeleteDatastore;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;

/**
 * @author sanjes
 *
 */
@Service("VMware-UnsharedNfsDatastoreFromDeletedHost")
public class UnshareNfsDatastoreFromHostService extends VMwareHostService {
    @Param(PROJECT)
    protected URI project;
    @Param(DATASTORE_NAME)
    protected String datastoreName;

    protected Datastore datastore;
    protected FileShareRestRep fileSystem;
    protected List<HostSystem> hostsDeleted;
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
        hostsDeleted = vmware.getTheHostsDeleted(cluster, datastore);
        String mountPoint = execute(new LookupDatastoreMountPointOnFilesystem(fileSystem, vcenterId, datacenterId, datastoreName));
        export = FileStorageUtils.findNfsExportByMountPoint(fileSystem.getId(), mountPoint);
        vmware.checkFsMountpathOfDs(datastore, fileSystem);
    }

    @Override
    public void execute() throws Exception {

        // add the NFS datastore to the h
        for (HostSystem deletedHost : hostsDeleted) {
            execute(new DeleteDatastore(deletedHost, datastore));
        }
        // update the export list
        updateExportHosts();
        ExecutionUtils.addAffectedResource(hostId.toString());

    }

    private void updateExportHosts() {
        Set<String> ipAddresses = vmware.getEndpoints(host, cluster);
        if (export.getEndpoints() != null) {
            ipAddresses.removeAll(export.getEndpoints());
        }

        // Add any missing IPs to the export list
        if (!ipAddresses.isEmpty()) {
            execute(new RemoveNfsExportEndPoints(fileSystem.getId(), export, ipAddresses));
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        vmware.disconnect();
    }

}
