/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.util.List;
import java.util.Set;

import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.block.CreateBlockVolumeHelper;
import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationParameters;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

/**
 * Task that will create multiple block volumes in parallel. Executes a single create volume API
 * call for each CreateBlockVolumeHelper instance and returns tasks for all volumes that are created.
 */
public class CreateMultipleBlockVolumes extends WaitForTasks<VolumeRestRep> {
    private final List<? extends CreateBlockVolumeHelper> helpers;

    public CreateMultipleBlockVolumes(List<? extends CreateBlockVolumeHelper> helpers) {
        this.helpers = helpers;
        if (!helpers.isEmpty()) {
            CreateBlockVolumeHelper helper = helpers.get(0);
            provideDetailArgs(helper.getVirtualPool(), helper.getVirtualArray(), helper.getProject(),
                    getDetails(helpers));
        }
    }

    @Override
    public Tasks<VolumeRestRep> doExecute() throws Exception {
        Set<String> errorMessages = Sets.newHashSet();
        Tasks<VolumeRestRep> tasks = null;
        for (CreateBlockVolumeHelper param : helpers) {
            String volumeSize = BlockStorageUtils.gbToVolumeSize(param.getSizeInGb());
            VolumeCreate create = new VolumeCreate();
            create.setVpool(param.getVirtualPool());
            create.setVarray(param.getVirtualArray());
            create.setProject(param.getProject());
            create.setName(param.getName());
            create.setSize(volumeSize);
            create.setComputeResource(param.getComputeResource());
            create.setPortGroup(param.getPortGroup());
            int numberOfVolumes = 1;
            if ((param.getCount() != null) && (param.getCount() > 1)) {
                numberOfVolumes = param.getCount();
            }
            create.setCount(numberOfVolumes);
            create.setConsistencyGroup(param.getConsistencyGroup());

            BlockVirtualPoolRestRep vpool = getClient().blockVpools().get(param.getVirtualPool());
            if ((vpool != null) && (vpool.getProtection() != null) &&
                    (vpool.getProtection().getRemoteReplicationParam() != null)) {

                List<NamedRelatedResourceRep> rrSets = getClient().remoteReplicationSets().
                        listRemoteReplicationSets(param.getVirtualArray(),param.getVirtualPool()).
                        getRemoteReplicationSets();

                if (rrSets.isEmpty()) {
                    throw new IllegalStateException("Unable to find a RemoteReplicationSet " +
                            "' for VirtualArray (" + param.getVirtualArray() + ") and VirtualPool (" +
                            param.getVirtualPool() + ").");
                }

                if (rrSets.size() > 1) {
                    throw new IllegalStateException("More than one RemoteReplicationSet found " +
                           "' for VirtualArray (" + param.getVirtualArray() + ") and VirtualPool (" +
                            param.getVirtualPool() + ").  " + rrSets.size() +
                            " RemoteReplicationSets were found: " + rrSets);
                }

                RemoteReplicationParameters replicationParam = new RemoteReplicationParameters();
                replicationParam.setRemoteReplicationSet(rrSets.get(0).getId());
                replicationParam.setRemoteReplicationMode(param.getRemoteReplicationMode());
                replicationParam.setRemoteReplicationGroup(param.getRemoteReplicationGroup());
                create.setRemoteReplicationParameters(replicationParam);
            }

            try {
                if (tasks == null) {
                    tasks = getClient().blockVolumes().create(create);
                } else {
                    tasks.getTasks().addAll(getClient().blockVolumes().create(create).getTasks());
                }
            } catch (ServiceErrorException ex) {
                errorMessages.add(ex.getDetailedMessage());
                logError(getMessage("CreateMultipleBlockVolumes.getTask.error", create.getName(), ex.getDetailedMessage()));
            }
        }
        
        if (tasks == null) {
            throw stateException("CreateMultipleBlockVolumes.illegalState.invalid", Joiner.on('\n').join(errorMessages));
        }
        return tasks;
    }

    private String getDetails(List<? extends CreateBlockVolumeHelper> helpers) {
        String result = "";
        for (CreateBlockVolumeHelper helper : helpers) {
            result += String.format("[Name: %s, Size: %s, Count: %s] ", helper.getName(), helper.getSizeInGb(), helper.getCount());
        }
        result = result.trim();
        return result;
    }
}