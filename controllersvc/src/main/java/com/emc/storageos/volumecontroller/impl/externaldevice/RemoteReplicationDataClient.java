package com.emc.storageos.volumecontroller.impl.externaldevice;


import java.util.List;

import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.storagedriver.model.StorageSystem;

public interface RemoteReplicationDataClient {

    /**
     * Implementation of this method processes remote replication sets for a given storage system. Client should call this method with a list of all
     * remote replication sets where the storage system is either in source or target role.
     *
     * Implementation will check existing remote replication sets in the database where the storage system is in source or
     * target role against the list of sets supplied in this call.
     * Implementation creates new remote replication sets, updates existing remote replication sets from
     * the {@code replicationSets} list and sets
     * existing replication sets which are not part of the {@code replicationSets} list to not reachable.
     * Implementation uses {@code nativeId} and {@code storageSystemType} of the {@code RemoteReplicationSet} instances as instance identity.
     *
     * @param storageSystem storage system, type: Input.
     * @param replicationSets all known replication sets for the storage system, type: Input.
     */
    public void processRemoteReplicationSetsForStorageSystem(StorageSystem storageSystem,
                                                             List<com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet> replicationSets);

    /**
     * Implementation of this method processes remote replication for a given storage system. Client should call this method with a list of all
     * remote replication groups where the storage system is either in source or target role.
     *
     * Implementation will check existing remote replication groups in the database with the storage system against list of groups
     * supplied in this call. Implementation will create new remote replication groups, update existing remote replication groups from
     * the {@code replicationGroups} list and will set
     * existing replication groups which are not part of the {@code replicationGroups} list to not reachable.
     * Implementation uses {@code nativeId} and {@code storageSystemType} of the {@code RemoteReplicationSet} instances as instance identity.
     *
     * @param storageSystem storage system, type: Input.
     * @param replicationGroups all known replication groups for the storage system, type: Input.
     */
    public void processRemoteReplicationGroupsForStorageSystem(StorageSystem storageSystem,
                                                               List<com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup> replicationGroups);

    /**
     * Implementation of this method creates new remote replication pair in database.
     * Implementation uses {@code nativeId} and {@code storageSystemType} of the {@code RemoteReplicationPair} instance as instance identity.
     *
     * @param replicationPair, remote replication pairs, type: Input.
     *
     * @throws DatabaseException {@inheritDoc}
     */
    public void createRemoteReplicationPair(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair replicationPair) throws DatabaseException;

    /**
     * Implementation of this method updates remote replication pair in database.
     * Implementation uses {@code nativeId} and {@code storageSystemType} of the {@code RemoteReplicationPair} instance as instance identity.
     *
     * @param replicationPair
     *
     * @throws DatabaseException {@inheritDoc}
     */
    public void updateRemoteReplicationPair(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair replicationPair)  throws DatabaseException;

    /**
     * Implementation of this method deletes remote replication pair in database.
     * Implementation uses {@code nativeId} and {@code storageSystemType} of the {@code RemoteReplicationPair} instance as instance identity.
     *
     * @param replicationPair
     *
     * @throws DatabaseException {@inheritDoc}
     */
    public void deleteRemoteReplicationPair(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair replicationPair) throws DatabaseException;

}
