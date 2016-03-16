/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.curator.framework.recipes.barriers.DistributedBarrier;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessReadWriteLock;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.curator.framework.recipes.queue.QueueConsumer;
import org.apache.curator.framework.recipes.queue.QueueSerializer;

import com.emc.storageos.coordinator.client.model.CoordinatorSerializable;
import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.coordinator.client.model.MigrationStatus;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DistributedLockQueueTaskConsumer;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.vipr.model.sys.ClusterInfo;

/**
 * The main client API for service information lookup, distributed lock/queue, controller
 * leader election, etc.
 */
public interface CoordinatorClient {
    // enumeration for the storage type associated
    // with the license.
    public static enum LicenseType {
        CONTROLLER,
        OBJECT,
        HDFS,
        OBJECTHDFS, // Kept in case upgrade may need it
        UNSTRUCTURED,
        CAS,
        BLOCK,
        COMMODITY,
        ECS;

        public static LicenseType findByValue(String str) {
            for (LicenseType v : values()) {
                if (v.toString().equals(str)) {
                    return v;
                }
            }
            return null;
        }

        public static List<String> getValuesAsStrings() {
            List<String> valStrs = new ArrayList<String>();
            for (LicenseType val : values()) {
                valStrs.add(val.toString());
            }
            return valStrs;
        }
    }

    /**
     * Looks up a service with clazz, name and version; tag and endpointKey (optional)
     * Binds advertised endpoint with a given interface and returns a stub object that implements this interface.
     * Currently supported endpoint types are rmi, tbd...
     * <p/>
     * Default coordinator implementation may implement any load balancing scheme when multiple services of the same name and version are
     * available. Client stub object for the same endpoint may be cached in CoordinatorClient implementation for performance.
     * <p/>
     * Note that liveness of endpoint is not guaranteed - any retry mechanism is a stub object implementation specific.
     * 
     * @param clazz
     * @param name
     * @param version
     * @param tag
     * @param endpointKey
     * @param <T>
     * @return
     * @throws CoordinatorException
     */
    public <T> T locateService(Class<T> clazz, String name, String version, String tag, String endpointKey)
            throws CoordinatorException;

    /**
     * Looks up a service with clazz, name and version; tag, default tag and endpointKey (optional)
     * If cannot find service for a tag, returns service for default tag.
     * Binds advertised endpoint with a given interface and returns a stub object that implements this interface.
     * Currently supported endpoint types are rmi, tbd...
     * <p/>
     * Default coordinator implementation may implement any load balancing scheme when multiple services of the same name and version are
     * available. Client stub object for the same endpoint may be cached in CoordinatorClient implementation for performance.
     * <p/>
     * Note that liveness of endpoint is not guaranteed - any retry mechanism is a stub object implementation specific.
     *
     * @param clazz
     * @param name
     * @param version
     * @param tag
     * @param defaultTag
     * @param endpointKey
     * @param <T>
     * @return
     * @throws CoordinatorException
     */
    public <T> T locateService(Class<T> clazz, String name, String version, String tag, String defaultTag, String endpointKey)
            throws CoordinatorException;

    /**
     * Look up all services with given name, version, tag, and endpointKey
     * 
     * @param name service name
     * @param version service version
     * @param tag service tag. if null, does not filter on tag
     * @param endpointKey endpoint key. if null, does not filter on endpoint key
     * @return matching services
     */
    public List<Service> locateAllServices(String name, String version, String tag, String endpointKey)
            throws CoordinatorException;

    /**
     * Look up all services with site uuid, given name, version, tag, and endpointKey
     * 
     * @param uuid site uuid
     * @param name service name
     * @param version service version
     * @param tag service tag. if null, does not filter on tag
     * @param endpointKey endpoint key. if null, does not filter on endpoint key
     * @return matching services
     */
    public List<Service> locateAllServices(String uuid, String name, String version, String tag, String endpointKey)
            throws CoordinatorException;

    /**
     * Look up all services of all versions with given name from local site
     *
     * @param name service name
     * @return matching services
     */
    List<Service> locateAllSvcsAllVers(String name) throws CoordinatorException;
    
    /**
     * Look up all services of all versions with given name in a specific site
     *
     * @param siteId site uuid
     * @param name service name
     * @return matching services
     */
    List<Service> locateAllSvcsAllVers(String siteId, String name) throws CoordinatorException;

