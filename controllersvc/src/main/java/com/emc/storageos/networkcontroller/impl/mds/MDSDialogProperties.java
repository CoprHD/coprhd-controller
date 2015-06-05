/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.mds;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MDSDialogProperties {
    public static final int SLEEP_TIME_PER_RETRY = 5000;  // 5 seconds in milliseconds    
	protected static final Log _log = LogFactory.getLog(MDSDialogProperties.class);
	private static final String BUNDLE_NAME = "networksystem-mds-dialog"; //$NON-NLS-1$

	private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle
			.getBundle(BUNDLE_NAME);

	private MDSDialogProperties() {
	}

	public static String getString(String key) {
		try {
			return RESOURCE_BUNDLE.getString(key);
		} catch (MissingResourceException e) {
			_log.fatal("Could not locate Resource Bundle: " + key);
			return '!' + key + '!';
		}
	}
}
