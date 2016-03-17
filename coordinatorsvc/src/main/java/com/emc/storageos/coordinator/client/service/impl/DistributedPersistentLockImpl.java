/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service.impl;

import java.nio.charset.Charset;
import java.util.List;
import java.util.UUID;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.DistributedPersistentLock;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.EnsurePath;
import org.apache.curator.utils.ZKPaths;

/**
 * ZK backed distributed persistent lock implementation.
 * 
 * - It survives client reboots.
 * - It has to be explicitly released.
 * - It is not re-entrant ... use getLockOwner(), to determine current owner.
 * 
 * Implementation details: A persistent lock comprises a root (parent) ZNode and a child ZNode.
 * 
 * - RootZNode is created with the requested persistent lock name.
 * => Since the node has a fixed name, only one thread wins the acquireLock race, others get NodeExistsException.
 * => This node alone does not suffice to handle releaseLock races.
 * - ChildZNode is created with a randomUUID and is used to store owner information.
 * => Created as a child of RootZNode
 * => A randomUUID is used to be able to provide a given ZNode unique identification, which enables
 * better handling of releaseLock races.
 * => Owner information is used to verify ownership during releaseLock operations.
 * - Attempt is made to make AcquireLock and ReleaseLock atomic.
 */
public class DistributedPersistentLockImpl implements DistributedPersistentLock {
    private static final Logger _log = LoggerFactory.getLogger(DistributedPersistentLockImpl.class);
    private final CuratorFramework _zkClient;
    private final String _persistentLockPath;
    private final String _persistentLockName;

    /**
     * Constructor
     * 
     * @param conn ZK connection
     * @param path ZK path under which persistent locks are created
     * @param name Name of the persistent lock
     */
    public DistributedPersistentLockImpl(final ZkConnection conn, final String path, final String name) {
        _zkClient = conn.curator();
        _persistentLockPath = path;
        _persistentLockName = name;
    }

    @Override
    public synchronized void start() throws Exception {
        EnsurePath path = new EnsurePath(_persistentLockPath);
        path.ensure(_zkClient.getZookeeperClient());
    }

    @Override
    public synchronized void stop() {
    }

    @Override
    public boolean acquireLock(final String clientName) throws Exception {
        boolean bLockAcquired;
        _log.debug("acquireLock(): Client: {} wants to acquire lock: {}", clientName, _persistentLockName);
        if (clientName == null) {
            throw CoordinatorException.fatals.clientNameCannotBeNull();
        }
        _log.debug("acquireLock(): Creating ZNodes...");
        bLockAcquired = createZNodes(clientName);
        _log.debug("acquireLock(): Completed: {}", bLockAcquired);
        return bLockAcquired;
    }

    @Override
    public boolean releaseLock(final String clientName) throws Exception {
        boolean bLockReleased;
        _log.debug("releaseLock(): Client: {} wants to releaseLock lock: {}", clientName, _persistentLockName);
        if (clientName == null) {
            throw CoordinatorException.fatals.clientNameCannotBeNull();
        }
        _log.debug("releaseLock(): Deleting ZNodes...");
        bLockReleased = deleteZNodes(clientName);
        _log.debug("releaseLock(): Completed: {}", bLockReleased);
        return bLockReleased;
    }

    @Override
    public String getLockOwner() throws Exception {
        String currOwnerName = null;
        try {
            _log.debug("getLockOwner(): For lock: {}", _persistentLockName);
            String lockRootPath = ZKPaths.makePath(_persistentLockPath, _persistentLockName);
            List<String> children = _zkClient.getChildren().forPath(lockRootPath);
            String versionId = children.get(0);
            String lockPath = ZKPaths.makePath(lockRootPath, versionId);
            byte[] currOwnerNameInBytes = _zkClient.getData().forPath(lockPath);
            currOwnerName = new String(currOwnerNameInBytes, Charset.forName("UTF-8"));
        } catch (KeeperException.NoNodeException e) {
            _log.debug("getLockOwner(): lock {} doesn't exist", _persistentLockName);
        } catch (Exception e) {
            _log.debug("getLockOwner(): Problem getting ZNodes for Lock {} ... could not determine owner",
                    _persistentLockName, e);
        }
        return currOwnerName;
    }

    /**
     * Creates the ZNodes
     * 
     * @param clientName
     * @return true, if success; false otherwise
     */
    private boolean createZNodes(final String clientName) {
        boolean bZNodesCreated = false;
        try {
            byte[] clientNameInBytes = clientName.getBytes(Charset.forName("UTF-8"));
            String versionId = UUID.randomUUID().toString();
            String lockRootPath = ZKPaths.makePath(_persistentLockPath, _persistentLockName);
            String lockPath = ZKPaths.makePath(lockRootPath, versionId);
            _zkClient.inTransaction().create().withMode(CreateMode.PERSISTENT).forPath(lockRootPath).and()
                    .create().withMode(CreateMode.PERSISTENT).forPath(lockPath, clientNameInBytes).and()
                    .commit();
            bZNodesCreated = true;
        } catch (KeeperException.NodeExistsException nee) {
            _log.debug("createZNodes(): For lock: {}, ZNodes already exist", _persistentLockName, nee);
        } catch (Exception e) {
            _log.debug("createZNodes(): Problem while creating ZNodes: {}", _persistentLockName, e);
        }
        _log.debug("createZNodes(): Result: {}", bZNodesCreated);
        return bZNodesCreated;
    }

    /**
     * Deletes the ZNodes
     * 
     * @param clientName
     * @return true, if success; false otherwise
     */
    private boolean deleteZNodes(final String clientName) {
        boolean bZNodesDeleted = false;
        try {
            String lockRootPath = ZKPaths.makePath(_persistentLockPath, _persistentLockName);
            List<String> children = _zkClient.getChildren().forPath(lockRootPath);
            String versionId = children.get(0);
            _log.debug("deleteZNodes(): For lock: {}, Found ChildZNode.", _persistentLockName);
            String lockPath = ZKPaths.makePath(lockRootPath, versionId);
            byte[] currOwnerNameInBytes = _zkClient.getData().forPath(lockPath);
            String currOwnerName = new String(currOwnerNameInBytes, Charset.forName("UTF-8"));
            if (currOwnerName.equals(clientName)) {
                _log.debug("deleteZNodes(): For lock: {}, Verified owner. Deleting ZNodes", _persistentLockName);
                _zkClient.inTransaction().delete().forPath(lockPath).and()
                        .delete().forPath(lockRootPath).and().commit();
                bZNodesDeleted = true;
            } else {
                _log.debug("deleteZNodes(): For lock: {}, Cannot delete ZNodes ... Invalid owner.",
                        _persistentLockName);
            }
        } catch (KeeperException.NoNodeException nne) {
            _log.debug("deleteZNodes(): For lock: {}, ZNodes not found.", _persistentLockName);
            bZNodesDeleted = true;
        } catch (Exception e) {
            _log.debug("deleteZNodes(): For lock: {}, Problem while deleting ZNodes", _persistentLockName);
        }
        _log.debug("deleteZNodes(): Result: {}", bZNodesDeleted);
        return bZNodesDeleted;
    }
}
