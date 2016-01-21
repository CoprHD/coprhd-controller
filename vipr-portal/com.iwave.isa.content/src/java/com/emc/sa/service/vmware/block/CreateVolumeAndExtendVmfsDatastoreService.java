/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block;

import static com.emc.sa.service.ServiceParams.DATASTORE_NAME;
import static com.emc.sa.service.ServiceParams.MULTIPATH_POLICY;

import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.block.CreateBlockVolumeForHostHelper;
import com.emc.sa.service.vmware.VMwareHostService;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.vmware.vim25.mo.Datastore;

@Service("VMware-CreateVolumeAndExtendVmfsDatastore")
public class CreateVolumeAndExtendVmfsDatastoreService extends VMwareHostService {
    @Bindable
    protected CreateBlockVolumeForHostHelper createBlockVolumeHelper = new CreateBlockVolumeForHostHelper();
    @Param(DATASTORE_NAME)
    protected String datastoreName;
    @Param(value = MULTIPATH_POLICY, required = false)
    protected String multipathPolicy;

    private Datastore datastore;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        createBlockVolumeHelper.precheck();
        acquireHostLock();
        datastore = vmware.getDatastore(datacenter.getLabel(), datastoreName);
        vmware.disconnect();
    }

    @Override
    public void execute() throws Exception {
        List<BlockObjectRestRep> volumes = createBlockVolumeHelper.createAndExportVolumes();
        if (volumes.isEmpty()) {
            ExecutionUtils.fail("CreateVolumeAndExtendVmfsDatastoreService.illegalState.noVolumesCreated", args(), args());
        }
        BlockObjectRestRep volume = volumes.get(0);

        connectAndInitializeHost();
        datastore = vmware.getDatastore(datacenter.getLabel(), datastoreName);
        vmware.extendVmfsDatastore(host, cluster, hostId, volume, datastore);
        vmware.setMultipathPolicy(host, cluster, multipathPolicy, volume);
    }
}
