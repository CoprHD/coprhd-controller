/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.tasks;

import javax.inject.Inject;

import com.emc.sa.engine.ExecutionTask;
import com.iwave.ext.vmware.VCenterAPI;
import com.iwave.ext.vmware.VMwareUtils;
import com.vmware.vim25.HostSystemConnectionState;
import com.vmware.vim25.mo.HostSystem;

public class FindESXHost extends ExecutionTask<HostSystem> {
    @Inject
    private VCenterAPI vcenter;
    private String datacenterName;
    private String esxHostName;
    private boolean verifyHostExists;

    public FindESXHost(String datacenterName, String esxHostName, boolean verifyHostExists) {
        this.datacenterName = datacenterName;
        this.esxHostName = esxHostName;
        this.verifyHostExists = verifyHostExists;
        provideDetailArgs(esxHostName, datacenterName);
    }

    @Override
    public HostSystem executeTask() throws Exception {
        debug("Executing: %s", getDetail());
        HostSystem host = vcenter.findHostSystem(datacenterName, esxHostName);
        if (host == null) {
            if (verifyHostExists) {
                throw stateException("FindESXHost.illegalState.noHost", datacenterName, esxHostName);
            } else {
                return null;
            }
        }
        if (verifyHostExists) {
            // Check the connection state of this host
            HostSystemConnectionState connectionState = VMwareUtils.getConnectionState(host);
            logInfo("find.esx.host.state", esxHostName, connectionState);
            if (connectionState == null) {
                throw stateException("FindESXHost.illegalState.noState", esxHostName, datacenterName);
            } else if (connectionState == HostSystemConnectionState.notResponding) {
                throw stateException("FindESXHost.illegalState.notResponding", esxHostName);
            } else if (connectionState == HostSystemConnectionState.disconnected) {
                throw stateException("FindESXHost.illegalState.notConnected", esxHostName);
            }
        }
        return host;
    }
}
