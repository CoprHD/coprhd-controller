/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.tasks;

import java.net.URL;

import com.emc.sa.engine.ExecutionTask;
import com.emc.storageos.db.client.model.Vcenter;
import com.iwave.ext.vmware.VCenterAPI;

public class ConnectToVCenter extends ExecutionTask<VCenterAPI> {
    private Vcenter vcenter;

    public ConnectToVCenter(Vcenter vcenter) {
        this.vcenter = vcenter;
        provideDetailArgs(vcenter.getLabel(), vcenter.getIpAddress());
    }

    @Override
    public VCenterAPI executeTask() throws Exception {
        debug("Executing: %s", getDetail());
        String host = vcenter.getIpAddress();
        int port = vcenter.getPortNumber() != null ? vcenter.getPortNumber() : 443;

        URL url = new URL("https", host, port, "/sdk");

        VCenterAPI vcenterAPI = new VCenterAPI(url);
        vcenterAPI.login(vcenter.getUsername(), vcenter.getPassword());
        logInfo("connect.vcenter.logged", vcenter.getLabel(), vcenterAPI.getAboutInfo().getFullName());
        return vcenterAPI;
    }
}
