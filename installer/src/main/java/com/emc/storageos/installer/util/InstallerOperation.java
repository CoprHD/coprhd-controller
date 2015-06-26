/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.installer.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import com.emc.storageos.model.property.PropertyConstants;
import com.emc.storageos.services.util.ServerProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.Configuration;
import com.emc.storageos.services.util.Exec;

public class InstallerOperation {
	private static final Logger log = LoggerFactory.getLogger(InstallerOperation.class);
	private static final String LIST_DISK_CMD = "lsblk -i|grep ^sd|awk '{print $1 \"    \" $4}'";
	private static final String GENISO_CMD = "/opt/storageos/bin/geniso";
    private static final long CMD_DEFAULT_TIMEOUT = 60 * 1000; // 1 min
    private static final long _INSTALLER_PARTITION_TIMEOUT = 600 * 1000;    // 10 min
    private static final long _INSTALLER_COPYROOTIMG_TIMEOUT = 3600 * 1000; // 60 min
    private static final long GENERATE_ISO_FILE_TIMEOUT = 30000; // 30 seconds timeout
    private static final long PROBE_OVFENV_PARTITION_TIMEOUT = 60 * 1000; // 1 min

    
	public static int initializeDisk(String disk) {
		final String[] cmds = {"/etc/mkdisk.sh", "native", disk}; 
		Exec.Result result = Exec.sudo(_INSTALLER_PARTITION_TIMEOUT, cmds);
		if (!result.exitedNormally() || result.getExitValue() != 0) {
			log.error("Init disk failed with exit value is: {}",
					result.getExitValue());
		}
		return result.getExitValue();
	}

	public static int installImage(String disk, String pathToImage) {
		final String[] cmds = {"/etc/systool", "--bootfs-dev=" + disk + "1", 
				"--bootfs-mntp=/mnt/.volumes/bootfs", "--DO_NOT_INCLUDE=yes", 
                "--install-baremetal", "/.volumes/bootfs/vipr-*/rootimg"};
		
		Exec.Result result = Exec.sudo(_INSTALLER_COPYROOTIMG_TIMEOUT, cmds);
		if (!result.exitedNormally() || result.getExitValue() != 0) {
			log.error("Install Image failed with exit value is: {}",
					result.getExitValue());
		}
		return result.getExitValue();
	}

    private static String formatProperties(Configuration config){
        // TODO: we could convert this to properties and make it simpler, as the new getovfproperties accepts both.

        // Compose cluster configuration string
        StringBuffer clusterprops = new StringBuffer();
        clusterprops.append("   <PropertySection>\n");
        // Looping through each property and add them to the StringBuffer
        for (Map.Entry<String, String> entry : config.getOVFProps().entrySet()) {
            clusterprops.append("         <Property oe:key=\"");
            clusterprops.append(entry.getKey());
            clusterprops.append("\" oe:value=\"");
            clusterprops.append(entry.getValue());
            clusterprops.append("\"/>\n");
        }
        clusterprops.append("   </PropertySection>\n");

        // Generate the final string for ovf-env.xml file
        StringBuffer props = new StringBuffer();

        props.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        props.append("<Environment\n");
        props.append("    oe:id=\"").append(config.getNodeId()).append("\">\n"); // Append the nodeId first
        props.append("   <PlatformSection>\n");
        props.append("      <Kind>Commodity hardware</Kind>\n");
        props.append("      <Version>No hypervisor</Version>\n");
        props.append("   </PlatformSection>\n");

        props.append(clusterprops.toString());

        if ( 1 == config.getNodeCount()) {
            props.append("<Entity />\n");
        } else {
            for (int i=1; i <= config.getNodeCount(); i++) {
                StringBuffer tmpNodeId = new StringBuffer();
                tmpNodeId.append("vipr");
                tmpNodeId.append(i);
                if (! config.getNodeId().equals(tmpNodeId.toString()) ) {
                    props.append("<Entity oe:id=\"").append(tmpNodeId).append("\">\n");
                    props.append(clusterprops.toString());
                    props.append("</Entity>\n");
                }
            }
        }

        // TODO: for native installed env, put selected data disk and netif info into into xml.
        props.append("</Environment>\n");
        return props.toString();
    }
    
