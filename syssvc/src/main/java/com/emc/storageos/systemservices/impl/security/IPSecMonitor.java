package com.emc.storageos.systemservices.impl.security;


import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.*;

import static com.emc.storageos.coordinator.client.model.Constants.*;

public class IPSecMonitor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(IPSecMonitor.class);

    public static int IPSEC_CHECK_INTERVAL = 10;  // minutes
    public static int IPSEC_CHECK_INITIAL_DELAY = 5;  // minutes

    public ScheduledExecutorService scheduledExecutorService;

    public void start() throws Exception {
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

    @Override
    public void run() {
        log.info("start checking ipsec connections");
        String[] problemNodes = LocalRepository.getInstance().checkIpsecConnection();
        Map<String, String> latest = getLatestIPSecProperties(problemNodes);

        if (isSyncNeeded(latest)) {
            String latestKey = latest.get(Constants.IPSEC_KEY);
            LocalRepository.getInstance().syncIpsecKeyToLocal(latestKey);
            log.info("synced latest ipsec key to local: " + latestKey);
        } else {
            log.info("local already has latest ipsec key, skip syncing");
        }
    }

    /**
     *
     * @param nodes
     * @return
     */
    private Map<String, String> getLatestIPSecProperties(String[] nodes) {
        Map<String, String> latest = null;

        if (nodes != null && nodes.length != 0) {
            for (String node : nodes) {
                if (StringUtils.isEmpty(node)) {
                    continue;
                }

                Map<String, String> props = LocalRepository.getInstance().getIpsecProperties(node);
                String configVersion = props.get(VDC_CONFIG_VERSION);

                if (latest == null ||
                        compareVdcConfigVersion(configVersion,
                                latest.get(VDC_CONFIG_VERSION)) > 0) {
                    latest = props;
                }

                log.info("checking " + node + ": " + " configVersion=" + configVersion
                    + ", ipsecKey=" + props.get(Constants.IPSEC_KEY)
                    + ", lastestKey=" + latest.get(Constants.IPSEC_KEY));
            }
        }

        return latest;
    }

    private boolean isSyncNeeded(Map<String, String> props) {
        String localIP = getLocalIPAddress();
        Map<String, String> localIpsecProp = LocalRepository.getInstance().getIpsecProperties(localIP);
        if (localIpsecProp.get(IPSEC_KEY).equals(props.get(IPSEC_KEY))) {
            return false;
        } else {
            int result = compareVdcConfigVersion(
                    localIpsecProp.get(VDC_CONFIG_VERSION),
                    props.get(VDC_CONFIG_VERSION));
            if (result < 0) {
                return false;
            } else {
                return true;
            }
        }
    }

    private String getLocalIPAddress() {
        try {
            InetAddress IP = InetAddress.getLocalHost();
            String localIP = IP.getHostAddress();
            log.info("IP of my system is := " + localIP);
            return localIP;
        } catch (Exception ex) {
            log.warn("error in getting local ip: " + ex.getMessage());
            return null;
        }
    }

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
