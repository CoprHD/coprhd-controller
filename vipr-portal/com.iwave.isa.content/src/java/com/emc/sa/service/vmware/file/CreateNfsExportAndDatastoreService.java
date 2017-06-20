/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.file;

import static com.emc.sa.service.ServiceParams.DATASTORE_NAME;
import static com.emc.sa.service.ServiceParams.EXPORT_HOSTS;
import static com.emc.sa.service.ServiceParams.STORAGE_IO_CONTROL;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.file.CreateNfsExportHelper;
import com.emc.sa.service.vipr.file.FileConstants;
import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.sa.service.vmware.VMwareHostService;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.google.common.collect.Lists;
import com.vmware.vim25.mo.Datastore;

@Service("VMware-CreateNfsExportAndDatastore")
public class CreateNfsExportAndDatastoreService extends VMwareHostService {
    @Bindable
    protected CreateNfsExportHelper createNfsExportHelper = new CreateNfsExportHelper();
    @Param(DATASTORE_NAME)
    protected String datastoreName;
    @Param(value = EXPORT_HOSTS, required = false)
    protected List<String> exportHosts;
    @Param(value = STORAGE_IO_CONTROL, required = false)
    protected Boolean storageIOControl;

    protected List<String> calculateExportHosts() {
        Set<String> ipAddresses = vmware.getEndpoints(host, cluster);
        if (exportHosts != null) {
            for (String host : exportHosts) {
                if (StringUtils.isNotBlank(host)) {
                    ipAddresses.add(host);
                }
            }
        }
        return Lists.newArrayList(ipAddresses);
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        createNfsExportHelper.setPermissions(FileConstants.ROOT_PERMISSIONS);
        createNfsExportHelper.setExportHosts(calculateExportHosts());
        createNfsExportHelper.setRootUser("nobody");
        acquireHostLock();
        vmware.verifyDatastoreDoesNotExist(datacenter.getLabel(), datastoreName);
        vmware.disconnect();
    }

    @Override
    public void execute() throws Exception {
        URI fileSystemId = createNfsExportHelper.createNfsExport();
        List<Datastore> datastores = Lists.newArrayList();
        FileShareRestRep fileSystem = FileStorageUtils.getFileSystem(fileSystemId);
        FileSystemExportParam export = createNfsExportHelper.getNfsExport(fileSystemId);
        connectAndInitializeHost();
        if (cluster != null) {
            datastores.addAll(vmware.createNfsDatastore(cluster, fileSystem, export, datacenterId, datastoreName));
        } else {
            datastores.add(vmware.createNfsDatastore(host, fileSystem, export, datacenterId, datastoreName));
        }

        if (hostId != null) {
            ExecutionUtils.addAffectedResource(hostId.toString());
        }

        for (Datastore datastore : datastores) {
            vmware.setStorageIOControl(datastore, storageIOControl, true);
        }
    }
}