	public static void installISOImage(Configuration config) {
		String ovfProperties = formatProperties(config);
        
        byte[] bFile = ovfProperties.getBytes();
        String xmlFilePath = "/tmp/ovf-env.xml";
        File xmlFile = new File(xmlFilePath);
        try {
			writeFile(xmlFilePath, bFile);
		} catch (IOException e1) {
			log.error("Write to xml file failed with exception: {}",
					e1.getMessage());
			xmlFile.delete(); 
		}
        File isoFile = new File("/tmp/ovf-env.iso");

        String[] genISOImageCommand = {GENISO_CMD, "--label", "CDROM", "-f", "/tmp/ovf-env.xml", "-o", "/tmp/ovf-env.iso", "ovf-env.xml", "4096"};
        Exec.Result result = Exec.sudo(GENERATE_ISO_FILE_TIMEOUT, genISOImageCommand);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
			log.error("Generating ISO image failed with exit value: {}, err:{}",
					result.getExitValue(), result.getStdError());
		}
        
        try {
            String cdPartition = "";
            if (config.isInstallMode()) {
                // For "install" mode:
                // 1. Only for Baremetal installation, all partitions are in same disk for now
                // 2. In Hyper-V and KVM, installation is under the way same as ovftool etc.
                cdPartition = config.getHwConfig().get(PropertyConstants.PROPERTY_KEY_DISK)+"4";
            } else {
                // For "config" and "redeploy" mode:
                // 1. Need to probe ovfenv partition for baremetal, hyper-v and kvm env.
                cdPartition = InstallerOperation.probeOvfenvPartition();
                log.info("Probed ovfenv partition {}", cdPartition);
            }
	        Path path = Paths.get("/tmp/ovf-env.iso");
			byte[] data = Files.readAllBytes(path);
			FileOutputStream fileOuputStream = new FileOutputStream(cdPartition); 
	        fileOuputStream.write(data);
	        fileOuputStream.close();
        } catch (IOException e) {
        	log.error("Creating ISO image partition failed with exception: {}",
					e.getMessage());
        	isoFile.delete();
        }
        
