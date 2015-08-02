/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.imageservercontroller.impl;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ImageServerDialogProperties {

    protected static final Log _log = LogFactory.getLog(ImageServerDialogProperties.class);
    private static final String BUNDLE_NAME = "image-server-dialog"; //$NON-NLS-1$

    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

    private ImageServerDialogProperties() {
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
