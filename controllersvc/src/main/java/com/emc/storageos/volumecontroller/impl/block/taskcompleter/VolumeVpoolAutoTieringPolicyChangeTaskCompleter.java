/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.util.VPlexUtil;

public class VolumeVpoolAutoTieringPolicyChangeTaskCompleter extends
        VolumeVpoolChangeTaskCompleter {

    private static final Logger _logger = LoggerFactory
            .getLogger(VolumeVpoolAutoTieringPolicyChangeTaskCompleter.class);

    /**
     * If volumes are from different storage system and if the target vPool has
     * unique AutoTieringPolicy flag enabled, each volume will have policy URI
     * associated with its own system.
     */
    private Map<URI, URI> oldVolToPolicyMap;

    public VolumeVpoolAutoTieringPolicyChangeTaskCompleter(URI volumeURI,
            URI oldVpool, Map<URI, URI> oldVolToPolicyMap, String task) {
        super(volumeURI, oldVpool, task);
        this.oldVolToPolicyMap = oldVolToPolicyMap;
    }

    public VolumeVpoolAutoTieringPolicyChangeTaskCompleter(List<URI> volumeURIs,
            URI oldVpool, Map<URI, URI> oldVolToPolicyMap, String task) {
        super(volumeURIs, oldVpool, task);
        this.oldVolToPolicyMap = oldVolToPolicyMap;
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status,
            ServiceCoded serviceCoded) {
        switch (status) {
            case error:
                _log.error(
                        "An error occurred during virtual pool change "
                                + "- restore the old auto tiering policy to the volume(s): {}",
                        serviceCoded.getMessage());
                List<Volume> volumesToUpdate = new ArrayList<Volume>();
                for (URI id : getIds()) {
                    Volume volume = dbClient.queryObject(Volume.class, id);
                    _log.info("rolling back auto tiering policy on volume {}({})",
                            id, volume.getLabel());
                    URI policyURI = oldVolToPolicyMap.get(id);
                    if (policyURI == null) {
                        policyURI = NullColumnValueGetter.getNullURI();
                    }
                    volume.setAutoTieringPolicyUri(policyURI);
                    _log.info("set volume's auto tiering policy back to {}",
                            policyURI);
                    volumesToUpdate.add(volume);

                    rollBackPolicyOnVplexBackendVolume(volume, volumesToUpdate, dbClient);
                }
                dbClient.updateObject(volumesToUpdate);
                break;
            case ready:
                // The new auto tiering policy has already been stored in the volume
                // in BlockDeviceExportController.

                // record event.
                OperationTypeEnum opType = OperationTypeEnum.CHANGE_VOLUME_AUTO_TIERING_POLICY;
                try {
                    boolean opStatus = (Operation.Status.ready == status) ? true : false;
                    String evType = opType.getEvType(opStatus);
                    String evDesc = opType.getDescription();
                    for (URI id : getIds()) {
                        recordBourneVolumeEvent(dbClient, id, evType, status, evDesc);
                    }
                } catch (Exception ex) {
                    _logger.error(
                            "Failed to record block volume operation {}, err: {}",
                            opType.toString(), ex);
                }
                break;
            default:
                break;
        }
        super.complete(dbClient, status, serviceCoded);
    }

    /**
     * Roll back policy on vplex backend volumes.
     */
    private void rollBackPolicyOnVplexBackendVolume(Volume volume, List<Volume> volumesToUpdate, DbClient dbClient) {
        // Check if it is a VPlex volume, and get backend volumes
        Volume backendSrc = VPlexUtil.getVPLEXBackendVolume(volume, true, dbClient, false);
        if (backendSrc != null) {
            _log.info("rolling back auto tiering policy on VPLEX backend source volume {}({})",
                    backendSrc.getId(), backendSrc.getLabel());
            URI policyURI = oldVolToPolicyMap.get(backendSrc.getId());
            if (policyURI == null) {
                policyURI = NullColumnValueGetter.getNullURI();
            }
            backendSrc.setAutoTieringPolicyUri(policyURI);
            _log.info("set volume's auto tiering policy back to {}",
                    policyURI);
            volumesToUpdate.add(backendSrc);

            // VPlex volume, check if it is distributed
            Volume backendHa = VPlexUtil.getVPLEXBackendVolume(volume, false, dbClient, false);
            if (backendHa != null) {
                _log.info("rolling back auto tiering policy on VPLEX backend distributed volume {}({})",
                        backendHa.getId(), backendHa.getLabel());
                policyURI = oldVolToPolicyMap.get(backendHa.getId());
                if (policyURI == null) {
                    policyURI = NullColumnValueGetter.getNullURI();
                }
                backendHa.setAutoTieringPolicyUri(policyURI);
                _log.info("set volume's auto tiering policy back to {}",
                        policyURI);
                volumesToUpdate.add(backendHa);
            }
        }
    }
}
