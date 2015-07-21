/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.services.util;

import java.util.HashMap;
import java.util.Map;

import com.emc.storageos.model.property.PropertyConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * A singleton class to probe the server hardware configurations such as memory, cpu and disk
 * capacity etc.
 *
 */
final public class ServerProbe {
	private static final Logger log = LoggerFactory.getLogger(ServerProbe.class);
	private static ServerProbe instance = null;
	// meet mini req for memory, cpu and at least one disk meet size mini req
	private boolean meetMinimumRequirement = true;
	private boolean allHasViPRPartition = false;
	private String cpuCoreNum = null;
	private String memorySize = null;
	private String[] networkInterfaces = null;
	private Map<String, String> diskCapacity = new HashMap<String, String>();;
	private Map<String, Map<String, Object>> diskInfo = new HashMap<String, Map<String, Object>>();
    private static final long CMD_TIMEOUT = 120 * 1000;

	private ServerProbe() {
		probeHardwares();
	}
	
	private void probeHardwares() {
		probeNetworkInterface();
		probeCpuAndMemory();
		probeDisks();
		if (!meetMinimumHwRequirement()) {
			meetMinimumRequirement = false;
		}
	}

	public static ServerProbe getInstance() {
		if (instance == null) {
			instance = new ServerProbe();
		}
		return instance;
	}
	
	public boolean isMetMinimumRequirement() {
		return meetMinimumRequirement;
	}

	public String getCpuCoreNum() {
		return cpuCoreNum;
	}

	public String getMemorySize() {
		return memorySize;
	}

	public String[] getNetworkInterfaces() {
		return networkInterfaces;
	}
	
	public Map<String, String> getDiskCapacity() {
		return diskCapacity;
	}
	
	public String getDiskCapacity(String name) {
		return (String) diskInfo.get(name).get(PropertyConstants.NODE_PROBE_KEY_DISK_CAPACITY);
	}
	
	public boolean hasViPRPartition(String name) {
		return (boolean) diskInfo.get(name).get(PropertyConstants.NODE_PROBE_KEY_DISK_HAS_VIPR_PARTITION);
	}
	
	public boolean allHasViPRPartition() {
		return allHasViPRPartition;
	}
	
	public boolean diskMetMiniumSizeReq(String name) {
		return (boolean) diskInfo.get(name).get(PropertyConstants.NODE_PROBE_KEY_DISK_MET_MIN_REQ);
	}
	
	private void probeNetworkInterface() {
		networkInterfaces = getInterfaces();
	}

    private String[] getInterfaces() {
        String[] cmds = { "/bin/sh", "-c",
                "ls /sys/class/net | egrep -v lo | awk '{print $1}'" };
        return PlatformUtils.executeCommand(cmds);
    }
	
	private void probeCpuAndMemory() {
		cpuCoreNum = probeCpuCoreNumber();
    	memorySize = probeMemorySize();
	}

    /*
     * Probe CPU core number on the server with cmd
     * @return the number of CPU cores (e.g. String 2)
     */
    private String probeCpuCoreNumber() {
        final String[] cmds = {"cat", "/proc/cpuinfo"};
        Exec.Result result = Exec.sudo(CMD_TIMEOUT, cmds);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            log.error("Failed to get cpu core number with errcode: {}, error: {}",
                    result.getExitValue(), result.getStdError());
        }

