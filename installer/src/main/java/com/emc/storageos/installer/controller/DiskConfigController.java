/**
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
package com.emc.storageos.installer.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import charvax.swing.event.ListSelectionEvent;
import charvax.swing.event.ListSelectionListener;

import com.emc.storageos.services.data.Configuration;
import com.emc.storageos.services.util.InstallerConstants;
import com.emc.storageos.services.util.InstallerOperation;
import com.emc.storageos.services.util.ServerProbe;
import com.emc.storageos.installer.widget.SelectListPanel;

/**
 * Class implements the control on the Disk selection page.
 *
 */
public class DiskConfigController implements IConfigPanelController {
	private static final Logger log = LoggerFactory.getLogger(DiskConfigController.class);
	private SelectListPanel panel;
	private Configuration config;
	private String diskInputStr;
	private String diskName;
	private String sizeStr;
	private String[] status;
	
	public DiskConfigController(Configuration config, SelectListPanel panel) {
		this.panel = panel;
		this.config = config;
		setupEventListener();
	}

	private void setupEventListener() {
		this.panel.addListSelectionListener(new SelectDiskListener());
	}

	/**
	 * Class implements action listener on disk selection.
	 *
	 */
	class SelectDiskListener implements ListSelectionListener {
		@Override
		public void valueChanged(ListSelectionEvent ie) {
			status = null;
			Object[] items = panel.getList().getSelectedValues();
			String s = "";
	        for (int i = 0; i < items.length; i++) {
	            if (i != 0)
	                s += ",";
	            s += (String) items[i];
	        }
	        diskInputStr = s;
	        if (diskInputStr != null && !diskInputStr.isEmpty()) {
	        	diskName = InstallerOperation.parseDiskString(diskInputStr);
	        	log.debug("Do validation on disk '{}'", diskName);
		        if (checkDiskCapacityForMinimumRequirement()) {
		        	checkDiskForViprPartitions();
		        }
	        }
		}
		
		private void checkDiskForViprPartitions() {
			if (ServerProbe.getInstance().hasViPRPartition(diskName)) {
				log.warn("Disk {} has ViPR partition already", diskName);
				String[] warningMsg = new String[] {
						String.format(InstallerConstants.WARN_MSG_DISK_HAS_VIPR_PARTITION_1, diskName),
						InstallerConstants.WARN_MSG_DISK_HAS_VIPR_PARTITION_2,
						InstallerConstants.WARN_MSG_DISK_HAS_VIPR_PARTITION_3};
				panel.getRoot().displayWarningMessage(warningMsg);
			}
		}

		private boolean checkDiskCapacityForMinimumRequirement() {
			sizeStr = ServerProbe.getInstance().getDiskCapacity(diskName);
			log.debug("Check disk {} and size {} for minimum size requirement", 
					diskName, sizeStr);
			if (!ServerProbe.getInstance().diskMetMiniumSizeReq(diskName)) {
				log.warn("Disk {} failed minimum size check", diskName);
				status = new String[] {InstallerConstants.ERROR_MSG_NOT_MEET_MIN_REQ_1, 
						InstallerConstants.ERROR_MSG_HW_NOT_SAME_2,
						InstallerConstants.ERROR_MSG_NOT_MEET_MIN_REQ_4};
				panel.getRoot().displayErrorMessage(status);
				return false;
			}
			return true;
		}
	}
	
	@Override
	public String[] configurationIsCompleted() {
		String msg = null;
		if (diskInputStr == null || diskInputStr.isEmpty()) {
			msg = InstallerConstants.DISK_CONFIG_WARN_MSG;
		} else {
			if (status != null && status.length != 0) {
				return status;
			} else {
				config.getHwConfig().put(InstallerConstants.PROPERTY_KEY_DISK, diskName);
				config.getHwConfig().put(InstallerConstants.PROPERTY_KEY_DISK_CAPACITY, sizeStr);
			}
		}
		return (msg == null) ? null : new String[] {msg};
	}
}
