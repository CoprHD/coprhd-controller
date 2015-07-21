/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;                                                    
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.EnsurePath;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.db.client.GlobalLockItf;
import com.emc.storageos.db.client.model.GlobalLock;
import com.emc.storageos.db.client.recipe.CustomizedDistributedRowLock;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnMap;
import com.netflix.astyanax.model.ConsistencyLevel;
import com.netflix.astyanax.recipes.locks.BusyLockException;
import com.netflix.astyanax.recipes.locks.StaleLockException;
import com.netflix.astyanax.retry.BoundedExponentialBackoff;

/**
 * Cassandra and ZK backed distributed global lock implementation.
 *
 * - We have three modes for the global lock.
 *   a. Node/Service Shared Mode
 *      Set node or service name as owner for the lock. (e.g. vipr1 or vipr1:dbsvc1 etc.)
 *      1. Only one thread in the node/svc own the lock at one time.
 *   	2. It could be released manually by release() or expired after timeout.
 *   b. VdcShared Shared Mode
 *      Set vdc name as owner for the lock. (e.g. vdc1 etc.)
 *      1. The nodes/svcs within the vdc could share the lock.  
 *      2. After one node acquires the global lock, all the nodes within the VDC own the Lock.  All of 
 *         them could acquire the global lock, enter critical section and release the global lock. 
 *   	3. It could be released manually or expired after timeout.
 *      4. We use local VDC ZK ephemeral nodes as reference count to release the lock in right way.
 *      5. When one node down, other nodes within the VDC could take over the task via leader selector if necessary.
 *   c. Exclusive Mode (Not implemented yet.)
 */
public class GlobalLockImpl implements GlobalLockItf {
    private static final Logger _log = LoggerFactory.getLogger(GlobalLockImpl.class);
    
    private final String _name;     
    private final GlobalLock.GL_Mode _mode;
    private final long _timeout;
    private String _vdc = null;



    private final DbClientImpl _dbClient;	        // db client 
    private final Keyspace _keyspace;			    // geo keyspace
    private final ColumnFamily<String, String> _cf;	// global lock CF

    // internal distributed row lock
    private CustomizedDistributedRowLock<String> _cpDistRowlock = null;
    // timeout (in seconds) for the internal distributed row low
    private static final long CustomizedDistributedRowLock_Timeout = 60;

    private CuratorFramework _zkClient = null;
    private static final String HOLDER = "holder";
    // holder root path format: /globallock/<lock name>/holder
    private String _holderRoot = null;
    private String _localvdcHolderRoot = null;
    private String errMsg;

    /**
     * Constructor
     *
     * @param dbClient db client instance
     * @param name Name of the global lock
     * @param mode Mode of the global lock
     * @param timeout timeout (in milliseconds) of the global lock; 
     *                0 means infinite lock
     * @param vdc name of the vdc where the user is from
     */
    public GlobalLockImpl(final DbClientImpl dbClient, final String name, final GlobalLock.GL_Mode mode, final long timeout, final String vdc) throws Exception {
        if(dbClient == null
           || name == null || name.isEmpty() 
           || vdc == null || vdc.isEmpty()) {
            throw new IllegalStateException("GlobalLockImpl constructor parameters is incorrect.");
        }

        _name = name;
        _mode = mode;
        _timeout = timeout;
        _vdc = vdc;

        _dbClient = dbClient;
        _keyspace = _dbClient.getGeoKeyspace();
        _cf = TypeMap.getGlobalLockType().getCf();
        _cpDistRowlock = new CustomizedDistributedRowLock<String>(_keyspace, _cf, _name)
            .withBackoff(new BoundedExponentialBackoff(250, 10000, 10))
            .withConsistencyLevel(ConsistencyLevel.CL_EACH_QUORUM)
            .expireLockAfter(CustomizedDistributedRowLock_Timeout, TimeUnit.SECONDS);

        if(!_mode.equals(GlobalLock.GL_Mode.GL_NodeSvcShared_MODE)) {
            CoordinatorClientImpl _coordinatorClient = (CoordinatorClientImpl)dbClient.getCoordinatorClient();
            _zkClient = _coordinatorClient.getZkConnection().curator();
            _holderRoot = String.format("%1$s/%2$s/%3$s", ZkPath.GLOBALLOCK.toString(), _name, HOLDER);
            _localvdcHolderRoot = String.format("%1$s/%2$s", _holderRoot, _vdc);
            try {
                EnsurePath path = new EnsurePath(_localvdcHolderRoot);
                path.ensure(_zkClient.getZookeeperClient());
            } catch(Exception e) {
                _log.error("global lock holder root {} could not be created. e={}", _localvdcHolderRoot, e);
                throw e;
            }
        }
    }

