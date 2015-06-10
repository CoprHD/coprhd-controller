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
package com.emc.storageos.installer.controller;

import charvax.swing.event.ListSelectionEvent;
import charvax.swing.event.ListSelectionListener;

import com.emc.storageos.services.data.Configuration;
import com.emc.storageos.services.util.InstallerConstants;
import com.emc.storageos.installer.widget.SelectListPanel;

/**
 * Class implements the control for Network Interface page.
 *
 */
public class NetworkInterfaceConfigController implements IConfigPanelController {
	
	private SelectListPanel netIfPanel;
	private Configuration config;
	private String netIf;
	
	public NetworkInterfaceConfigController(Configuration config, SelectListPanel netIfPanel) {
		this.netIfPanel = netIfPanel;
		this.config = config;
		setupEventListener();
	}

	private void setupEventListener() {
		this.netIfPanel.addListSelectionListener(new SelectNetworkIfsListener());
	}

	class SelectNetworkIfsListener implements ListSelectionListener {
		@Override
		public void valueChanged(ListSelectionEvent ie) {
			Object[] items = netIfPanel.getList().getSelectedValues();
			String s = "";
	        for (int i = 0; i < items.length; i++) {
	            if (i != 0)
	                s += ",";
	            s += (String) items[i];
	        }
	        netIf = s;
		}
	}

	@Override
	public String[] configurationIsCompleted() {
		String msg = null;
		if (netIf == null || netIf.isEmpty()) {
			msg = InstallerConstants.NETWORK_INT_CONFIG_WARN_MSG;
		} else {
			config.getHwConfig().put(InstallerConstants.PROPERTY_KEY_NETIF, netIf);
		}
		return (msg == null) ? null : new String[] {msg};
	}
}
