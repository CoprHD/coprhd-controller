/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller.completers;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ApplicationTaskCompleter;

public class VolumeGroupUpdateTaskCompleter extends ApplicationTaskCompleter {

    private static final long serialVersionUID = 8574883265512570806L;
    private static final Logger log = LoggerFactory.getLogger(VolumeGroupUpdateTaskCompleter.class);
    
    public VolumeGroupUpdateTaskCompleter(URI volumeGroupId, List<URI> addVolumes, List<URI>removeVols, 
                                          Collection<URI>cgs, String opId) {
        super(volumeGroupId, addVolumes, removeVols, cgs, opId);
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        log.info("START VolumeGroupUpdateTaskCompleter complete.");
        super.setStatus(dbClient, status, coded);
        updateWorkflowStatus(status, coded);
        if (addVolumes != null) {
            for (URI voluri : addVolumes) {
                Volume volume = dbClient.queryObject(Volume.class, voluri);
                switch (status) {
                    case error:
                        setErrorOnDataObject(dbClient, Volume.class, voluri, coded);
                        break;
                    default:
                        setReadyOnDataObject(dbClient, Volume.class, voluri);
                        addApplicationToVolume(volume, dbClient);
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
                        removeVolumeGroupFromVolume(voluri, dbClient);
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
                        setReadyOnDataObject(dbClient, BlockConsistencyGroup.class, cguri);
                        updateConsistencyGroup(cguri, dbClient);
                }
            }
        }
        log.info("END VolumeGroupUpdateCompleter complete");
    }
    
    /**
     * Remove the volume group to the volume volumeGroupIds attribute and add back consistency group Id to 
     * its backend volumes
     * @param voluri The volumes that will be updated
     * @param dbClient
     */
    private void removeVolumeGroupFromVolume(URI voluri, DbClient dbClient) {
        Volume volume = dbClient.queryObject(Volume.class, voluri);
        String volumeGroupId = getId().toString();
        StringSet volumeGroupIds = volume.getVolumeGroupIds();
        if(volumeGroupIds != null) {
            volumeGroupIds.remove(volumeGroupId);
        }
        dbClient.updateObject(volume);
        URI cgURI = volume.getConsistencyGroup();
        if (NullColumnValueGetter.isNullURI(cgURI)) {
            return;
        }
        StringSet backends = volume.getAssociatedVolumes();
        if (backends != null) {
            for (String backendId : backends) {
                Volume backendVol = dbClient.queryObject(Volume.class, URI.create(backendId));
                if (backendVol != null) {
                    backendVol.setConsistencyGroup(cgURI);
                    dbClient.updateObject(backendVol);
                }
            }
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
