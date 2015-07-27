/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.util;


/**
 * Utility class to validate iSCSI port initiator port name formats. 
 */
public class iSCSIUtility {
    
    // Regular Expression to match an iSCSI IQN port name.
    private static final String IQN_PATTERN = "(iqn|IQN)\\.[0-9]{4}-[0-9]{2}\\.[A-Za-z][A-Za-z0-9\\-]*[A-Za-z0-9](.*)?";
    
    // Regular Expression to match an iSCSI EUI port name.
    private static final String EUI_PATTERN = "eui\\.[0-9A-Fa-f]{16}";

    /**
     * Validate the IQN port name.
     * 
     * @param portName The port name to validate.
     * 
     * @return true if it is valid, else false.
     */
    public static boolean isValidIQNPortName(String portName) {
        return portName.matches(IQN_PATTERN);
    }

    /**
     * Validate the EUI port name.
     * 
     * @param portName The port name to validate.
     * 
     * @return true if it is valid, else false.
     */
    public static boolean isValidEUIPortName(String portName) {
        return portName.matches(EUI_PATTERN);
    }
}
