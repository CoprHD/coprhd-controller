package com.emc.storageos.util;

import static com.emc.storageos.db.client.constraint.AlternateIdConstraint.Factory.getVolumesByAssociatedId;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;

public class VPlexSrdfUtil {
    
    /**
     * Returns the srdf underlying volume if there is one, otherwise null.
     * Assumes both legs of vplex cannot be srdf protected.
     * @param dbClient -- dbClient handle
     * @param vplexVolume -- Vplex volume object.
     * @return -- Srdf volume if present, or null
     */
    static public Volume getSrdfVolumeFromVplexVolume(DbClient dbClient, Volume vplexVolume) {
        for (String assocVolumeId : vplexVolume.getAssociatedVolumes()) {
            Volume assocVolume = dbClient.queryObject(Volume.class, URI.create(assocVolumeId));
            if (assocVolume == null || assocVolume.getInactive()) {
                continue;
            }
            if (assocVolume.checkForSRDF()) {
                return assocVolume;
            }
        }
        return null;
    }
    
    /**
     * Returns the Vplex volume that is covering an SRDF volume.
     * @param dbClient - dbclient handle
     * @param srdfVolume - SRDF Volume object
     * @return - Vplex volume fronting SRDF volume, or null
     */
    static public Volume getVplexVolumeFromSrdfVolume(DbClient dbClient, Volume srdfVolume) {
        List<Volume> vplexVolumes = CustomQueryUtility
                .queryActiveResourcesByConstraint(dbClient, Volume.class, 
                        getVolumesByAssociatedId(srdfVolume.getId().toString()));
        for (Volume vplexVolume : vplexVolumes) {
            // There should only be one volume in list, ie. target only associated with
            // one Vplex volume
            return vplexVolume;
        }
        return null;
    }
    
    /**
     * Given an SRDF or VPLEX volume, will always return the SRDF volume.
     * If vplex it's the corresponding srdf volume.
     * @param dbClient
     * @param id - URI of volume
     * @return - URI of SRDF volume
     */
    static public URI getSrdfIdFromVolumeId(DbClient dbClient, URI id) {
        if (id == null) {
            return null;
        }
        Volume volume = dbClient.queryObject(Volume.class, id);
        if (volume == null || !volume.isVPlexVolume(dbClient)) {
            return id;
        }
        Volume srdfVolume = getSrdfVolumeFromVplexVolume(dbClient, volume);
        return (srdfVolume == null ? id : srdfVolume.getId());
    }
    
    /**
     * Returns the vplex front volume ids for the srdf targets if they have a vplex volume,
     * otherwise returns the original srdf target id
     * @param dbClient - DbClient handle
     * @param srdfVolume - An Srdf volume having targets
     * @return String set of volume ids for either vplex/srdf volume or srdf volume in target list
     */
    static public StringSet getSrdfOrVplexTargets(DbClient dbClient, Volume srdfVolume) {
        StringSet targets = new StringSet();
        for (String target : srdfVolume.getSrdfTargets()) {
            Volume srdfTarget = dbClient.queryObject(Volume.class, URI.create(target));
            Volume vplexTarget = getVplexVolumeFromSrdfVolume(dbClient, srdfTarget);
            if (vplexTarget != null) {
                targets.add(vplexTarget.getId().toString());
            } else {
                targets.add(srdfTarget.getId().toString());
            }
        }
        return targets;
    }

}
