/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.sa.engine.ExecutionContext;
import com.emc.sa.engine.ExecutionException;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.ExecutionUtils.ViPRTaskHandler;
import com.emc.sa.engine.ViPRTaskMonitor;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.block.tasks.CreateBlockVolumeByName;
import com.emc.sa.service.vipr.compute.tasks.SetBootVolume;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Task;

public class BootVolumeHelper implements ViPRTaskHandler<VolumeRestRep> {
    private URI project;
    private URI virtualArray;
    private URI virtualPool;
    private double sizeInGb;
    private List<Host> hosts;
    private Map<URI, Host> volumeIdToHost = new HashMap<>();
    private Set<URI> successfulHosts = new HashSet<>();

    public BootVolumeHelper(URI project, URI virtualArray, URI virtualPool, double sizeInGb, List<Host> hosts) {
        this.project = project;
        this.virtualArray = virtualArray;
        this.virtualPool = virtualPool;
        this.sizeInGb = sizeInGb;
        this.hosts = hosts;
    }

    /**
     * Gets the IDs of the hosts that have successfully had boot volumes created.
     * 
     * @return the set of successful host IDs.
     */
    public Set<URI> getSuccessfulHostId() {
        return successfulHosts;
    }

    /**
     * Creates a number of boot volumes and waits for all tasks to complete.
     */
    public void run() {
        ExecutionContext context = ExecutionUtils.currentContext();

        List<ViPRTaskMonitor<VolumeRestRep>> tasks = new ArrayList<>();
        for (Host host : hosts) {
            try {
                tasks.add(createBootVolume(host));
            } catch (ExecutionException e) {
                context.logError("computeutils.makebootvolumes.failure", host.getHostName(), e.getMessage());
            }
        }
        if (!ExecutionUtils.waitForTask(tasks, this)) {
            // TODO: Re-throw the error?
            // ExecutionUtils.checkForError(tasks);
        }
    }

    /**
     * Gets the name to use for the boot volume.
     * 
     * @param host
     *            the host for the boot volume.
     * @return the boot volume name.
     */
    private String getBootVolumeName(Host host) {
        String baseVolumeName = host.getHostName().replaceAll("[^A-Za-z0-9_]", "_").concat("_boot");
        String volumeName = baseVolumeName;
        int volumeNumber = 0;
        while (isVolumeNameTaken(volumeName)) {
            volumeName = String.format("%s_%d", baseVolumeName, volumeNumber);
            volumeNumber++;
        }
        return volumeName;
    }

    /**
     * Determines if the given volume name is already taken.
     * 
     * @param name
     *            the name to test.
     * @return true if the volume name is avaiable for use.
     */
    private boolean isVolumeNameTaken(String name) {
        return !BlockStorageUtils.getVolumeByName(name).isEmpty();
    }

    /**
     * Creates a boot volume for the given host.
     * 
     * @param host
     *            the host to create the boot volume for.
     * @return the task for monitoring the volume creation.
     */
    private ViPRTaskMonitor<VolumeRestRep> createBootVolume(Host host) {
        String volumeName = getBootVolumeName(host);
        String volumeSize = BlockStorageUtils.gbToVolumeSize(sizeInGb);
        ViPRTaskMonitor<VolumeRestRep> task = ExecutionUtils.startViprTask(new CreateBlockVolumeByName(project,
                virtualArray, virtualPool, volumeSize, null, volumeName));
        URI volumeId = task.getTask().getResourceId();
        volumeIdToHost.put(volumeId, host);
        return task;
    }

    @Override
    public void onSuccess(Task<VolumeRestRep> task, VolumeRestRep volume) {
        ViPRExecutionUtils.addAffectedResource(volume);

        Host host = volumeIdToHost.get(volume.getId());
        if (host != null) {
            try {
                ExecutionUtils.execute(new SetBootVolume(host, volume.getId(),false));
                successfulHosts.add(host.getId());
            } catch (ExecutionException e) {
                ExecutionUtils.currentContext().logError(e, "SetBootVolume.failure", host.getHostName(),
                        volume.getName());
            }
        }
    }

    @Override
    public void onFailure(Task<VolumeRestRep> task, ExecutionException e) {
        String volumeName = task.getResource().getName();
        ExecutionUtils.currentContext().logError("computeutils.makebootvolumes.createvolume.failure", volumeName,
                task.getMessage());
    }
}
