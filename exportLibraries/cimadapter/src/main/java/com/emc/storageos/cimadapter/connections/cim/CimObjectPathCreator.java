/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cimadapter.connections.cim;

import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;

public class CimObjectPathCreator {
    
    public static CIMObjectPath createInstance(String pObjectPath) {
        return new CIMObjectPath(pObjectPath);
    }
    
    public static CIMObjectPath createInstance(String pObjectName, String pNamespace) {
        return new CIMObjectPath(null, null, null, pNamespace, pObjectName, null);
    }
    
    public static CIMObjectPath createInstance(String pScheme, String pHost, String pPort, String pNamespace,
            String pObjectName, CIMProperty<?>[] pKeys) {
        return new CIMObjectPath(pScheme,pHost,pPort,pNamespace,pObjectName,pKeys);
    }
    
    public static CIMObjectPath createInstance(String pObjectName, String pNamespace,
             CIMProperty<?>[] pKeys) {
        return new CIMObjectPath(null, null, null, pNamespace, pObjectName, pKeys);
    }
}
