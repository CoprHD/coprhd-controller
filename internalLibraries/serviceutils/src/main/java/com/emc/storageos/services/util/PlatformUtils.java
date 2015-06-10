/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.services.util;

import com.emc.storageos.model.property.PropertyConstants;
import com.emc.vipr.model.sys.ipreconfig.ClusterIpInfo;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PlatformUtils {
    private static final Logger log = LoggerFactory.getLogger(PlatformUtils.class);
    private static final String GET_OVF_PROPERTY_CMD = "/etc/getovfproperties";
    private static final String GENISO_CMD = "/opt/storageos/bin/geniso";
    private static final String IS_VAPP  = "--is-vapp";
    private static final String SYSTOOL_CMD = "/etc/systool";
    private static final String IS_APPLIANCE  = "--test";
    private static final String IS_APPLIANCE_OUTPUT = "Ok";
    private static final long CMD_TIMEOUT = 120 * 1000;

    /*
     * Get ovfenv properties
     * @return key/value property pairs
     */
    public static Map<String, String> getOvfenvProperties() {
        final String[] cmds = {GET_OVF_PROPERTY_CMD, "--readCDROM"};
        Exec.Result result = Exec.sudo(CMD_TIMEOUT, cmds);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            log.error("Failed to get properties from ovfenv device directly with errcode: {}, error: {}",
                    result.getExitValue(), result.getStdError());
            throw new IllegalStateException("Failed to get properties from ovfenv device directly");
        }

        String[] props = result.getStdOutput().split("\n");
        Map<String, String> propMap = new HashMap<String, String>();
        for (String s : props) {
            propMap.put(s.split(PropertyConstants.DELIMITER)[0], s.split(PropertyConstants.DELIMITER)[1]);
        }
        return propMap;
    }

    /**
     * Generate key/value pairs string for ovfenv properties
     * @param ipinfo
     * @param nodeid
     * @param node_count
     * @return
     */
    public static String genOvfenvPropertyKVString(ClusterIpInfo ipinfo, String nodeid, int node_count){
        // Compose cluster configuration key/value properties string
        StringBuffer clusterprops = new StringBuffer();
        clusterprops.append(ipinfo.toString());
        clusterprops.append(PropertyConstants.NODE_COUNT_KEY).append(PropertyConstants.DELIMITER).append(node_count).append("\n");
        clusterprops.append(PropertyConstants.NODE_ID_KEY).append(PropertyConstants.DELIMITER).append(nodeid).append("\n");

        return clusterprops.toString();
    }

    /**
     * Generate ovfenv ISO image which will be then saved to ovfenv partition
     * @param ovfenvPropKVStr ovfenv key/value property string
     * @param isoFilePath the path of the ovfenv ISO
     */
    public static void genOvfenvIsoImage(String ovfenvPropKVStr, String isoFilePath){
        byte[] bOvfenvPropKVStr = ovfenvPropKVStr.getBytes();
        String propFilePath = "/tmp/ovf-env.properties";
        File propFile = new File(propFilePath);
        try {
            FileUtils.writePlainFile(propFilePath, bOvfenvPropKVStr, 0);
        } catch (Exception e1) {
            propFile.delete();
            log.error("Write to prop file failed with exception: {}",
                    e1.getMessage());
            throw new IllegalStateException("Failed to generate ovfenv prop file.");
        }

        try {
            File isoFile = new File(isoFilePath);
            String[] genISOImageCommand = {GENISO_CMD, "--label", "CDROM", "-f", propFilePath, "-o", isoFilePath, "ovf-env.properties", "4096"};
            Exec.Result result = Exec.sudo(CMD_TIMEOUT, genISOImageCommand);
            if (!result.exitedNormally() || result.getExitValue() != 0) {
                log.error("Generating ISO image failed with exit value: {}, error: {}",
                        result.getExitValue(), result.getStdError());
                throw new IllegalStateException("Failed to generate ISO image.");
            }
        } catch (Exception e) {
            throw e;
        } finally {
            propFile.delete();
        }
    }

    /**
     * Probe ovfenv parition
     * @return ovfenv partition
     */
    public static String probeOvfenvPartition() {
        final String[] cmds = {GET_OVF_PROPERTY_CMD, "--probe-ovfenv-partition"};
        Exec.Result result = Exec.sudo(CMD_TIMEOUT, cmds);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            log.error("Failed to get ovfenv device with errcode: {}, error: {}",
                    result.getExitValue(), result.getStdError());
            throw new IllegalStateException("Failed to get ovfenv device");
        }
        String ovfenv_partition = result.getStdOutput().split("\n")[0];
        log.info("Probed ovfenv partition {}", ovfenv_partition);
        return ovfenv_partition;
    }

    /**
     * Check if current deployment is VMWare vapp 
     * 
     * @return true if it is VMWare vapp, otherwise false
     */
    public static boolean isVMwareVapp() {
        final String[] cmd = { GET_OVF_PROPERTY_CMD, IS_VAPP };
        Exec.Result result = Exec.sudo(CMD_TIMEOUT, cmd);
        if (!result.exitedNormally()) {
            log.error("Failed to check if it's vApp {}", result.getStdError());
            throw new IllegalStateException("Failed to check platform");
        }
        if (result.getExitValue() == 0) {
            log.info("Current platform is VMware vApp");
            return true;
        }
        log.info("The exit value of platform check: {}", result.getExitValue());
        return false;
    }

    /**
     * Check if current deployment is an appliance
     *
     * @return true if it is an appliance, otherwise false(e.g.: devkit)
     */
    public static boolean isAppliance() {
        final String[] cmd = { SYSTOOL_CMD, IS_APPLIANCE };
        Exec.Result result = Exec.sudo(CMD_TIMEOUT, cmd);
        if (!result.exitedNormally()) {
            log.error("Failed to check if it's appliance {}", result.getExitValue());
            throw new IllegalStateException("Failed to check if it's appliance");
        }
        log.debug("result={}", result.toString());
        if (IS_APPLIANCE_OUTPUT.equals(result.getStdError().trim())) {
            log.info("It's an appliance");
            return true;
        }
        log.info("The output of appliance check: {}", result.getStdError());
        return false;
    }

    /**
     * Checks if the build is a open source build or emc enterprise build
     *
     * @return true if it is an open source build otherwise false.
     */
    public static boolean isOssBuild() {
        boolean isOssBuild = false;
        String buildType = System.getProperty("buildType");
        if (StringUtils.isNotBlank(buildType) &&
                buildType.equalsIgnoreCase("oss")) {
            isOssBuild = true;
        }
        return isOssBuild;
    }
}