    /**
     * Acquire the global lock
     *
     * @param owner the name of the local owner (e.g. node name or svc name etc.)
     */
    @Override
    public boolean acquire(final String owner) throws Exception {
        // 1. assemble global owner 
        String localOwner = owner;
        String globalOwner = _vdc;
        if(_mode.equals(GlobalLock.GL_Mode.GL_NodeSvcShared_MODE)) {
            globalOwner = String.format("%1$s:%2$s", _vdc, owner);
        }

        // 2. acquire global lock
        _log.info("{} is acquiring global lock {} ...", localOwner, _name);
        boolean bLockAcquired = false;

        MutationBatch m = _keyspace.prepareMutationBatch();
        try {
            ColumnMap<String> columns = _cpDistRowlock.acquireLockAndReadRow();

            String currMode = columns.getString(GlobalLock.GL_MODE_COLUMN, null);
            String currOwner = columns.getString(GlobalLock.GL_OWNER_COLUMN, null);
            String currExpiration = columns.getString(GlobalLock.GL_EXPIRATION_COLUMN, null);

            if(currMode != null && !currMode.equals(_mode.toString())) {
                errMsg = String.format("The global lock %s has been acquired by incompatible mode %s.", _name, currMode);
                _log.error(errMsg);
                throw new IllegalStateException(errMsg);
            }

            long curTimeMicros = System.currentTimeMillis();
            if (currExpiration != null) {
                long expirationTime = Long.parseLong(currExpiration);
                if (curTimeMicros < expirationTime || expirationTime == 0) {
                    if (currOwner == null) {
                        errMsg = String.format("The global lock %s owner should not be null.", _name);
                        _log.error(errMsg);
                        throw new IllegalStateException(errMsg);
                    }

                    if (!currOwner.isEmpty() && !currOwner.equals(globalOwner)) {
                        errMsg = String.format("The global lock %s has been acquired by another owner %s.", _name, currOwner);
                        _log.error(errMsg);
                        return bLockAcquired;
                    }
                }
            }

            m.withRow(_cf, _name).putColumn(GlobalLock.GL_MODE_COLUMN, _mode.toString());
            m.withRow(_cf, _name).putColumn(GlobalLock.GL_OWNER_COLUMN, globalOwner);
            long expirationTime = (_timeout == 0)? 0: curTimeMicros + _timeout;
            m.withRow(_cf, _name).putColumn(GlobalLock.GL_EXPIRATION_COLUMN, String.valueOf(expirationTime));

            // add global lock holder in current vdc ZK
            if (!_mode.equals(GlobalLock.GL_Mode.GL_NodeSvcShared_MODE)) {
                addLocalHolder(localOwner);
            }

            _cpDistRowlock.releaseWithMutation(m);

            bLockAcquired = true;
        } catch (StaleLockException e) {
            errMsg = String.format("%s failed to acquire global lock %s due to internal distributed row lock becoming stale.", localOwner, _name);
            _log.error(errMsg);
            return bLockAcquired;
        } catch (BusyLockException e) {
            errMsg = String.format("%s failed to acquire global lock %s due to locked by others.", localOwner, _name);
            _log.error(errMsg);
            return bLockAcquired;
        } catch (Exception e) {
            errMsg = String.format("Failed to acquire global lock %s due to unexpected exception : %s.", _name, e.getMessage());
            _log.error("Failed to acquire global lock {} due to unexpected exception {}.", _name, e);
            throw e;
        }
        finally {
            _log.debug("internal distributed row lock released.");
            _cpDistRowlock.release();
        }

        _log.info("{} acquired global lock {} successfully.", localOwner, _name);
        return bLockAcquired; 
    }

