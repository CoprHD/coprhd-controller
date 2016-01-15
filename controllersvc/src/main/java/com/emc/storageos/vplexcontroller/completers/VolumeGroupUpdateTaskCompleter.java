/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller.completers;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class VolumeGroupUpdateTaskCompleter extends TaskCompleter {

    private static final long serialVersionUID = 8574883265512570806L;
    private static final Logger log = LoggerFactory.getLogger(VolumeGroupUpdateTaskCompleter.class);
    private List<URI> addVolumes;
    private List<URI> removeVolumes;
    
    public VolumeGroupUpdateTaskCompleter(URI volumeGroupId, List<URI> addVolumes, List<URI>removeVols, String opId) {
        super(VolumeGroup.class, volumeGroupId, opId);
        this.addVolumes = addVolumes;
        this.removeVolumes = removeVols;
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        log.info("START VolumeGroupUpdateTaskCompleter complete.");
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
                        addVolumeGroupToVolume(voluri, dbClient);
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
     * Add the application to the volume applicationIds attribute
     * @param voluri The volumes that will be updated
     * @param dbClient
     */
    private void addVolumeGroupToVolume(URI voluri, DbClient dbClient) {
        Volume volume = dbClient.queryObject(Volume.class, voluri);
        StringSet volumeGroups = volume.getVolumeGroupIds();
        if (volumeGroups == null) {
            volumeGroups = new StringSet();
        }
        volumeGroups.add(getId().toString());
        volume.setVolumeGroupIds(volumeGroups);
        dbClient.updateObject(volume);
        // Once one of volume in a VPLEX CG is added to an application, the CG's arrayConsistency
        // should turn to false
        URI cguri = volume.getConsistencyGroup();
        if (NullColumnValueGetter.isNullURI(cguri)) {
        	return;
        }
        BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cguri);
        if (cg.getArrayConsistency()) {
        	log.info("Updated consistency group arrayConsistency");
        	cg.setArrayConsistency(false);
        	dbClient.updateObject(cg);
        }
    }

}
