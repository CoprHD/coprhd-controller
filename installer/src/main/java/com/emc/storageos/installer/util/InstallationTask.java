/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.installer.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

import com.emc.storageos.model.property.PropertyConstants;
import com.emc.storageos.services.util.MulticastUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.Configuration;
import com.emc.storageos.installer.widget.DisplayPanel;

/**
 * Class implements installer operations for install/config/redeploy cases.
 *
 */
public class InstallationTask extends Thread {
	private static final Logger log = LoggerFactory.getLogger(InstallationTask.class);
	private Configuration config;
	private DisplayPanel confPanel;
	private String releaseVersion;
	private boolean localInstallDone = false;
	private boolean allNodesDone = false;
	private static final long SCAN_BROADCAST_INTERVAL = 1000; // 1 second scan interval
	private Set<String> nodes = new HashSet<String>();
	private String statusMsg = "success";
	private static final String TASK_INIT_DISK = "Initializing disk '%s' ";
	private static final String TASK_INSALL_IMG = "Installing rootfs image on disk '%s' ";
	private static final String TASK_INSALL_OVFENV_IMG = "Installing ovf-env property image ";
	private static final String TASK_CREATE_STARTUPMODE_FILES = "Create re-deployment files ";
	private static final String TASK_BROADCAST = "Broadcast configuration ";
	private static final String ERR_LOG_MSG = "Please check /opt/storageos/log/installer.log for details.";
	
    public InstallationTask(Configuration config, DisplayPanel confPanel, String releaseVersion) {
    	this.config = config;
    	this.confPanel = confPanel;
    	this.releaseVersion = releaseVersion;
    }

    /**
     * Check if local install/config is done.
     * @return true if local install/config done, false otherwise
     */
    public boolean isLocalInstallDone() {
    	return localInstallDone;
    }
    
    /**
     * Check if all nodes in the cluster install/config is done.
     * @return true if it is, false otherwise
     */
    public boolean isClusterInstallDone() {
    	return allNodesDone;
    }

    /**
     * Check if local install/config failed.
     * @return true if failed, false otherwise
     */
    public boolean localInstallFailed() {
    	return !statusMsg.equals("success") ? true : false;
    }
    
    /* 
     * Run install/config operations
     * (non-Javadoc)
     * @see java.lang.Thread#run()
     */
    public void run() {
    	final String disk = config.getHwConfig().get(PropertyConstants.PROPERTY_KEY_DISK);
        int progress = 2;
        updateProgressBarValue(progress);
		if (config.isInstallMode() || 
			(config.isRedeployMode() && InstallerOperation.getBootDeviceType().equals("cdrom")) ) {
                        // Only initialize disks and install images in installation mode or redeploy mode in baremetal env.
                        // Note: If redeploying procedure is bootup from harddisk, it is in hypervisor env in which harddisks and 
                        //       and filesystems are already prepared.

			// 1. init disk
			updateProgressStatusMessage(String.format(TASK_INIT_DISK, disk) + "...");
			if (!doInitDisk(disk)) {
				log.error(String.format(TASK_INIT_DISK, disk) + statusMsg);
				updateProgressStatusMessage(String.format(TASK_INIT_DISK, disk) + statusMsg);
				popupErrorMsg(new String[] {String.format(TASK_INIT_DISK, disk) + statusMsg, ERR_LOG_MSG});
				return;
			}
            progress = 5;
			updateProgressBarValue(progress);

			// 2. install rootfs image
			updateProgressStatusMessage(String.format(TASK_INSALL_IMG,  disk) + "...");
			if (!doInstallRootFsImage(disk)) {
				log.error(String.format(TASK_INSALL_IMG,  disk) + statusMsg);
				updateProgressStatusMessage(String.format(TASK_INSALL_IMG,  disk) + statusMsg);
				popupErrorMsg(new String[] {String.format(TASK_INSALL_IMG,  disk) + statusMsg, ERR_LOG_MSG});
				return;
			}
            progress = 10;
			updateProgressBarValue(progress);
		}
    	
    	// 3. install ovf-env property image
		updateProgressStatusMessage(TASK_INSALL_OVFENV_IMG + "...");
    	if (!doInstallOvfEnvPropertyImage()) {
    		log.error(TASK_INSALL_OVFENV_IMG + statusMsg);
    		updateProgressStatusMessage(TASK_INSALL_OVFENV_IMG + statusMsg);
    		popupErrorMsg(new String[] {TASK_INSALL_OVFENV_IMG + statusMsg, ERR_LOG_MSG});
    		return;
    	}
        progress = 15;
    	updateProgressBarValue(progress);
    	
    	// 4. create two empty flag files if it is re-join case
    	if (config.isRedeployMode()) {
			// create two empty files
			if (!createDbStartupModeFiles()) {
				log.error(TASK_CREATE_STARTUPMODE_FILES + statusMsg);
				popupErrorMsg(new String[] {TASK_CREATE_STARTUPMODE_FILES + statusMsg, ERR_LOG_MSG});
				return;
			} else {
				log.info("Re-deployment files are created.");
			}
		}

        // 6. multicast configuration over network in a dedicated thread
        startMulticastConf();

        // 7. scan broadcast configuration over network from other servers,
    	// set allNodesDone flag to true if other nodes in the same cluster are detected
    	Thread scanTask = new Thread(new ScanTask());
    	log.info("Starting scanning broadcast task ....");
    	scanTask.start();

        // 8. Sleep for a while for conf is totally multicasted in the network and scanned.
        try {
            // Each round scanning needs about 6 seconds, retry 3 times.
            log.info("Waiting for the conf is totally multicasted and scanned...");
            int retry=3;
            while (retry-- > 0) {
                Thread.sleep(8*1000);
                progress+=5;
                updateProgressBarValue(progress);
            }
        }catch (Exception e) {
            log.warn("multicast conf task threw", e);
        }

        // 9. set local configuration done.
        localInstallDone = true;
        updateProgressStatusMessage(String.format("Local %s Done! Multicasting configuration...",
                getOperationType()));
        log.info("Local installation done");
        updateProgressBarValue(30);

        // 9. Wait for the whole cluster configured and reboot
        try {
            log.info("Waiting for the whole cluster configured and reboot ...");
            while (true) {
                Thread.sleep(5*1000);
            }
        }catch (Exception e) {
            log.warn("Failed to waiting for the whole cluster configuration finished. Threw", e);
        }
    }
    
