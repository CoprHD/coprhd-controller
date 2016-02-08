/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block;

import static com.emc.sa.service.ServiceParams.DATASTORE_NAME;
import static com.emc.sa.service.ServiceParams.MULTIPATH_POLICY;
import static com.emc.sa.service.ServiceParams.VOLUME;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vmware.VMwareHostService;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.vmware.vim25.mo.Datastore;

@Service("VMware-ExtendVmfsDatastore")
public class ExtendVmfsDatastoreService extends VMwareHostService {
    @Param(VOLUME)
    protected URI volumeId;
    @Param(DATASTORE_NAME)
    protected String datastoreName;
    @Param(value = MULTIPATH_POLICY, required = false)
    protected String multipathPolicy;

    private BlockObjectRestRep volume;
    private Datastore datastore;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        volume = BlockStorageUtils.getVolume(volumeId);
        acquireHostLock();
        datastore = vmware.getDatastore(datacenter.getLabel(), datastoreName);
        vmware.verifySupportedMultipathPolicy(host, multipathPolicy);
        vmware.disconnect();
    }

    @Override
    public void execute() throws Exception {
        connectAndInitializeHost();
        datastore = vmware.getDatastore(datacenter.getLabel(), datastoreName);
        vmware.extendVmfsDatastore(host, cluster, hostId, volume, datastore);
        if (hostId != null) {
            ExecutionUtils.addAffectedResource(hostId.toString());
        }
        vmware.setMultipathPolicy(host, cluster, multipathPolicy, volume);
    }
}
