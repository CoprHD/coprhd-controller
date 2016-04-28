/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.system;

import com.emc.vipr.client.impl.RestClient;
import static com.emc.vipr.client.impl.jersey.ClientUtils.addQueryParam;
import static com.emc.vipr.client.system.impl.PathConstants.CLUSER_IP_INFO_URL;
import static com.emc.vipr.client.system.impl.PathConstants.CLUSER_IP_RECONFIG_STATUS_URL;
import static com.emc.vipr.client.system.impl.PathConstants.CLUSER_IP_RECONFIG_URL;
import static com.emc.vipr.client.system.impl.PathConstants.CLUSTER_DB_HEALTH_STATUS_URL;
import static com.emc.vipr.client.system.impl.PathConstants.CLUSTER_NODE_RECOVERY_URL;
import static com.emc.vipr.client.system.impl.PathConstants.CONTROL_POWER_OFF_CLUSTER_URL;
import static com.emc.vipr.client.system.impl.PathConstants.CONTROL_REBOOT_NODE_URL;
import static com.emc.vipr.client.system.impl.PathConstants.CONTROL_RESTART_URL;
import com.emc.vipr.model.sys.ipreconfig.ClusterIpInfo;
import com.emc.vipr.model.sys.ipreconfig.ClusterNetworkReconfigStatus;
import com.emc.vipr.model.sys.recovery.DbRepairStatus;
import com.emc.vipr.model.sys.recovery.RecoveryStatus;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import javax.ws.rs.core.UriBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Control {

    private Logger log = LoggerFactory.getLogger(getClass());

    private static final String NODE_ID_PARAM = "node_id";
    private static final String NODE_NAME_PARAM = "node_name";
    private static final String NAME_PARAM = "name";
    private static final String FORCE_PARAM = "force";
    private static final String FORCE_VALUE = "1";
    private static final String RECONFIG_POWEROFF_PARAM = "postOperation";
    private static final String RECONFIG_POWEROFF_VALUE = "poweroff";

    private RestClient client;

    public Control(RestClient client) {
        this.client = client;
    }

    /**
     * Restart a service on a virtual machine.
     * <p>
     * API Call: POST /control/service/restart
     * 
     * @param nodeId Virtual machine id
     * @param name Service name
     * @deprecated Replaced by
     * @see #restartServiceByNodeId(String, String)
     */
    @Deprecated
    public void restartService(String nodeId, String name) {
        UriBuilder builder = client.uriBuilder(CONTROL_RESTART_URL);
        addQueryParam(builder, NODE_ID_PARAM, nodeId);
        addQueryParam(builder, NAME_PARAM, name);
        client.postURI(String.class, builder.build());
    }

    /**
     * Restart a service on a virtual machine by node id.
     * <p>
     * API Call: POST /control/service/restart
     *
     * @param nodeId Virtual machine id
     * @param name Service name
     */
    public void restartServiceByNodeId(String nodeId, String name) {
        UriBuilder builder = client.uriBuilder(CONTROL_RESTART_URL);
        addQueryParam(builder, NODE_ID_PARAM, nodeId);
        addQueryParam(builder, NAME_PARAM, name);
        client.postURI(String.class, builder.build());
    }

    /**
     * Restart a service on a virtual machine by node name.
     * <p>
     * API Call: POST /control/service/restart
     *
     * @param nodeName Virtual machine name
     * @param name Service name
     */
    public void restartServiceByNodeName(String nodeName, String name) {
        UriBuilder builder = client.uriBuilder(CONTROL_RESTART_URL);
        addQueryParam(builder, NODE_NAME_PARAM, nodeName);
        addQueryParam(builder, NAME_PARAM, name);
        client.postURI(String.class, builder.build());
    }

    /**
     * Reboot a virtual machine.
     * <p>
     * API Call: POST /control/node/reboot
     * 
     * @param nodeId Virtual machine id
     * @deprecated Replaced by
     * @see #rebootNodeByNodeId(String)
     */
    @Deprecated
    public void rebootNode(String nodeId) {
        UriBuilder builder = client.uriBuilder(CONTROL_REBOOT_NODE_URL);
        addQueryParam(builder, NODE_ID_PARAM, nodeId);
        client.postURI(String.class, builder.build());
    }

    /**
     * Reboot a virtual machine by node id.
     * <p>
     * API Call: POST /control/node/reboot
     *
     * @param nodeId Virtual machine id
     */
    public void rebootNodeByNodeId(String nodeId) {
        UriBuilder builder = client.uriBuilder(CONTROL_REBOOT_NODE_URL);
        addQueryParam(builder, NODE_ID_PARAM, nodeId);
        client.postURI(String.class, builder.build());
    }

    /**
     * Reboot a virtual machine by node name.
     * <p>
     * API Call: POST /control/node/reboot
     *
     * @param nodeName Virtual machine name
     */
    public void rebootNodeByNodeName(String nodeName) {
        UriBuilder builder = client.uriBuilder(CONTROL_REBOOT_NODE_URL);
        addQueryParam(builder, NODE_NAME_PARAM, nodeName);
        client.postURI(String.class, builder.build());
    }

    /**
     * Powers off all nodes in a ViPR cluster.
     * <p>
     * API Call: POST /control/cluster/poweroff
     */
    public void powerOffCluster() {
        powerOffCluster(false);
    }

    /**
     * Powers off all nodes in a ViPR cluster.
     * <p>
     * API Call: POST /control/cluster/poweroff
     * 
     * @param force Set to true to force poweroff
     */
    public void powerOffCluster(boolean force) {
        UriBuilder builder = client.uriBuilder(CONTROL_POWER_OFF_CLUSTER_URL);
        if (force) {
            addQueryParam(builder, FORCE_PARAM, FORCE_VALUE);
        }
        client.postURI(String.class, builder.build());
    }

    /**
     * Start the minority node recovery process
     * <p>
     * API Call: POST /cluster/recovery
     * 
     */
    public void recoverMinorityNode() {
        UriBuilder builder = client.uriBuilder(CLUSTER_NODE_RECOVERY_URL);
        client.postURI(String.class, builder.build());
    }

    /**
     * Gets status of recoverMinotiryNode.
     * <p>
     * Cluster recovery status: Current status of the cluster recovery process INIT - triggering recover PREPARING - preparing recovery
     * REPAIRING - repairing db inconsistency.
     * <p>
     * SYNCING - new node is syncing data FAILED - recovery failed DONE - recovery successful
     * <p>
     * API Call: GET /cluster/recovery
     * 
     * @return The Recovery Status
     */
    public RecoveryStatus getRecoveryStatus() {
        RecoveryStatus status = null;
        UriBuilder builder = client.uriBuilder(CLUSTER_NODE_RECOVERY_URL);

        try {
            status = client.getURI(RecoveryStatus.class, builder.build());
        } catch (UniformInterfaceException e) {
            log.warn("Issue with retrieving response from client.", e);
            status = new RecoveryStatus();
        }

        return status;
    }

    /**
     * Gets current IP configuration information of the cluster
     * <p>
     * API Call: GET /cluster/ipinfo
     * 
     * @return ClusterIpInfo
     */
    public ClusterIpInfo getClusterIpinfo() {
        UriBuilder builder = client.uriBuilder(CLUSER_IP_INFO_URL);
        return client.getURI(ClusterIpInfo.class, builder.build());
    }

    /**
     * Triggers IP reconfiguration with provided IPs. Returns true is request accepted, false otherwise
     * <p>
     * API Call: POST /cluster/ipreconfig
     * 
     * @param clusterIpInfo
     * @param powerOff
     * @return boolean
     */
    public boolean reconfigClusterIps(ClusterIpInfo clusterIpInfo, boolean powerOff) {
        UriBuilder builder = client.uriBuilder(CLUSER_IP_RECONFIG_URL);
        if (powerOff) {
            addQueryParam(builder, RECONFIG_POWEROFF_PARAM, RECONFIG_POWEROFF_VALUE);
        }
        ClientResponse response = client.postURI(ClientResponse.class, clusterIpInfo, builder.build());

        return (response.getClientResponseStatus() == ClientResponse.Status.ACCEPTED);
    }

    /**
     * Gets IP reconfiguration status of the cluster
     * <p>
     * API Call: GET /cluster/ipreconfig_status
     * 
     * @return IpReconfigStatus
     */
    public ClusterNetworkReconfigStatus getClusterIpReconfigStatus() {
        UriBuilder builder = client.uriBuilder(CLUSER_IP_RECONFIG_STATUS_URL);
        return client.getURI(ClusterNetworkReconfigStatus.class, builder.build());
    }

    /**
     * Gets DB health status of cluster
     * API call: GET /cluster/dbrepair-status
     * 
     */
    public DbRepairStatus getdbhealth() {
        return client.get(DbRepairStatus.class, CLUSTER_DB_HEALTH_STATUS_URL);
    }
}
