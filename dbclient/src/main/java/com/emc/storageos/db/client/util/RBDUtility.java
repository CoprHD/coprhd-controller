/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

public final class RBDUtility {

    private RBDUtility() {
    }

    public static boolean isValidRBDPseudoPort(String element) {
        return element.trim().startsWith("rbd:");
    }
}
