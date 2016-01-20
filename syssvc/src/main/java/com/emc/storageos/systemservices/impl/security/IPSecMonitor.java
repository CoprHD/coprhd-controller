/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.security;


import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.security.geo.GeoClientCacheManager;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.security.ipsec.IpUtils;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;

import static com.emc.storageos.coordinator.client.model.Constants.*;

public class IPSecMonitor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(IPSecMonitor.class);

    public static int IPSEC_CHECK_INTERVAL = 10;  // minutes
    public static int IPSEC_CHECK_INITIAL_DELAY = 10;  // minutes

    public ScheduledExecutorService scheduledExecutorService;
    private static ApplicationContext appCtx;

    @Autowired
    private GeoClientCacheManager geoClientManager;

    @Autowired
    CoordinatorClient coordinaotr;

    public void start() {
        log.info("start IPSecMonitor.");
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(
                this,
                IPSEC_CHECK_INITIAL_DELAY,
                IPSEC_CHECK_INTERVAL,
                TimeUnit.MINUTES);
        log.info("scheduled IPSecMonitor.");
    }

    public void shutdown() {
        scheduledExecutorService.shutdown();
    }

    public static void setApplicationContext(ApplicationContext ctx) {
        appCtx = ctx;
    }

    public static ApplicationContext getApplicationContext() {
        return appCtx;
    }
    
    @Override
    public void run() {
        try {
            // geo checking
            log.info("the dbclient instance is {}", appCtx.getBean("dbclient"));

            log.info("step 1: start checking ipsec connections");
            String[] problemNodes = LocalRepository.getInstance().checkIpsecConnection();

            if (problemNodes == null || problemNodes.length == 0) {
                log.info("all connections are good, skip ipsec sync step");
                return;
            }
            log.info("problem nodes are: " + Arrays.toString(problemNodes));

            log.info("step 2: get latest ipsec properties of the no connection nodes");
            Map<String, String> latest = getLatestIPSecProperties(problemNodes);
            if (latest == null) {
                log.info("no latest ipsec properties found, skip following check steps");
                return;
            }
            log.info("latest ipsec properties: " + latest.toString());


            log.info("step 3: compare the latest ipsec properties with local, to determine if sync needed");
            if (isSyncNeeded(latest)) {
                String latestKey = latest.get(Constants.IPSEC_KEY);
                String latestStatus = latest.get(Constants.IPSEC_STATUS);
                LocalRepository localRepository = LocalRepository.getInstance();
                log.info("syncing latest properties to local: key=" + latestKey + ", status=" + latestStatus);
                localRepository.syncIpsecKeyToLocal(latestKey);
                localRepository.syncIpsecStatusToLocal(latestStatus);
                log.info("reloading ipsec");
                localRepository.reconfigProperties("ipsec");
                localRepository.reload("ipsec");
            } else {
                log.info("local already has latest ipsec key, skip syncing");
            }
            log.info("step 4: ipsec check finish");

            log.info("step 5: checking ipsec enabled post upgrading");
            initGeoClientManager();
            if (geoClientManager == null) {
                log.info("GeoClientManager instance is not available. Skip checking.");
                return;
            }

            checkIPsecEnabledPostUpgrade();

        } catch (Exception ex) {
            log.warn("error when run ipsec monitor: " + ex.getMessage());
        }
    }

    private void checkIPsecEnabledPostUpgrade() {

    }

    private void initGeoClientManager() {
        if (geoClientManager != null) {
            return;
        }

        geoClientManager = (GeoClientCacheManager) appCtx.getBean("geoClientCache");
        coordinaotr = (CoordinatorClient) appCtx.getBean("coordinator");
        DrUtil drUtil = new DrUtil(coordinaotr);
        String vdcId = drUtil.getLocalVdcShortId();
        GeoServiceClient geoClient = geoClientManager.getGeoClient(vdcId);
        log.info("get geo client " + geoClient);
    }

    /**
     * iterate given nodes, to retrieve ipsec properties from them, and return the newest one.
     *
     * @param nodes
     * @return
     */
    private Map<String, String> getLatestIPSecProperties(String[] nodes) {
        Map<String, String> latest = null;

        if (nodes != null && nodes.length != 0) {
            for (String node : nodes) {
                if (StringUtils.isEmpty(node) || node.trim().length() == 0) {
                    continue;
                }

                Map<String, String> props = null;

                // if the node is in the same vdc as local node, through ssh to get its ipsec props,
                // else through https REST API to get ipsec props.
                if (isSameVdcAsLocalNode(node)) {
                    props = LocalRepository.getInstance().getIpsecProperties(node);
                } else {
                    props = getIpsecPropsThroughHTTPS(node);
                }

                if (props == null) {
                    continue;
                }

                String configVersion = props.get(VDC_CONFIG_VERSION);
                if (latest == null ||
                        compareVdcConfigVersion(configVersion,
                                latest.get(VDC_CONFIG_VERSION)) > 0) {
                    latest = props;
                }

                log.info("checking " + node + ": " + " configVersion=" + configVersion
                    + ", ipsecKey=" + props.get(Constants.IPSEC_KEY)
                    + ", ipsecStatus=" + props.get(Constants.IPSEC_STATUS)
                    + ", latestKey=" + latest.get(Constants.IPSEC_KEY)
                    + ", latestStatus=" + latest.get(Constants.IPSEC_STATUS));
            }
        }

        return latest;
    }


    /**
     * check if specified node is in the same VDC as the local node
     *
     * @param node
     * @return
     */
    private boolean isSameVdcAsLocalNode(String node) {
        PropertyInfoExt vdcProps = LocalRepository.getInstance().getVdcPropertyInfo();
        String myVdcId = vdcProps.getProperty("vdc_myid");
        String nodeKey = null;
        for (String key : vdcProps.getAllProperties().keySet()) {
            String value = vdcProps.getProperty(key);
            if (key.contains("ipaddr6")) {
                value = IpUtils.decompressIpv6Address(value);
            }

            if (value !=null && value.toLowerCase().equals(node.toLowerCase())) {
                nodeKey = key;
                break;
            }
        }

        if (nodeKey != null && nodeKey.contains(myVdcId)) {
            log.info(node + " is in the same vdc as localhost");
            return true;
        }

        log.info(node + " is NOT in the same vdc as localhost");
        return false;
    }

    private Map<String, String>  getIpsecPropsThroughHTTPS(String node) {
        return null;
    }

    /**
     * compare local ipsec properties with the specified properties
     *
     * @param props
     *
     * @return  true  - local properties is older, need to sync
     *          false - local properties is newer, NO need to sync
     */
    private boolean isSyncNeeded(Map<String, String> props) {
        if (props == null) {
            return false;
        }

        String localIP = getLocalIPAddress();
        Map<String, String> localIpsecProp = LocalRepository.getInstance().getIpsecProperties(localIP);
        String localKey = localIpsecProp.get(IPSEC_KEY);
        String localStatus = localIpsecProp.get(IPSEC_STATUS);
        log.info("local ipsec properties: ipsecKey=" + localKey
                + ", ipsecStatus=" + localStatus
                + ", vdcConfigVersion=" + localIpsecProp.get(VDC_CONFIG_VERSION));

        boolean bKeyEqual = false;
        boolean bStatusEqual = false;

        if (localKey == null && props.get(IPSEC_KEY) == null) {
            bKeyEqual = true;
        } else if (localKey != null && localKey.equals(props.get(IPSEC_KEY))) {
            bKeyEqual = true;
        }
        log.info("IPsec key equals or not: " + bKeyEqual);

        if (localStatus == null && props.get(IPSEC_STATUS) == null) {
            bStatusEqual = true;
        } else if (localStatus != null && localStatus.equals(props.get(IPSEC_STATUS))) {
            bStatusEqual = true;
        }
        log.info("IPsec status equals or not: " + bStatusEqual);

        if (bKeyEqual && bStatusEqual) {
            return false;
        }

        int result = compareVdcConfigVersion(
                localIpsecProp.get(VDC_CONFIG_VERSION),
                props.get(VDC_CONFIG_VERSION));
        if (result > 0) {
            return false;
        } else {
            return true;
        }

    }

    /**
     * get local ip address
     *
     * @return local ip string
     */
    private String getLocalIPAddress() {
        try {
            InetAddress IP = InetAddress.getLocalHost();
            String localIP = IP.getHostAddress();
            if(IP instanceof Inet6Address) {
                localIP = IpUtils.decompressIpv6Address(localIP);
            }
            log.info("IP of my system is : " + localIP);
            return localIP;
        } catch (Exception ex) {
            log.warn("error in getting local ip: " + ex.getMessage());
            return null;
        }
    }

    /**
     * compare vdc config version
     * @param left
     * @param right
     *
     * @return   (int)left - (int)right
     */
    private int compareVdcConfigVersion(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }

        if (left == null && right != null) {
            return -1;
        }

        if (left != null && right == null) {
            return 1;
        }

        return (int)(Long.parseLong(left) - Long.parseLong(right));
    }
}
