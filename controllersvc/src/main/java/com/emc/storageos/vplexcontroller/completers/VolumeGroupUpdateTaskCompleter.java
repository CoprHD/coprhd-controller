/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller.completers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ApplicationTaskCompleter;

public class VolumeGroupUpdateTaskCompleter extends ApplicationTaskCompleter {

    private static final long serialVersionUID = 8574883265512570806L;
    private static final Logger log = LoggerFactory.getLogger(VolumeGroupUpdateTaskCompleter.class);
    
    public VolumeGroupUpdateTaskCompleter(URI volumeGroupId, Collection<URI> addVolumes, Collection<URI>removeVols, 
                                          Collection<URI>cgs, String opId) {
        super(volumeGroupId, addVolumes, removeVols, cgs, opId);
    }
    
    /**
     * Remove the volume group to the volume volumeGroupIds attribute and add back consistency group Id to 
     * its backend volumes
     * @param voluri The volumes that will be updated
     * @param dbClient
     */
    @Override
    protected void removeApplicationFromVolume(URI voluri, DbClient dbClient) {
        Volume volume = dbClient.queryObject(Volume.class, voluri);
        log.info(String.format("removing the volume %s from application", volume.getLabel()));
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
                log.info(String.format("update the volume %s cg", backendVol.getLabel()));
                if (backendVol != null) {
                    backendVol.setConsistencyGroup(cgURI);
                    dbClient.updateObject(backendVol);
                }
            }
        }
        // handle clones
        StringSet fullCopies = volume.getFullCopies();
        List<Volume> fullCopiesToUpdate = new ArrayList<Volume>();
        if (fullCopies != null && !fullCopies.isEmpty()) {
            for (String fullCopyId : fullCopies) {
                Volume fullCopy = dbClient.queryObject(Volume.class, URI.create(fullCopyId));
                if (fullCopy != null && !fullCopy.getInactive()) {
                    fullCopy.setFullCopySetName(NullColumnValueGetter.getNullStr());
                    fullCopiesToUpdate.add(fullCopy);
                }
            }
        }

        if (!fullCopiesToUpdate.isEmpty()) {
            dbClient.updateObject(fullCopiesToUpdate);
        }
    }
}
