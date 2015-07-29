/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

/**
 * InvalidArgument.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis2 version: 1.6.2  Built on : Apr 17, 2012 (05:33:49 IST)
 */

package com.emc.storageos.vasa;

public class InvalidArgument extends java.lang.Exception {

    private static final long serialVersionUID = 1348562968837L;

    private com.emc.storageos.vasa.VasaServiceStub.InvalidArgumentE faultMessage;

    public InvalidArgument() {
        super("InvalidArgument");
    }

    public InvalidArgument(java.lang.String s) {
        super(s);
    }

    public InvalidArgument(java.lang.String s, java.lang.Throwable ex) {
        super(s, ex);
    }

    public InvalidArgument(java.lang.Throwable cause) {
        super(cause);
    }

    public void setFaultMessage(com.emc.storageos.vasa.VasaServiceStub.InvalidArgumentE msg) {
        faultMessage = msg;
    }

    public com.emc.storageos.vasa.VasaServiceStub.InvalidArgumentE getFaultMessage() {
        return faultMessage;
    }
}