    /**
     * Retrieves/creates a distributed queue with given name. Default implementation provides
     * at least once delivery semantics with partition tolerance (majority partition
     * holds onto queue state). A item is only removed from queue iff {@link QueueConsumer#consumeMessage(Object)} returns successfully. If
     * consumer dies
     * or errors out, item is redelivered to another consumer.
     * <p/>
     * 
     * @param name queue name
     * @param consumer consumer callback implementation
     * @param serializer de/serializer
     * @param maxThreads maximum number of threads to use for concurrent delivery
     * @param maxItem maximum number of items that can be processed concurrently / waiting in this queue
     * @param <T>
     * @return
     * @throws CoordinatorException
     */
    public <T> DistributedQueue<T> getQueue(String name, DistributedQueueConsumer<T> consumer,
            QueueSerializer<T> serializer, int maxThreads, int maxItem)
            throws CoordinatorException;

    /**
     * Overload of getQueue(name, consumer, serializer, maxThreads, maxItem) that uses default
     * configuration for maximum number of items
     */
    public <T> DistributedQueue<T> getQueue(String name, DistributedQueueConsumer<T> consumer,
            QueueSerializer<T> serializer, int maxThreads)
            throws CoordinatorException;

    /**
     * Gets an instance of a DistributedLockQueueManager, creating one if necessary.
     *
     * TODO more javadoc
     *
     * @param consumer
     * @param <T>
     * @return
     */
    <T> DistributedLockQueueManager getLockQueue(DistributedLockQueueTaskConsumer<T> consumer)
            throws CoordinatorException;

    /**
     * Gets/creates work pool with given name. WorkPool holds a set of work items clients
     * are assigned. Pool itself does not preemptively reschedule based on work distribution.
     * For use cases where rebalancing is required, it is recommended that workers periodically
     * relinquish work item ownership - released work items are randomly assigned to next
     * available client achieving redistribution when new workers show up.
     * 
     * @param name work pool name
     * @param name workerUuid worker UUID
     * @param listener work assignment listener
     * @return work pool
     * @throws CoordinatorException
     */
    public WorkPool getWorkPool(String name, WorkPool.WorkAssignmentListener listener)
            throws CoordinatorException;

    /**
     * Retrieves/creates a distributed (counting) semaphore with given name.
     * 
     * @param name Semaphore name
     * @param maxPermits Max number of permits required
     * 
     * @return DistributedSemaphore
     * 
     * @throws CoordinatorException
     */
    public DistributedSemaphore getSemaphore(String name, int maxPermits)
            throws CoordinatorException;

    /**
     * Retrieves/creates a distributed mutex
     *
     * @param name mutex name
     * @return mutex
     */
    public InterProcessLock getLock(String name) throws CoordinatorException;

    /**
     * Retrieves/creates a distributed mutex for local site
     *
     * @param name
     * @return
     * @throws CoordinatorException
     */
    public InterProcessLock getSiteLocalLock(String name) throws CoordinatorException;

    /**
     * Retrieves/creates a distributed read write lock
     *
     * @param name read write lock name
     * @return read write lock
     */
    public InterProcessReadWriteLock getReadWriteLock(String name) throws CoordinatorException;

    /**
     * Retrieves/creates a distributed mutex that can be released by any thread.
     * 
     * @param name mutex name
     * @return mutex
     */
    public InterProcessSemaphoreMutex getSemaphoreLock(String name) throws CoordinatorException;

    /**
     * Retrieves/creates a distributed persistent lock from site-specific area.
     * This should be the default choice of persistent lock.
     *
     * @param name lock name
     * @return DistributedPersistentLock
     */

    DistributedPersistentLock getSiteLocalPersistentLock(String name) throws CoordinatorException;

    /**
     * Retrieves/creates a distributed persistent lock from the global area.
     * This is primarily used by controllersvc and UpgradeManager to provide backward compatibility.
     * Use with extreme caution since all the DR sites are sharing this lock and there will be race conditions.
     * Right now it's probably fine to controllersvc since we don't have controllersvc running on standby sites.
     * 
     * @param name lock name
     * @return DistributedPersistentLock
     */

