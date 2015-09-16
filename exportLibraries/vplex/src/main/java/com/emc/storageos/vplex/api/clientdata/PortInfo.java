/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api.clientdata;

/**
 * Bean specifying port information. Is passed from the client to identify
 * ports, for example, to be included in a storage view.
 * 
 * NOTE: Port and Node WWNs should be specified in all caps and not contain
 * colons.
 */
public class PortInfo {

    // The port WWN
    private String _portWWN;

    // The node WWN is optional
    private String _nodeWWN;

    // The port name is optional.
    private String _name;

    // The port type is optional.
    private String _type;

    /**
     * Constructor.
     * 
     * @param portWWN The port WWN
     */
    public PortInfo(String portWWN) {
        _portWWN = portWWN;
    }

    /**
     * Constructor.
     * 
     * @param portWWN The port WWN is required.
     * @param nodeWWN The node WWN is optional.
     * @param name The port name is optional. Typically used to name an
     *            initiator port when it is registered with the VPlex.
     * @param type The port type is optional. Typically used for initiator ports
     *            to specify the initiator type when registering a initiator port.
     */
    public PortInfo(String portWWN, String nodeWWN, String name, String type) {
        _portWWN = portWWN;
        _nodeWWN = nodeWWN;
        _name = name;
        _type = type;
    }

    /**
     * Getter for the port WWN.
     * 
     * @return The port WWN.
     */
    public String getPortWWN() {
        return _portWWN;
    }

    /**
     * Setter for the port WWN.
     * 
     * @param portWWN The port WWN.
     */
    public void setPortWWN(String portWWN) {
        _portWWN = portWWN;
    }

    /**
     * Getter for the node WWN.
     * 
     * @return The node WWN.
     */
    public String getNodeWWN() {
        return _nodeWWN;
    }

    /**
     * Setter for the node WWN.
     * 
     * @param nodeWWN The node WWN.
     */
    public void setNodeWWN(String nodeWWN) {
        _nodeWWN = nodeWWN;
    }

    /**
     * Getter for the port name.
     * 
     * @return The port name.
     */
    public String getName() {
        return _name;
    }

    /**
     * Setter for the port name.
     * 
     * @param name The port name.
     */
    public void setName(String name) {
        _name = name;
    }

    /**
     * Getter for the port type.
     * 
     * @return The port type.
     */
    public String getType() {
        return _type;
    }

    /**
     * Setter for the port type.
     * 
     * @param type The port type.
     */
    public void setType(String type) {
        _type = type;
    }
}
