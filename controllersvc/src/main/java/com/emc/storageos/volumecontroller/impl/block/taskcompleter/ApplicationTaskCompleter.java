/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * Task completer for update application volumes
 *
 */
public class ApplicationTaskCompleter extends TaskCompleter{

    private static final long serialVersionUID = -9188670003331949130L;
    private static final Logger log = LoggerFactory.getLogger(ApplicationTaskCompleter.class);
    private List<URI> addVolumes;
    private List<URI> removeVolumes;
    private Collection<URI> consistencyGroups;
    
    public ApplicationTaskCompleter(URI volumeGroupId, List<URI> addVolumes, List<URI>removeVols, Collection<URI> consistencyGroups, String opId) {
        super(VolumeGroup.class, volumeGroupId, opId);
        this.addVolumes = addVolumes;
        this.removeVolumes = removeVols;
        this.consistencyGroups = consistencyGroups;
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        log.info("START ApplicationCompleter complete");
        super.setStatus(dbClient, status, coded);
        updateWorkflowStatus(status, coded);
        if (addVolumes != null) {
            for (URI voluri : addVolumes) {
                switch (status) {
                    case error:
                        setErrorOnDataObject(dbClient, Volume.class, voluri, coded);
                        break;
                    default:
                        setReadyOnDataObject(dbClient, Volume.class, voluri);
                        addApplicationToVolume(voluri, dbClient);
                }
            }
        }

        if (removeVolumes != null) {
            for (URI voluri : removeVolumes) {
               switch (status) {
                    case error:
                        setErrorOnDataObject(dbClient, Volume.class, voluri, coded);
                        break;
                    default:
                        setReadyOnDataObject(dbClient, Volume.class, voluri);
                        removeApplicationFromVolume(voluri, dbClient);
                }
            }
        }

        if (consistencyGroups != null && !consistencyGroups.isEmpty()) {
            for (URI cguri : consistencyGroups) {
                switch (status) {
                    case error:
                        setErrorOnDataObject(dbClient, BlockConsistencyGroup.class, cguri, coded);
                        break;
                    default:
                        updateConsistencyGroup(cguri, dbClient);
                        setReadyOnDataObject(dbClient, BlockConsistencyGroup.class, cguri);
                }
            }
        }
        log.info("END ApplicationCompleter complete");
    }

    /**
     * Remove application from the volume applicationIds attribute
     * @param voluri The volumes that will be updated
     * @param dbClient
     */
    private void removeApplicationFromVolume(URI voluri, DbClient dbClient) {
        Volume volume = dbClient.queryObject(Volume.class, voluri);
        String appId = getId().toString();
        StringSet appIds = volume.getVolumeGroupIds();
        if(appIds != null) {
            appIds.remove(appId);
        }
        dbClient.updateObject(volume);
    }

    /**
     * Add the application to the volume applicationIds attribute
     * @param voluri The volume that will be updated
     * @param dbClient
     */
    private void addApplicationToVolume(URI voluri, DbClient dbClient) {
        Volume volume = dbClient.queryObject(Volume.class, voluri);
        StringSet applications = volume.getVolumeGroupIds();
        if (applications == null) {
            applications = new StringSet();
        }
        applications.add(getId().toString());
        volume.setVolumeGroupIds(applications);
        dbClient.updateObject(volume);

        // handle clones
        StringSet fullCopies = volume.getFullCopies();
        List<Volume> fullCopiesToUpdate = new ArrayList<Volume>();
        if (fullCopies != null && !fullCopies.isEmpty()) {
            for (String fullCopyId : fullCopies) {
                Volume fullCopy = dbClient.queryObject(Volume.class, URI.create(fullCopyId));
                if (fullCopy != null) {
                    fullCopy.setFullCopySetName(fullCopy.getReplicationGroupInstance());
                    fullCopiesToUpdate.add(fullCopy);
                }
            }
        }

        if (!fullCopiesToUpdate.isEmpty()) {
            dbClient.updateObject(fullCopiesToUpdate);
        }
    }

    /**
     * Update arrayConsistency attribute
     * @param cguri The consistency group that will be updated
     * @param dbClient
     */
    private void updateConsistencyGroup(URI cguri, DbClient dbClient) {
        BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cguri);
        if (cg != null && !cg.getInactive()) {
            if (cg.getArrayConsistency()) {
                log.info("Updated consistency group arrayConsistency");
                cg.setArrayConsistency(false);
                dbClient.updateObject(cg);
            }
        }
    }
}
