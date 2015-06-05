/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.util;

import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Simple wrapper around a resource bundle that provides some chaining and formatting support.
 * 
 * @author Jonny Miller
 */
public class Messages {
    private Messages parent;
    private ResourceBundle bundle;

    public Messages(Messages parent, String name) {
        this.parent = parent;
        this.bundle = ResourceBundle.getBundle(name);
    }

    public Messages(Messages parent, Class<?> clazz, String name) {
        this(parent, clazz.getPackage().getName() + "." + name.replace('/', '.'));
    }

    /**
     * Creates a messages bundle by full name.
     * 
     * @param name
     *        the full message bundle name.
     */
    public Messages(String name) {
        this((Messages) null, name);
    }

    /**
     * Creates a messages bundle within the package of the provided class.
     * 
     * @param clazz
     *        the class whose package is used as the base for the bundle.
     * @param name
     *        the name of the bundle.
     */
    public Messages(Class<?> clazz, String name) {
        this((Messages) null, clazz, name);
    }

    public String get(String key, Object... args) {
        String message = getString(key);
        if ((message != null) && (args != null && args.length > 0)) {
            message = String.format(message, args);
        }
        return message;
    }
    
    public Set<String> getKeySet() {
        return bundle.keySet();
    }

    private String getString(String key) {
        try {
            return bundle.getString(key);
        }
        catch (MissingResourceException e) {
            // try to find the key in the parent bundle if possible
            if (parent != null) {
                try {
                    return parent.getString(key);
                }
                catch (MissingResourceException parentE) {
                    // Fallthrough and rethrow the original exception
                }
            }
            throw e;
        }
    }
}