        // processor info in /proc/cpuinfo is "processor       : 0" etc.
        int processor_no = 0;
        String[] lines = result.getStdOutput().split("\n");
        for(String line: lines) {
            if (line.startsWith("processor")) {
                if (processor_no < Integer.parseInt(line.split(":")[1].trim())) {
                    processor_no = Integer.parseInt(line.split(":")[1].trim());
                }
            }
        }
        return String.valueOf(++processor_no);
    }

    /*
     * Probe memory size on the server with cmd (cat /proc/meminfo).
     * @return the memory size in kB.
     * 		For example String 6125560 is returned from "MemTotal:        6125560 kB"
     */
    private String probeMemorySize() {
        final String[] cmds = {"cat", "/proc/meminfo"};
        Exec.Result result = Exec.sudo(CMD_TIMEOUT, cmds);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            log.error("Failed to get cpu core number with errcode: {}, error: {}",
                    result.getExitValue(), result.getStdError());
        }

        String[] lines = result.getStdOutput().split("\n");
        for(String line: lines) {
            // Total memory line is like "MemTotal:        6125560 kB"
            if (line.startsWith("MemTotal")) {
                String delims = "[ ]+";
                String[] tokens = line.split(delims);
                return tokens[1];
            }
        }
        return null;
    }

    /* Get Disk name and capacity
     * By default, there are four disks in hypervisor platform.
     *   In ESX,     {"sda 4G", "sdb 16G", "sdc 100G", "sdd 125K"}
     *   In Hyper-V, {"sda 4G", "sdb 16G", "sdc 100G", "sdd 1G"}
     * @Return Example
     *   sda                           8:0    0    50G  0
     *   sdb                           8:16   0    60G  0 /project
     *   sdc                           8:32   0   200G  0
     *   ...
     */
    private String[] getDiskAndCapacityNumber() {
        final String[] cmds = {"lsblk", "-i"};
        Exec.Result result = Exec.sudo(CMD_TIMEOUT, cmds);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            log.error("Failed to get cpu core number with errcode: {}, error: {}",
                    result.getExitValue(), result.getStdError());
        }

        return result.getStdOutput().split("\n");
    }

	private void probeDisks() {
		log.info("Probing disks, should only run once.");
		for (String disk : getDiskAndCapacityNumber()) {
            if (!disk.startsWith("sd"))
                continue;
			Map<String, Object> info = new HashMap<String, Object>();
			String delims = "[ ]+";
			String[] tokens = disk.split(delims);
			String name = tokens[0].trim();
			String diskName = name.contains("dev") ? name : "/dev/" + name;
			String diskCap = tokens[3].trim();
			diskCapacity.put(diskName, diskCap);
			info.put(PropertyConstants.NODE_PROBE_KEY_DISK_CAPACITY, diskCap);
			
			// check if disk meet mini req
			log.debug("Run local disk {} minimum requirement check against {}", 
	    			diskName +"/" + diskCap, PropertyConstants.MIN_REQ_DISK_SIZE);
			boolean metMinReq = false;
            if (diskCap.endsWith(PropertyConstants.DISK_CAPACITY_UNIT_DEFAULT)) {
                // Disk capacity is represented normally in Gigabyte for all disks.
                // The disk4 is 1G in Hyper-V, while just about 125Kin ESXi Env.
                if (Integer.parseInt(diskCap.split(PropertyConstants.DISK_CAPACITY_UNIT_DEFAULT)[0])
                        >= PropertyConstants.MIN_REQ_DISK_SIZE) {
                    metMinReq = true;
                }
            }
			info.put(PropertyConstants.NODE_PROBE_KEY_DISK_MET_MIN_REQ, metMinReq);
			
			// check if disk has vipr partition
			boolean hasViPRPartition = false;
			if (PlatformUtils.diskHasViprPartitions(diskName) != 0) {
				log.warn("Disk {} has ViPR partition already", diskName);
				hasViPRPartition = true;
			}
			info.put(PropertyConstants.NODE_PROBE_KEY_DISK_HAS_VIPR_PARTITION, hasViPRPartition);
			
			diskInfo.put(diskName, info);
			log.info("Probed diskInfo: {}", diskInfo);
		}
		
		allHasViPRPartition = allDisksHaveViPRPartition();
	}
	
	private boolean meetMinimumHwRequirement() {
		log.info("Checking mininum requirement, should only run once.");
		if (!runMemoryMinimumRequirementCheck()) {
			return false;
		}
		if (!runCPUMinimumRequirementCheck()) {
			return false;
		}
		if (!runDiskCapacityMinimumRequirementCheck()) {
			return false;
		}
		return true;
	}
	
	// return true if all disks have ViPR partition
	private boolean allDisksHaveViPRPartition() {
		boolean noPartition = true;
		for (String disk : diskInfo.keySet()) {
			if (!(boolean) diskInfo.get(disk).get(PropertyConstants.NODE_PROBE_KEY_DISK_HAS_VIPR_PARTITION)) {
				noPartition = false;
				break;
			}
		}
		return noPartition;
	}
		
	// return true if at least one disk met min req
	private boolean runDiskCapacityMinimumRequirementCheck() {
		log.debug("Run local disk {} minimum requirement check against {}", 
				diskInfo.keySet(), PropertyConstants.MIN_REQ_DISK_SIZE);
		for (String disk : diskInfo.keySet()) {
			if ((boolean) diskInfo.get(disk).get(PropertyConstants.NODE_PROBE_KEY_DISK_MET_MIN_REQ)) {
				return true;
			}
		}
		log.warn("No disk(s) {} meet minimum requirement for installation {}",  
				diskInfo, PropertyConstants.MIN_REQ_DISK_SIZE);
		return false;
	}

	private boolean runCPUMinimumRequirementCheck() {
		boolean ret = true;
    	log.debug("Run local CPU core {} minimum requirement check against {}", 
    			cpuCoreNum, PropertyConstants.MIN_REQ_CPU_CORE);
    	try {
			int cpuCore = Integer.parseInt(cpuCoreNum);
			if (cpuCore < PropertyConstants.MIN_REQ_CPU_CORE) {
				log.warn("CPU core number {} does not meet minimum requirement for installation {}",
						cpuCore, PropertyConstants.MIN_REQ_CPU_CORE);
				ret = false;
			}
    	} catch (Exception e) {
    		log.error("Check min CPU requirement caught exception {} for {}", e.getMessage(), cpuCoreNum);
    	}
		return ret;
    }
	
	private boolean runMemoryMinimumRequirementCheck() {
		boolean ret = true;
    	log.debug("Run local memory size {} minimum requirement check against {}", 
    			memorySize, PropertyConstants.MIN_REQ_MEM_SIZE);
    	try {
			int memSize = Integer.parseInt(memorySize);
			if (memSize < PropertyConstants.MIN_REQ_MEM_SIZE) {
				log.warn("Memory size {} kB does not meet minimum requirement for installation {}",
						memSize, PropertyConstants.MIN_REQ_MEM_SIZE + " kB");
				ret = false;
			}
    	} catch (Exception e) {
    		log.error("Check min memory requirement caught exception {} for {}", e.getMessage(), memorySize);
    	}
		return ret;
    }
}
