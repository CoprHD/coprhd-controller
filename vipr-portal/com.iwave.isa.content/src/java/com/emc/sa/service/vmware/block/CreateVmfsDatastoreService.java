/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block;

import static com.emc.sa.service.ServiceParams.DATASTORE_NAME;
import static com.emc.sa.service.ServiceParams.MULTIPATH_POLICY;
import static com.emc.sa.service.ServiceParams.STORAGE_IO_CONTROL;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.net.URI;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.block.ExportBlockVolumeHelper;
import com.emc.sa.service.vmware.VMwareHostService;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.vmware.vim25.mo.Datastore;

@Service("VMware-CreateVmfsDatastore")
public class CreateVmfsDatastoreService extends VMwareHostService {

    @Param(VOLUMES)
    protected URI volumeId;

    @Param(DATASTORE_NAME)
    protected String datastoreName;

    @Param(value = MULTIPATH_POLICY, required = false)
    protected String multipathPolicy;

    @Param(value = STORAGE_IO_CONTROL, required = false)
    protected Boolean storageIOControl;

    @Bindable
    protected ExportBlockVolumeHelper exportBlockVolumeHelper = new ExportBlockVolumeHelper();

    private BlockObjectRestRep volume;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        exportBlockVolumeHelper.precheck();

        acquireHostLock();
        vmware.verifyDatastoreDoesNotExist(datacenter.getLabel(), datastoreName);
        vmware.verifySupportedMultipathPolicy(host, multipathPolicy);
        vmware.disconnect();
    }

    @Override
    public void execute() throws Exception {
        exportBlockVolumeHelper.exportVolumes();
        volume = BlockStorageUtils.getVolume(volumeId);
        connectAndInitializeHost();
        Datastore datastore = vmware.createVmfsDatastore(host, cluster, hostId, volume, datastoreName);
        vmware.refreshStorage(host, cluster);
        vmware.setMultipathPolicy(host, cluster, multipathPolicy, volume);
        vmware.setStorageIOControl(datastore, storageIOControl);
    }
}
