/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.tasks;

import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionTask;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.client.model.Host;
import com.iwave.ext.vmware.VCenterAPI;
import com.vmware.vim25.DatastoreHostMount;
import com.vmware.vim25.DatastoreSummary;
import com.vmware.vim25.HostMountInfo;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;

public class VerifyDatastoreForRemoval extends ExecutionTask<Void> {
    @Inject
    private VCenterAPI vcenter;
    private Datastore datastore;
    private String datacenterName;
    private List<Host> hosts;
    private List<HostSystem> hostSystems;

    public VerifyDatastoreForRemoval(Datastore datastore) {
        this(datastore, null, null);
    }

    public VerifyDatastoreForRemoval(Datastore datastore, String datacenterName, List<Host> hosts) {
        this.datastore = datastore;
        this.datacenterName = datacenterName;
        this.hosts = hosts;
        provideDetailArgs(datastore.getName());
    }

    public VerifyDatastoreForRemoval(Datastore datastore, List<HostSystem> hostSystems) {
        this(datastore, null, null);
        this.hostSystems = hostSystems;
    }

    @Override
    public void execute() throws Exception {
        DatastoreSummary summary = datastore.getSummary();
        if (summary == null) {
            throw stateException("verify.datastore.removal.illegalState.summaryUnavailable", datastore.getName());
        }
        checkDatastoreAccessibility(summary);
        ComputeSystemHelper.checkMaintenanceMode(datastore, summary);
        if (hosts != null && !hosts.isEmpty()) {
            for (Host host : hosts) {
                HostSystem hostSystem = vcenter.findHostSystem(datacenterName, host.getHostName());
                ComputeSystemHelper.checkVirtualMachines(datastore, hostSystem);
            }
        } else if (hostSystems != null && !hostSystems.isEmpty()) {
            for (HostSystem hostSystem : hostSystems) {
                ComputeSystemHelper.checkVirtualMachines(datastore, hostSystem);
            }
        } else {
            ComputeSystemHelper.checkVirtualMachines(datastore, null);
        }
    }

    private void checkDatastoreAccessibility(DatastoreSummary summary) {
        if (!summary.isAccessible()) {
            throw stateException("verify.datastore.removal.illegalState.notAccessible", datastore.getName());
        }
        if (datastore.getHost() != null) {
            for (DatastoreHostMount mount : datastore.getHost()) {
                checkDatastoreAccessibility(mount);
            }
        }
    }

    private void checkDatastoreAccessibility(DatastoreHostMount mount) {
        HostMountInfo mountInfo = mount.getMountInfo();
        if (mountInfo.getAccessible() == Boolean.FALSE) {
            HostSystem host = vcenter.lookupManagedEntity(mount.getKey());
            String hostName = host.getName();
            String reason = StringUtils.defaultIfBlank(mountInfo.getInaccessibleReason(), "unknown");
            logWarn("verify.datastore.removal.inaccessible", hostName, reason);
        }
    }
}
