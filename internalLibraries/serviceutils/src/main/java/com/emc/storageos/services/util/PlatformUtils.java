/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.services.util;

import com.emc.storageos.model.property.PropertyConstants;
import com.emc.vipr.model.sys.ipreconfig.ClusterIpInfo;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class PlatformUtils {
    private static final Logger log = LoggerFactory.getLogger(PlatformUtils.class);
    private static final String GET_OVF_PROPERTY_CMD = "/etc/getovfproperties";
    private static final String GENISO_CMD = "/opt/storageos/bin/geniso";
    private static final String IS_VAPP = "--is-vapp";
    private static final String SYSTOOL_CMD = "/etc/systool";
    private static final String IS_APPLIANCE = "--test";
    private static final String GET_VDCPROPS = "--getvdcprops";
    private static final String IS_APPLIANCE_OUTPUT = "Ok";

    private static final String VDCPROP_SITEIDS = "site_ids";

    private static final long CMD_TIMEOUT = 120 * 1000;
    private static final long CMD_PARTITION_TIMEOUT = 600 * 1000;    // 10 min
    private static String PID_DIR = "/var/run/storageos/";
    // matches <svcname>.pid, <svcname>-debug.pid, <svcname>-coverage.pid which contains pid
    private static final String PID_FILENAME_PATTERN = "%s(-(coverage|debug))?.pid";
    private static final String PRODUCT_IDENT_PATH = "/opt/storageos/etc/product";

    private static volatile Boolean isVMwareVapp;
    private static volatile Boolean isAppliance;

    /*
     * Get local configuration by reading ovfenv partition and detecting real h/w
     * 
     * @return local configuration
     */
    public static Configuration getLocalConfiguration() {
        Configuration conf = new Configuration();

        // read ovfenv properties from ovfenv partition
        String[] props = getOvfenvPropertyStrings();
        Map<String, String> propMap = new HashMap<String, String>();
        for (String s : props) {
            if(s.contains(PropertyConstants.DELIMITER)) {
                if (s.split(PropertyConstants.DELIMITER).length == 2) {
                    propMap.put(s.split(PropertyConstants.DELIMITER)[0], s.split(PropertyConstants.DELIMITER)[1]);
                }else if(s.split(PropertyConstants.DELIMITER).length == 1) {
                    propMap.put(s.split(PropertyConstants.DELIMITER)[0],"");
                }else {
                    log.error("ovf properties file contain line in unexpected format : {}", s);
                }
            }
        }

        // load major properties (network info etc.)
        conf.loadFromPropertyMap(propMap);

        // Update local conf to reflect the current memory size, CPU count, disk(data) size and network interface
        conf.getHwConfig().put(PropertyConstants.PROPERTY_KEY_MEMORY_SIZE, ServerProbe.getInstance().getMemorySize());
        conf.getHwConfig().put(PropertyConstants.PROPERTY_KEY_CPU_CORE, ServerProbe.getInstance().getCpuCoreNum());

        String diskName = propMap.get(PropertyConstants.PROPERTY_KEY_DISK);
        if (diskName == null) {
            diskName = PropertyConstants.DATA_DISK_DEFAULT;
        }
        conf.getHwConfig().put(PropertyConstants.PROPERTY_KEY_DISK, diskName);
        conf.getHwConfig().put(PropertyConstants.PROPERTY_KEY_DISK_CAPACITY, ServerProbe.getInstance().getDiskCapacity(diskName));

        String netif = propMap.get(PropertyConstants.PROPERTY_KEY_NETIF);
        if (netif == null) {
            netif = PropertyConstants.NETIF_DEFAULT;
        }
        conf.getHwConfig().put(PropertyConstants.PROPERTY_KEY_NETIF, netif);

        log.info("Local found config: {}", conf.toString());
        return conf;
    }

    public static int diskHasViprPartitions(String disk) {
        final String[] cmds = { "/etc/mkdisk.sh", "check", disk };
        Exec.Result result = Exec.sudo(CMD_PARTITION_TIMEOUT, cmds);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            log.warn("Check disk {} for vipr partition failed with exit value is: {}",
                    disk, result.getExitValue());
        }
        return result.getExitValue();
    }

    public static String[] executeCommand(String[] commands) {
        StringBuffer output = new StringBuffer();
        ArrayList<String> out = new ArrayList<String>();
        Process p;
        try {
            p = Runtime.getRuntime().exec(commands);
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    p.getInputStream()));
            String line = "";
            while ((line = reader.readLine()) != null) {
                output.append(line + "\t");
                out.add(line);
            }
            reader.close();
        } catch (Exception e) {
        	log.error(e.getMessage(), e);
        }
        return out.toArray(new String[out.size()]);
    }

    /*
     * Get ovfenv properties
     * 
     * @return strings of key/value property pairs
     */
    public static String[] getOvfenvPropertyStrings() {
        final String[] cmds = { "/etc/getovfproperties", "--readCDROM" };
        Exec.Result result = Exec.sudo(CMD_TIMEOUT, cmds);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            log.error("Failed to get ovfenv properties with errcode: {}, error: {}",
                    result.getExitValue(), result.getStdError());
        }
        return result.getStdOutput().split("\n");
    }

    /*
     * Get ovfenv properties
     * 
     * @return map of key/value property pairs
     */
    public static Map<String, String> getOvfenvProperties() {
        final String[] cmds = { GET_OVF_PROPERTY_CMD, "--readCDROM" };
        Exec.Result result = Exec.sudo(CMD_TIMEOUT, cmds);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            log.error("Failed to get properties from ovfenv device directly with errcode: {}, error: {}",
                    result.getExitValue(), result.getStdError());
            throw new IllegalStateException("Failed to get properties from ovfenv device directly");
        }

        String[] props = result.getStdOutput().split("\n");
        Map<String, String> propMap = new HashMap<String, String>();
        for (String s : props) {
            if (s.contains(PropertyConstants.DELIMITER)) {
                propMap.put(s.split(PropertyConstants.DELIMITER)[0], s.split(PropertyConstants.DELIMITER)[1]);
            }
        }
        return propMap;
    }

    /**
     * Generate key/value pairs string for ovfenv properties
     * 
     * @param ipinfo
     * @param nodeid
     * @param node_count
     * @return
     */
    public static String genOvfenvPropertyKVString(ClusterIpInfo ipinfo, String nodeid, int node_count) {
        // Compose cluster configuration key/value properties string
        StringBuffer clusterprops = new StringBuffer();
        clusterprops.append(ipinfo.toString());
        clusterprops.append(PropertyConstants.NODE_COUNT_KEY).append(PropertyConstants.DELIMITER).append(node_count).append("\n");
        clusterprops.append(PropertyConstants.NODE_ID_KEY).append(PropertyConstants.DELIMITER).append(nodeid).append("\n");

        return clusterprops.toString();
    }

    /**
     * Generate ovfenv ISO image which will be then saved to ovfenv partition
     * 
     * @param ovfenvPropKVStr ovfenv key/value property string
     * @param isoFilePath the path of the ovfenv ISO
     */
    public static void genOvfenvIsoImage(String ovfenvPropKVStr, String isoFilePath) {
        byte[] bOvfenvPropKVStr = ovfenvPropKVStr.getBytes();
        String propFilePath = "/tmp/ovf-env.properties";
        File propFile = new File(propFilePath);
        try {
            FileUtils.writePlainFile(propFilePath, bOvfenvPropKVStr);
        } catch (Exception e1) {
            propFile.delete();
            log.error("Write to prop file failed with exception: {}",
                    e1.getMessage());
            throw new IllegalStateException("Failed to generate ovfenv prop file.");
        }

        try {
            File isoFile = new File(isoFilePath);
            String[] genISOImageCommand = { GENISO_CMD, "--label", "CDROM", "-f", propFilePath, "-o", isoFilePath, "ovf-env.properties",
                    "4096" };
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
     * 
     * @return ovfenv partition
     */
    public static String probeOvfenvPartition() {
        final String[] cmds = { GET_OVF_PROPERTY_CMD, "--probe-ovfenv-partition" };
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
        if (isVMwareVapp != null) {
            log.info("Return value {} from cached result", isVMwareVapp.booleanValue());
            return isVMwareVapp.booleanValue();
        }

        final String[] cmd = { GET_OVF_PROPERTY_CMD, IS_VAPP };
        Exec.Result result = Exec.sudo(CMD_TIMEOUT, cmd);
        if (!result.exitedNormally()) {
            log.error("Failed to check if it's vApp {}", result.getStdError());
            throw new IllegalStateException("Failed to check platform");
        }
        if (result.getExitValue() == 0) {
            log.info("Current platform is VMware vApp");
            isVMwareVapp = Boolean.TRUE;
            return isVMwareVapp.booleanValue();
        }
        log.info("The exit value of platform check: {}", result.getExitValue());
        isVMwareVapp = Boolean.FALSE;
        return isVMwareVapp.booleanValue();
    }

    /**
     * Check if current deployment is an appliance
     * 
     * @return true if it is an appliance, otherwise false(e.g.: devkit)
     */
    public static boolean isAppliance() {
        if (isAppliance != null) {
            log.info("Return value {} from cached result", isAppliance.booleanValue());
            return isAppliance.booleanValue();
        }

        final String[] cmd = { SYSTOOL_CMD, IS_APPLIANCE };
        Exec.Result result = Exec.sudo(CMD_TIMEOUT, cmd);
        if (!result.exitedNormally()) {
            log.error("Failed to check if it's appliance {}", result.getExitValue());
            throw new IllegalStateException("Failed to check if it's appliance");
        }
        log.debug("result={}", result.toString());
        if (IS_APPLIANCE_OUTPUT.equals(result.getStdError().trim())) {
            log.info("It's an appliance");
            isAppliance = Boolean.TRUE;
            return isAppliance.booleanValue();
        }
        log.info("The output of appliance check: {}", result.getStdError());
        isAppliance = Boolean.FALSE;
        return isAppliance.booleanValue();
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

    /**
     * Checks if the env has multiple DR sites
     *
     * @return true if it has multiple DR sites
     */
    public static boolean hasMultipleSites() {
        boolean hasMultipleSites = false;
        final String[] cmds = { SYSTOOL_CMD, GET_VDCPROPS };
        Exec.Result result = Exec.sudo(CMD_TIMEOUT, cmds);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            log.error("Failed to get vdc properties with errcode: {}, error: {}",
                    result.getExitValue(), result.getStdError());
            throw new IllegalStateException("Failed to get vdc properties");
        }

        String[] props = result.getStdOutput().split("\n");
        for (String s : props) {
            String key = s.split(PropertyConstants.DELIMITER)[0];
            String value = s.split(PropertyConstants.DELIMITER)[1];
            if (!key.equals(VDCPROP_SITEIDS)) continue;

            if (value.contains(",")) {
                hasMultipleSites = true;
                break;
            }
        }
        return hasMultipleSites;
    }

    /**
     * Get service process ID by service name.
     * 
     * @param svcName service name 
     * @return service process ID
     */
    public static int getServicePid(String svcName) throws FileNotFoundException {
        String pidFileRegex = String.format(PID_FILENAME_PATTERN, svcName);
        List<File> files = FileUtils.getFileByRegEx(new File(PID_DIR), pidFileRegex);
        
        if (files == null || files.isEmpty()) {
            log.warn("could not find {} pid file , please check service status", svcName);
            throw new IllegalStateException("can't find pid file, please check service status"); 
        }
        
        try (Scanner scanner = new Scanner(files.get(0))) {
            return scanner.nextInt();
        }
    }

    /**
     * Get product ident.
     *
     * @return Product ident
     */
    public static String getProductIdent() throws IOException {
    	byte[] productIdent = FileUtils.readDataFromFile(PRODUCT_IDENT_PATH);
        return new String(productIdent).trim();
    }
}
