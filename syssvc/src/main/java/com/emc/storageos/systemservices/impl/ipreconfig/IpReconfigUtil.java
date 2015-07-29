/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.ipreconfig;

import com.emc.storageos.services.util.FileUtils;
import com.emc.vipr.model.sys.ipreconfig.ClusterIpInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Ip Reconfig Util Class
 */
public class IpReconfigUtil {
    private static final Logger log = LoggerFactory.getLogger(IpReconfigUtil.class);

    public IpReconfigUtil() {
    }

    public static void writeIpinfoFile(ClusterIpInfo ipinfo, String path) throws Exception {
        log.info("writing ip info into {} of local disk ...", path);
        FileUtils.writeObjectToFile(ipinfo, path);
    }

    public static ClusterIpInfo readIpinfoFile(String path) throws Exception {
        log.info("reading ip info from {} of local disk ...", path);
        return (ClusterIpInfo) FileUtils.readObjectFromFile(path);
    }

    public static void writeNodeStatusFile(String nodestatus) throws Exception {
        log.info("writing node status {} into local disk ...", nodestatus);
        FileUtils.writeObjectToFile(nodestatus, IpReconfigConstants.NODESTATUS_PATH);
    }

    public static String readNodeStatusFile() throws Exception {
        log.info("reading node status from local disk ...");
        return (String) FileUtils.readObjectFromFile(IpReconfigConstants.NODESTATUS_PATH);
    }

    public static void cleanupLocalFiles() throws Exception {
        File ipreconfigdir = new File(IpReconfigConstants.IPRECONFIG_PATH);
        if (ipreconfigdir != null && ipreconfigdir.exists()) {
            org.apache.commons.io.FileUtils.deleteDirectory(ipreconfigdir);
            log.info("Succeed to remove local ipreconfig dir {}", IpReconfigConstants.IPRECONFIG_PATH);
        }
    }

}
