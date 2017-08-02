/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.compute.OsInstallParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.vipr.client.Task;

public class InstallOs extends ViPRExecutionTask<Task<HostRestRep>> {
    private Host host;
    private OsInstallParam param;
    private String encryptedPassword;
    private String decryptedPassword;

    public InstallOs(Host host, OsInstallParam param) {
        this.host = host;
        this.param = param;
        encryptedPassword = param.getRootPassword();
        provideDetailArgs(host.getHostName());
    }

    @Override
    public Task<HostRestRep> executeTask() throws Exception {
        decryptedPassword = decrypt(encryptedPassword);
        param.setRootPassword(decryptedPassword);
        Task<HostRestRep> task = getClient().hosts().osInstall(host.getId(), param);
        addOrderIdTag(task.getTaskResource().getId());
        return task;
    }
}
