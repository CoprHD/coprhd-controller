/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.util;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ExternalChangeProperties {
    protected static final Log _log = LogFactory.getLog(ExternalChangeProperties.class);
    private static final String BUNDLE_NAME = "external-change"; //$NON-NLS-1$

    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

    private ExternalChangeProperties() {
    }

    public static String getString(String key) {
        try {
            return RESOURCE_BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            _log.fatal("Could not locate Resource Bundle: " + key);
            return '!' + key + '!';
        }
    }

    public static String getMessage(String key, Object... arguments) {
        String message = ExternalChangeProperties.getString(key);
        return MessageFormat.format(message, arguments);
    }
}
