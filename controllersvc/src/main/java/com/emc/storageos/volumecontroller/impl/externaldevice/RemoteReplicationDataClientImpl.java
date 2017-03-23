package com.emc.storageos.volumecontroller.impl.externaldevice;


import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet;

import java.util.List;

public class RemoteReplicationDataClientImpl implements RemoteReplicationDataClient {
    @Override
    public void processRemoteReplicationSetsForStorageSystem(StorageSystem storageSystem, List<RemoteReplicationSet> replicationSets) {

    }

    @Override
    public void processRemoteReplicationGroupsForStorageSystem(StorageSystem storageSystem, List<RemoteReplicationGroup> replicationGroups) {

    }

    @Override
    public void createRemoteReplicationPair(RemoteReplicationPair replicationPair) throws DatabaseException {

    }

    @Override
    public void updateRemoteReplicationPair(RemoteReplicationPair replicationPair) throws DatabaseException {

    }

    @Override
    public void deleteRemoteReplicationPair(RemoteReplicationPair replicationPair) throws DatabaseException {

    }
}
