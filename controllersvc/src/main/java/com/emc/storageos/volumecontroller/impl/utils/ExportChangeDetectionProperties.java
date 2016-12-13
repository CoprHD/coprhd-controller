/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ExportChangeDetectionProperties {
    
    protected static final Log _log = LogFactory.getLog(ExportChangeDetectionProperties.class);
    private static final String BUNDLE_NAME = "exportchange-detection"; //$NON-NLS-1$
    
    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);
    
    private ExportChangeDetectionProperties() {
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
        String message = ExportChangeDetectionProperties.getString(key);
        return MessageFormat.format(message, arguments);
    }
    
}
