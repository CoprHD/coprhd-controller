/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

/**
 * StorageFault.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis2 version: 1.6.2  Built on : Apr 17, 2012 (05:33:49 IST)
 */

package com.emc.storageos.vasa;

public class StorageFault extends java.lang.Exception {

    private static final long serialVersionUID = 1348562968901L;

    private com.emc.storageos.vasa.VasaServiceStub.StorageFaultE faultMessage;

    public StorageFault() {
        super("StorageFault");
    }

    public StorageFault(java.lang.String s) {
        super(s);
    }

    public StorageFault(java.lang.String s, java.lang.Throwable ex) {
        super(s, ex);
    }

    public StorageFault(java.lang.Throwable cause) {
        super(cause);
    }

    public void setFaultMessage(com.emc.storageos.vasa.VasaServiceStub.StorageFaultE msg) {
        faultMessage = msg;
    }

    public com.emc.storageos.vasa.VasaServiceStub.StorageFaultE getFaultMessage() {
        return faultMessage;
    }
}