    DistributedPersistentLock getPersistentLock(String name) throws CoordinatorException;

    /**
     * Starts coordinator client service. Default implementation attempts to connect
     * to coordinator cluster when cluster becomes unavailable. Calling any API during
     * a disconnect results in IOException.
     * 
     * @throws IOException when coordinator cluster is unreachable
     */
    public void start() throws IOException;

    /**
     * stop coordinator client service.
     */
    public void stop();

    /**
     * Returns connection status
     * 
     * @return true if connected to coordinator cluster. false, otherwise.
     */
    public boolean isConnected();

    /**
     * Permanently persists configuration information in global area. Note that most (if not all) services do not
     * need to persist their configuration information. This is used for services (such as dbsvc) that need
     * to adjust cluster configuration using existing configuration of other nodes.
     * 
     * @param config
     */
    public void persistServiceConfiguration(Configuration... config) throws CoordinatorException;
    
    /**
     * Persist service configuration to site specific area
     * 
     * @param siteId
     * @param config
     * @throws CoordinatorException
     */
    public void persistServiceConfiguration(String siteId, Configuration... config) throws CoordinatorException;

    /**
     * Removes configured service information in global area. See above notes about when this feature may be used.
     * 
     * @param config
     */
    public void removeServiceConfiguration(Configuration... config) throws CoordinatorException;
    
    /**
     * Removes configured service information from site specific area.
     * 
     * @param config
     */
    public void removeServiceConfiguration(String siteId, Configuration... config) throws CoordinatorException;

    /**
     * Queries all configuration with given kind in zk global config area(/config)
     * 
     * @param kind
     * @return
     */
    public List<Configuration> queryAllConfiguration(String kind) throws CoordinatorException;

    /**
     * Queries all configuration with given kind in site specific area(/config/<site id>/)
     * 
     * @param kind
     * @return
     */
    public List<Configuration> queryAllConfiguration(String siteId, String kind) throws CoordinatorException;
    
    /**
     * Queries configuration with given kind and id in zk global config area(/config)
     * 
     * @param kind
     * @param id
     * @return
     * @throws CoordinatorException
     */
    public Configuration queryConfiguration(String kind, String id) throws CoordinatorException;

    /**
     * Query configuration for a site in site specific area(/config/<site id>/)
     * 
     * @param siteId
     * @param kind
     * @param id
     * @return
     * @throws CoordinatorException
     */
    public Configuration queryConfiguration(String siteId, String kind, String id) throws CoordinatorException;
    
    /**
     * Registers a connection listener
     * 
     * @param listener
     */
    public void setConnectionListener(ConnectionStateListener listener);

    /**
     * Get property information
     * 
     * @return property object
     * @throws CoordinatorException
     */
    public PropertyInfo getPropertyInfo() throws CoordinatorException;

    /**
     * Create a distributed data manager instance for accessing the ZK tree,
     * and a new thread is forked for each distributedDataManager, plase call
     * DistributedDataManager.close() to release the thread and other resources
     * 
     * @param basePath the portion of the ZK tree which this instance will manage
     * @return the data manager
     * @throws CoordinatorException
     */
    public DistributedDataManager createDistributedDataManager(String basePath) throws CoordinatorException;

    /**
     * Create a distributed data manager instance for accessing the ZK tree with configurable max nodes,
     * and a new thread is forked for each distributedDataManager, plase call
     * DistributedDataManager.close() to release the thread and other resources
     * 
     * @param basePath the portion of the ZK tree which this instance will manage
     * @param maxNodes the max number of nodes
     * @return the data manager
     * @throws CoordinatorException
     */
    public DistributedDataManager createDistributedDataManager(String basePath, long maxNodes) throws CoordinatorException;

    /**
     * Get an unlimited workflow version of the data manager for accessing the ZK tree
     * 
     * This API is present only for the workflow use case in controllersvc, and is not
     * recommended for any other use case. It does not provide any limits on the number
     * of nodes or memory consumption on ZK server side, so it is the caller's responsibility
     * to enforce this through some external means
     * 
     * @return the workflow version of DistributedDataManager
     * @throws CoordinatorException
     */
    public DistributedDataManager getWorkflowDataManager() throws CoordinatorException;

