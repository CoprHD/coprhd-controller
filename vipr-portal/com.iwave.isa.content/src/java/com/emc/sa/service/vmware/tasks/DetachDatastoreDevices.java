package com.emc.sa.service.vmware.tasks;

import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VMWareException;
import com.vmware.vim25.ResourceInUse;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;

public class DetachDatastoreDevices extends RetryableTask<Void> {
    private final Datastore datastore;
    private final HostStorageAPI hostStorageAPI;

    public DetachDatastoreDevices(HostSystem host, Datastore datastore) {
        this.datastore = datastore;
        this.hostStorageAPI = new HostStorageAPI(host);
        provideDetailArgs(datastore.getName(), host.getName());
        provideNameArgs(datastore.getName());
    }

    @Override
    protected Void tryExecute() {
        hostStorageAPI.detachDatastore(datastore);
        return null;
    }

    @Override
    protected boolean canRetry(VMWareException e) {
        return e.getCause() instanceof ResourceInUse;
    }
}
