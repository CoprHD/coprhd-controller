/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.tasks;

import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VMWareException;
import com.iwave.ext.vmware.VMwareUtils;
import com.vmware.vim25.ResourceInUse;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;

/**
 * This task is responsible for umounting all of the Vmfs Volumes on a
 * Datastore on all of its necessary hosts
 * 
 * @author Jay Logelin
 *
 */
public class UnmountDatastore extends RetryableTask<Void> {

    private final Datastore datastore;

    private final Collection<HostSystem> hosts;

    public UnmountDatastore(Collection<HostSystem> hosts, Datastore datastore) {
        this.datastore = datastore;

        this.hosts = hosts;
        List<String> names = Lists.newArrayList();
        for (HostSystem host : hosts) {
            names.add(host.getName());
        }
        provideDetailArgs(datastore.getName(), StringUtils.join(names, ", "));
        provideNameArgs(datastore.getName());
    }

    @Override
    protected Void tryExecute() {
        for (HostSystem host : hosts) {
            if (VMwareUtils.isDatastoreMountedOnHost(datastore, host)) {
                HostStorageAPI storageAPI = new HostStorageAPI(host);
                storageAPI.unmountVmfsDatastore(datastore);
            }
        }
        return null;
    }

    @Override
    protected boolean canRetry(VMWareException e) {
        return e.getCause() instanceof ResourceInUse;
    }
}