    /*
     * @return 'true' if success and 'false' if failed
     */
    private boolean createDbStartupModeFiles() {
    	log.info("Creating startup mode files");
    	boolean taskSuccess = true;
		try {
            setDbStartupModeAsHibernate(InstallerConstants.DB_DIR);
            setDbStartupModeAsHibernate(InstallerConstants.GEODB_DIR);
		} catch (IOException e) {
			statusMsg = "caught exception!";
			taskSuccess = false;
			log.error(TASK_CREATE_STARTUPMODE_FILES + "caught exception with: " + e.getMessage());
		}
		return taskSuccess;
    }

    private void setDbStartupModeAsHibernate(String dir) throws IOException {
        File bootModeFile = new File(dir, InstallerConstants.STARTUPMODE);
        try (OutputStream fos = new FileOutputStream(bootModeFile)) {
            Properties properties = new Properties();
            properties.setProperty(InstallerConstants.STARTUPMODE, InstallerConstants.STARTUPMODE_HIBERNATE);
            properties.store(fos, null);
            log.info("Set startup mode as hibernate under {} successful", dir);
        }
    }

    /*
     * Initialize disk
     * @param disk the disk to init
     * @return true if init disk successful, false if failed.
     */
    private boolean doInitDisk(String disk) {
    	boolean taskSuccess = true;
		try {
			if (InstallerOperation.initializeDisk(disk) != 0) {
				statusMsg = "failed!";
				taskSuccess = false;
			}
		} catch (Exception e) {
			statusMsg = "caught exception!";
			taskSuccess = false;
			log.error(String.format(TASK_INIT_DISK, disk) + "caught exception with: " + e.getMessage());
		}
		return taskSuccess;
    }
    
    /*
     * Install rootfs image on the disk
     * @param disk the disk to install image
     * @return true if install image successful, false if failed.
     */
    private boolean doInstallRootFsImage(String disk) {
    	boolean taskSuccess = true;
		try {
			if (InstallerOperation.installImage(disk, "") != 0) {
				statusMsg = "failed!";
				taskSuccess = false;
			}
		} catch (Exception e) {
			statusMsg = "caught exception!";
			taskSuccess = false;
			log.error(String.format(TASK_INSALL_IMG, disk) + "caught exception with: " + e.getMessage());
		}
		return taskSuccess;
    }
    
    /*
     * Install ovf-env property image
     * @return true if install image successful, false if failed.
     */
    private boolean doInstallOvfEnvPropertyImage() {
    	boolean taskSuccess = true;
		try {
			InstallerOperation.installISOImage(config);
		} catch (Exception e) {
			statusMsg = "caught exception!";
			taskSuccess = false;
			log.error(TASK_INSALL_OVFENV_IMG + "caught exception with: " + e.getMessage());
		}
		return taskSuccess;
    }
    
