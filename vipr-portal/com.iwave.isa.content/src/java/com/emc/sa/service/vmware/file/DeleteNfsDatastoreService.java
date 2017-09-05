/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.file;

import static com.emc.sa.service.ServiceParams.DATACENTER;
import static com.emc.sa.service.ServiceParams.DATASTORE_NAME;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.VCENTER;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vmware.VMwareSupport;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.model.file.FileShareRestRep;
import com.vmware.vim25.mo.Datastore;

@Service("VMware-DeleteNfsDatastore")
public class DeleteNfsDatastoreService extends ViPRService {
    @Param(PROJECT)
    protected URI project;
    @Param(VCENTER)
    protected URI vcenterId;
    @Param(DATACENTER)
    protected URI datacenterId;
    @Param(DATASTORE_NAME)
    protected String datastoreName;

    protected Datastore datastore;
    protected FileShareRestRep fileSystem;
    protected VcenterDataCenter datacenter;

    protected VMwareSupport vmware = new VMwareSupport();

    @Override
    public void init() throws Exception {
        super.init();
        vmware.connect(vcenterId);
        datacenter = vmware.getDatacenter(datacenterId);
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        // try {
            fileSystem = vmware.findFileSystemWithDatastore(project, datacenterId, datastoreName);
            datastore = vmware.getDatastore(datacenter.getLabel(), datastoreName);
            vmware.verifyDatastoreForRemoval(datastore);
            vmware.checkFsMountpathOfDs(datastore, fileSystem);
        /*
         * } catch (ExecutionException e) {
         * if (e.getMessage().contains("Unable to find datastore")) {
         * vmware.removeNfsDatastoreTag(fileSystem, datacenterId, datastoreName);
         * 
         * }
         * throw e;
         * }
         */
    }

    @Override
    public void execute() throws Exception {
        vmware.deleteNfsDatastore(fileSystem, datacenterId, datastore, datastoreName);
    }

    @Override
    public void destroy() {
        super.destroy();
        vmware.disconnect();
    }

}