    /**
     * Validates if the product has been licensed.
     * 
     * @param licenseType
     * @return
     */
    public boolean isStorageProductLicensed(LicenseType licenseType);

    /**
     * Creates a leaderLatch instance for particular task based on the latchPath.
     * leaderElection will happen once leaderLatch.start() initiated
     * 
     * @param latchPath path to participate leader election
     * @return
     */
    public LeaderLatch getLeaderLatch(String latchPath);

    /**
     * Create a leader selector for a specific task.
     * LeaderSelector is an abstraction to select a "leader" amongst multiple
     * contenders in a group of JMVs connected to a Zookeeper cluster. If a group
     * of N thread/processes contends for leadership, one will be assigned leader
     * until it releases leadership at which time another one from the group will
     * be chosen.
     * 
     * @param leaderPath leader path in zookeeper
     * @param listener leader assignment listener
     * @return LeaderSelector
     * @throws CoordinatorException
     */
    public LeaderSelector getLeaderSelector(String leaderPath, LeaderSelectorListener listener) throws CoordinatorException;

    /**
     * Create a leader selector. For specific site only. See comment for {@link #getLeaderSelector(String, LeaderSelectorListener)}
     * 
     * @param siteId - null for global area. Non null site id for some specific site.
     * @param leaderPath leader path
     * @param listener leader assignment listener
     * @return LeaderSelector
     * @throws CoordinatorException
     */
    public LeaderSelector getLeaderSelector(String siteId, String leaderPath, LeaderSelectorListener listener) throws CoordinatorException;

    /**
     * Set Target info
     * 
     * @param info
     * @param id
     * @param kind
     * @throws CoordinatorException
     */
    public void setTargetInfo(final CoordinatorSerializable info) throws CoordinatorException;
    
    /**
     * Set target info for specific site
     * 
     * @param siteId
     * @param info
     * @throws CoordinatorException
     */
    public void setTargetInfo(String siteId, final CoordinatorSerializable info) throws CoordinatorException;
    
    /**
     * Get control nodes' state
     */
    public ClusterInfo.ClusterState getControlNodesState();

    /**
     * Get control nodes state for specified site
     * 
     * @param siteId
     * @return
     */
    public ClusterInfo.ClusterState getControlNodesState(String siteId);
    
    /**
     * Get target info
     * 
     * @param clazz
     * @param <T>
     * @return
     * @throws Exception
     */
    public <T extends CoordinatorSerializable> T getTargetInfo(final Class<T> clazz) throws CoordinatorException;
    
    public <T extends CoordinatorSerializable> T getTargetInfo(String siteId, final Class<T> clazz) throws CoordinatorException;

    /**
     * Get all Node Infos.
     * 
     * @param clazz
     * @param nodeIdFilter
     * @return
     * @throws Exception
     */
    public <T extends CoordinatorSerializable> Map<Service,
            T> getAllNodeInfos(Class<T> clazz, Pattern nodeIdFilter) throws Exception;

    <T extends CoordinatorSerializable> Map<Service,
            T> getAllNodeInfos(Class<T> clazz, Pattern nodeIdFilter, String siteId) throws Exception;

    public <T extends CoordinatorSerializable> T getNodeInfo(Service service, String nodeId, Class<T> clazz)
            throws Exception;

    /**
     * Load the state object from specified key.
     * 
     * @param key
     * @param clazz
     * @param <T>
     * @return
     * @throws Exception
     */
    public <T extends CoordinatorSerializable> T queryRuntimeState(String key, Class<T> clazz) throws CoordinatorException;

    /**
     * Save the state object to specified key
     * 
     * @param key
     * @param state
     * @param <T>
     * @throws Exception
     */
    public <T extends CoordinatorSerializable> void persistRuntimeState(String key, T state) throws CoordinatorException;
    
    /**
     * Removes runtime state of specified key.
     * 
     * @param key
     */
    public void removeRuntimeState(String key) throws CoordinatorException;

    /**
     * The method to identify and return the node which is currently holding the
     * persistent upgrade lock
     * 
     * @param lockId
     *            - lock id
     * @return NodeHandle - for node which holds the lock null - If no node
     *         holds the lock
     */
    public String getUpgradeLockOwner(String lockId);

    public void setDbVersionInfo(DbVersionInfo info);