    /**
     * Release the global lock
     * For VdcShared Mode, the release might just remove zk holder from local VDC ZK.
     * If no other holders, it will remove the global lock from geodb then.
     *
     * @param owner the name of the local owner (e.g. node name or svc name etc.)
     * @param force whether to allow a lock to be released by a different owner in the
     *              same VDC.
     */
    public boolean release(final String owner, final boolean force) throws Exception {
        // 1. assemble global owner 
        String localOwner = owner;
        String globalOwner = _vdc;
        if(_mode.equals(GlobalLock.GL_Mode.GL_NodeSvcShared_MODE)) {
            globalOwner = String.format("%1$s:%2$s", _vdc, owner);
        }

        // 2. release global lock
        _log.info("{} is releasing global lock {} ...", localOwner, _name);

        boolean bLockReleased = false;

        MutationBatch m = _keyspace.prepareMutationBatch();
        try {
            ColumnMap<String> columns = _cpDistRowlock.acquireLockAndReadRow();

            String currMode = columns.getString(GlobalLock.GL_MODE_COLUMN, null);
            String currOwner = columns.getString(GlobalLock.GL_OWNER_COLUMN, null);
            
            if (currMode == null || currOwner == null) {
                // the lock is not active; return true
                _log.error("The global lock {} has is not active.", _name);
                return true;
            }

            if(!currMode.equals(_mode.toString())) {
                errMsg = String.format("The global lock %s has been acquired by incompatible mode %s.", _name, currMode);
                _log.error(errMsg);
                throw new IllegalStateException(errMsg);
            }

            if (!currOwner.isEmpty() && !currOwner.equals(globalOwner)) {
                if (force && isForceReleaseEligible(currMode, globalOwner, currOwner)) {
                    _log.warn("Forcibly releasing global lock with owner {}, was acquired" +
                            " by owner {}.", globalOwner, currOwner);
                } else {
                    errMsg = String.format("The global lock %s has been acquired by different owner %s.", _name, currOwner);
                    _log.error(errMsg);
                    return bLockReleased;
                }
            }

            // remove global lock holder from current vdc ZK
            if (!_mode.equals(GlobalLock.GL_Mode.GL_NodeSvcShared_MODE)) {
                removeLocalHolder(localOwner);
            }

            if (_mode.equals(GlobalLock.GL_Mode.GL_NodeSvcShared_MODE) || getLocalHolderNumber() == 0) {
                m.withRow(_cf, _name).deleteColumn(GlobalLock.GL_MODE_COLUMN);
                m.withRow(_cf, _name).deleteColumn(GlobalLock.GL_OWNER_COLUMN);
                m.withRow(_cf, _name).deleteColumn(GlobalLock.GL_EXPIRATION_COLUMN);
                _cpDistRowlock.releaseWithMutation(m);
                _log.info("{} released global lock {} successfully.", localOwner, _name);
            } else {
                // avoid releasing global lock if it is still hold by others
                _log.info("Skip releasing the global lock {}. It is still hold by other nodes within the vdc {}.", _name, _vdc);
            }

            bLockReleased = true;
        } catch (StaleLockException e) {
            errMsg = String.format("%s failed to release global lock %s due to internal distributed row lock becoming stale.", localOwner, _name);
            _log.error(errMsg);
            return bLockReleased;
        } catch (BusyLockException e) {
            errMsg = String.format("%s failed to release global lock %s due to locked by others.", localOwner, _name);
            _log.error(errMsg);
            return bLockReleased;
        } catch (Exception e) {
            errMsg = String.format("Failed to release global lock %s due to unexpected exception : %s.", _name, e.getMessage());
            _log.error("Failed to release global lock {} due to unexpected exception {}.", _name, e);
            throw e;
        } finally {
            _log.info("finally,internal distributed row lock releasing...");
            _cpDistRowlock.release();
            _log.info("finally,internal distributed row lock released.");
        }

        return bLockReleased;
    }

    @Override
    public boolean release(final String owner) throws Exception {
        return release(owner, false);
    }

    private boolean isForceReleaseEligible(String lockMode, String releaseOwner,
                                           String lockOwner) {
        return lockMode.equals(GlobalLock.GL_Mode.GL_NodeSvcShared_MODE.toString())
                && getVdcFromOwner(releaseOwner).equals(getVdcFromOwner(lockOwner));
    }

    private String getVdcFromOwner(String owner) {
        // if the owner is the VDC id, returns the VDC id.
        return owner.split(":", 2)[0];
    }

