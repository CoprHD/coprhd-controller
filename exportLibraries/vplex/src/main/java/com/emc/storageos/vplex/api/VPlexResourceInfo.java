/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vplex.api;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Info for a VPlex resource
 */
public class VPlexResourceInfo {
    
    // Resource type enum
    public static enum ResourceType {
        EXTENT("extent"),
        LOCAL_DEVICE("local-device"),
        STORAGE_VOLUME("storage-volume");

        // The VPlex type.
        private String _type;
        
        /**
         * Constructor.
         * 
         * @param type The VPlex type.
         */
        ResourceType(String type) {
            _type = type;
        }
        
        /**
         * Getter for the VPlex name for the attribute.
         * 
         * @return The VPlex name for the attribute.
         */
        public String getResourceType() {
             return _type;
        }
               
        /**
         * Returns the enum whose type matches the passed type, else null when
         * not found.
         * 
         * @param type The type to match.
         * 
         * @return The enum whose type matches the passed type, else null when
         *         not found.
         */
        public static ResourceType valueOfType(String type) {
            ResourceType[] types = values();
            for (int i = 0; i < types.length; i++) {
                if (types[i].getResourceType().equals(type)) {
                    return types[i];
                }
            }
            return null;
        }
    };
    
    // A logger reference.
    protected static Logger s_logger = LoggerFactory.getLogger(VPlexResourceInfo.class);
    
    // Resource type
    private String type;
    
    // Resource name
    private String name;
    
    // Resource path
    private String contextPath;
    
    /**
     * Getter for the resource type.
     * 
     * @return The resource type.
     */
    public String getType() {
        return type;
    }
    
    /**
     * Setter for the resource type.
     * 
     * @param strVal The resource type.
     */
    public void setType(String strVal) {
        type = strVal;
    }

    /**
     * Getter for the resource name.
     * 
     * @return The resource name.
     */
    public String getName() {
        return name;
    }
    
    /**
     * Setter for the resource name.
     * 
     * @param strVal The resource name.
     */
    public void setName(String strVal) {
        name = strVal;
    }
    
    /**
     * Getter for the resource path.
     * 
     * @return The resource path.
     */
    public String getPath() {
        return contextPath;
    }
    
    /**
     * Returns the names of the desired attributes for the resource. A resource
     * will always have a name and a type. These are the names of additional
     * attributes for resources of this type. An empty list or null means that
     * all available attributes are set. Derived classes can override to specify
     * the specific attributes that should be set.
     * 
     * @return A list of attribute names.
     */
    public List<String> getAttributeFilters() {
        return new ArrayList<String>();
    }

    /**
     * Setter for the resource path.
     * 
     * @param path The resource path.
     */
    public void setPath(String path) {
        contextPath = path;
    }
    
    public String getAttributeSetterMethodName(String attributeName) {
        StringBuilder methodBuilder = new StringBuilder("set");
        StringTokenizer tokenizer = new StringTokenizer(attributeName, "-");
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            String startsWith = token.substring(0, 1);
            String startsWithUC = startsWith.toUpperCase();
            methodBuilder.append(token.replaceFirst(startsWith, startsWithUC));
        }
        
        return methodBuilder.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("name: " + name);
        str.append(", type: " + type);
        str.append(", contextPath: " + contextPath);
        return str.toString();
    }
}
