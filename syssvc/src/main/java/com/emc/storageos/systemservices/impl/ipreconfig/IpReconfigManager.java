/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.ipreconfig;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.model.property.PropertyConstants;
import com.emc.storageos.services.util.FileUtils;
import com.emc.storageos.services.util.NamedThreadPoolExecutor;
import com.emc.storageos.services.util.PlatformUtils;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.vipr.model.sys.ClusterInfo;
import com.emc.vipr.model.sys.ipreconfig.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Ip Reconfig Manager drives whole procedure of cluster ip reconfiguration.
 * It persists both cluster and node status both in ZK and local node.
 */
public class IpReconfigManager implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(IpReconfigManager.class);
    private static Charset UTF_8 = Charset.forName("UTF-8");
    private static final long IPRECONFIG_TIMEOUT = 24 * 60 * 60 * 1000; // 24 hours timeout for the procedure
    private static final long POLL_INTERVAL = 10 * 1000; // 10 second polling interval
    private static final String UPDATE_ZKIP_LOCK = "update_zkip";

    // ipreconfig entry in ZK
    Configuration config = null;

    private ClusterIpInfo localIpinfo = null;   // local/current ip info
    private ClusterIpInfo newIpinfo = null;     // new ip info
    private Integer localNodeId;                // local node id (1~5)
    private Integer nodeCount;
    private long expiration_time = 0L;         // ipreconfig would fail if not finished at this time


    @Autowired
    private CoordinatorClientExt _coordinator;

    @Autowired
    private LocalRepository localRepository;

    private ThreadPoolExecutor _pollExecutor;

    private IpReconfigListener ipReconfigListener = null;

    private DrUtil drUtil;
    public void setDrUtil(DrUtil drUtil) {
        this.drUtil = drUtil;
    }

    private Properties ovfProperties;    // local ovfenv properties

    public void setOvfProperties(Properties ovfProps) {
        ovfProperties = ovfProps;
    }

    // this shouldn't be named the same as the default getter, since the return type
    // is different with the argument type of the setter.
    public Map<String, String> getOvfProps() {
        return (Map) ovfProperties;
    }

    /**
     * Responds to connection drops / reconnects.
     */
    private final ConnectionStateListener _connectionListener = new ConnectionStateListener() {
        @Override
        public void stateChanged(final CuratorFramework client, final ConnectionState newState) {
            log.info("Entering stateChanged method : {}", newState);
            if (newState == ConnectionState.CONNECTED || newState == ConnectionState.RECONNECTED) {
                addIpreconfigListener();
            }
        }
    };

    public IpReconfigManager() {
    }

    /**
     * Initialize ipreconfig manager
     * 1. Load local ovfenv properties
     * 2. Register node listener for ipreconfig config znode in ZK
     */
    public void init() {
        loadLocalOvfProps();
        addIpreconfigListener();

        _coordinator.getZkConnection().curator().getConnectionStateListenable().addListener(_connectionListener);
    }

    /*
     * Load local ovfenv properties
     */
    private void loadLocalOvfProps() {
        Map<String, String> ovfprops = getOvfProps();
        localIpinfo = new ClusterIpInfo();
        localIpinfo.loadFromPropertyMap(ovfprops);

        String node_id = ovfprops.get(PropertyConstants.NODE_ID_KEY);
        if (node_id == null || node_id.equals(Constants.STANDALONE_ID)) {
            localNodeId = 1;
        } else {
            localNodeId = Integer.valueOf(node_id.split("vipr")[1]);
        }
        nodeCount = Integer.valueOf(ovfprops.get(PropertyConstants.NODE_COUNT_KEY));
    }

    /**
     * Register ipreconfig listener to monitor ipreconfig related changes (new IPs, status etc.)
     */
    private void addIpreconfigListener() {
        try {
            if (ipReconfigListener != null) {
                _coordinator.getCoordinatorClient().removeNodeListener(ipReconfigListener);
            }
            ipReconfigListener = new IpReconfigListener();
            _coordinator.getCoordinatorClient().addNodeListener(ipReconfigListener);
        } catch (Exception e) {
            log.error("Fail to add node listener for ip reconfig config znode", e);
            throw APIException.internalServerErrors.addListenerFailed();
        }
        log.info("Succeed to add node listener for ip reconfig config znode");
    }

    /**
     * Main retry loop of the IP reconfiguration procedure.
     */
    @Override
    public void run() {
        init();
        while (true) {
            try {
                handleIpReconfig();

                // wait for any ipreconfig related changes (new IPs, procedure status or node status change etc.)
                await();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Handle the ip reconfiguration procedure for any ipreconfiguration info/status changes.
     * 1. It drives ip reconfiguration state machine to move forward the whole procedure
     * 2. It launches a polling executor to handle two special scenarios -- procedure timeout and missed ZK event
     * 3. It also will cleanup env if the procedure is failed etc.
     */
    private synchronized void handleIpReconfig() throws Exception {
        config = _coordinator.getCoordinatorClient().queryConfiguration(IpReconfigConstants.CONFIG_KIND, IpReconfigConstants.CONFIG_ID);
        if (config == null) {
            log.info("no ipreconfig request coming in yet.");
            assureIPConsistent();
            return;
        }

        if (isRollback()) {
            assureIPConsistent();
            return;
        }

        if (!isStarted(config)) {
            // Barely do nothing if stauts is SUCCEED or FAILED
            log.info("ip reconfig procedure is not started.");
            if (isFailed(config)) {
                // cleanup local temp files when the procedure failed
                log.info("ip reconfig procedure failed. cleanup...");
                IpReconfigUtil.cleanupLocalFiles();
                // stop polling executor
            }
            stopPollExecutor();
            return;
        }

        expiration_time = Long.valueOf(config.getConfig(IpReconfigConstants.CONFIG_EXPIRATION_KEY));
        if (System.currentTimeMillis() >= expiration_time) {
            // set procedure failed when it is expired
            setFailed(IpReconfigConstants.ERRSTR_TIMEOUT);
            return;
        }

        // start polling executor
        startPollExecutor();

        // drive ip reconfiguration status machine
        driveIpReconfigStateMachine();
    }

    /**
     * Handle core status change for the ip reconfig procedure
     * For each node, the status could be
     * 1. None
     * System just receiving ip reconfiguration request.
     * 2. LOCALAWARE_LOCALPERSISTENT
     * Local node has got the new IPs persisted while it has no idea of other nodes' status.
     * 3. LOCALAWARE_CLUSTERPERSISTENT
     * Local node knows the new IPs has been persisted in cluster domain, but not sure if all other nodes know about the fact yet.
     * Local node would try to guess if the new IPs has been committed in cluster domain in some failure scenarios.
     * 4. CLUSTERACK_CLUSTERPERSISTENT
     * Every node knows the new IPs has been persisted in cluster domain and get the same acknowledgement from others.
     * During next reboot, local node would commit the new IPs directly at this status.
     * 5. LOCAL_SUCCEED (Set after reboot)
     * New IP has taken effect in local node.
     * The whole procedure would be set to SUCCEED when all the nodes' status are set to LOCAL_SUCCEED
     * Each node would go to next status only if all the cluster nodes are at least in the same status.
     * 
     * @throws Exception
     */
    private void driveIpReconfigStateMachine() throws Exception {
        log.info("driving ipreconfig state machine ...");

        // Start to handle ip reconfig procedure if it is started and not expired.
        String localnode_status_key = String.format(IpReconfigConstants.CONFIG_NODESTATUS_KEY, localNodeId);
        IpReconfigConstants.NodeStatus localnode_status = IpReconfigConstants.NodeStatus.valueOf(config.getConfig(localnode_status_key));
        IpReconfigConstants.NodeStatus target_nodestatus = null;

        String base64Encoded_newipinfo = config.getConfig(IpReconfigConstants.CONFIG_IPINFO_KEY);
        newIpinfo = ClusterIpInfo.deserialize(Base64.decodeBase64(base64Encoded_newipinfo.getBytes(UTF_8)));
        if (!newIpinfo.equals(localIpinfo)) {
            // NewIP has not been applied yet, in the process of syncing among all nodes.
            switch (localnode_status) {
                case None:
                    // NewIP is just set in ZK.
                    IpReconfigUtil.writeIpinfoFile(localIpinfo, IpReconfigConstants.OLDIP_PATH);
                    IpReconfigUtil.writeIpinfoFile(newIpinfo, IpReconfigConstants.NEWIP_PATH);

                    String strExpirationTime = config.getConfig(IpReconfigConstants.CONFIG_EXPIRATION_KEY);
                    FileUtils.writeObjectToFile(strExpirationTime, IpReconfigConstants.NEWIP_EXPIRATION);

                    target_nodestatus = IpReconfigConstants.NodeStatus.LOCALAWARE_LOCALPERSISTENT;
                    setNodeStatus(target_nodestatus.toString());
                    break;
                case LOCALAWARE_LOCALPERSISTENT:
                    // Local node persists the NewIP, while it has no idea of other nodes' status.
                    target_nodestatus = IpReconfigConstants.NodeStatus.LOCALAWARE_CLUSTERPERSISTENT;
                    if (isReadyForNextStatus(localnode_status, target_nodestatus)) {
                        setNodeStatus(target_nodestatus.toString());
                    }
                    break;
                case LOCALAWARE_CLUSTERPERSISTENT:
                    // Local node is aware of NewIP is persisted in cluster domain, but has no idea if other nodes know the fact.
                    target_nodestatus = IpReconfigConstants.NodeStatus.CLUSTERACK_CLUSTERPERSISTENT;
                    if (isReadyForNextStatus(localnode_status, target_nodestatus)) {
                        setNodeStatus(target_nodestatus.toString());
                    }
                    break;
                case CLUSTERACK_CLUSTERPERSISTENT:
                    // Every node knows the new IPs has been persisted in cluster domain and get the same acknowledgement from others.
                    target_nodestatus = IpReconfigConstants.NodeStatus.LOCAL_SUCCEED;
                    if (isReadyForNextStatus(localnode_status, target_nodestatus)) {
                        // After all nodes are in ClusterACK_ClusterPersistent, we will
                        // 1. powoff cluster
                        // 2. commit new IP during next reboot
                        // 3. set local node status to "Local_Succed"
                        // 4. set total status to "Succeed" when all nodes are "Local_Succeed".
                        haltNode(config.getConfig(IpReconfigConstants.CONFIG_POST_OPERATION_KEY));
                    }
                    break;
                default:
                    log.error("unexpected node status before reboot: {}", localnode_status);
                    // if installer is used before the procedure finished, we will get unexpected node status
                    setFailed(IpReconfigConstants.ERRSTR_MANUAL_CONFIGURED);
                    break;
            }
        } else {
            // newIP has taken effect, we need to set procedure status SUCCEED after all nodes got new IP.
            switch (localnode_status) {
                case LOCALAWARE_CLUSTERPERSISTENT:
                    // The current node confirms there are quorum nodes are using new IPs and then
                    // adopt the newIPs during bootstrap. We need to jump it to ClusterACK_ClusterPersistent status
                    log.info("jumping to ClusterACK_ClusterPersistent status...");
                    setNodeStatus(IpReconfigConstants.NodeStatus.CLUSTERACK_CLUSTERPERSISTENT.toString());
                    break;
                case CLUSTERACK_CLUSTERPERSISTENT:
                    // set Local_Succeed after
                    // 1. local node already adopted new IP
                    // 2. Every node knows the new IPs has been persisted in cluster domain and get the same acknowledgement from others.
                    setNodeStatus(IpReconfigConstants.NodeStatus.LOCAL_SUCCEED.toString());
                    break;
                case LOCAL_SUCCEED:
                    // New IP has taken effect in local node, set total status to "Succeed" when all nodes are "Local_Succeed".
                    target_nodestatus = IpReconfigConstants.NodeStatus.CLUSTER_SUCCEED;
                    if (isReadyForNextStatus(localnode_status, target_nodestatus)) {
                        assureIPConsistent();
                        setSucceed();
                    }
                    break;
                default:
                    log.error("unexpected node status after reboot: {}", localnode_status);
                    // if installer is used before the procedure finished, we will get unexpected node status
                    setFailed(IpReconfigConstants.ERRSTR_MANUAL_CONFIGURED);
                    break;
            }
        }
    }

    /**
     * check the current node is read to go to next status
     * 
     * @param currNodeStatus
     * @param targetNodeStatus
     * @return
     */
    private boolean isReadyForNextStatus(IpReconfigConstants.NodeStatus currNodeStatus, IpReconfigConstants.NodeStatus targetNodeStatus) {
        boolean bReadyForNextStatus = true;
        for (int i = 1; i <= nodeCount; i++) {
            String node_status_key = String.format(IpReconfigConstants.CONFIG_NODESTATUS_KEY, i);
            IpReconfigConstants.NodeStatus node_status = IpReconfigConstants.NodeStatus.valueOf(config.getConfig(node_status_key));
            if (node_status.ordinal() < currNodeStatus.ordinal()) {
                bReadyForNextStatus = false;
                log.info("local node is not ready to step into next status: {}", targetNodeStatus);
                break;
            }
        }
        if (bReadyForNextStatus) {
            log.info("local node is ready to step into next status: {}", targetNodeStatus);
        }
        return bReadyForNextStatus;
    }

    /**
     * set local node status
     * 
     * @param nodestatus
     * @throws Exception
     */
    private void setNodeStatus(String nodestatus) throws Exception {
        log.info("changing to node status:{}", nodestatus);
        IpReconfigUtil.writeNodeStatusFile(nodestatus);
        persistZKNodeStatus(nodestatus);
    }

    /**
     * persist node status into ZK
     * 
     * @param nodestatus
     * @throws Exception
     */
    private void persistZKNodeStatus(String nodestatus) throws Exception {
        String nodestatus_key = String.format(IpReconfigConstants.CONFIG_NODESTATUS_KEY, localNodeId);
        config.setConfig(nodestatus_key, nodestatus);
        _coordinator.getCoordinatorClient().persistServiceConfiguration(config);
    }

    /**
     * Set ipreconfig status as successful and set end time
     * 
     * @throws Exception
     */
    private void setSucceed() throws Exception {
        log.info("Succeed to reconfig cluster ip!");
        setStatus(ClusterNetworkReconfigStatus.Status.SUCCEED);
        FileUtils.deleteFile(IpReconfigConstants.NODESTATUS_PATH);
    }

    /**
     * Set ipreconfig status as failed and error message
     * 
     * @param error
     * @throws Exception
     */
    private void setFailed(String error) throws Exception {
        log.error("ipreconfig failed. Error: {}", error);
        config.setConfig(IpReconfigConstants.CONFIG_STATUS_KEY, ClusterNetworkReconfigStatus.Status.FAILED.toString());
        config.setConfig(IpReconfigConstants.CONFIG_ERROR_KEY, error);
        _coordinator.getCoordinatorClient().persistServiceConfiguration(config);
    }

    /**
     * set final status for ip reconfig procedure
     * 
     * @param reconfigStatus
     * @throws Exception
     */
    private void setStatus(ClusterNetworkReconfigStatus.Status reconfigStatus) throws Exception {
        config.setConfig(IpReconfigConstants.CONFIG_STATUS_KEY, reconfigStatus.toString());
        _coordinator.getCoordinatorClient().persistServiceConfiguration(config);
    }

    /**
     * set final error info for ip reconfig procedure
     * 
     * @param error
     * @throws Exception
     */
    private void setError(String error) throws Exception {
        config.setConfig(IpReconfigConstants.CONFIG_ERROR_KEY, error);
        _coordinator.getCoordinatorClient().persistServiceConfiguration(config);
    }

    /**
     * check if ip reconfiguraiton is started
     * 
     * @param config
     * @return true/false
     */
    private boolean isStarted(Configuration config) {
        String status = config.getConfig(IpReconfigConstants.CONFIG_STATUS_KEY);
        return status.equals(ClusterNetworkReconfigStatus.Status.STARTED.toString());
    }

    /**
     * check if ip reconfiguraiton is succeed
     * 
     * @param config
     * @return true/false
     */
    private boolean isSucceed(Configuration config) {
        String status = config.getConfig(IpReconfigConstants.CONFIG_STATUS_KEY);
        return status.equals(ClusterNetworkReconfigStatus.Status.SUCCEED.toString());
    }

    /**
     * check if ip reconfiguraiton is failed
     * 
     * @param config
     * @return true/false
     */
    private boolean isFailed(Configuration config) {
        String status = config.getConfig(IpReconfigConstants.CONFIG_STATUS_KEY);
        return status.equals(ClusterNetworkReconfigStatus.Status.FAILED.toString());
    }

    /**
     * Check if cluster is under ip reconfiguration procedure
     * 
     * @return
     */
    public synchronized boolean underIpReconfiguration() {
        config = _coordinator.getCoordinatorClient().queryConfiguration(IpReconfigConstants.CONFIG_KIND, IpReconfigConstants.CONFIG_ID);
        if (config != null && isStarted(config)) {
            return true;
        }
        return false;
    }

    /**
     * the listener class to listen the ipconfig node change.
     */
    class IpReconfigListener implements NodeListener {
        public String getPath() {
            String path = String.format("/config/%s/%s", IpReconfigConstants.CONFIG_KIND, IpReconfigConstants.CONFIG_ID);
            return path;
        }

        /**
         * called when user modify IPs, procedure or node status from ipreconfig point of view
         */
        @Override
        public void nodeChanged() {
            log.info("IpReconfig info/status changed. Waking up the ip reconfig procedure...");
            wakeup();
        }

        /**
         * called when connection state changed.
         */
        @Override
        public void connectionStateChanged(State state) {
            log.info("ipreconfig connection state changed to {}", state);
            if (state.equals(State.CONNECTED)) {
                log.info("Curator (re)connected. Waking up the ip reconfig procedure...");
                wakeup();
            }
        }
    }

    private synchronized void await() throws InterruptedException {
        this.wait();
    }

    private synchronized void wakeup() {
        this.notifyAll();
    }

    /**
     * Poweroff/Reboot the node
     */
    public void haltNode(String postOperation) throws Exception {
        Thread.sleep(6 * 1000);
        if (postOperation.equals("poweroff")) {
            localRepository.poweroff();
        } else {
            localRepository.reboot();
        }
    }

    /**
     * Launch polling executor which will handle
     * 1. Procedure expiration scenario
     * 2. Underlying missed notification event
     * 
     * @throws Exception
     */
    private void startPollExecutor() throws Exception {
        if (_pollExecutor != null && !_pollExecutor.isTerminated()) {
            return;
        }

        log.info("starting polling executor ...");
        _pollExecutor = new NamedThreadPoolExecutor(IpReconfigManager.class.getSimpleName() + "_Polling", 1);
        _pollExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        synchronized (this) {
                            // Check if procedure expired periodically, set failed and exit polling thread.
                            config = _coordinator.getCoordinatorClient().queryConfiguration(IpReconfigConstants.CONFIG_KIND,
                                    IpReconfigConstants.CONFIG_ID);
                            if (config != null && isStarted(config)) {
                                expiration_time = Long.valueOf(config.getConfig(IpReconfigConstants.CONFIG_EXPIRATION_KEY));
                                if (expiration_time < System.currentTimeMillis()) {
                                    setFailed(IpReconfigConstants.ERRSTR_TIMEOUT);
                                    return;
                                }
                            }
                        }

                        // NodeCacheListener recipe might miss event occasionally.
                        // We could wakeup main loop to drive the procedure periodically.
                        Thread.sleep(POLL_INTERVAL);
                        wakeup();
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        });
    }

    /**
     * Stop polling executor
     * 
     * @throws Exception
     */
    private void stopPollExecutor() throws Exception {
        if (_pollExecutor != null && !_pollExecutor.isTerminated()) {
            log.info("stopping polling executor ...");
            _pollExecutor.shutdownNow();
        }
        _pollExecutor = null;
    }

    /**
     * User might rollback even before the last ip reconfigured is finished.
     * So we always need to set the last ip reconfiguration status to failure to avoid
     * that ip reconfiguration is triggered again.
     * 
     * @return true if user is trying to rollback ip conf.
     */
    private boolean isRollback() {
        try {
            if (FileUtils.exists(IpReconfigConstants.NODESTATUS_PATH)) {
                IpReconfigConstants.NodeStatus localnode_status =
                        IpReconfigConstants.NodeStatus.valueOf(IpReconfigUtil.readNodeStatusFile());
                if (localnode_status == IpReconfigConstants.NodeStatus.LOCAL_ROLLBACK) {
                    log.info("User is trying to rollback last ip reconfiguration.");
                    setFailed(IpReconfigConstants.ERRSTR_ROLLBACK);
                    FileUtils.deleteFile(IpReconfigConstants.NODESTATUS_PATH);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Failed to check if user is trying to rollback.", e);
        }
        return false;
    }

    /**
     * trigger ip reconfiguration
     * 
     * @param clusterIpInfo
     * @param postOperation
     * @throws Exception
     */
    public void triggerIpReconfig(ClusterIpInfo clusterIpInfo, String postOperation) throws Exception {
        // 1. validate cluster ip reconfig parameter
        validateParameter(clusterIpInfo, postOperation);

        // 2. check env
        sanityCheckEnv();

        // 3. check if there is already another ip reconfiguration procedure is in progress
        synchronized (this) {
            config = _coordinator.getCoordinatorClient().queryConfiguration(IpReconfigConstants.CONFIG_KIND, IpReconfigConstants.CONFIG_ID);
            if (config != null) {
                if (isStarted(config)) {
                    String errmsg = "Cluster is already under ip reconfiguration.";
                    log.error(errmsg);
                    throw new IllegalStateException(errmsg);
                }
            }
        }

        // 4. Initial ip reconfig procedure
        initIpReconfig(clusterIpInfo, postOperation);
    }

    /**
     * Sanity check if cluster env is qualified
     */
    private void sanityCheckEnv() throws Exception {
        // 1. check if platform is supported
        checkPlatform();

        // 2. check if cluster is in stable status
        checkClusterStatus();

        // 3. check if cluster is in GEO env
        if (!VdcUtil.isLocalVdcSingleSite()) {
            String errmsg = "Cluster is in GEO env.";
            log.error(errmsg);
            throw new IllegalStateException(errmsg);
        }
    }

    /**
     * Valide cluster ip reconfig param
     * 
     * @param clusterIpInfo
     * @param postOperation
     * @return error msg
     * @throws Exception
     */
    private void validateParameter(ClusterIpInfo clusterIpInfo, String postOperation) throws Exception {
        boolean bValid = true;
        String errmsg = "";

        if (!postOperation.equals("poweroff") && !postOperation.equals("reboot")) {
            bValid = false;
            errmsg = "post operation is invalid.";
        }

        errmsg = clusterIpInfo.validate(nodeCount);
        if (!errmsg.isEmpty()) {
            bValid = false;
        }

        if (!bValid) {
            throw new IllegalStateException(errmsg);
        }
    }

    /**
     * Check if platform is supported
     */
    private void checkPlatform() {
        if (PlatformUtils.isVMwareVapp()) {
            log.info("Platform(VApp) is unsupported for ip reconfiguraiton");
            throw new UnsupportedOperationException("VApp is unsupported for ip reconfiguration");
        }
        if (PlatformUtils.hasMultipleSites()) {
            log.info("Multiple sites env is unsupported for ip reconfiguraiton");
            throw new UnsupportedOperationException("Multiple sites env is unsupported for ip reconfiguration");
        }
    }

    /**
     * Check if cluster is in health status
     */
    private void checkClusterStatus() throws Exception {
        ClusterInfo.ClusterState controlNodeState = _coordinator.getCoordinatorClient().getControlNodesState();
        if (controlNodeState == null ||
                !controlNodeState.equals(ClusterInfo.ClusterState.STABLE)) {
            String errmsg = "Cluster is not stable.";
            log.error(errmsg);
            throw new IllegalStateException(errmsg);
        }
    }

    /**
     * Initiate ip reconfig procedure by creating ipreconfig config znode in ZK
     * The config znode include:
     * ipinfo
     * procedure status
     * each node's status
     * expiration time for the procedure
     * 
     * @param clusterIpInfo The new cluster ip info
     * @param postOperation
     * @throws Exception
     */
    private void initIpReconfig(ClusterIpInfo clusterIpInfo, String postOperation) throws Exception {
        ClusterIpInfo ipinfo = new ClusterIpInfo(clusterIpInfo.getIpv4Setting(), clusterIpInfo.getIpv6Setting());
        log.info("Initiating ip reconfiguraton procedure {}", ipinfo.toString());

        ConfigurationImpl cfg = new ConfigurationImpl();
        cfg.setKind(IpReconfigConstants.CONFIG_KIND);
        cfg.setId(IpReconfigConstants.CONFIG_ID);
        cfg.setConfig(IpReconfigConstants.CONFIG_IPINFO_KEY, new String(Base64.encodeBase64(ipinfo.serialize()), UTF_8));
        cfg.setConfig(IpReconfigConstants.CONFIG_STATUS_KEY, ClusterNetworkReconfigStatus.Status.STARTED.toString());
        for (int i = 1; i <= ipinfo.getIpv4Setting().getNetworkAddrs().size(); i++) {
            String nodestatus_key = String.format(IpReconfigConstants.CONFIG_NODESTATUS_KEY, i);
            cfg.setConfig(nodestatus_key, IpReconfigConstants.NodeStatus.None.toString());
        }

        // Set ip reconfiguration timeout to 1 day
        // 1. For poweroff case, user might need to change subnet or even migrate VMs,
        // thus we should set timeout longer for the procedure to be finished.
        // Later we should extend API for user to set desired expiration time
        // 2. For directly reboot case, it would be better to set longer timeout as well to cover
        // underlying unexpected node bootstrap issue which needs manual recovery etc.
        expiration_time = System.currentTimeMillis() + IPRECONFIG_TIMEOUT;

        cfg.setConfig(IpReconfigConstants.CONFIG_EXPIRATION_KEY, String.valueOf(expiration_time));
        cfg.setConfig(IpReconfigConstants.CONFIG_POST_OPERATION_KEY, postOperation);
        config = cfg;

        _coordinator.getCoordinatorClient().persistServiceConfiguration(config);
    }

    /**
     * query current status&error for ip reconfig procedure
     * 
     * @return
     * @throws Exception
     */
    public synchronized ClusterNetworkReconfigStatus queryClusterNetworkReconfigStatus() throws Exception {
        ClusterNetworkReconfigStatus ipReconfigStatus = new ClusterNetworkReconfigStatus();
        config = _coordinator.getCoordinatorClient().queryConfiguration(IpReconfigConstants.CONFIG_KIND, IpReconfigConstants.CONFIG_ID);
        if (config != null) {

            ClusterNetworkReconfigStatus.Status status = ClusterNetworkReconfigStatus.Status.valueOf(config
                    .getConfig(IpReconfigConstants.CONFIG_STATUS_KEY));
            ipReconfigStatus.setStatus(status);
            if (isFailed(config)) {
                String errmsg = config.getConfig(IpReconfigConstants.CONFIG_ERROR_KEY);
                ipReconfigStatus.setMessage(errmsg);
            }
            ipReconfigStatus.setExpiration(config.getConfig(IpReconfigConstants.CONFIG_EXPIRATION_KEY));
        }
        return ipReconfigStatus;
    }

    /**
     * query current cluster ip info
     * 
     * @return
     * @throws Exception
     */
    public ClusterIpInfo queryCurrentClusterIpinfo() throws Exception {
        return localIpinfo;
    }

    /**
     * Assure local site IP info is consistent with that in ZK.
     * (DR/GEO procedures store IP info in ZK even for single site since Yoda.)
     */
    void assureIPConsistent() {
        InterProcessLock lock = null;
        try {
            log.info("Assuring local site IPs are consistent with ZK ...");
            lock = _coordinator.getCoordinatorClient().getLock(UPDATE_ZKIP_LOCK);
            lock.acquire();
            log.info("Got lock for updating local site IPs into ZK ...");

            Site site = drUtil.getLocalSite();
            if (localIpinfo.weakEqual(site.getVip(), site.getHostIPv4AddressMap(), site.getHostIPv6AddressMap())) {
                log.info("local site IPs are consistent with ZK, no need to update.");
                return;
            } else {
                log.info("local site IPs are not consistent with ZK, updating.");
                log.info("    local ipinfo:{}", localIpinfo.toString());
                log.info("    zk ipinfo: vip={}", site.getVip());
                SortedSet<String> nodeIds = new TreeSet<String>(site.getHostIPv4AddressMap().keySet());
                for (String nodeId : nodeIds) {
                    log.info("    {}: ipv4={}", nodeId, site.getHostIPv4AddressMap().get(nodeId));
                    log.info("    {}: ipv6={}", nodeId, site.getHostIPv6AddressMap().get(nodeId));
                }
            }

            if (site.getVip().contains(":")) {
                site.setVip(localIpinfo.getIpv6Setting().getNetworkVip6());
            } else {
                site.setVip(localIpinfo.getIpv4Setting().getNetworkVip());
            }

            Map<String, String> ipv4Addresses = new HashMap<>();
            Map<String, String> ipv6Addresses = new HashMap<>();
            int nodeIndex = 1;
            for (String nodeip : localIpinfo.getIpv4Setting().getNetworkAddrs()) {
                String nodeId;
                nodeId = IpReconfigConstants.VDC_NODE_PREFIX + nodeIndex++;
                ipv4Addresses.put(nodeId, nodeip);
            }
            nodeIndex = 1;
            for (String nodeip : localIpinfo.getIpv6Setting().getNetworkAddrs()) {
                String nodeId;
                nodeId = IpReconfigConstants.VDC_NODE_PREFIX + nodeIndex++;
                ipv6Addresses.put(nodeId, nodeip);
            }
            site.setHostIPv4AddressMap(ipv4Addresses);
            site.setHostIPv6AddressMap(ipv6Addresses);

            _coordinator.getCoordinatorClient().persistServiceConfiguration(site.toConfiguration());

            // wake up syssvc to regenerate configurations
            drUtil.updateVdcTargetVersion(_coordinator.getCoordinatorClient().getSiteId(), SiteInfo.IP_OP_CHANGE, System.currentTimeMillis());

            log.info("Finished update local site IPs into ZK");
        } catch (Exception e) {
            log.warn("Unexpected exception during updating local site IPs into ZK", e);
        } finally {
            if (lock != null) {
                try {
                    lock.release();
                } catch (Exception e) {
                    log.warn("Unexpected exception during unlocking update_zkip lock", e);
                }
            }
        }
    }
}
