/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.block.CreateBlockStorageForHostService;
import com.emc.sa.service.vmware.VMwareHostService;

@Service("VMware-CreateBlockVolume")
public class CreateBlockVolumeService extends VMwareHostService {

    @Bindable
    protected CreateBlockStorageForHostService hostService = new CreateBlockStorageForHostService();

    @Override
    public boolean checkClusterConnectivity() {
        return false;
    }

    @Override
    public void init() throws Exception {
        hostService.setClientConfig(getClientConfig());
        hostService.setModelClient(getModelClient());
        hostService.setEncryptionProvider(getEncryptionProvider());
        hostService.setProxyUser(getProxyUser());
        hostService.init();
    }

    @Override
    public void precheck() throws Exception {
        hostService.precheck();
    }

    @Override
    public void execute() throws Exception {
        hostService.execute();
        super.connectAndInitializeHost();

        vmware.refreshStorage(host, cluster);
        vmware.attachLuns(host, cluster, hostService.getVolumes());
    }

}