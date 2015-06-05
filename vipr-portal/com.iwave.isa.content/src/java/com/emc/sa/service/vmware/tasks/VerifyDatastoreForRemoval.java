/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vmware.tasks;

import java.util.Set;

import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionTask;
import com.google.common.collect.Sets;
import com.iwave.ext.vmware.VCenterAPI;
import com.vmware.vim25.DatastoreHostMount;
import com.vmware.vim25.DatastoreSummary;
import com.vmware.vim25.DatastoreSummaryMaintenanceModeState;
import com.vmware.vim25.HostMountInfo;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.VirtualMachine;

public class VerifyDatastoreForRemoval extends ExecutionTask<Void> {
    @Inject
    private VCenterAPI vcenter;
    private Datastore datastore;

    public VerifyDatastoreForRemoval(Datastore datastore) {
        this.datastore = datastore;
        provideDetailArgs(datastore.getName());
    }

    @Override
    public void execute() throws Exception {
        DatastoreSummary summary = datastore.getSummary();
        if (summary == null) {
            throw stateException("verify.datastore.removal.illegalState.summaryUnavailable", datastore.getName());
        }
        checkDatastoreAccessibility(summary);
        checkMaintenanceMode(summary);
        checkVirtualMachines();
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

    private void checkMaintenanceMode(DatastoreSummary summary) {
        String mode = summary.getMaintenanceMode();

        // If a datastore is already entering maintenance mode, fail
        if (DatastoreSummaryMaintenanceModeState.enteringMaintenance.name().equals(mode)) {
            throw stateException("verify.datastore.removal.illegalState.maintenanceMode", datastore.getName());
        }
        else if (DatastoreSummaryMaintenanceModeState.inMaintenance.name().equals(mode)) {
            logInfo("verify.datastore.removal.maintenance.mode", datastore.getName());
        }
    }

    private void checkVirtualMachines() {
        VirtualMachine[] vms = datastore.getVms();
        if ((vms != null) && (vms.length > 0)) {
            Set<String> names = Sets.newTreeSet();
            for (VirtualMachine vm : vms) {
                names.add(vm.getName());
            }
            throw stateException("verify.datastore.removal.illegalState.notEmpty",
                    datastore.getName(), vms.length, names);
        }
    }
}
