package com.emc.storageos.remoterreplicationcontroller;


import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class RemoteReplicationUtils {

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
}
