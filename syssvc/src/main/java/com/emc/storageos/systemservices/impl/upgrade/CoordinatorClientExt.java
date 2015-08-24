/*
 * Copyright (c) 2012-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.upgrade;

import static com.emc.storageos.coordinator.client.model.Constants.CONTROL_NODE_SYSSVC_ID_PATTERN;
import static com.emc.storageos.coordinator.client.model.Constants.NEW_VERSIONS_LOCK;
import static com.emc.storageos.coordinator.client.model.Constants.NODE_INFO;
import static com.emc.storageos.coordinator.client.model.Constants.REMOTE_DOWNLOAD_LEADER;
import static com.emc.storageos.coordinator.client.model.Constants.REMOTE_DOWNLOAD_LOCK;
import static com.emc.storageos.coordinator.client.model.Constants.TARGET_INFO;
import static com.emc.storageos.coordinator.client.model.Constants.TARGET_INFO_LOCK;
import static com.emc.storageos.systemservices.mapper.ClusterInfoMapper.toClusterInfo;
import static com.emc.storageos.coordinator.client.model.Constants.DBSVC_NAME;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.db.common.DbConfigConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.ConfigVersion;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.services.util.Strings;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.CoordinatorClient.LicenseType;
import com.emc.storageos.coordinator.client.service.DistributedPersistentLock;
import com.emc.storageos.coordinator.client.model.CoordinatorClassInfo;
import com.emc.storageos.coordinator.client.model.CoordinatorSerializable;
import com.emc.storageos.coordinator.client.model.PowerOffState;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.db.common.DbServiceStatusChecker;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.exceptions.InvalidLockOwnerException;
import com.emc.storageos.systemservices.exceptions.SyssvcException;
import com.emc.storageos.systemservices.impl.SysSvcBeaconImpl;
import com.emc.vipr.model.sys.ClusterInfo;
import com.google.common.collect.ImmutableSet;

import org.apache.curator.framework.recipes.locks.InterProcessLock;

public class CoordinatorClientExt {
    private static final Logger _log = LoggerFactory.getLogger(CoordinatorClientExt.class);

    private static final Set<String> CONTROL_NODE_ROLES =
            new ImmutableSet.Builder<String>().add("sys").add("control").build();
    private static final Set<String> EXTRA_NODE_ROLES =
            new ImmutableSet.Builder<String>().add("sys").add("object").build();

    private CoordinatorClient _coordinator;
    private SysSvcBeaconImpl _beacon;
    private ServiceImpl _svc;
    private InterProcessLock _remoteDownloadLock = null;
    private volatile InterProcessLock _targetLock = null;
    private InterProcessLock _newVersionLock = null;
    // Node id used for external display/query purpose.
    // EX: vipr1, vipr2, dataservice-10_247_100_15
    private String _myNodeId= null;
    private String _myNodeName= null;
    // Service id is for internal use to talk to coordinator.
    // EX: syssvc-1, syssvc-2, syssvc-10_247_100_15
    private String mySvcId = null;
    private int _nodeCount = 0;
    private DbServiceStatusChecker statusChecker = null;

    public CoordinatorClient getCoordinatorClient() {
        return _coordinator;
    }

    public ZkConnection getZkConnection() {
        return ((CoordinatorClientImpl) _coordinator).getZkConnection();
    }

    public void setServiceBeacon(SysSvcBeaconImpl beacon) {
        _beacon = beacon;
    }

    public void setService(ServiceImpl service) {
        _svc = service;
        _myNodeId= _svc.getNodeId();
        _myNodeName= _svc.getNodeName();
        mySvcId = _svc.getId();
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    /**
     * Get property
     * 
     * This method gets target properties from coordinator service as a string.
     * And decode it into propertyInfo object.
     * Syssvc is responsible for publishing the target property information into coordinator
     * 
     * @return property object
     * @throws CoordinatorException
     */
    public PropertyInfo getPropertyInfo() {
        return _coordinator.getPropertyInfo();
    }

    public void setNodeCount(int count) {
        _nodeCount = count;
    }

    public int getNodeCount() {
        return _nodeCount;
    }

    public void setStatusChecker(DbServiceStatusChecker checker) {
        statusChecker = checker;
    }

    public boolean isControlNode() {
        return CONTROL_NODE_SYSSVC_ID_PATTERN.matcher(mySvcId).matches();
    }

    /**
     * Gets the roles associated with the current node.
     */
    public Set<String> getNodeRoles() {
        if (isControlNode()) {
            return CONTROL_NODE_ROLES;
        } else {
            return EXTRA_NODE_ROLES;
        }
    }

    /**
     * Set node info to session scope.
     * Everything in session scope is gone when the session goes away.
     * 
     * @param state
     * @throws CoordinatorClientException
     */
    public void setNodeSessionScopeInfo(final CoordinatorSerializable state) throws CoordinatorClientException {
        if (state == null) {
            return;
        }

        try {
            String attr = state.getCoordinatorClassInfo().attribute;
            _svc.setAttribute(attr, state.encodeAsString());
            _beacon.publish();
        } catch (Exception e) {
            _log.error("set node scope info error:", e);
            throw SyssvcException.syssvcExceptions.coordinatorClientError("Failed to set node session info");
        }
    }

    /**
     * Set node info to global scope.
     * 
     * @param info
     * @param kind
     * @throws CoordinatorClientException
     */
    public void setNodeGlobalScopeInfo(final CoordinatorSerializable info, final String kind, final String id)
            throws CoordinatorClientException {
        if (info == null || kind == null) {
            return;
        }

        try {
            ConfigurationImpl cfg = new ConfigurationImpl();
            cfg.setId(id);
            cfg.setKind(kind); // We can use service id as the "id" and the type of info as "kind", then we can persist certain type of info
                               // about a particular node in coordinator
            cfg.setConfig(NODE_INFO, info.encodeAsString());
            _coordinator.persistServiceConfiguration(cfg);
        } catch (Exception e) {
            _log.error("Failed to set node global scope info", e);
            throw SyssvcException.syssvcExceptions.coordinatorClientError("Failed to set node global scope info. " + e.getMessage());
        }
    }

    /**
     * Get node info from global scope.
     * 
     * @param clazz
     * @param kind
     * @param <T>
     * @return
     * @throws Exception
     */
    public <T extends CoordinatorSerializable> T getNodeGlobalScopeInfo(final Class<T> clazz, final String kind, final String id)
            throws Exception {
        final T info = clazz.newInstance();

        final Configuration config = _coordinator.queryConfiguration(kind, id);
        if (config != null && config.getConfig(NODE_INFO) != null) {
            final String infoStr = config.getConfig(NODE_INFO);
            _log.debug("getNodeGlobalScopelInfo({}): info={}", clazz.getName(), Strings.repr(infoStr));
            final T decodeInfo = info.decodeFromString(infoStr);
            _log.debug("getNodeGlobalScopelInfo({}): info={}", clazz.getName(), decodeInfo);

            return decodeInfo;
        }
        return null;
    }

    /**
     * Wrapper setTarget method with checking cluster state stable
     * 
     * @param info
     * @throws CoordinatorClientException
     */
    public void setTargetInfo(final CoordinatorSerializable info) throws CoordinatorClientException {
        setTargetInfo(info, true);
    }

    /**
     * Set target info shared by all ndoes.
     * checkClusterUpgradable = true is used for rest api's. It forces to check the cluster state stable or not.
     * checkClusterUpgradable = false will not check the cluster state. It is used when cluster is not in stable state,
     * but we have to set target info at the same time.
     * 
     * @param info
     * @param checkClusterUpgradable
     * @throws CoordinatorClientException
     */
    public void setTargetInfo(final CoordinatorSerializable info, boolean checkClusterUpgradable) throws CoordinatorClientException {
        if (info == null) {
            return;
        }

        final CoordinatorClassInfo coordinatorInfo = info.getCoordinatorClassInfo();
        String id = coordinatorInfo.id;
        String kind = coordinatorInfo.kind;

        if (getTargetInfoLock()) {
            try {
                // check we are in stable state if checkState = true specified
                if (checkClusterUpgradable && !isClusterUpgradable()) {
                    throw APIException.serviceUnavailable.clusterStateNotStable();
                }
                ConfigurationImpl cfg = new ConfigurationImpl();
                cfg.setId(id);
                cfg.setKind(kind);
                cfg.setConfig(TARGET_INFO, info.encodeAsString());
                _coordinator.persistServiceConfiguration(cfg);
                _log.info("Target info set: {}", info);
            } catch (Exception e) {
                throw SyssvcException.syssvcExceptions.coordinatorClientError("Failed to set target state. " + e.getMessage());
            } finally {
                releaseTargetVersionLock();
            }
        } else {
            throw SyssvcException.syssvcExceptions.coordinatorClientError("Failed to set target state. Unable to obtain target lock");
        }
    }

    /**
     * Set target info shared by all nodes.
     * 
     * @param info info, String id, String kind
     * @throws CoordinatorClientException
     */
    public void setTargetInfo(final CoordinatorSerializable info, String id, String kind) throws CoordinatorClientException {
        if (info == null) {
            return;
        }

        if (getTargetInfoLock()) {
            try {
                // check we are in stable state & version exists in available
                if (!isClusterUpgradable()) {
                    throw APIException.serviceUnavailable.clusterStateNotStable();
                }
                ConfigurationImpl cfg = new ConfigurationImpl();
                cfg.setId(id);
                cfg.setKind(kind);
                cfg.setConfig(TARGET_INFO, info.encodeAsString());
                _coordinator.persistServiceConfiguration(cfg);
                _log.info("Target info set: {}", info);
            } catch (Exception e) {
                throw SyssvcException.syssvcExceptions.coordinatorClientError("Failed to set target state. " + e.getMessage());
            } finally {
                releaseTargetVersionLock();
            }
        } else {
            throw SyssvcException.syssvcExceptions.coordinatorClientError("Failed to set target state. Unable to obtain target lock");
        }
    }

    /**
     * Remove target info shared by all nodes.
     * checkClusterUpgradable = true is used for rest api's. It forces to check the cluster state stable or not.
     * checkClusterUpgradable = false will not check the cluster state. It is used when cluster is not in stable state,
     * but we have to set target info at the same time.
     * 
     * @param info
     * @param checkClusterUpgradable
     * @throws CoordinatorClientException
     */
    public void removeTargetInfo(final CoordinatorSerializable info, boolean checkClusterUpgradable) throws CoordinatorClientException {
        if (info == null) {
            return;
        }

        final CoordinatorClassInfo coordinatorInfo = info.getCoordinatorClassInfo();
        String id = coordinatorInfo.id;
        String kind = coordinatorInfo.kind;

        if (getTargetInfoLock()) {
            try {
                // check we are in stable state if checkState = true specified
                if (checkClusterUpgradable && !isClusterUpgradable()) {
                    throw APIException.serviceUnavailable.clusterStateNotStable();
                }
                ConfigurationImpl cfg = new ConfigurationImpl();
                cfg.setId(id);
                cfg.setKind(kind);
                cfg.setConfig(TARGET_INFO, info.encodeAsString());
                _coordinator.removeServiceConfiguration(cfg);
                _log.info("Target info removed: {}", info);
            } catch (Exception e) {
                throw SyssvcException.syssvcExceptions.coordinatorClientError("Failed to remove target info. " + e.getMessage());
            } finally {
                releaseTargetVersionLock();
            }
        } else {
            throw SyssvcException.syssvcExceptions.coordinatorClientError("Failed to remove target info. Unable to obtain target lock");
        }
    }

    /**
     * Remove target info shared by all nodes.
     * 
     * @param info info, String id, String kind
     * @throws CoordinatorClientException
     */
    public void removeTargetInfo(final CoordinatorSerializable info, String id, String kind) throws CoordinatorClientException {
        if (info == null) {
            return;
        }

        if (getTargetInfoLock()) {
            try {
                // check we are in stable state & version exists in available
                if (!isClusterUpgradable()) {
                    throw APIException.serviceUnavailable.clusterStateNotStable();
                }
                ConfigurationImpl cfg = new ConfigurationImpl();
                cfg.setId(id);
                cfg.setKind(kind);
                cfg.setConfig(TARGET_INFO, info.encodeAsString());
                _coordinator.removeServiceConfiguration(cfg);
                _log.info("Target info removed: {}", info);
            } catch (Exception e) {
                throw SyssvcException.syssvcExceptions.coordinatorClientError("Failed to remove target info. " + e.getMessage());
            } finally {
                releaseTargetVersionLock();
            }
        } else {
            throw SyssvcException.syssvcExceptions.coordinatorClientError("Failed to remove target info. Unable to obtain target lock");
        }
    }

    /**
     * Get target info
     * 
     * @param clazz
     * @param <T>
     * @return
     * @throws Exception
     */
    public <T extends CoordinatorSerializable> T getTargetInfo(final Class<T> clazz) throws Exception {
        return _coordinator.getTargetInfo(clazz);
    }

    /**
     * Get target info
     * 
     * @param clazz
     * @param id
     * @param kind
     * @return
     * @throws Exception
     */
    public <T extends CoordinatorSerializable> T getTargetInfo(final Class<T> clazz, String id, String kind) throws Exception {
        return _coordinator.getTargetInfo(clazz,id,kind);
    }

    /**
     * Get all Node Infos.
     * 
     * @param clazz
     * @param nodeIdFilter
     * @return
     * @throws Exception
     */
    public <T extends CoordinatorSerializable> Map<Service,
            T> getAllNodeInfos(Class<T> clazz, Pattern nodeIdFilter) throws Exception {
        return _coordinator.getAllNodeInfos(clazz, nodeIdFilter);
    }

    public <T extends CoordinatorSerializable> T getNodeInfo(String node, Class<T> clazz) throws CoordinatorClientException {
        try {
            T state = _coordinator.getNodeInfo(_svc,node,clazz);
            if (state != null) {
                return state;
            }
            throw SyssvcException.syssvcExceptions.coordinatorClientError(MessageFormat.format(
                    "Failed to get node info for node={0}: Can't find this node.", node));
        } catch (Exception e) {
            throw SyssvcException.syssvcExceptions.coordinatorClientError(MessageFormat.format("Failed to get node info for node={0}: {1}",
                    node, e));
        }
    }

    /**
     * The method to collect/retrieve the upgrade state of all the nodes in the
     * cluster
     * 
     * @return - ClusterInfo
     */
    public ClusterInfo getClusterInfo() {

        try {
            // get target repository and configVersion
            final RepositoryInfo targetRepository = _coordinator.getTargetInfo(RepositoryInfo.class);
            final PropertyInfoExt targetProperty = _coordinator.getTargetInfo(PropertyInfoExt.class);
            final PowerOffState targetPowerOffState = _coordinator.getTargetInfo(PowerOffState.class);

            // get control nodes' repository and configVersion info
            final Map<Service, RepositoryInfo> controlNodesInfo = getAllNodeInfos(RepositoryInfo.class, CONTROL_NODE_SYSSVC_ID_PATTERN);
            final Map<Service, ConfigVersion> controlNodesConfigVersions = getAllNodeInfos(ConfigVersion.class,
                    CONTROL_NODE_SYSSVC_ID_PATTERN);
            final ClusterInfo.ClusterState controlNodesState = _coordinator.getControlNodesState();

            // construct cluster information by both control nodes and extra nodes.
            // cluster state is determined both by control nodes' state and extra nodes
            return toClusterInfo(controlNodesState, controlNodesInfo, controlNodesConfigVersions, targetRepository,
                    targetProperty);
        } catch (Exception e) {
            _log.info("Fail to get the cluster information ", e);
            return null;
        }
    }

    /**
     * The method to get target configuration from coordinator
     * 
     * @param kind
     * @param id
     * @return - PropertyInfoExt
     */
    public PropertyInfoExt getConfigFromCoordinator(String kind, String id) {
        Configuration config = _coordinator.queryConfiguration(kind, id);
        if (config != null) {
            String str = config.getConfig(PropertyInfoExt.TARGET_INFO);
            return new PropertyInfoExt(new PropertyInfoExt().decodeFromString(str).getAllProperties());
        }

        return null;
    }

    /**
     * Check if the cluster is in a upgradable state
     * A cluster is stably upgradable if both control nodes and extra nodes are upgradable or control node is in initializing state
     * Initializing state is a special state that one control node will set it after deployed.
     * 
     * getExtraNodesState() checks the extra node upgrade lock.
     * 
     * @return
     */
    public boolean isClusterUpgradable() {
        return _coordinator.isClusterUpgradable();
    }

    /**
     * Check if control cluster are upgradable.
     * Note : INITIALIZING is a special state that it only happens when target info is not set
     * 
     * @return
     */
    public boolean isControlClusterUpgradable() {
        ClusterInfo.ClusterState state = _coordinator.getControlNodesState();

        return (state != null && (state.equals(ClusterInfo.ClusterState.STABLE) || state.equals(ClusterInfo.ClusterState.INITIALIZING)));
    }

    /**
     * Verify nodes' poweroff state not before the specified poweroff state
     * 
     * @param state compared state
     * @param checkNumOfControlNodes flag to check number of control nodes or not
     * @return true if all nodes are in the state; false otherwise
     * @throws Exception
     */
    public boolean verifyNodesPowerOffStateNotBefore(PowerOffState.State state, boolean checkNumOfControlNodes) {
        try {
            Map<Service, PowerOffState> controlNodesPowerOffState = getAllNodeInfos(PowerOffState.class,CONTROL_NODE_SYSSVC_ID_PATTERN);

            if (checkNumOfControlNodes && controlNodesPowerOffState.size() != getNodeCount()) {
                return false;
            }

            for (Map.Entry<Service, PowerOffState> entry : controlNodesPowerOffState.entrySet()) {
                if (entry.getValue().getPowerOffState().compareTo(state) < 0) {
                    return false;
                }
            }
        } catch (Exception e) {
            _log.info("Fail to get nodes' poweroff state information ", e);
            return false;
        }

        return true;
    }

    /**
     * Get node endpoint from node id
     *
     * @param nodeId
     * @return
     */
    public URI getNodeEndpoint(String nodeId) {
        try {
            List<Service> svcs = _coordinator.locateAllServices(_svc.getName(),_svc.getVersion(),(String)null,null);
            for (Service svc : svcs) {
                if (svc.getNodeId().equals(nodeId)) {
                    return svc.getEndpoint();
                }
            }
        } catch (Exception e) {
            _log.info("Fail to get the cluster information " + e.getMessage());
        }
        return null;
    }

    /**
     * Get node endpoint from syssvc id
     * 
     * @param svcId
     * @return
     */
    public URI getNodeEndpointForSvcId(String svcId) {
        try {
            List<Service> svcs = _coordinator.locateAllServices(_svc.getName(),_svc.getVersion(),(String)null,null);
            for (Service svc : svcs) {
                if (svc.getId().equals(svcId)) {
                    return svc.getEndpoint();
                }
            }
        } catch (Exception e) {
            _log.info("Fail to get the cluster information "+e.getMessage());
        }
        return null;
    }

    /**
     * The method to check if the given node holds the persistent upgrade lock
     * 
     * @param svcId
     *            - the node whose candidacy for lock owner to check
     * @param lockId
     *            - lock id
     * @return True - If node holds the lock false - otherwise
     */
    public boolean hasPersistentLock(String svcId, String lockId) throws Exception {
        try {
            DistributedPersistentLock lock = _coordinator.getPersistentLock(lockId);
            if (lock != null) {
                String lockOwner = lock.getLockOwner();
                if (lockOwner != null && serviceIdMatches(lockOwner, svcId)) {
                    _log.info("Current owner of the {} lock: {} ", lockId, lockOwner);
                    return true;
                }
            }
        } catch (Exception e) {
            _log.error("Can not get {} lock owner ",lockId);
        }
        return false;
    }

    /**
     * The method to try and grab the persistent upgrade lock for given node
     * 
     * @param svcId
     *            - The candidate node who wants to grab the lock
     * @param lockId
     *            - lock id
     * @return True - If node can get the lock False - Otherwise
     */
    public boolean getPersistentLock(String svcId, String lockId) {
        try {
            DistributedPersistentLock lock = _coordinator.getPersistentLock(lockId);
            _log.info("Acquiring the {} lock for {}...", lockId, svcId);
            boolean result = lock.acquireLock(svcId);
            if (result) {
                return true;
            }
        } catch (Exception e) {
            _log.info("Can not get {} lock",lockId,e);
        }
        return false;
    }

    /**
     * The method to release the persistent upgrade lock. It first ensures that
     * the node holds the lock
     * 
     * @param svcId
     *            - which wants to release lock
     * @param lockId
     *            - lock id
     * @return true if succeed; false otherwise
     * @throws InvalidLockOwnerException
     */
    public boolean releasePersistentLock(String svcId, String lockId) throws Exception {
        DistributedPersistentLock lock = _coordinator.getPersistentLock(lockId);
        if (lock != null) {
            String lockOwner = lock.getLockOwner();

            if (lockOwner == null) {
                _log.info("{} lock is not held by any node", lockId);
                return true;
            }

            if (!serviceIdMatches(lockOwner, svcId)) {
                throw SyssvcException.syssvcExceptions.invalidLockOwnerError("Lock owner is " + lockOwner);
            } else {
                boolean result = lock.releaseLock(lockOwner);
                if (result) {
                    _log.info("{} lock released by owner {} successfully", lockId, lockOwner);
                    return true;
                } else {
                    _log.info("{} lock released failed for owner {}", lockId, lockOwner);
                }
            }
        }

        return false;
    }

    /**
     * Check if the service ID matches the current ID or
     * The ID in a previous release
     * 
     * @param previousSvcId the previous service ID
     * @param currentSvcId the ID of the service
     * @return
     */
    private boolean serviceIdMatches(String previousSvcId, String currentSvcId) {
        // In 1.1 datanodes had _ and now they have - instead
        // If the current and previous IDs match return true or
        // If this is a datanode return true if the previous ID is just 1.1 format
        return previousSvcId.equals(currentSvcId) || (!isControlNode() && currentSvcId.equals(previousSvcId.replace('_', '-')));
    }

    /**
     * The method to release the persistent upgrade lock.
     * Any node which calls the method can release the lock.
     * 
     * @param lockId
     *            - lock id
     * @return true if succeed; false otherwise
     * @throws Exception
     */
    public boolean releasePersistentLock(String lockId) throws Exception {
        DistributedPersistentLock lock = _coordinator.getPersistentLock(lockId);
        if (lock != null) {
            String lockOwner = lock.getLockOwner();

            if (lockOwner == null) {
                _log.info("Upgrade lock is not held by any node");
                return true;
            }

            boolean result = lock.releaseLock(lockOwner);
            if (result) {
                _log.info("Upgrade lock {} released successfully", lockId);
                return true;
            } else {
                _log.info("Upgrade lock {} released failed", lockId);
            }
        }

        return false;
    }

    /**
     * The method to identify and return the node which is currently holding the
     * persistent upgrade lock
     * 
     * @param lockId
     *            - lock id
     * @return NodeHandle - for node which holds the lock null - If no node
     *         holds the lock
     */
    public String getUpgradeLockOwner(String lockId) {
        // Special handling for 1.1 when we changed - to _
        return _coordinator.getUpgradeLockOwner(lockId);
    }

    /**
     * The method to check if the input node holds the non-persistent remote download
     * lock
     * 
     * @param svcId
     * 
     * @return True - if given node holds the lock false-otherwise
     */
    public boolean hasRemoteDownloadLock(String svcId) {
        return svcId.equals(getRemoteDownloadLeader());
    }

    /**
     * The method which try and gran the non-persistent remote download lock
     * 
     * @param svcId
     *            - The candidate node which wants to get the lock
     * @return True - If node gets the lock False - Otherwise
     */
    public boolean getRemoteDownloadLock(String svcId) {
        try {
            String leader = this.getRemoteDownloadLeader();
            if (leader != null) {
                if (leader.equals(svcId)) {
                    return true;
                }
            }
            if (_remoteDownloadLock == null) {
                _remoteDownloadLock = _coordinator.getLock(REMOTE_DOWNLOAD_LOCK);
            }
            if (_remoteDownloadLock.acquire(2, TimeUnit.SECONDS)) {
                publishRemoteDownloadLeader(svcId);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            _log.error("Can not get leader lock {}", svcId, e);
            return false;
        }
    }

    /**
     * The method to release the non-persistent remote download lock
     * 
     * @param svcId
     *            - which wants to release the lock
     */
    public void releaseRemoteDownloadLock(String svcId) {
        try {
            if (_remoteDownloadLock != null) {
                _remoteDownloadLock.release();
                publishRemoteDownloadLeader(null);
            }
        } catch (Exception e) {
            _log.error("Can not release leader lock {}", svcId, e);
        }
    }

    /**
     * The method to publish/store the identification of the node which holds
     * the non-persistent lock. When the node releases the lock, it still needs
     * to call this method with "null" argument to clear/reset the lock owner
     * status
     * 
     * @param leader
     *            - Which hold the lock null - when any node release the lock
     */
    private void publishRemoteDownloadLeader(String leader) {
        if (leader != null) {
            _svc.setAttribute(REMOTE_DOWNLOAD_LEADER, leader);
        } else {
            _svc.setAttribute(REMOTE_DOWNLOAD_LEADER, "");
        }
        try {
            _log.info("Publishing leader: {}", leader);
            _beacon.publish();
        } catch (Exception ex) {
            _log.error("Failed to publish the leader", ex);
        }
    }

    /**
     * The method to retrieve the node holding the non-persistent remote download lock
     * 
     * @return id for node which holds the leader lock, null if no node
     *         holds the leader lock
     */
    public String getRemoteDownloadLeader() {
        try {
            String leader = _svc.getAttribute(REMOTE_DOWNLOAD_LEADER);
            if (leader != null && !leader.isEmpty()) {
                return leader;
            }
            List<Service> svcList = _coordinator.locateAllServices(_svc.getName(),
                    _svc.getVersion(), (String) null, null);
            Iterator<Service> svcIter = svcList.iterator();
            while (svcIter.hasNext()) {
                Service svc = svcIter.next();
                leader = svc.getAttribute(REMOTE_DOWNLOAD_LEADER);
                if (leader != null && !leader.isEmpty()) {
                    return leader;
                }
            }
        } catch (Exception ex) {
            _log.error("Failed probing for leader", ex);
        }
        return null;
    }

    /**
     * The method which try and gran the non-persistent target version lock
     * 
     * @return True - If node gets the lock False - Otherwise
     */
    public boolean getTargetInfoLock() {
        try {
            if (_targetLock == null) {
                _targetLock = _coordinator.getLock(TARGET_INFO_LOCK);
            }
            _targetLock.acquire();
        } catch (Exception e) {
            _log.error("Can not get target version lock", e);
            return false;
        }
        return true;
    }

    /**
     * The method to release the non-persistent target version lock
     * 
     */
    public void releaseTargetVersionLock() {
        try {
            if (_targetLock != null) {
                _targetLock.release();
            }
        } catch (Exception e) {
            _log.error("Can not release target version lock", e);
        }
    }

    private List<Service> getAllServices() throws Exception {
        return _coordinator.locateAllServices(_svc.getName(), _svc.getVersion(), (String) null, null);
    }

    /**
     * The utility method to find all the nodes which are available in the
     * cluster. When each node starts, the system management service on each
     * node, registers themselves with the coordninator. This method iterates
     * over that registration namespace to find all the nodes in the cluster
     * 
     * @return List of NodeHandles for all nodes in the cluster
     */
    public List<String> getAllNodes() {
        List<String> nodeIds = new ArrayList<String>();
        try {
            List<Service> svcs = getAllServices();
            for (Service svc : svcs) {
                final String nodeId = svc.getId();
                if (nodeId != null) {
                    nodeIds.add(nodeId);
                }
            }
        } catch (Exception e) {
            _log.error("getAllNodes(): Failed to get all nodeIds: {}", e);
        }
        _log.info("getAllNodes(): Node Ids: {}", Strings.repr(nodeIds));
        return nodeIds;
    }

    /**
     * The utility method to find the corresponding nodeIds for the provided
     * node names. When each node starts, the system management service on each
     * node, registers themselves with the coordninator. This method iterates
     * over that registration namespace to find the nodes in the cluster
     *
     * @return NodeHandle for mathing node in the cluster
     */
    public List<String> getMatchingNodeIds(List<String> nodeNames) {
        List<String> nodeIds = new ArrayList<String>();

        //if use short name is enabled allow matching short name to nodeId
        boolean useShortName = Boolean.parseBoolean(getPropertyInfo().getProperty("use_short_node_name"));

        try {
            List<Service> svcs = getAllServices();
            for (Service svc : svcs) {
                if (useShortName){
                    if(nodeNames.contains(svc.getNodeName()) ||
                            nodeNames.contains(svc.getNodeName().split("\\.")[0])) {
                        final String nodeId=svc.getNodeId();
                        nodeIds.add(nodeId);
                    }
                } else {
                    if (nodeNames.contains(svc.getNodeName())) {
                        final String nodeId=svc.getNodeId();
                        nodeIds.add(nodeId);
                    }
                }
            }
        } catch (Exception e) {
            _log.error("getMatchingNodeIds(): Failed to get all nodeIds for nodeNames {}: {}",nodeNames, e);
        }
        _log.info("getMatchingNodeIds(): Node Ids: {}", Strings.repr(nodeIds));
        return nodeIds;
    }

    /**
     * The utility method to find the corresponding nodeId for the provided
     * node name. When each node starts, the system management service on each
     * node, registers themselves with the coordninator. This method iterates
     * over that registration namespace to find the node in the cluster
     *
     * @return NodeHandle for mathing node in the cluster
     */
    public String getMatchingNodeId(String nodeName) {
        String nodeId = null;

        //if use short name is enabled allow matching short name to nodeId
        boolean useShortName = Boolean.parseBoolean(getPropertyInfo().getProperty("use_short_node_name"));

        try {
            List<Service> svcs = getAllServices();
            for (Service svc : svcs) {
                if (useShortName){
                    if(nodeName.equals(svc.getNodeName()) ||
                            nodeName.equals(svc.getNodeName().split("\\.")[0])) {
                        nodeId =svc.getNodeId();
                    }
                } else {
                    if (nodeName.equals(svc.getNodeName())) {
                        nodeId =svc.getNodeId();
                    }
                }
            }
        } catch (Exception e) {
            _log.error("getMatchingNodeId(): Failed to get all nodes while searching for {}: {}",nodeName, e);
        }

        if (nodeId==null) {
            _log.error("getMatchingNodeId(): Failed to get nodeId for nodeName {}",nodeName);
        } else {
            _log.info("getMatchingNodeId(): Node Id: {}", nodeId);
        }

        return nodeId;
    }

    /**
     * The utility method to combine list of nodeIds and list of nodeNames into a single list of nodeIds
     * Duplicate nodes are removed.
     *
     * @return nodeIds for mathing nodeNames and nodeIds combined
     */
    public List<String> combineNodeNamesWithNodeIds(List<String> nodeNames, List<String> nodeIds) {
        if (!nodeNames.isEmpty()) {
            //get nodeIds for node names
            List<String> matchedIds = getMatchingNodeIds(nodeNames);

            if (matchedIds.size() != nodeNames.size()){
                throw APIException.badRequests.parameterIsNotValid("node name");
            }

            //join list with nodeIds passed
            for (String id : matchedIds){
                if (!nodeIds.contains(id))
                    nodeIds.add(id);
            }
        }

        return nodeIds;
    }

    /**
     * The utility method to find the corresponding nodeId for the provided
     * node name. When each node starts, the system management service on each
     * node, registers themselves with the coordninator. This method iterates
     * over that registration namespace to find the node in the cluster
     *
     * @return NodeHandle for mathing node in the cluster
     */
    public String getMatchingNodeName(String nodeId) {
        String nodeName = null;
        try {
            List<Service> svcs = getAllServices();
            for (Service svc : svcs) {
                if (nodeId.equals(svc.getNodeId())){
                    nodeName=svc.getNodeName();
                    break;
                }
            }
        } catch (Exception e) {
            _log.error("getMatchingNodeName(): Failed to get all nodes while searching for {}: {}",nodeId, e);
        }

        if (nodeId==null) {
            _log.error("getMatchingNodeName(): Failed to get Node Name for Node Id {}",nodeId);
        } else {
            _log.info("getMatchingNodeName(): Node Name: {}", nodeName);
        }

        return nodeName;
    }


    /**
     * The utility method to find all the controller nodes which are not available in the
     * cluster(they might be powered off or the syssvc is off). When each node starts, the system management service on each
     * node, registers themselves with the coordninator. This method iterates
     * over that registration namespace to find all the nodes in the cluster
     * and thus find out the nodes that are not available.
     * 
     * @return List of node id for all unavailable nodes in the cluster(get external id like vipr2)
     */
    public ArrayList<String> getUnavailableControllerNodes() {
        ArrayList<String> fullList = new ArrayList<String>();
        ArrayList<String> availableNodes = (ArrayList<String>) getAllNodes();

        if (getNodeCount() > 1) {
            for (int i = 1; i <= getNodeCount(); i++) {
                if (!availableNodes.contains("syssvc-" + i)) {
                    fullList.add("vipr" + i);
                }
            }
        }

        _log.info("getUnavailableControllerNodes(): Node Ids: {}", Strings.repr(fullList));
        return fullList;
    }

    public RepositoryInfo getRepositoryInfo(String node) throws CoordinatorClientException {
        return getNodeInfo(node, RepositoryInfo.class);
    }

    /**
     * The method to retrieve the available software versions for the given node
     * 
     * @param node
     *            - for which to find the available versions
     * @return List of available software versions on given node
     */
    public List<SoftwareVersion> getVersions(String node) throws CoordinatorClientException {
        return getNodeInfo(node, RepositoryInfo.class).getVersions();
    }

    /**
     * Get id for "this" node
     */
    public String getMyNodeId() {
        return _myNodeId;
    }

    /**
     * Get name for "this" node
     */
    public String getMyNodeName() {
        return _myNodeName;
    }

    /**
     * Get id for "this" node
     */
    public String getMySvcId() {
        return mySvcId;
    }

    /**
     * The method which try and grant the non-persistent target version lock
     * 
     * @return True - If node gets the lock False - Otherwise
     */
    public boolean getNewVersionLock() {
        try {
            if (_newVersionLock == null) {
                _newVersionLock = _coordinator.getLock(NEW_VERSIONS_LOCK);
            }
            _newVersionLock.acquire();
        } catch (Exception e) {
            _log.error("Can not get new version lock", e);
            return false;
        }
        return true;
    }

    /**
     * The method to release the non-persistent target version lock
     * 
     */
    public void releaseNewVersionLock() {
        try {
            if (_newVersionLock != null) {
                _newVersionLock.release();
            }
        } catch (Exception e) {
            _log.error("Can not release new version lock", e);
        }
    }

    /**
     * Check to see if product is licensed for the specified license type.
     * 
     * @return true if the product is licensed for the specified type
     */

    public boolean isProductLicensed(LicenseType licenseType) {
        return _coordinator.isStorageProductLicensed(licenseType);
    }

    /**
     * Get list of services for a Service.
     * 
     * @param service
     * @param version
     * @param tag
     * @param endpointKey
     * @return
     */
    public List<Service> locateAllServices(String service, String version, String tag, String endpointKey) {
        return _coordinator.locateAllServices(service, version, tag, endpointKey);
    }

    /**
     * Check if connection to cluster's zookeeper is active
     * 
     * @return
     */
    public boolean isConnected() {
        return _coordinator.isConnected();
    }

    /**
     * Get ip address from vipr end point
     * If IPV6 is configured, end point is in the format of https://[ipv6_addr]:port
     * Otherwise, the end point is https://ipv4_addr:port
     * 
     * @param endpointUri
     * @return
     */
    public String getIPAddrFromUri(URI endpointUri) {
        if (endpointUri != null) {
            String host = endpointUri.getHost();
            if (host.startsWith("[")) {
                // ipv6 address
                return host.substring(1, host.indexOf("]", 1));
            }
            return host;
        }
        return null;
    }

    public boolean isDBServiceStarted() {

        List<Service> svcs = null;
        try {
            svcs = locateAllServices(DBSVC_NAME, _coordinator.getTargetDbSchemaVersion(),
                    (String) null, null);
        } catch (CoordinatorException e) {
            return false;
        }
        String dbSvcId = "db" + mySvcId.substring(mySvcId.lastIndexOf("-"));
        for (Service svc : svcs) {
            if (svc.getId().equals(dbSvcId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the dbsvc on current node has completed its adjustNumTokens() call.
     * If not, it's UpgradeManager's responsibility to call it through DbManager's MBean interface.
     * 
     * @return
     */
    public boolean isLocalNodeTokenAdjusted() {
        if (this.getNodeCount() == 1) {
            _log.info("single node cluster, skip adjust token");
            return true;
        }
        String dbSvcId = "db" + this.mySvcId.substring(this.mySvcId.lastIndexOf("-"));

        Configuration config = this._coordinator.queryConfiguration(Constants.DB_CONFIG, dbSvcId);

        String numToken = config.getConfig(DbConfigConstants.NUM_TOKENS_KEY);
        if (numToken == null) {
            _log.info("Did not found {} for {}, treating as not adjusted", DbConfigConstants.NUM_TOKENS_KEY, dbSvcId);
            return false;
        }

        return Integer.valueOf(numToken).equals(DbConfigConstants.DEFUALT_NUM_TOKENS);
    }

    public boolean isDBMigrationDone() {
        return statusChecker.isMigrationDone();
    }

    public String getCurrentDbSchemaVersion() {
        return _coordinator.getCurrentDbSchemaVersion();
    }

    /**
     * Get node seq from given svc id like syssvc-1, db-1 etc
     * 
     * @param svcId svc id with format syssvc-1, syssvc-2 etc
     * @return node seq in format db-1, db-2 etc
     */
    private String getNodeSeqFromSvcId(String svcId) {
        return svcId.substring(svcId.lastIndexOf("-") + 1);
    }

    /**
     * Get a set with good nodes with available service and version
     * 
     * @param svcName - service name
     * @param version - service version
     * @return a Set instance with good node seq id(1, 2, or 3 etc).
     */
    private Set<String> getGoodNodes(String svcName, String version) {
        Set<String> goodNodes = new HashSet<String>();
        List<Service> svcs = _coordinator.locateAllServices(svcName, version, (String) null, null);
        for (Service svc : svcs) {
            String svcId = svc.getId();
            goodNodes.add(getNodeSeqFromSvcId(svcId));
        }
        return goodNodes;
    }

    /**
     * Check if dbsvc/geodbsvc beacon is good or not
     * 
     * @param svcName either Constants.DBSVC_NAME or Constants.GEODBSVC_NAME
     * @return
     */
    public boolean isMyDbSvcGood(String svcName) {
        String nodeSeq = getNodeSeqFromSvcId(mySvcId);
        String dbVersion = _coordinator.getTargetDbSchemaVersion();
        return getGoodNodes(svcName, dbVersion).contains(nodeSeq);
    }

    /**
     * Get a syssvc endpoint URI for a node where has good dbsvc/geodbsvc
     */
    public URI getNodeEndpointWithGoodDbsvc() {
        try {
            String dbVersion = _coordinator.getTargetDbSchemaVersion();
            Set<String> localDbSvcState = getGoodNodes(Constants.DBSVC_NAME, dbVersion);
            Set<String> geoDbSvcState = getGoodNodes(Constants.GEODBSVC_NAME, dbVersion);

            List<Service> sysSvcs = _coordinator.locateAllServices(_svc.getName(), _svc.getVersion(), (String) null, null);
            for (Service sysSvc : sysSvcs) {
                String nodeSeq = getNodeSeqFromSvcId(sysSvc.getId());
                if (localDbSvcState.contains(nodeSeq) && geoDbSvcState.contains(nodeSeq)) {
                    return sysSvc.getEndpoint();
                }
                _log.info("Syssvc " + nodeSeq + " is ignored for its dbsvc state: " + localDbSvcState.contains(nodeSeq) + ", "
                        + geoDbSvcState.contains(nodeSeq));
            }

        } catch (Exception e) {
            _log.info("Fail to get the cluster information " + e.getMessage());
        }
        return null;
    }
}