    // TODO support schema version of geodbsvc
    public String getCurrentDbSchemaVersion();

    public String getTargetDbSchemaVersion();

    /**
     * Get DB migration status
     * 
     * TODO support migration status of geodbsvc
     */
    public MigrationStatus getMigrationStatus();

    /**
     * Get if the DB schema version changed
     */
    public boolean isDbSchemaVersionChanged();

    /**
     * Get dbconfig root node in zk
     * 
     * @param serviceName - dbsvc or geodbsvc
     * @return
     */
    public String getDbConfigPath(String serviceName);

    /**
     * Check if the cluster is in a upgradable state. A cluster is stably upgradable if
     * all nodes are upgradable, or in initializing state.
     * Initializing state is a special state that one control node will set it after
     * deployed.
     * 
     * @return
     */
    public boolean isClusterUpgradable();

    /**
     * Get versioned dbconfig root node in zk
     * 
     * @param serviceName
     *            - dbsvc or geodbsvc
     * @param version
     *            - current dbschema version
     * @return
     */
    public String getVersionedDbConfigPath(String serviceName, String version);

    /**
     * Gets inet address lookup map.
     */
    public CoordinatorClientInetAddressMap getInetAddessLookupMap();

    /**
     * Sets inet address lookup map.
     * 
     * @param inetAddessLookupMap
     *            The instance of CoordinatorClientInetAddressMap
     */
    public void setInetAddessLookupMap(CoordinatorClientInetAddressMap inetAddessLookupMap);

    /**
     * add and start a NodeListener to listen the zk node change. Will not be notified for change on child node.
     * 
     * @param listener
     */
    public void addNodeListener(NodeListener listener) throws Exception;

    /**
     * remove the NodeListener from coordinator client.
     * 
     * @param listener
     */
    public void removeNodeListener(NodeListener listener);
    
    /**
     * Get a unique id for current site, which is used to access site specific area in ZK
     * @return site uuid
     */
    public String getSiteId();
    
    /**
     * Add a site ZNode in ZK
     * This should only be used by the add standby site API
     */
    public void addSite(String siteId) throws Exception;
    
    /**
     * Create a Curator recipe - double barrier 
     * 
     * @param barrierPath Znode path for this barrier
     * @param memberQty - number of members that plan to wait on the barrier
     * @return
     */
    public DistributedDoubleBarrier getDistributedDoubleBarrier(String barrierPath, int memberQty);
    
    /**
     * Create a Curator recipe - distributed barrier 
     * 
     * @param barrierPath Znode path for this barrier
     * @return
     */
    public DistributedBarrier getDistributedBarrier(String barrierPath);

    /**
     * Checks for the existence of a lock (znode) at the given path.  The lock is available
     * if no znode exists at the given path.
     *
     * @param lockPath
     * @return true if the lock is available
     * @throws Exception
     */
    boolean isDistributedOwnerLockAvailable(String lockPath) throws Exception;

    /**
     * Set an instance of {@link DistributedAroundHook} that exposes the ability to wrap arbitrary code
     * with before and after hooks that lock and unlock the owner locks "globalLock", respectively.
     *
     * @param ownerLockAroundHook An instance to help with owner lock management.
     */
    void setDistributedOwnerLockAroundHook(DistributedAroundHook ownerLockAroundHook);

    /**
     * Gets the instance of {@link DistributedAroundHook} for owner lock management.
     *
     * @return An instance to help with owner lock management.
     */
    DistributedAroundHook getDistributedOwnerLockAroundHook();
    
    /**
     * Delete a ZK path recursively
     * 
     * @param path full path on zk tree
     */
    void deletePath(String path);
    
    /**
     * check whether specified ZK node exists
     * @param path
     * @return true if node exists
     */
    boolean nodeExists(String path);
    
    /**
     * Start a ZK transaction for a serial of ZK updates. Currently we support 
     * only persistServiceConfig/removeSerivceConfig calls.
     */
    public void startTransaction();
    
    /**
     * Commit transaction. All ZK updates may succeed, or fail. No partial completion is 
     * guranteed
     * 
     */
    public void commitTransaction() throws CoordinatorException;
    
    /**
     * Discard current zk transaction
     */
    public void discardTransaction();

}
