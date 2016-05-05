/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.ipreconfig;

import com.emc.storageos.model.property.PropertyConstants;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.services.util.FileUtils;
import com.emc.storageos.services.util.PlatformUtils;
import com.emc.storageos.systemservices.impl.ipreconfig.IpReconfigConstants;
import com.emc.storageos.systemservices.impl.ipreconfig.IpReconfigUtil;
import com.emc.vipr.model.sys.ipreconfig.ClusterIpInfo;
import com.emc.vipr.model.sys.ipreconfig.SiteIpInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.Exception;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Ip reconfiguration tool which could
 * 1. Commit new IPs to the ovfenv partition
 * 2. Rollback to original IPs to the ovfenv partition
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final int CMD_TIMEOUT = 10 * 1000;
    public static String active_site_id;
    public static String my_site_id;

    private static void usage() {
        System.out.println("Usage: ");
        System.out.println("ipreconfig <active_site_id> <my_site_id> [commit|rollback]");
        System.exit(-1);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            usage();
        }

        try {
            active_site_id = args[0];
            my_site_id = args[1];
            log.info("active site id:{}, my site id:{}", active_site_id, my_site_id);

            if (args[2].equals("commit")) {
                commitNewIP();
            } else if (args[2].equals("rollback")) {
                rollbackToOldIP();
            } else {
                usage();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private static void commitNewIP() throws Exception {
        if (!FileUtils.exists(IpReconfigConstants.NEWIP_PATH)) {
            log.info("No new ip file {} for applying. Exit...", IpReconfigConstants.NEWIP_PATH);
            return;
        }

        String strExpirationTime = (String) FileUtils.readObjectFromFile(IpReconfigConstants.NEWIP_EXPIRATION);
        if (System.currentTimeMillis() >= Long.valueOf(strExpirationTime)) {
            log.info("Ip reconfiguration procedure has expired.  Will not applying new IPs info. Exit...");
            return;
        }

        // 1. get current ip info from ovfenv device
        Map<String, String> ovfenvprop = PlatformUtils.getOvfenvProperties();
        String node_id = ovfenvprop.get(PropertyConstants.NODE_ID_KEY);

        // 2. get new ip info from local file
        ClusterIpInfo newIpinfo = IpReconfigUtil.readIpinfoFile(IpReconfigConstants.NEWIP_PATH);

        // 3. load node status from local file
        IpReconfigConstants.NodeStatus localnode_status =
                IpReconfigConstants.NodeStatus.valueOf(IpReconfigUtil.readNodeStatusFile());
        log.info("Local node's status is {}.", localnode_status);
        switch (localnode_status) {
            case None:
            case LOCALAWARE_LOCALPERSISTENT:
                log.info("NewIP exists, but should not be commited. Exiting...", localnode_status);
                return;
            case LOCALAWARE_CLUSTERPERSISTENT:
                log.info("Trying to connect with new cluster ...", localnode_status);
                handlePossibleJump(newIpinfo, node_id);
                break;
            case CLUSTERACK_CLUSTERPERSISTENT:
                log.info("Trying to commit new IPs directly ...", localnode_status);
                applyIpinfo(newIpinfo, node_id, true);
                break;
            default:
                log.info("No need to handle status {}...", localnode_status);
                return;
        }
    }

    private static void rollbackToOldIP() throws Exception {
        if (!FileUtils.exists(IpReconfigConstants.OLDIP_PATH)) {
            log.info("No old ip file {} for rollback. Exit...", IpReconfigConstants.OLDIP_PATH);
            return;
        }

        // 1. get current ip info from ovfenv device
        Map<String, String> ovfenvprop = PlatformUtils.getOvfenvProperties();
        String node_id = ovfenvprop.get(PropertyConstants.NODE_ID_KEY);

        // 2. get old ip info from local file
        ClusterIpInfo oldIpinfo = IpReconfigUtil.readIpinfoFile(IpReconfigConstants.OLDIP_PATH);

        // 3. apply old ipinfo
        applyIpinfo(oldIpinfo, node_id, false);
    }

    private static void applyIpinfo(ClusterIpInfo ipinfo, String nodeid, boolean bNewIp) throws Exception {
        log.info("applying ip info: {}", ipinfo.toString());
        String isoFilePath = "/tmp/ovf-env.iso";
        File isoFile = new File(isoFilePath);
        try {
            int my_site_index = Integer.valueOf(my_site_id.substring(PropertyConstants.SITE_SHORTID_PREFIX.length()));
            SiteIpInfo siteIpInfo = ipinfo.getSiteIpInfoMap().get(String.format(PropertyConstants.IPPROP_PREFIX, my_site_index));
            if (siteIpInfo == null) {
                log.error("Failed to find new ip info for the current site.");
            }

            String tmpstr = PlatformUtils.genOvfenvPropertyKVString(siteIpInfo, nodeid);
            log.info("new ovfenv property key-value string is: {}", tmpstr);

            PlatformUtils.genOvfenvIsoImage(tmpstr, isoFilePath);

            String ovfenv_part = PlatformUtils.probeOvfenvPartition();

            Path path = Paths.get(isoFilePath);
            byte[] data = Files.readAllBytes(path);
            FileUtils.writePlainFile(ovfenv_part, data);
            log.info("Succeed to apply the ip info : {} to ovfenv partition", ipinfo.toString());
        } catch (Exception e) {
            log.error("Applying ip info to ovfenv partition failed with exception: {}",
                    e.getMessage());
        } finally {
            isoFile.delete();
        }

        /*TODO: uncomment after test
        if (bNewIp) {
            FileUtils.deleteFile(IpReconfigConstants.NEWIP_PATH);
            FileUtils.deleteFile(IpReconfigConstants.NEWIP_EXPIRATION);
        } else {
            IpReconfigUtil.writeNodeStatusFile(IpReconfigConstants.NodeStatus.LOCAL_ROLLBACK.toString());
            FileUtils.deleteFile(IpReconfigConstants.OLDIP_PATH);
        }
        */
    }

    /**
     * If the local node is in LocalAware_ClusterPersistent status, the cluster might be using either
     * original IPs or the new IPs.
     * The local node should commit new IPs if quorum nodes of cluster (with new IPs) are available.
     * 
     * @param newIpinfo
     * @param node_id
     * @param node_count
     * @throws Exception
     */
    private static void handlePossibleJump(ClusterIpInfo newIpinfo, String node_id) throws Exception {
/* TODO:
        Integer node_id_number = Integer.valueOf(node_id.split("vipr")[1]);
        boolean bIpv4 = true;
        if (newIpinfo.getIpv4Setting().getNetworkNetmask().equals(PropertyConstants.IPV4_ADDR_DEFAULT)) {
            bIpv4 = false;
        }

        log.info("Checking if new IPs had already been commited...");
        for (int i = 1; i < 24; i++) {
            // Here we assume each node would startup with functional network within at most 2m.
            // So we will check new IPs for 24 times (5s interval).
            log.info("Trying to ping other nodes ...");
            int alivenodes = 0;
            for (String network_addr : newIpinfo.getIpv4Setting().getNetworkAddrs()) {
                if (ping(network_addr, bIpv4) == 0) {
                    alivenodes++;
                }
            }

            // do not count local node
            alivenodes--;

            if (alivenodes > node_count / 2) {
                log.info("new IPs have been commited in quorum nodes of the cluster, so committing it in local node...");
                applyIpinfo(newIpinfo, node_id, node_count, true);
                return;
            }
            Thread.sleep(5 * 1000);
        }
        log.info("New IPs seems have NOT been commited in quorum nodes of the cluster, so still using current IPs.");
*/

        return;
    }

    private static int ping(String ip, boolean bIpv4) {
        String cmd = "ping";
        String ipstr = ip;
        if (!bIpv4) {
            cmd += "6";
            ipstr += "%eth0";
        }
        final String[] cmds = { cmd, ipstr, "-c", "1" };
        Exec.Result result = Exec.sudo(CMD_TIMEOUT, cmds);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            log.warn("Failed to ping {} with exit value: {}", ip, result.getExitValue());
        }
        return result.getExitValue();
    }
}
