package com.emc.storageos.api.mapper;

import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupRestRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationSetRestRep;


public class RemoteReplicationMapper {

    public static RemoteReplicationSetRestRep map(RemoteReplicationSet from) {
        if (from == null) {
            return null;
        }
        RemoteReplicationSetRestRep to = new RemoteReplicationSetRestRep();

        to.setId(from.getId());
        to.setDeviceLabel(from.getDeviceLabel());
        to.setNativeId(from.getNativeId());
        to.setReachable(from.getReachable());
        to.setStorageSystemType(from.getStorageSystemType());
        if (from.getSupportedElementTypes() != null) {
            to.setSupportedElementTypes(from.getSupportedElementTypes());
        }
        if (from.getSupportedReplicationModes() != null) {
            to.setSupportedReplicationModes(from.getSupportedReplicationModes());
        }

        return to;
    }

    public static RemoteReplicationGroupRestRep map(RemoteReplicationGroup from) {
        if (from == null) {
            return null;
        }
        RemoteReplicationGroupRestRep to = new RemoteReplicationGroupRestRep();

        to.setId(from.getId());
        to.setDeviceLabel(from.getDeviceLabel());
        to.setNativeId(from.getNativeId());
        to.setReachable(from.getReachable());
        to.setStorageSystemType(from.getStorageSystemType());
        to.setDisplayName(from.getDisplayName());
        to.setReplicationMode(from.getReplicationMode());

        return to;
    }
}
