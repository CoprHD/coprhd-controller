/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Info for a VPlex initiator port.
 */
public class VPlexInitiatorInfo extends VPlexResourceInfo {
    
    // Initiator types can be specified when an initiator
    // is registered.
    public enum Initiator_Type {
        HPUX("hpux"),
        SUN_VCS("sun-vcs"),
        AIX("aix"),
        RP("recoverpoint"),
        DEFAULT("default");

        // The VPlex name for the initiator type.
        private String _type;
        
        /**
         * Constructor.
         * 
         * @param type The VPlex initiator type.
         */
        Initiator_Type(String type) {
            _type = type;
        }
        
        /**
         * Getter for the VPlex initiator type.
         * 
         * @return The VPlex initiator type.
         */
        public String getType() {
             return _type;
        }
        
        /**
         * Returns the enum whose type matches the passed type, else null when
         * not found.
         * 
         * @param type The initiator type to match.
         * 
         * @return The enum whose type matches the passed type, else null when
         *         not found.
         */
        public static Initiator_Type valueOfType(String type) {
            Initiator_Type[] initiatorTypes = values();
            for (int i = 0; i < initiatorTypes.length; i++) {
                if (initiatorTypes[i].getType().equals(type)) {
                    return initiatorTypes[i];
                }
            }
            return null;
        }        
    };

    // Enumerates the initiator attributes we are interested in and
    // parse from the VPlex initiator response. There must be a setter
    // method for each attribute specified. The format of the setter
    // method must be as specified by the base class method
    // getAttributeSetterMethodName.
    public static enum InitiatorAttribute {
        PORT_WWN("port-wwn"),
        NODE_WWN("node-wwn");
        
        // The VPlex name for the attribute.
        private String _name;
        
        /**
         * Constructor.
         * 
         * @param name The VPlex attribute name.
         */
        InitiatorAttribute(String name) {
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
        public static InitiatorAttribute valueOfAttribute(String name) {
            InitiatorAttribute[] initiatorAtts = values();
            for (int i = 0; i < initiatorAtts.length; i++) {
                if (initiatorAtts[i].getAttributeName().equals(name)) {
                    return initiatorAtts[i];
                }
            }
            return null;
        }
    };
    
    // The port WWN id.
    private String portWwn;

    // The node WWN id.
    private String nodeWwn;
    
    // The registration name for the initiator. The name will be
    // the registration name if the port is registered.
    private String registrationName;
    
    // The initiator type for registration.
    private Initiator_Type initiatorType = Initiator_Type.DEFAULT;

    /**
     * Getter for the port WWN.
     * 
     * @return The port WWN.
     */
    public String getPortWwn() {
        return VPlexApiUtils.formatWWN(portWwn);
    }
    
    /**
     * Getter for the raw port WWN.
     * 
     * @return The port WWN as returned from the VPlex.
     */
    public String getPortWwnRaw() {
        return portWwn;
    }
    
    /**
     * Setter for the port WWN.
     * 
     * @param strVal The port WWN.
     */
    public void setPortWwn(String strVal) {
        portWwn = strVal;
    }
    
    /**
     * Getter for the node WWN.
     * 
     * @return The node WWN.
     */
    public String getNodeWwn() {
        return VPlexApiUtils.formatWWN(nodeWwn);
    }
    
    /**
     * Getter for the raw node WWN.
     * 
     * @return The node WWN as returned from the VPlex.
     */
    public String getNodeWwnRaw() {
        return nodeWwn;
    }    
    
    /**
     * Setter for the node WWN.
     * 
     * @param strVal The node WWN.
     */
    public void setNodeWwn(String strVal) {
        nodeWwn = strVal;
    }
    
    /**
     * Getter for the registration  name.
     * 
     * @return The registration name.
     */
    public String getRegistrationName() {
        return registrationName;
    }
    
    /**
     * Setter for the registration  name.
     * 
     * @param name The registration  name.
     */
    public void setRegistrationName(String name) {
        registrationName = name;
    }
    
    public void updateOnRegistration() {
        // When an initiator becomes registered, the context path for the 
        // initiator on the VPlex will now use the registration name, rather
        // than the unregistered name. Also, the initiator name becomes the
        // registration name. We update these in the object so they are 
        // accurate.
        String currentPath = getPath();
        setPath(currentPath.replace(getName(), registrationName));
        setName(registrationName);
    }
    
    /**
     * Getter for the initiator registration type.
     * 
     * @return The initiator registration type.
     */
    public Initiator_Type getInitiatorType() {
        return initiatorType;
    }
    
    /**
     * Setter for the initiator registration type.
     * 
     * @param type The initiator registration type.
     */
    public void setInitiatorType(Initiator_Type type) {
        initiatorType = type;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAttributeFilters() {
        List<String> attFilters = new ArrayList<String>();
        for (InitiatorAttribute att : InitiatorAttribute.values()) {
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
        str.append("InitiatorInfo ( ");
        str.append(super.toString());
        str.append(", portWwn: " + portWwn);
        str.append(", nodeWwn: " + nodeWwn);
        str.append(", registrationName: " + registrationName);
        str.append(" )");
        return str.toString();
    }
}
