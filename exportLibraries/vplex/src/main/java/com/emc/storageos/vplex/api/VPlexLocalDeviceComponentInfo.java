/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Info for a component of a VPLEX local device.
 */
public class VPlexLocalDeviceComponentInfo extends VPlexResourceInfo {
    
    // Enumerates the local device component attributes we are interested
    // in and parse from the VPlex local device component response. There
    // must be a setter method for each attribute specified. The format of
    // the setter method must be as specified by the base class method
    // getAttributeSetterMethodName.
    public static enum ComponentAttribute {
        COMPONENT_TYPE("component-type");       
        
        // The VPlex name for the attribute.
        private String _name;
        
        /**
         * Constructor.
         * 
         * @param name The VPlex attribute name.
         */
        ComponentAttribute(String name) {
            _name = name;
        }
        
        /**
         * Getter for the VPlex name for the attribute.
         * 
         * @return The VPlex name for the attribute.
         */
        public String getAttributeName() {
             return _name;
        }
               
        /**
         * Returns the enum whose name matches the passed name, else null when
         * not found.
         * 
         * @param name The name to match.
         * 
         * @return The enum whose name matches the passed name, else null when
         *         not found.
         */
        public static ComponentAttribute valueOfAttribute(String name) {
            ComponentAttribute[] componentAtts = values();
            for (int i = 0; i < componentAtts.length; i++) {
                if (componentAtts[i].getAttributeName().equals(name)) {
                    return componentAtts[i];
                }
            }
            return null;
        }
    };
    
    // The component type.
    private String componentType;

    /**
     * Getter for the component type.
     * 
     * @return The component type.
     */
    public String getComponentType() {
        return componentType;
    }

    /**
     * Setter for the component type.
     * 
     * @param id The component type.
     */
    public void setComponentType(String type) {
        componentType = type;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAttributeFilters() {
        List<String> attFilters = new ArrayList<String>();
        for (ComponentAttribute att : ComponentAttribute.values()) {
            attFilters.add(att.getAttributeName());
        }
        return attFilters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("LocalDeviceComponentInfo ( ");
        str.append(super.toString());
        str.append(", componentType: " + componentType);
        str.append(" )");
        
        return str.toString();
    }
}
