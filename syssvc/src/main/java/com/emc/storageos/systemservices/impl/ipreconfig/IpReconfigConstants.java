/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.ipreconfig;

import com.emc.storageos.coordinator.client.model.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ip Reconfig Constants
 */
public class IpReconfigConstants {
    private static final Logger log = LoggerFactory.getLogger(IpReconfigConstants.class);

    public static final String CONFIG_KIND = "ipreconfig";
    public static final String CONFIG_ID = Constants.GLOBAL_ID;
    public static final String CONFIG_IPINFO_KEY = "ipinfo";
    public static final String CONFIG_STATUS_KEY = "status";
    public static final String CONFIG_ERROR_KEY = "error";
    public static final String VDC_NODE_PREFIX = "node";
    public static final String CONFIG_NODESTATUS_KEY = "node%d_status";
    public static final String CONFIG_EXPIRATION_KEY = "expiration";
    public static final String CONFIG_POST_OPERATION_KEY = "postoperation";

    public static final String IPRECONFIG_PATH = "/data/ipreconfig";
    public static final String NEWIP_PATH = "/data/ipreconfig/newip";
    public static final String OLDIP_PATH = "/data/ipreconfig/oldip";
    public static final String NODESTATUS_PATH = "/data/ipreconfig/node_status";
    public static final String NEWIP_EXPIRATION = "/data/ipreconfig/newip_expiration";

    public static final String ERRSTR_TIMEOUT = "Ip reconfiguration timeout";
    public static final String ERRSTR_ROLLBACK = "User rollback to the original ip configuration";
    public static final String ERRSTR_MANUAL_CONFIGURED = "Network reconfiguration has been interrupted";

    /**
     * Each node's statuses during the whole ip reconfiguration procedure
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
     * 6. LOCAL_ROLLBACK (Set after reboot)
     * User could rollback IP conf via boot menu, ipreconfigutil would set this node status.
     * Syssvc would set the last IP reconfiguraiton procedure failure and clean the node status file.
     */
    public enum NodeStatus {
        None("None"),
        LOCALAWARE_LOCALPERSISTENT("LOCALAWARE_LOCALPERSISTENT"),
        LOCALAWARE_CLUSTERPERSISTENT("LOCALAWARE_CLUSTERPERSISTENT"),
        CLUSTERACK_CLUSTERPERSISTENT("CLUSTERACK_CLUSTERPERSISTENT"),
        LOCAL_SUCCEED("LOCAL_SUCCEED"),
        LOCAL_ROLLBACK("LOCAL_ROLLBACK"),
        CLUSTER_SUCCEED("CLUSTER_SUCCEED");

        private String name;

        private NodeStatus(String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }
    }
}
