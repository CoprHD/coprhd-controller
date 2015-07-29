/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

/**
 * InvalidLogin.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis2 version: 1.6.2  Built on : Apr 17, 2012 (05:33:49 IST)
 */

package com.emc.storageos.vasa;

public class InvalidLogin extends java.lang.Exception {

    private static final long serialVersionUID = 1348562968853L;

    private com.emc.storageos.vasa.VasaServiceStub.InvalidLoginE faultMessage;

    public InvalidLogin() {
        super("InvalidLogin");
    }

    public InvalidLogin(java.lang.String s) {
        super(s);
    }

    public InvalidLogin(java.lang.String s, java.lang.Throwable ex) {
        super(s, ex);
    }

    public InvalidLogin(java.lang.Throwable cause) {
        super(cause);
    }

    public void setFaultMessage(com.emc.storageos.vasa.VasaServiceStub.InvalidLoginE msg) {
        faultMessage = msg;
    }

    public com.emc.storageos.vasa.VasaServiceStub.InvalidLoginE getFaultMessage() {
        return faultMessage;
    }
}