    /*
     * Class implements scan configuration over network.
     *
     */
    private class ScanTask implements Runnable {
    	private ScanTask() {
		// init nodes list in this cluster based on node count
		for (int i = 1; i <= config.getNodeCount(); i++) {
			nodes.add("vipr" + i);
		}

		// if it is redeploy mode, remove the alive nodes as well
		List<String> aliveNodes = config.getAliveNodes();
		if (config.isRedeployMode() && !aliveNodes.isEmpty()) {
			for (String aliveNode : aliveNodes) {
				nodes.remove(aliveNode);
			}
		}
    	}
    	
	@Override
		public void run() {
			if (nodes.size() == 1 && nodes.contains(config.getNodeId()) ) {
				log.info("Redeploying a single node, unnecessary to scan alive nodes.");
				allNodesDone = true;
				return;
			}

			while (true) {
				log.info("{} - Scanning for broadcasting configs from others", config.getNodeId());
				Set<Configuration> availableClusters = InstallerUtil.scanClusters(config.getHwConfig()
						.get(PropertyConstants.PROPERTY_KEY_NETIF), releaseVersion, config.getScenario());
				log.info("{} - found {} configurations", config.getNodeId(), availableClusters.size());
				if (availableClusters != null && !availableClusters.isEmpty()) {
					if (allNodesAreInstalled(availableClusters)) {
						allNodesDone = true;
						// stop scanning after all nodes in the cluster done local installation
						break;
					}
				}

				try {
					Thread.sleep(SCAN_BROADCAST_INTERVAL);
				} catch (InterruptedException e) {
					log.error("Caught an InterruptedException");
				}

			}
		}
		
		/*
		 * Check on scanned configuration with same cluster as local (currently based on
		 * IPv4 VIP). If all the nodes within the cluster broadcasted the configuration,
		 * all nodes are done with local install/config.
		 * @param configs set of available configuration scanned over network.
		 * @return true if all nodes are done, false otherwise
		 */
		private boolean allNodesAreInstalled(Set<Configuration> configs) {
			if (nodes.isEmpty()) {
				log.info("{} - all nodes in the cluster are done with local install", config.getNodeId());
				return true;
			}
			log.info("{} - Scan waiting for node(s) {} to finish local install and broadcast", 
					config.getNodeId(), nodes);
			boolean ret = false;
			for (Configuration c : configs) {
				log.debug("{} - scanned found config: {}", config.getNodeId(), c.toString());
				String vip = c.getNetworkVip();
				if (vip.equals(config.getNetworkVip())) {
					// the same cluster, remove its node id from list
					// if it is redeploy mode, remove the alive nodes from list
					List<String> aliveNodes = c.getAliveNodes();
					if (config.isRedeployMode() && !aliveNodes.isEmpty()){
						for (String aliveNode : aliveNodes) {
							nodes.remove(aliveNode);
						}
					} else {
						String node = c.getNodeId();
						if (nodes.contains(node)) {
							log.info("{} - found cluster with {}", config.getNodeId(),
									vip + "/" + node);
							nodes.remove(node);
						}
					}
				}
			}
			if (nodes.isEmpty()) {
				log.info("{} - all nodes in the cluster are done with local install", config.getNodeId());
				ret = true;
			}
			return ret;
		}
    }
    
    private void updateProgressStatusMessage(String status) {
    	confPanel.getProgress().setText(status);
    }
    
    private void updateProgressBarValue(int value) {
    	confPanel.getProgressBar().setValue(value);
    }
    
    private void popupErrorMsg(String[] errs) {
    	confPanel.getRoot().displayErrorMessage(errs);
    }
    
    private String getOperationType() {
    	String type;
    	if (config.isRedeployMode()) {
    		type = "re-deployment";
    	} else if (config.isConfigMode()) {
    		type = "configuration";
    	} else {
    		type = "install";
    	}
    	return type;
    }

    /**
     * Cluster Configuration Multicast Thread
     */
    private void startMulticastConf() {
        Thread multicastConfThread = new Thread(new Runnable() {
            public void run() {
                try {
                    log.info("Broadcasting configuration {} over network {}",
                            config.getNetworkVip() + "/" + config.getNodeId(),
                            config.getHwConfig().get(PropertyConstants.PROPERTY_KEY_NETIF));

                    // in redeploy case, add local node id to alive node list before broadcast
                    if (config.isRedeployMode() && config.getAliveNodes() != null) {
                        config.getAliveNodes().add(config.getNodeId());
                    }
                    if (!MulticastUtil.doBroadcast(releaseVersion, config, InstallerConstants.NORMAL_MULTICAST_TIMEOUT)) {
                        statusMsg = "caught exception!";
                        log.error(TASK_BROADCAST + statusMsg);
                        popupErrorMsg(new String[] {TASK_BROADCAST + statusMsg, ERR_LOG_MSG});
                    }
                } catch (Exception e) {
                    log.warn("multicast conf task threw", e);
                }
            }
        }, "conf multicast thread");

        multicastConfThread.start();
    }

}