        xmlFile.delete(); 
        isoFile.delete(); // Those files are temporary, we delete them after using        
	}
	
	private static String getVendorInfo(String disk) {
		String cmd = "cat /sys/block/" + disk + "/device/vendor";
		return executeCommand(cmd).trim();
	}
	
	private static String getRevInfo(String disk) {
		String cmd = "cat /sys/block/" + disk + "/device/rev";
		return executeCommand(cmd).trim();
	}
	
	private static String getModelInfo(String disk) {
		String cmd = "cat /sys/block/" + disk + "/device/model";
		return executeCommand(cmd).trim();
	}

    public static String probeOvfenvPartition() {
        String[] cmds = { "/bin/sh", "-c", "/etc/getovfproperties --probe-ovfenv-partition" };
        String[] disks = executeCommand(cmds);
        if (disks.length == 0) {
            log.warn("Probing did not find local ovfenv partition.");
            return null;
        } else {
            return disks[0];
        }
    }

    public static int probeOvfenvEmptyPartition() {
        final String[] cmds = {"/etc/getovfproperties", "--probe-ovfenv-empty-partition"};
        Exec.Result result = Exec.sudo(PROBE_OVFENV_PARTITION_TIMEOUT, cmds);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            log.error("Failed to probe empty ovfenv partition with exit value is: {}",
                    result.getExitValue());
        }
        return result.getExitValue();
    }

        public static String getBootDeviceType() {
            String[] cmds = { "/bin/sh", "-c", "/usr/bin/grep bootfs_dev=/dev/sr /proc/cmdline" };
            String[] results = executeCommand(cmds);
            if (results.length == 0) {
                log.info("system boot from hard disk.");
                return "harddisk";
            } else {
                log.info("system boot from cdrom.");
                return "cdrom";
            }
        }
    
	/*
	 * Probe the disk name and its capacity.
	 * @return disk name and capacity String (e.g. "sda  200G")
	 */
	private static String[] getDiskAndCapacity() {
		String[] cmds = { "/bin/sh", "-c", LIST_DISK_CMD };
		return executeCommand(cmds);
    }

	/**
	 * Get the disk name out from the input String.
	 * @param input the String to be parsed (e.g. "sda  200G")
	 * @return disk name (e.g. "sda")
	 */
	public static String parseDiskString(String input) {
		String delims = "[ ]+";
		String[] tokens = input.split(delims);
		return tokens[0];
	}
	
	public static String[] getDiskInfo() {
		String[] diskAndCap = getDiskAndCapacity();
		ArrayList<String> info = new ArrayList<String>();
		for (String s : diskAndCap) {
			String disk = parseDiskString(s);
			String i = getVendorInfo(disk) + " " 
					+ getModelInfo(disk) + " " + getRevInfo(disk);
			info.add("/dev/" + s + " " + i);
		}
		return info.toArray(new String[info.size()]);
	}


	private static String executeCommand(String command) {
		StringBuffer output = new StringBuffer();
		Process p;
		try {
			p = Runtime.getRuntime().exec(command);
			p.waitFor();
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					p.getInputStream()));
			String line = "";
			while ((line = reader.readLine()) != null) {
				output.append(line + "\n");
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return output.toString();
	}
	
	private static String[] executeCommand(String [] commands) {
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
			e.printStackTrace();
		}
		return out.toArray(new String[out.size()]);
	}
	
	private static void writeFile(String filePath, byte[] content) throws IOException {
		FileOutputStream fileOuputStream = new FileOutputStream(filePath); 
        fileOuputStream.write(content);
        fileOuputStream.close();
	}
	
	/**
	 * Check local node hardware (i.e. Memory size, CPU core, Disk Capacity) 
	 * are the same as selected one.
	 * @param name the selected disk name
	 * @param size the selected disk size
	 * @return true if they are the same, false otherwise
	 */
	private static boolean hasSameDiskInfo(String name, String size) {
		Map<String, String> localDiskCap = ServerProbe.getInstance().getDiskCapacity();
		if (name != null && size != null && size.equals(localDiskCap.get(name))) {
			return true;
		}
		log.warn("Local disk(s) {} are not the same as selected cluster {}",
				localDiskCap, name + "=" + size);
		return false;
	}
	
	/**
	 * Check local node hardware (i.e. Memory size, CPU core, Disk Capacity) 
	 * are the same as in the input map.
	 * @param hwMap the input map with hardware info.
	 * @param checkDisk a flag to indicate if disk info needs to be compared
	 * @return true if they are the same; false otherwise
	 */
	public static String compareHardware(Map<String, String> hwMap, boolean checkDisk) {
		// this is the String in kB
		String localMemSizeStr = ServerProbe.getInstance().getMemorySize();
		String localCpuCoreStr = ServerProbe.getInstance().getCpuCoreNum();

		hwMap.get(PropertyConstants.PROPERTY_KEY_DISK_CAPACITY);
		if (!localMemSizeStr.equals(hwMap.get(PropertyConstants.PROPERTY_KEY_MEMORY_SIZE))) {
			log.warn("Local memory {} is not the same as selected cluster {}",
					localMemSizeStr, hwMap.get(PropertyConstants.PROPERTY_KEY_MEMORY_SIZE));
			return String.format("Local memory {%s} is not the same as selected cluster {%s}",
					localMemSizeStr, hwMap.get(PropertyConstants.PROPERTY_KEY_MEMORY_SIZE));
		}
		if (!localCpuCoreStr.equals(hwMap.get(PropertyConstants.PROPERTY_KEY_CPU_CORE))) {
			log.warn("Local CPU core number {} is not the same as selected cluster {}",
					localCpuCoreStr, hwMap.get(PropertyConstants.PROPERTY_KEY_CPU_CORE));
			return String.format("Local CPU core number {%s} is not the same as selected cluster {%s}",
					localCpuCoreStr, hwMap.get(PropertyConstants.PROPERTY_KEY_CPU_CORE));
		}
		if (checkDisk && !hasSameDiskInfo(hwMap.get(PropertyConstants.PROPERTY_KEY_DISK),
				hwMap.get(PropertyConstants.PROPERTY_KEY_DISK_CAPACITY))) {
			log.warn("Local disk(s) are not the same as selected cluster capacity {}",
				 hwMap.get(PropertyConstants.PROPERTY_KEY_DISK_CAPACITY));
			return String.format("Local disk(s) are not the same as selected cluster capacity {%s}",
					 hwMap.get(PropertyConstants.PROPERTY_KEY_DISK_CAPACITY));
		}
		return null;
	}



}
