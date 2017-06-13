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
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.impl.utils.SRDFOperationContext;
import com.emc.storageos.workflow.WorkflowService;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.emc.storageos.volumecontroller.impl.utils.SRDFOperationContext.SRDFOperationType.CHANGE_VPOOL_ON_SOURCE;
import static java.lang.String.format;

public class SRDFMirrorRollbackCompleter extends SRDFTaskCompleter {

    private static final Logger log = LoggerFactory.getLogger(SRDFMirrorRollbackCompleter.class);
    private static final String UNKNOWN_STATUS_MSG = "May require manually suspending and removal from RDF group";

    private Set<String> rollbackSuccesses;
    private Map<String, ServiceCoded> rollbackFailures;

    public SRDFMirrorRollbackCompleter(List<URI> sourceURIs, String opId) {
        super(sourceURIs, opId);
        this.rollbackSuccesses = new HashSet<>();
        this.rollbackFailures = new HashMap<>();
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        SRDFOperationContext ctx = (SRDFOperationContext) WorkflowService.getInstance().loadStepData(this.getOpId());

        List<Function<Volume, Volume>> undoActions = new ArrayList<>();

        if (ctx != null) {
            for (SRDFOperationContext.SRDFOperationContextEntry entry : ctx.getEntries()) {
                if (CHANGE_VPOOL_ON_SOURCE.toString().equals(entry.getOperation())) {
                    undoActions.add(undoVPoolChange(entry));
                }
            }
        }

        List<Volume> sourceVolumeList = dbClient.queryObject(Volume.class, getIds());
        if (null != sourceVolumeList && !sourceVolumeList.isEmpty()) {
            List<Volume> volumesToUpdate = new ArrayList<Volume>();
            for (Volume volume : sourceVolumeList) {
                if (null != volume.getSrdfTargets() && !volume.getSrdfTargets().isEmpty()) {
                    List<URI> targetVolumeURIs = new ArrayList<>(Collections2.transform(volume.getSrdfTargets(),
                            CommonTransformerFunctions.FCTN_STRING_TO_URI));
                    List<Volume> targetVolumes = dbClient.queryObject(Volume.class, targetVolumeURIs);
                    for (Volume targetVolume : targetVolumes) {
                        targetVolume.setPersonality(NullColumnValueGetter.getNullStr());
                        volumesToUpdate.add(targetVolume);

                        logRollbackStatus(volume, targetVolume);
                    }
                }
                volume.setLinkStatus(Volume.LinkStatus.OTHER.toString());
                volume.setPersonality(NullColumnValueGetter.getNullStr());

                for (Function<Volume, Volume> action : undoActions) {
                    action.apply(volume);
                }

                volumesToUpdate.add(volume);
            }
            dbClient.updateObject(volumesToUpdate);

            if (coded != null && coded instanceof ServiceError) {
                ServiceError error = (ServiceError) coded;
                String originalMessage = error.getMessage();
                String updatedMessage = String.format("%s\n%s", originalMessage,
                        "Rollback of SRDF volumes may require manual cleanup, involving RDF group cleanup." +
                                "  Check logs for more detail.");
                error.setMessage(updatedMessage);
            }
        }

        updateWorkflowStatus(status, coded);
    }

    /**
     * Returns a function for undo'ing the VirtualPool change operation.
     *
     * @param entry Entry from the SRDFOperationContext
     * @return  Function
     */
    private Function<Volume, Volume> undoVPoolChange(SRDFOperationContext.SRDFOperationContextEntry entry) {
        final URI from = (URI) entry.getArgs().get(0);
        final URI to = (URI) entry.getArgs().get(1);

        return new Function<Volume, Volume>() {

            @Override
            public Volume apply(Volume volume) {
                log.info(format("Reverting VirtualPool change for %s from %s to %s", volume.getId(), from, to));
                volume.setVirtualPool(from);
                return volume;
            }
        };
    }

    /**
     * Determines if rollback likely encountered failure, which could mean an explicit failure occurred
     * or rollback somehow was unable to process anything at all.
     *
     * @return true, if rollback failed in anyway.
     */
    public boolean hasRollbackFailures() {
        return !rollbackFailures.isEmpty() || rollbackSuccesses.isEmpty();
    }

    public void addRollbackStatus(Volume source, Volume target, Operation.Status status, ServiceCoded coded) {
        String key = rollbackStatusKey(source, target);
        switch (status) {
            case error:
                rollbackFailures.put(key, coded);
                break;
            case ready:
                rollbackSuccesses.add(key);
                break;
            default:
                //ignore
        }
    }

    private String rollbackStatusKey(Volume source, Volume target) {
        return String.format("%s:%s", source.getNativeGuid(), target.getNativeGuid());
    }

    /**
     * Write a log message to help the user understand the potential state of a given
     * source & target volume pair.
     *
     * @param source    Source volume
     * @param target    Target volume
     */
    private void logRollbackStatus(Volume source, Volume target) {
        StringBuilder msg = new StringBuilder();
        msg.append(String.format("Rollback of Source:%s, Target:%s for Task:%s ",
                source.getNativeGuid(), target.getNativeGuid(), getOpId()));
        String key = rollbackStatusKey(source, target);

        if (rollbackFailures.containsKey(key)) {
            ServiceCoded coded = rollbackFailures.get(rollbackStatusKey(source, target));
            msg.append(String.format("failed: %s\n%s.", coded, UNKNOWN_STATUS_MSG));
        } else if (rollbackSuccesses.contains(key)) {
            msg.append("complete.");
        } else {
            msg.append(String.format("did not occur.  %s", UNKNOWN_STATUS_MSG));
        }

        log.info(msg.toString());
    }
}