    /**
     * Get the global lock owner
     * For VdcShared Mode, the owner would be the global owner but not local owner (i.e node name etc.)
     * Also note that in order to call this method successfully, the caller must be able
     * to write to geodbsvc (and acquire the internal distributed row lock).
     *
     * @return the global owner name 
     */
    @Override
    public String getOwner() throws Exception {
        _log.info("querying the current owner of global lock {} ...", _name);
        String currOwner = null;
        try {
            ColumnMap<String> columns = _cpDistRowlock.acquireLockAndReadRow();

            currOwner = columns.getString(GlobalLock.GL_OWNER_COLUMN, null);
            String currExpiration = columns.getString(GlobalLock.GL_EXPIRATION_COLUMN, null);

            long curTimeMicros = System.currentTimeMillis();
            if (currExpiration != null) {
                long expirationTime = Long.parseLong(currExpiration);
                if (curTimeMicros < expirationTime || expirationTime == 0) {
                    if (currOwner == null) {
                        errMsg = String.format("The global lock %s owner should not be null.", _name);
                        _log.error(errMsg);
                        throw new IllegalStateException(errMsg);
                    }

                    if (!currOwner.isEmpty()) {
                        _log.info("The current owner of global lock {} is {}.", _name, currOwner);
                        return currOwner;
                    }
                } else { // curTimeMicros >= expirationTime, i.e., the lock has expired
                    return null;
                }
            }
        } catch (StaleLockException e) {
            errMsg = String.format("Failed to query current owner of global lock %s due to internal distributed row lock becoming stale.", _name);
            _log.error(errMsg);
        } catch (BusyLockException e) {
            errMsg = String.format("Failed to query current owner of global lock %s due to locked by others.", _name);
            _log.error(errMsg);
        } catch (Exception e) {
            errMsg = String.format("Failed to query current owner of global lock %s due to unexpected exception : %s.", _name, e.getMessage());
            _log.error(errMsg);
            throw e;
        } finally {
            _log.info("internal distributed row lock releasing...");
            _cpDistRowlock.release();
        }
        return currOwner;
    }

    /**
     * Add local owner to local VDC zk
     * Only validated in VdcShared Mode
     *
     * @param owner the local owner name
     */
    private void addLocalHolder(String owner) throws Exception {
        String holderPath = ZKPaths.makePath(_localvdcHolderRoot, owner);
        _log.info("adding global lock holder {}", holderPath);
        try {
            _zkClient.create().withMode(CreateMode.EPHEMERAL).forPath(holderPath);
        } catch(KeeperException.NodeExistsException e) {
            _log.debug("global lock holder {} already exist", holderPath);
        } catch(KeeperException e) {
            _log.error("failed to add global lock holder {}. e={}", holderPath, e);
            throw e;
        } catch(Exception e) {
            _log.error("failed to add global lock holder {} due to unexpected exception {}", holderPath, e);
            throw e;
        }
        _log.info("added global lock holder {}", holderPath);
    }

    /**
     * Get the number of the local VDC holders for the global lock
     * Only validated in VdcShared Mode
     *
     */
    private int getLocalHolderNumber() throws Exception {
        _log.info("getting global lock {} holder number", _name);
        List<String> holders = null;
        try {
            holders = _zkClient.getChildren().forPath(_localvdcHolderRoot);
        } catch(KeeperException e) {
            _log.error("failed to get global lock {} holder number. e={}", _name, e);
            throw e;
        } catch(Exception e) {
            _log.error("failed to get global lock {} holder number. e={}", _name, e);
            throw e;
        }
        _log.info("global lock holder number {}", holders.size());
        return holders.size();
    }

    /**
     * Remove local owner from local VDC zk
     * Only validated in VdcShared Mode
     *
     * @param owner the local owner name
     */
    private void removeLocalHolder(String owner) throws Exception {
        String holderPath = ZKPaths.makePath(_localvdcHolderRoot, owner);
        _log.info("removing global lock holder {}", holderPath);
        try {
            _zkClient.delete().guaranteed().forPath(holderPath);
        }catch (KeeperException.NoNodeException e) {
            _log.warn("The global lock holder {} has already been removed. e={}", holderPath, e);
        } catch(KeeperException e) {
            _log.error("failed to remove global lock holder {}. e={}", holderPath, e);
            throw e;
        } catch(Exception e) {
            _log.error("failed to remove global lock holder {} due to unexpected exception {}", holderPath, e);
            throw e;
        }
        _log.info("removed global lock holder {}", holderPath);
    }

    public String getErrorMessage() {
        return errMsg;
    }
}

