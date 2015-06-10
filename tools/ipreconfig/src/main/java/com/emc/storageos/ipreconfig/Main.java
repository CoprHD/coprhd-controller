/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.ipreconfig;

import com.emc.storageos.model.property.PropertyConstants;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.services.util.FileUtils;
import com.emc.storageos.services.util.PlatformUtils;
import com.emc.storageos.systemservices.impl.ipreconfig.IpReconfigConstants;
import com.emc.storageos.systemservices.impl.ipreconfig.IpReconfigUtil;
import com.emc.vipr.model.sys.ipreconfig.ClusterIpInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Exception;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

/**
 * Ip reconfiguration tool which could
 * 1. Commit new IPs to the ovfenv partition
  * 2. Rollback to original IPs to the ovfenv partition
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final int CMD_TIMEOUT = 10 * 1000;

    //it's the offset of the disk4 where the data starts
    private static final int ISO_HEADER_LENGTH = 43008;
    private static final int OVF_ENV_FILE_SIZE= 4096;

    private static void usage() {
        System.out.println("Usage: ");
        System.out.println("ipreconfig [commit|rollback]");
        System.exit(-1);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            usage();
        }

        try {
            if (args[0].equals("commit")) {
                commitNewIP();
            } else if ( args[0].equals("rollback") ) {
                rollbackToOldIP();
            } else {
                usage();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private static void commitNewIP() throws Exception{
        if(!FileUtils.exists(IpReconfigConstants.NEWIP_PATH)) {
            log.info("No new ip file {} for applying. Exit...",IpReconfigConstants.NEWIP_PATH);
            return;
        }

        String strExpirationTime = (String)FileUtils.readObjectFromFile(IpReconfigConstants.NEWIP_EXPIRATION);
        if ( System.currentTimeMillis() >= Long.valueOf(strExpirationTime) ) {
            log.info("Ip reconfiguration procedure has expired.  Will not applying new IPs info. Exit...");
            return;
        }

        // 1. get current ip info from ovfenv device
        Map<String, String> ovfenvprop = PlatformUtils.getOvfenvProperties();
        String node_id = ovfenvprop.get(PropertyConstants.NODE_ID_KEY);
        int node_count = Integer.valueOf(ovfenvprop.get(PropertyConstants.NODE_COUNT_KEY));

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
                handlePossibleJump(newIpinfo, node_id, node_count);
                break;
            case CLUSTERACK_CLUSTERPERSISTENT:
                log.info("Trying to commit new IPs directly ...", localnode_status);
                applyIpinfo(newIpinfo, node_id, node_count, true);
                break;
            default:
                return;
        }
    }

    private static void rollbackToOldIP() throws Exception{
        if(!FileUtils.exists(IpReconfigConstants.OLDIP_PATH)) {
            log.info("No old ip file {} for rollback. Exit...",IpReconfigConstants.OLDIP_PATH);
            return;
        }

        // 1. get current ip info from ovfenv device
        Map<String, String> ovfenvprop = PlatformUtils.getOvfenvProperties();
        String node_id = ovfenvprop.get(PropertyConstants.NODE_ID_KEY);
        int node_count = Integer.valueOf(ovfenvprop.get(PropertyConstants.NODE_COUNT_KEY));

        // 2. get old ip info from local file
        ClusterIpInfo oldIpinfo = IpReconfigUtil.readIpinfoFile(IpReconfigConstants.OLDIP_PATH);

        // 3. apply old ipinfo
        applyIpinfo(oldIpinfo, node_id, node_count, false);
    }

    private static void applyIpinfo(ClusterIpInfo ipinfo, String nodeid, int node_count, boolean bNewIp) throws Exception{
        log.info("applying ip info: {}", ipinfo.toString());
        try {
            byte[] data= new byte[OVF_ENV_FILE_SIZE];
            Arrays.fill(data, (byte)' ');
            ByteBuffer buf = ByteBuffer.wrap(data);

            String ovfenv_part = PlatformUtils.probeOvfenvPartition();
            String tmpstr;

            /* If current node is upgraded from 2.2.0.1 or earlier, the disk4 is /dev/sdd1
             * and the config file is ovf-env.xml, we generate the configuration in xml format
             * in this case.
             */
            if ( ovfenv_part.equals("/dev/sdd1")) {
                tmpstr= genOvfEnvPropertyXMLString(ipinfo, nodeid, node_count);
                log.info("ovf-env.xml={}", tmpstr);
            }else {
                tmpstr = PlatformUtils.genOvfenvPropertyKVString(ipinfo, nodeid, node_count);
            }
            buf.put(tmpstr.getBytes());
            FileUtils.writePlainFile(ovfenv_part, data, ISO_HEADER_LENGTH);
        } catch (Exception e) {
            log.error("Applying ip info to ovfenv partition failed with exception", e);
        }

        if (bNewIp) {
            FileUtils.deleteFile(IpReconfigConstants.NEWIP_PATH);
            FileUtils.deleteFile(IpReconfigConstants.NEWIP_EXPIRATION);
        } else {
            IpReconfigUtil.writeNodeStatusFile(IpReconfigConstants.NodeStatus.LOCAL_ROLLBACK.toString());
            FileUtils.deleteFile(IpReconfigConstants.OLDIP_PATH);
        }
    }

    private static String genOvfEnvPropertyXMLString(ClusterIpInfo ipInfo, String nodeId, int nodeCount) {
        Map<String, String> ipv4Settings = ipInfo.getIPv4Settings();
        Map<String, String> ipv6Settings = ipInfo.getIPv6Settings();

        StringBuilder builder = new StringBuilder();
        //generate header
        builder.append(String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Environment oe:id=\"%s\">\n" +
                "   <PlatformSection>\n" +
                "      <Kind>Commodity hardware</Kind>\n" +
                "      <Version>No hypervisor</Version>\n" +
                "   </PlatformSection>\n", nodeId));

        //generate IPv4 settings in <PropertySection>
        String propertySection = String.format("   <PropertySection>\n" +
                        "    <Property oe:key=\"%s\" oe:value=\"%s\"/>\n" +
                        "      <Property oe:key=\"%s\" oe:value=\"%s\"/>\n" +
                        "      <Property oe:key=\"%s\" oe:value=\"%s\"/>\n" +
                        "      <Property oe:key=\"%s\" oe:value=\"%s\"/>\n",
                PropertyConstants.NODE_COUNT_KEY, ipv4Settings.get(PropertyConstants.NODE_COUNT_KEY),
                PropertyConstants.IPV4_VIP_KEY, ipv4Settings.get(PropertyConstants.IPV4_VIP_KEY),
                PropertyConstants.IPV4_GATEWAY_KEY, ipv4Settings.get(PropertyConstants.IPV4_GATEWAY_KEY),
                PropertyConstants.IPV4_NETMASK_KEY, ipv4Settings.get(PropertyConstants.IPV4_NETMASK_KEY));

        for (int i=1; i<= nodeCount; i++ ) {
            String key=String.format(PropertyConstants.IPV4_ADDR_KEY, i);
            propertySection+=String.format("      <Property oe:key=\"%s\" oe:value=\"%s\"/>\n", key, ipv4Settings.get(key));
        }

        //generate IPv6 settings in <PropertySection>
        propertySection +=String.format(
                        "      <Property oe:key=\"%s\" oe:value=\"%s\"/>\n" +
                        "      <Property oe:key=\"%s\" oe:value=\"%s\"/>\n" +
                        "      <Property oe:key=\"%s\" oe:value=\"%s\"/>\n",
                PropertyConstants.IPV6_VIP_KEY, ipv6Settings.get(PropertyConstants.IPV6_VIP_KEY),
                PropertyConstants.IPV6_GATEWAY_KEY, ipv6Settings.get(PropertyConstants.IPV6_GATEWAY_KEY),
                PropertyConstants.IPV6_PREFIX_KEY, ipv6Settings.get(PropertyConstants.IPV6_PREFIX_KEY));

        for (int i=1; i<= nodeCount; i++ ) {
            String key=String.format(PropertyConstants.IPV6_ADDR_KEY, i);
            propertySection += String.format("      <Property oe:key=\"%s\" oe:value=\"%s\"/>\n", key, ipv6Settings.get(key));
        }

        propertySection += "  </PropertySection>\n";

        for (int i = 0; i < nodeCount; i++) {
            builder.append(propertySection);
        }

        return builder.toString();
    }

    /**
     * If the local node is in LocalAware_ClusterPersistent status, the cluster might be using either
     * original IPs or the new IPs.
     * The local node should commit new IPs if quorum nodes of cluster (with new IPs) are available.
     * @param newIpinfo
     * @param node_id
     * @param node_count
     * @throws Exception
     */
    private static void handlePossibleJump(ClusterIpInfo newIpinfo, String node_id, int node_count) throws Exception{
        Integer node_id_number = Integer.valueOf(node_id.split("vipr")[1]);
        boolean bIpv4 = true;
        if (newIpinfo.getIpv4Setting().getNetworkNetmask().equals(PropertyConstants.IPV4_ADDR_DEFAULT)) {
            bIpv4 = false;
        }

        log.info("Checking if new IPs had already been commited...");
        for (int i=1; i< 24; i++) {
            // Here we assume each node would startup with functional network within at most 2m.
            // So we will check new IPs for 24 times (5s interval).
            log.info("Trying to ping other nodes ...");
            int alivenodes = 0;
            for (String network_addr : newIpinfo.getIpv4Setting().getNetworkAddrs()) {
               if(ping(network_addr, bIpv4) == 0) {
                    alivenodes ++;
                }
            }

            // do not count local node
            alivenodes--;

            if (alivenodes > node_count/2) {
                log.info("new IPs have been commited in quorum nodes of the cluster, so committing it in local node...");
                applyIpinfo(newIpinfo, node_id, node_count, true);
                return;
            }
            Thread.sleep(5 * 1000);
        }
        log.info("New IPs seems have NOT been commited in quorum nodes of the cluster, so still using current IPs.");
        return;
    }

    private static int ping(String ip, boolean bIpv4) {
        String cmd = "ping";
        String ipstr = ip;
        if(!bIpv4) {
            cmd+="6";
            ipstr+="%eth0";
        }
        final String[] cmds = {cmd, ipstr, "-c", "1"};
        Exec.Result result = Exec.sudo(CMD_TIMEOUT, cmds);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            log.warn("Failed to ping {} with exit value: {}", ip, result.getExitValue());
        }
        return result.getExitValue();
    }
}
