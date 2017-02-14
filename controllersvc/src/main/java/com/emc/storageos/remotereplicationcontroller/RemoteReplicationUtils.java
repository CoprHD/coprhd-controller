package com.emc.storageos.remotereplicationcontroller;


import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesByRelation;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.db.client.util.CustomQueryUtility;

public class RemoteReplicationUtils {
    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationUtils.class);
    public static List<URI> getElements(DbClient dbClient, List<URI> remoteReplicationPairs) {

        List<RemoteReplicationPair> systemReplicationPairs = dbClient.queryObject(RemoteReplicationPair.class, remoteReplicationPairs);
        List<URI> volumes = new ArrayList<>();
        for(RemoteReplicationPair pair : systemReplicationPairs) {
            URI sourceVolume = pair.getSourceElement().getURI();
            URI targetVolume = pair.getTargetElement().getURI();
            volumes.add(sourceVolume);
            volumes.add(targetVolume);
        }
        return volumes;
    }

    public static VirtualPool getTargetVPool(VirtualPool remoteReplicationVPool, DbClient dbClient) {
        StringMap remoteReplicationSettings = remoteReplicationVPool.getRemoteReplicationProtectionSettings();
        VirtualPool targetVirtualPool = remoteReplicationVPool;

        // There can be multiple entries for target varray and vpool combinations in the vpool
        for (Map.Entry<String, String> entry : remoteReplicationSettings.entrySet()) {
            String targetVirtualPoolId = entry.getValue();
            if (targetVirtualPoolId != null) {
                targetVirtualPool = dbClient.queryObject(VirtualPool.class, URIUtil.uri(targetVirtualPoolId));
            }
            break;
        }
        return targetVirtualPool;
    }

    public static VirtualArray getTargetVArray(VirtualPool remoteReplicationVPool, DbClient dbClient) {
        StringMap remoteReplicationSettings = remoteReplicationVPool.getRemoteReplicationProtectionSettings();
        VirtualArray targetVirtualArray = null;

        // There can be multiple entries for target varray and vpool combinations in the vpool
        for (Map.Entry<String, String> entry : remoteReplicationSettings.entrySet()) {
            String targetVirtualArrayId = entry.getKey();
            targetVirtualArray = dbClient.queryObject(VirtualArray.class, URIUtil.uri(targetVirtualArrayId));
            break;
        }
        return targetVirtualArray;
    }

    public static List<RemoteReplicationPair> getRemoteReplicationPairsForSourceElement(URI sourceElementURI, DbClient dbClient) {
        _log.info("Called: getRemoteReplicationPairsForSourceElement() for storage element {}", sourceElementURI);

        List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair> rrPairs =
                queryActiveResourcesByRelation(dbClient, sourceElementURI, com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair.class,
                        "sourceElement");

        _log.info("Found pairs: {}", rrPairs);

        return rrPairs;
    }

    public static List<RemoteReplicationPair> getRemoteReplicationPairsForCG(BlockConsistencyGroup cg, DbClient dbClient) {
        _log.info("Called: getRemoteReplicationPairsForCG() for consistency group {}", cg.getId());
        List<RemoteReplicationPair> rrPairs = new ArrayList<>();
        List<Volume> cgVolumes = CustomQueryUtility.queryActiveResourcesByRelation(dbClient, cg.getId(),
                Volume.class, "consistencyGroup");

        // get all remote replication pairs where these volumes are source volumes
        for (Volume volume : cgVolumes) {
           rrPairs.addAll(getRemoteReplicationPairsForSourceElement(volume.getId(), dbClient));
        }
        return rrPairs;
    }
}
