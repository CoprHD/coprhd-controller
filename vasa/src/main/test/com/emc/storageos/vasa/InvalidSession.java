/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

/**
 * InvalidSession.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis2 version: 1.6.2  Built on : Apr 17, 2012 (05:33:49 IST)
 */

package com.emc.storageos.vasa;

public class InvalidSession extends java.lang.Exception{

    private static final long serialVersionUID = 1348562968883L;
    
    private com.emc.storageos.vasa.VasaServiceStub.InvalidSessionE faultMessage;

    
        public InvalidSession() {
            super("InvalidSession");
        }

        public InvalidSession(java.lang.String s) {
           super(s);
        }

        public InvalidSession(java.lang.String s, java.lang.Throwable ex) {
          super(s, ex);
        }

        public InvalidSession(java.lang.Throwable cause) {
            super(cause);
        }
    

    public void setFaultMessage(com.emc.storageos.vasa.VasaServiceStub.InvalidSessionE msg){
       faultMessage = msg;
    }
    
    public com.emc.storageos.vasa.VasaServiceStub.InvalidSessionE getFaultMessage(){
       return faultMessage;
    }
}
    