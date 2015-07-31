/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.workflow.WorkflowStepCompleter;

import com.google.common.collect.Collections2;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class SRDFMirrorRollbackCompleter extends SRDFTaskCompleter {

    public SRDFMirrorRollbackCompleter(List<URI> sourceURIs, String opId) {
        super(sourceURIs, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        List<Volume> sourceVolumeList = dbClient.queryObject(Volume.class, getIds());
        if (null != sourceVolumeList && !sourceVolumeList.isEmpty()) {
            List<Volume> volumesToUpdate = new ArrayList<Volume>();
            for (Volume volume : sourceVolumeList) {
                if (null != volume.getSrdfTargets() && !volume.getSrdfTargets().isEmpty()) {
                    List<URI> targetVolumeURIs = new ArrayList<URI>(Collections2.transform(volume.getSrdfTargets(),
                            CommonTransformerFunctions.FCTN_STRING_TO_URI));
                    List<Volume> targetVolumes = dbClient.queryObject(Volume.class, targetVolumeURIs);
                    for (Volume targetVolume : targetVolumes) {
                        targetVolume.setPersonality(NullColumnValueGetter.getNullStr());
                        volumesToUpdate.add(targetVolume);
                    }
                }
                volume.setPersonality(NullColumnValueGetter.getNullStr());
                volumesToUpdate.add(volume);
            }
            dbClient.updateAndReindexObject(volumesToUpdate);
        }
        WorkflowStepCompleter.stepSucceded(getOpId());
    }
}
