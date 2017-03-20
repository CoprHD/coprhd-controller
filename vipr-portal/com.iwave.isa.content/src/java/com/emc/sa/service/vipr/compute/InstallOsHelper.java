/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResource;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.addRollback;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.sa.engine.ExecutionContext;
import com.emc.sa.engine.ExecutionException;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.ExecutionUtils.ViPRTaskHandler;
import com.emc.sa.engine.ViPRTaskMonitor;
import com.emc.sa.service.vipr.compute.tasks.DeactivateHost;
import com.emc.storageos.model.compute.OsInstallParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.vipr.client.Task;

public class InstallOsHelper implements ViPRTaskHandler<HostRestRep> {
    private Set<URI> successfulHostIds = new HashSet<>();
    private Map<HostRestRep, OsInstallParam> hostToOsInstall;

    public InstallOsHelper(Map<HostRestRep, OsInstallParam> hostToOsInstall) {
        this.hostToOsInstall = hostToOsInstall;
    }

    public Set<URI> getSuccessfulHostIds() {
        return successfulHostIds;
    }

    public void installOs() {
        ExecutionContext context = ExecutionUtils.currentContext();

        List<ViPRTaskMonitor<HostRestRep>> tasks = new ArrayList<>();
        for (HostRestRep host : hostToOsInstall.keySet()) {
            OsInstallParam osInstall = hostToOsInstall.get(host);
            if (osInstall != null) {
                try {
                    //tasks.add(ExecutionUtils.startViprTask(new InstallOs(host, osInstall)));
                } catch (ExecutionException e) {
                    context.logError("computeutils.installOs.failure", host.getId(), e.getMessage());
                }
            }
        }
        if (!ExecutionUtils.waitForTask(tasks, this)) {
            // TODO: Re-throw the error?
            // ExecutionUtils.checkForError(tasks);
        }
    }

    @Override
    public void onSuccess(Task<HostRestRep> task, HostRestRep host) {
        ExecutionUtils.currentContext().logInfo("computeutils.installOs.success", host.getHostName());
        addAffectedResource(host);
        addRollback(new DeactivateHost(host.getId(), true));
        successfulHostIds.add(host.getId());
    }

    @Override
    public void onFailure(Task<HostRestRep> task, ExecutionException e) {
        ExecutionUtils.currentContext().logError("computeutils.installOs.installing.failure.task",
                task.getResource().getName(), task.getMessage());
    }
}
