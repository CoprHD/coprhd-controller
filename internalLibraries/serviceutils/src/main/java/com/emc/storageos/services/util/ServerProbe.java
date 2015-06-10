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
package com.emc.storageos.services.util;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		return (String) diskInfo.get(name).get(InstallerConstants.NODE_PROBE_KEY_DISK_CAPACITY);
	}
	
	public boolean hasViPRPartition(String name) {
		return (boolean) diskInfo.get(name).get(InstallerConstants.NODE_PROBE_KEY_DISK_HAS_VIPR_PARTITION);
	}
	
	public boolean allHasViPRPartition() {
		return allHasViPRPartition;
	}
	
	public boolean diskMetMiniumSizeReq(String name) {
		return (boolean) diskInfo.get(name).get(InstallerConstants.NODE_PROBE_KEY_DISK_MET_MIN_REQ);
	}
	
	private void probeNetworkInterface() {
		networkInterfaces = InstallerOperation.getInterfaces();
	}
	
	private void probeCpuAndMemory() {
		cpuCoreNum = InstallerOperation.probeCpuCoreNumber();
    	memorySize = InstallerOperation.probeMemorySize();
	}
	private void probeDisks() {
		log.info("Probing disks, should only run once.");
		for (String disk : InstallerOperation.getDiskAndCapacityNumber()) {
            if (!disk.startsWith("sd"))
                continue;
			Map<String, Object> info = new HashMap<String, Object>();
			String delims = "[ ]+";
			String[] tokens = disk.split(delims);
			String name = tokens[0].trim();
			String diskName = name.contains("dev") ? name : "/dev/" + name;
			String diskCap = tokens[3].trim();
			diskCapacity.put(diskName, diskCap);
			info.put(InstallerConstants.NODE_PROBE_KEY_DISK_CAPACITY, diskCap);
			
			// check if disk meet mini req
			log.debug("Run local disk {} minimum requirement check against {}", 
	    			diskName +"/" + diskCap, InstallerConstants.MIN_REQ_DISK_SIZE);
			boolean metMinReq = false;
            if (diskCap.endsWith(InstallerConstants.DISK_CAPACITY_UNIT_DEFAULT)) {
                // Disk capacity is represented normally in Gigabyte for all disks.
                // The disk4 is 1G in Hyper-V, while just about 125Kin ESXi Env.
                if (Integer.parseInt(diskCap.split(InstallerConstants.DISK_CAPACITY_UNIT_DEFAULT)[0])
                        >= InstallerConstants.MIN_REQ_DISK_SIZE) {
                    metMinReq = true;
                }
            }
			info.put(InstallerConstants.NODE_PROBE_KEY_DISK_MET_MIN_REQ, metMinReq);
			
			// check if disk has vipr partition
			boolean hasViPRPartition = false;
			if (InstallerOperation.diskHasViprPartitions(diskName) != 0) {
				log.warn("Disk {} has ViPR partition already", diskName);
				hasViPRPartition = true;
			}
			info.put(InstallerConstants.NODE_PROBE_KEY_DISK_HAS_VIPR_PARTITION, hasViPRPartition);
			
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
			if (!(boolean) diskInfo.get(disk).get(InstallerConstants.NODE_PROBE_KEY_DISK_HAS_VIPR_PARTITION)) {
				noPartition = false;
				break;
			}
		}
		return noPartition;
	}
		
	// return true if at least one disk met min req
	private boolean runDiskCapacityMinimumRequirementCheck() {
		log.debug("Run local disk {} minimum requirement check against {}", 
				diskInfo.keySet(), InstallerConstants.MIN_REQ_DISK_SIZE);
		for (String disk : diskInfo.keySet()) {
			if ((boolean) diskInfo.get(disk).get(InstallerConstants.NODE_PROBE_KEY_DISK_MET_MIN_REQ)) {
				return true;
			}
		}
		log.warn("No disk(s) {} meet minimum requirement for installation {}",  
				diskInfo, InstallerConstants.MIN_REQ_DISK_SIZE);
		return false;
	}

	private boolean runCPUMinimumRequirementCheck() {
		boolean ret = true;
    	log.debug("Run local CPU core {} minimum requirement check against {}", 
    			cpuCoreNum, InstallerConstants.MIN_REQ_CPU_CORE);
    	try {
			int cpuCore = Integer.parseInt(cpuCoreNum);
			if (cpuCore < InstallerConstants.MIN_REQ_CPU_CORE) {
				log.warn("CPU core number {} does not meet minimum requirement for installation {}",
						cpuCore, InstallerConstants.MIN_REQ_CPU_CORE);
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
    			memorySize, InstallerConstants.MIN_REQ_MEM_SIZE);
    	try {
			int memSize = Integer.parseInt(memorySize);
			if (memSize < InstallerConstants.MIN_REQ_MEM_SIZE) {
				log.warn("Memory size {} kB does not meet minimum requirement for installation {}",
						memSize, InstallerConstants.MIN_REQ_MEM_SIZE + " kB");
				ret = false;
			}
    	} catch (Exception e) {
    		log.error("Check min memory requirement caught exception {} for {}", e.getMessage(), memorySize);
    	}
		return ret;
    }
}
